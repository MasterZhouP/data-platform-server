package com.ruoyi.integration.reference.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.ruoyi.integration.reference.model.ReferenceException;
import com.ruoyi.integration.reference.model.ReferenceTask;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterUtils;
import org.springframework.jdbc.core.namedparam.ParsedSql;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.LongFunction;

/** A SQL Server reference query engine. SQL is shipped as a trusted classpath resource. */
@Component
public class ReferenceEngine {
    private static final long BUDGET_MS = 10_000;
    // Bound both threads and admission: an unresponsive driver cannot grow an unbounded queue.
    private static final ExecutorService WORKERS = new ThreadPoolExecutor(0, 8, 30, TimeUnit.SECONDS,
        new SynchronousQueue<>(), r -> { Thread t = new Thread(r, "reference-query"); t.setDaemon(true); return t; });
    private static final Set<String> OPERATORS = Set.of("eq", "ne", "gt", "ge", "lt", "le", "contains",
        "startsWith", "endsWith", "between", "in", "isNull", "isNotNull");
    private final ReferenceDataSources sources;
    private final ObjectMapper mapper;

    public ReferenceEngine(ReferenceDataSources sources, ObjectMapper mapper) { this.sources = sources; this.mapper = mapper; }

    public void validate(ReferenceTask task, JsonNode request) { plan(task, request); }

    public ObjectNode query(ReferenceTask task, JsonNode request) {
        Plan plan = plan(task, request);
        return timed(deadline -> execute(task, plan, deadline));
    }

    /** Returns a candidate metadata document; this operation does not publish a new version. */
    public ObjectNode inspect(ReferenceTask task) {
        return timed(deadline -> {
            ObjectNode candidate = task.metadata().deepCopy();
            String sql = sql(task);
            try (Connection connection = connection(task)) {
                for (JsonNode set : candidate.path("resultSets")) {
                    List<Bind> parameters = new ArrayList<>();
                    String base = baseSql(sql, set, mapper.createObjectNode(), true, parameters);
                    {
                        List<Column> columns = inspectColumns(connection, base, parameters, deadline);
                        Map<String, JsonNode> previous = fields(set, false);
                        int nextOrder = previous.values().stream().mapToInt(field -> field.path("order").asInt()).max().orElse(0);
                        ArrayNode detected = mapper.createArrayNode();
                        for (int i = 0; i < columns.size(); i++) {
                            Column column = columns.get(i);
                            JsonNode old = previous.get(column.name);
                            ObjectNode field = old == null ? mapper.createObjectNode() : ((ObjectNode) old).deepCopy();
                            if (column.type.equals("NULL") && old == null) throw schema();
                            String type = column.type.equals("NULL") ? old.path("dataType").asText() : column.type;
                            field.put("name", column.name).put("dataType", type).put("nullable", column.nullable);
                            if (old == null) {
                                field.put("label", column.name).put("order", ++nextOrder).put("sortable", true);
                                field.putArray("filterOperators");
                            }
                            detected.add(field);
                        }
                        ((ObjectNode) set).set("fields", detected);
                    }
                    remaining(deadline);
                }
                return candidate;
            } catch (SQLException e) { throw databaseError(e); }
        });
    }

    private ObjectNode execute(ReferenceTask task, Plan plan, long deadline) {
        String from = " FROM (" + plan.base + ") ref_source" + plan.where;
        try (Connection connection = connection(task)) {
            // Inspect the unprojected SQL first, including for zero results and pages beyond the end.
            verifySchema(inspectColumns(connection, plan.base, plan.baseBindings, deadline), plan.fields);
            long total;
            try (PreparedStatement statement = statement(connection, "SELECT COUNT_BIG(*)" + from, plan.bindings, deadline);
                 ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw internal();
                total = result.getLong(1);
                if (total < 0 || total > 9_007_199_254_740_991L) throw internal();
            }
            List<Bind> pageBindings = new ArrayList<>(plan.bindings);
            pageBindings.add(new Bind((long) (plan.pageNum - 1) * plan.pageSize, Types.BIGINT));
            pageBindings.add(new Bind(plan.pageSize, Types.INTEGER));
            ArrayNode rows = mapper.createArrayNode();
            String paged = "SELECT *" + from + " ORDER BY " + plan.order + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
            try (PreparedStatement statement = statement(connection, paged, pageBindings, deadline);
                 ResultSet result = statement.executeQuery()) {
                List<Column> columns = columns(result.getMetaData());
                verifySchema(columns, plan.fields);
                while (result.next()) {
                    remaining(deadline);
                    ObjectNode row = mapper.createObjectNode();
                    for (int index = 1; index <= columns.size(); index++) {
                        Column column = columns.get(index - 1);
                        String cell = cell(result, index, column.type);
                        if (cell == null && !plan.fields.get(column.name).path("nullable").asBoolean()) throw schema();
                        if (cell == null) row.putNull(column.name); else row.put(column.name, cell);
                    }
                    rows.add(row);
                }
            }
            remaining(deadline);
            ObjectNode response = mapper.createObjectNode().put("taskCode", task.taskCode())
                .put("resultSetCode", plan.resultSetCode).put("metadataVersion", task.metadata().path("metadataVersion").asText())
                .put("total", total).put("pageNum", plan.pageNum).put("pageSize", plan.pageSize)
                .put("totalPages", total / plan.pageSize + (total % plan.pageSize == 0 ? 0 : 1));
            response.set("rows", rows);
            return response;
        } catch (SQLException e) { throw databaseError(e); }
    }

    private Plan plan(ReferenceTask task, JsonNode request) {
        if (!task.enabled()) throw new ReferenceException("TASK_DISABLED", 409, "参照任务已停用");
        keys(request, Set.of("resultSetCode", "metadataVersion", "pageNum", "pageSize", "filter", "sort", "parameters", "context"));
        String code = text(request.get("resultSetCode"), 1, 128);
        String version = text(request.get("metadataVersion"), 1, 128);
        if (!version.equals(task.metadata().path("metadataVersion").asText()))
            throw new ReferenceException("METADATA_VERSION_MISMATCH", 409, "参照配置已更新，请刷新配置后重试");
        JsonNode set = null;
        for (JsonNode candidate : task.metadata().path("resultSets")) if (code.equals(candidate.path("resultSetCode").asText())) set = candidate;
        if (set == null) throw new ReferenceException("RESULT_SET_NOT_FOUND", 404, "参照结果集不存在");
        int pageNum = integer(request, "pageNum", 1, 100000);
        int pageSize = integer(request, "pageSize", 200, 200);
        if (request.has("context")) {
            JsonNode context = request.get("context");
            keys(context, Set.of("formId", "masterId", "summaryId"));
            context.forEach(value -> { if (!value.isNull()) text(value, 1, 128); });
        }
        Map<String, JsonNode> fields = fields(set, true);
        JsonNode params = request.has("parameters") ? request.get("parameters") : mapper.createObjectNode();
        if (!params.isObject()) throw invalid();
        List<Bind> baseBindings = new ArrayList<>();
        String base = baseSql(sql(task), set, params, false, baseBindings);
        List<Bind> bindings = new ArrayList<>(baseBindings);
        String where = request.has("filter") ? " WHERE " + group(request.get("filter"), fields, bindings, 1, new int[]{0}) : "";
        JsonNode sort = request.has("sort") ? request.get("sort") : mapper.createArrayNode();
        if (!sort.isArray()) throw invalid();
        if (sort.isEmpty()) sort = set.path("defaults").path("sort");
        if (!sort.isArray() || sort.size() > 5) throw sortError();
        LinkedHashMap<String, String> order = new LinkedHashMap<>();
        for (JsonNode item : sort) {
            keys(item, Set.of("field", "direction"));
            String name = text(item.get("field"), 1, 128);
            String direction = text(item.get("direction"), 1, 4);
            JsonNode field = fields.get(name);
            if (field == null || !field.path("sortable").asBoolean() || !Set.of("ASC", "DESC").contains(direction)
                || order.putIfAbsent(name, direction) != null) throw sortError();
        }
        // Hidden fields (e.g. conversion rate) are essential ties: a business code need not be unique.
        fields.keySet().forEach(name -> order.putIfAbsent(name, "ASC"));
        StringJoiner ordering = new StringJoiner(", ");
        order.forEach((name, direction) -> ordering.add(quote(name) + " " + direction));
        return new Plan(code, pageNum, pageSize, fields, base, baseBindings, bindings, where, ordering.toString());
    }

    private String group(JsonNode node, Map<String, JsonNode> fields, List<Bind> bindings, int depth, int[] leaves) {
        keys(node, Set.of("logic", "conditions"));
        String logic = text(node.get("logic"), 2, 3);
        JsonNode conditions = node.get("conditions");
        if (depth > 3 || !Set.of("AND", "OR").contains(logic) || conditions == null || !conditions.isArray()
            || conditions.isEmpty() || conditions.size() > 20) throw invalid();
        StringJoiner sql = new StringJoiner(" " + logic + " ", "(", ")");
        for (JsonNode condition : conditions) {
            if (condition.has("logic") || condition.has("conditions")) sql.add(group(condition, fields, bindings, depth + 1, leaves));
            else {
                if (++leaves[0] > 20) throw invalid();
                sql.add(condition(condition, fields, bindings));
            }
        }
        return sql.toString();
    }

    private String condition(JsonNode node, Map<String, JsonNode> fields, List<Bind> bindings) {
        keys(node, Set.of("field", "operator", "values"));
        String name = text(node.get("field"), 1, 128);
        String operator = text(node.get("operator"), 1, 20);
        JsonNode field = fields.get(name);
        if (field == null || !OPERATORS.contains(operator) || !contains(field.path("filterOperators"), operator))
            throw new ReferenceException("FILTER_NOT_ALLOWED", 400, "筛选字段或操作符未获允许");
        JsonNode values = node.get("values");
        if (values == null || !values.isArray()) throw invalid();
        int expected = switch (operator) { case "isNull", "isNotNull" -> 0; case "between" -> 2; case "in" -> -1; default -> 1; };
        if ((expected >= 0 && values.size() != expected) || (expected == -1 && (values.isEmpty() || values.size() > 100))) throw invalid();
        String column = quote(name);
        if (operator.equals("isNull")) return column + " IS NULL";
        if (operator.equals("isNotNull")) return column + " IS NOT NULL";
        String type = field.path("dataType").asText();
        boolean like = Set.of("contains", "startsWith", "endsWith").contains(operator);
        List<Bind> typed = new ArrayList<>();
        for (JsonNode value : values) {
            String raw = text(value, 0, 1000);
            if (like) {
                if (!type.equals("STRING") || raw.isEmpty()) throw invalid();
                raw = raw.replace("!", "!!").replace("%", "!%").replace("_", "!_").replace("[", "![");
                raw = (operator.equals("startsWith") ? "" : "%") + raw + (operator.equals("endsWith") ? "" : "%");
            }
            typed.add(bind(type, raw));
        }
        // String ordering is defined by the source collation, not Java Unicode ordering.
        if (operator.equals("between") && !type.equals("STRING") && compare(typed.get(0).value, typed.get(1).value) > 0) throw invalid();
        bindings.addAll(typed);
        return switch (operator) {
            case "eq" -> column + " = ?"; case "ne" -> column + " <> ?";
            case "gt" -> column + " > ?"; case "ge" -> column + " >= ?";
            case "lt" -> column + " < ?"; case "le" -> column + " <= ?";
            case "between" -> column + " BETWEEN ? AND ?";
            case "in" -> column + " IN (" + String.join(",", Collections.nCopies(typed.size(), "?")) + ")";
            default -> column + " LIKE ? ESCAPE '!'";
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private int compare(Object lower, Object upper) { return ((Comparable) lower).compareTo(upper); }

    private String baseSql(String sql, JsonNode set, JsonNode provided, boolean probe, List<Bind> bindings) {
        Map<String, JsonNode> declared = new LinkedHashMap<>();
        set.path("parameters").forEach(parameter -> declared.put(parameter.path("name").asText(), parameter));
        provided.fieldNames().forEachRemaining(name -> {
            if (!declared.containsKey(name)) throw new ReferenceException("PARAMETER_NOT_ALLOWED", 400, "参数未获允许");
        });
        MapSqlParameterSource values = new MapSqlParameterSource();
        declared.forEach((name, definition) -> {
            JsonNode value = provided.get(name);
            if (!probe && definition.path("required").asBoolean() && value == null) throw invalid();
            if (!probe && value != null && value.isNull() && !definition.path("nullable").asBoolean()) throw invalid();
            Bind binding = bind(definition.path("dataType").asText(), value == null || value.isNull() ? null : text(value, 0, Integer.MAX_VALUE));
            values.addValue(name, binding.value, binding.sqlType);
        });
        try {
            ParsedSql parsed = NamedParameterUtils.parseSqlStatement(sql);
            String prepared = NamedParameterUtils.substituteNamedParameters(parsed, values);
            Object[] objects = NamedParameterUtils.buildValueArray(parsed, values, null);
            int[] types = NamedParameterUtils.buildSqlTypeArray(parsed, values);
            for (int index = 0; index < objects.length; index++) {
                Object value = objects[index];
                if (value instanceof org.springframework.jdbc.core.SqlParameterValue parameter) value = parameter.getValue();
                bindings.add(new Bind(value, types[index]));
            }
            return prepared;
        } catch (org.springframework.dao.DataAccessException e) { throw internal(); }
    }

    private Bind bind(String type, String raw) {
        int sqlType = switch (type) {
            case "STRING" -> Types.NVARCHAR; case "INTEGER" -> Types.NUMERIC; case "DECIMAL" -> Types.DECIMAL;
            case "BOOLEAN" -> Types.BOOLEAN; case "DATE" -> Types.DATE; case "DATETIME" -> Types.TIMESTAMP;
            default -> throw schema();
        };
        if (raw == null) return new Bind(null, sqlType);
        try {
            Object value = switch (type) {
                case "STRING" -> raw;
                case "INTEGER" -> { if (!raw.matches("-?[0-9]+")) throw invalid(); yield new BigDecimal(new BigInteger(raw)); }
                case "DECIMAL" -> { if (!raw.matches("-?[0-9]+(?:\\.[0-9]+)?")) throw invalid(); yield new BigDecimal(raw); }
                case "BOOLEAN" -> { if (!Set.of("true", "false").contains(raw)) throw invalid(); yield Boolean.valueOf(raw); }
                case "DATE" -> { if (!raw.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw invalid(); yield LocalDate.parse(raw); }
                case "DATETIME" -> { if (!raw.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,9})?")) throw invalid(); yield LocalDateTime.parse(raw); }
                default -> throw schema();
            };
            return new Bind(value, sqlType);
        } catch (IllegalArgumentException | java.time.DateTimeException e) { throw invalid(); }
    }

    private String sql(ReferenceTask task) {
        if (task.sqlResource() == null || !task.sqlResource().matches("integration/reference/[A-Za-z0-9_-]+\\.sql")) throw invalid();
        try (var stream = new ClassPathResource(task.sqlResource()).getInputStream()) {
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
            if (sql.contains("${") || sql.isEmpty()) throw internal();
            if (sql.endsWith(";")) sql = sql.substring(0, sql.length() - 1);
            return sql;
        } catch (IOException e) { throw internal(); }
    }

    private Map<String, JsonNode> fields(JsonNode set, boolean required) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        for (JsonNode field : set.path("fields")) {
            String name = field.path("name").asText();
            if (name.isEmpty() || name.length() > 128 || name.chars().anyMatch(Character::isISOControl)
                || result.putIfAbsent(name, field) != null) throw schema();
        }
        if (required && result.isEmpty()) throw schema();
        return result;
    }

    private List<Column> columns(ResultSetMetaData metadata) throws SQLException {
        List<Column> result = new ArrayList<>();
        Set<String> labels = new HashSet<>();
        for (int index = 1; index <= metadata.getColumnCount(); index++) {
            String name = metadata.getColumnLabel(index);
            if (name == null || name.isBlank() || name.length() > 128 || !labels.add(name)) throw schema();
            result.add(new Column(name, logicalType(metadata.getColumnType(index)), metadata.isNullable(index) != ResultSetMetaData.columnNoNulls));
        }
        if (result.isEmpty()) throw schema();
        return result;
    }

    private List<Column> inspectColumns(Connection connection, String base, List<Bind> bindings, long deadline) throws SQLException {
        // Inspect before introducing a derived table: databases reject duplicate labels on wrapping,
        // which would otherwise obscure the actionable schema-mismatch error.
        try (PreparedStatement statement = statement(connection, base, bindings, deadline)) {
            ResultSetMetaData metadata = statement.getMetaData();
            if (metadata != null) return columns(metadata);
            statement.setMaxRows(1);
            try (ResultSet result = statement.executeQuery()) { return columns(result.getMetaData()); }
        }
    }

    private String logicalType(int type) {
        return switch (type) {
            case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR -> "STRING";
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT -> "INTEGER";
            case Types.NUMERIC, Types.DECIMAL, Types.FLOAT, Types.REAL, Types.DOUBLE -> "DECIMAL";
            case Types.BOOLEAN, Types.BIT -> "BOOLEAN";
            case Types.DATE -> "DATE";
            case Types.TIMESTAMP -> "DATETIME";
            case Types.NULL -> "NULL";
            default -> throw schema();
        };
    }

    private void verifySchema(List<Column> columns, Map<String, JsonNode> fields) {
        if (columns.size() != fields.size()) throw schema();
        for (Column column : columns) {
            JsonNode expected = fields.get(column.name);
            if (expected == null || (!column.type.equals("NULL") && !column.type.equals(expected.path("dataType").asText()))) throw schema();
        }
    }

    private String cell(ResultSet result, int index, String type) throws SQLException {
        return switch (type) {
            case "DECIMAL" -> { BigDecimal value = result.getBigDecimal(index); yield value == null ? null : value.toPlainString(); }
            case "INTEGER" -> { BigDecimal value = result.getBigDecimal(index); yield value == null ? null : value.toBigIntegerExact().toString(); }
            case "BOOLEAN" -> { boolean value = result.getBoolean(index); yield result.wasNull() ? null : Boolean.toString(value); }
            case "DATE" -> { LocalDate value = result.getObject(index, LocalDate.class); yield value == null ? null : value.toString(); }
            case "DATETIME" -> {
                LocalDateTime value = result.getObject(index, LocalDateTime.class);
                yield value == null ? null : value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }
            case "NULL" -> null;
            default -> result.getString(index);
        };
    }

    private Connection connection(ReferenceTask task) throws SQLException {
        DataSource source = sources.get(task.datasourceKey());
        if (source == null) throw unavailable();
        try { return source.getConnection(); }
        catch (SQLTimeoutException e) { throw timeout(); }
        catch (SQLException e) { throw unavailable(); }
    }

    private PreparedStatement statement(Connection connection, String sql, List<Bind> bindings, long deadline) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        try {
            statement.setQueryTimeout((int) Math.max(1, (remaining(deadline) + 999) / 1000));
            for (int index = 0; index < bindings.size(); index++) {
                Bind binding = bindings.get(index);
                if (binding.value == null) statement.setNull(index + 1, binding.sqlType);
                else if (binding.value instanceof String value) statement.setNString(index + 1, value);
                else if (binding.value instanceof LocalDate value) statement.setDate(index + 1, java.sql.Date.valueOf(value));
                else if (binding.value instanceof LocalDateTime value) statement.setTimestamp(index + 1, Timestamp.valueOf(value));
                else statement.setObject(index + 1, binding.value, binding.sqlType);
            }
            return statement;
        } catch (RuntimeException | SQLException e) { statement.close(); throw e; }
    }

    private ObjectNode timed(LongFunction<ObjectNode> action) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(BUDGET_MS);
        Future<ObjectNode> future;
        try { future = WORKERS.submit(() -> action.apply(deadline)); }
        catch (RejectedExecutionException e) { throw new ReferenceException("SERVICE_UNAVAILABLE", 503, "参照服务繁忙，请稍后重试"); }
        try { return future.get(remaining(deadline), TimeUnit.MILLISECONDS); }
        catch (TimeoutException e) { future.cancel(true); throw timeout(); }
        catch (InterruptedException e) { future.cancel(true); Thread.currentThread().interrupt(); throw new ReferenceException("SERVICE_UNAVAILABLE", 503, "参照服务暂不可用"); }
        catch (ExecutionException e) {
            if (e.getCause() instanceof ReferenceException reference) throw reference;
            if (e.getCause() instanceof Error error) throw error;
            throw internal();
        }
    }

    private long remaining(long deadline) {
        long millis = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
        if (millis <= 0 || Thread.currentThread().isInterrupted()) throw timeout();
        return millis;
    }
    private void keys(JsonNode node, Set<String> allowed) {
        if (node == null || !node.isObject()) throw invalid();
        node.fieldNames().forEachRemaining(key -> { if (!allowed.contains(key)) throw invalid(); });
    }
    private String text(JsonNode node, int min, int max) {
        if (node == null || !node.isTextual() || node.textValue().length() < min || node.textValue().length() > max) throw invalid();
        return node.textValue();
    }
    private int integer(JsonNode node, String name, int fallback, int max) {
        if (!node.has(name)) return fallback;
        JsonNode value = node.get(name);
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 1 || value.intValue() > max) throw invalid();
        return value.intValue();
    }
    private boolean contains(JsonNode values, String value) { for (JsonNode node : values) if (value.equals(node.asText())) return true; return false; }
    private String quote(String name) { return "ref_source.[" + name.replace("]", "]]") + "]"; }
    private ReferenceException databaseError(SQLException e) {
        if (e instanceof SQLTimeoutException || "HYT00".equals(e.getSQLState()) || "HYT01".equals(e.getSQLState())) return timeout();
        if (e instanceof SQLTransientConnectionException || e instanceof SQLNonTransientConnectionException || (e.getSQLState() != null && e.getSQLState().startsWith("08"))) return unavailable();
        return internal();
    }
    private ReferenceException invalid() { return new ReferenceException("INVALID_ARGUMENT", 400, "参照请求参数不合法"); }
    private ReferenceException sortError() { return new ReferenceException("SORT_NOT_ALLOWED", 400, "排序字段或方向未获允许"); }
    private ReferenceException schema() { return new ReferenceException("RESULT_SCHEMA_MISMATCH", 409, "查询结果与已发布字段不一致，请重新核对并发布元数据"); }
    private ReferenceException unavailable() { return new ReferenceException("DATASOURCE_UNAVAILABLE", 503, "参照数据源暂不可用"); }
    private ReferenceException timeout() { return new ReferenceException("QUERY_TIMEOUT", 504, "参照查询超时，请缩小筛选范围或稍后重试"); }
    private ReferenceException internal() { return new ReferenceException("INTERNAL_ERROR", 500, "参照查询执行失败，请联系管理员"); }
    private record Bind(Object value, int sqlType) { }
    private record Column(String name, String type, boolean nullable) { }
    private record Plan(String resultSetCode, int pageNum, int pageSize, Map<String, JsonNode> fields,
                        String base, List<Bind> baseBindings, List<Bind> bindings, String where, String order) { }
}
