package com.ruoyi.integration.u8tooa.config;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.integration.oatou8.config.ReadQueryStep;
import com.ruoyi.integration.oatou8.config.ResultCardinality;
import com.ruoyi.integration.oatou8.config.TaskConfigException;

/** Validates the bounded U8-to-OA configuration language before it becomes a revision. */
public class U8ToOaTaskConfigValidator
{
    private static final Pattern CODE = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,49}");
    private static final Pattern DATASOURCE = Pattern.compile("[a-z][a-z0-9_-]{0,99}");
    private static final Pattern WRITE_SQL = Pattern.compile(
            "(?is)\\b(UPDATE|INSERT|DELETE|MERGE|REPLACE|CALL|EXEC|CREATE|ALTER|DROP|TRUNCATE|GRANT|REVOKE|INTO)\\b");
    private static final Pattern COMMENTS = Pattern.compile("(?s)/\\*.*?\\*/|--[^\\r\\n]*");
    private static final Pattern NAMED_PARAMETER = Pattern.compile(":([A-Za-z][A-Za-z0-9_]*)");
    private final ObjectMapper json;
    private final Predicate<String> readableDatasource;

    public U8ToOaTaskConfigValidator(ObjectMapper json)
    {
        this(json, DATASOURCE.asMatchPredicate());
    }

    public U8ToOaTaskConfigValidator(ObjectMapper json, Set<String> readableDatasourceKeys)
    {
        this(json, readableDatasourceKeys::contains);
    }

    private U8ToOaTaskConfigValidator(ObjectMapper json, Predicate<String> readableDatasource)
    {
        this.json = json;
        this.readableDatasource = readableDatasource;
    }

    public U8ToOaTaskConfig parseAndValidate(JsonNode source)
    {
        objectWithOnly(source, Set.of("constants", "dataSteps", "oa", "sync"), "INVALID_U8_TO_OA_CONFIG");
        Map<String, String> constants = textMap(source.path("constants"), "INVALID_CONSTANTS");
        List<ReadQueryStep> dataSteps = readDataSteps(source.path("dataSteps"), constants);
        OaProcessRequest oa = readOa(source.path("oa"), constants, dataSteps);
        IncrementalSyncConfig sync = readSync(source.path("sync"));
        return new U8ToOaTaskConfig(Map.copyOf(constants), List.copyOf(dataSteps), oa, sync);
    }

    private List<ReadQueryStep> readDataSteps(JsonNode source, Map<String, String> constants)
    {
        if (!source.isArray())
        {
            throw error("INVALID_DATA_STEPS", "数据准备步骤必须是数组");
        }
        List<ReadQueryStep> steps = new ArrayList<>();
        Set<String> codes = new LinkedHashSet<>();
        int expectedOrder = 1;
        for (JsonNode node : source)
        {
            objectWithOnly(node, Set.of("code", "order", "datasourceKey", "sql", "cardinality", "parameterBindings"),
                    "INVALID_DATA_STEP");
            String code = code(node.path("code"), "INVALID_DATA_STEP");
            if (!codes.add(code))
            {
                throw error("DUPLICATE_STEP_CODE", "数据准备步骤编码重复");
            }
            int order = integer(node.path("order"), 1, 100, "INVALID_DATA_STEP");
            if (order != expectedOrder++)
            {
                throw error("STEP_ORDER_INVALID", "数据准备步骤顺序必须从 1 连续递增");
            }
            String datasourceKey = datasource(node.path("datasourceKey"));
            String sql = readOnlySql(node.path("sql"), "INVALID_SQL");
            ResultCardinality cardinality = cardinality(node.path("cardinality"));
            Map<String, String> bindings = textMap(node.path("parameterBindings"), "INVALID_PARAMETER_BINDINGS");
            steps.add(new ReadQueryStep(code, order, datasourceKey, sql, cardinality, Map.copyOf(bindings)));
        }
        Map<String, ResultCardinality> all = new LinkedHashMap<>();
        steps.forEach(step -> all.put(step.code(), step.cardinality()));
        for (ReadQueryStep step : steps)
        {
            for (String variable : step.parameterBindings().values())
            {
                validateVariable(variable, constants, all, step.order());
            }
        }
        return steps;
    }

    private OaProcessRequest readOa(JsonNode source, Map<String, String> constants, List<ReadQueryStep> dataSteps)
    {
        objectWithOnly(source, Set.of("u8IdVariable", "payloadTemplate"), "INVALID_OA_REQUEST");
        String u8IdVariable = text(source.path("u8IdVariable"), 1, 200, "INVALID_OA_REQUEST");
        validateScalarVariable(u8IdVariable, constants, dataSteps);
        JsonNode template = source.path("payloadTemplate");
        if (!template.isObject())
        {
            throw error("INVALID_OA_REQUEST", "OA请求模板必须是JSON对象");
        }
        validateTemplateVariables(template, constants, dataSteps);
        return new OaProcessRequest(u8IdVariable, template.deepCopy());
    }

    private IncrementalSyncConfig readSync(JsonNode source)
    {
        objectWithOnly(source, Set.of("datasourceKey", "upperBoundSql", "createSql", "updateSql", "deleteSql",
                "initialCursor", "overlapMinutes", "cronExpression"), "INVALID_SYNC_CONFIG");
        String datasourceKey = datasource(source.path("datasourceKey"));
        String upperBound = readOnlySql(source.path("upperBoundSql"), "INVALID_SYNC_CONFIG");
        if (upperBound.matches("(?s).*:[A-Za-z][A-Za-z0-9_]*.*"))
        {
            throw error("SYNC_UPPER_BOUND_INVALID", "上界时间查询不能包含参数");
        }
        String create = changeSql(source.path("createSql"));
        String update = changeSql(source.path("updateSql"));
        String delete = changeSql(source.path("deleteSql"));
        LocalDateTime initial;
        try
        {
            initial = LocalDateTime.parse(text(source.path("initialCursor"), 1, 50, "INVALID_SYNC_CONFIG"));
        }
        catch (DateTimeParseException ex)
        {
            throw error("SYNC_CURSOR_INVALID", "初始同步游标必须是 ISO 日期时间");
        }
        int overlap = integer(source.path("overlapMinutes"), 0, 10080, "SYNC_OVERLAP_INVALID");
        String cron = source.has("cronExpression")
                ? text(source.path("cronExpression"), 1, 255, "SYNC_CRON_INVALID")
                : "0 0/5 * * * ?";
        if (!cron.matches("(?s)[0-9A-Za-z*/?,\\- ]{9,255}"))
        {
            throw error("SYNC_CRON_INVALID", "定时表达式格式不合法");
        }
        return new IncrementalSyncConfig(datasourceKey, upperBound, create, update, delete, initial, overlap, cron);
    }

    private String changeSql(JsonNode node)
    {
        String sql = readOnlySql(node, "SYNC_QUERY_INVALID");
        String normalized = COMMENTS.matcher(sql).replaceAll(" ");
        if (!normalized.matches("(?is).*\\bAS\\s+document_no\\b.*")
                || !normalized.matches("(?is).*\\bAS\\s+changed_at\\b.*"))
        {
            throw error("SYNC_QUERY_COLUMNS_INVALID", "增量查询必须返回 document_no 和 changed_at 列别名");
        }
        Set<String> parameters = new LinkedHashSet<>();
        java.util.regex.Matcher matcher = NAMED_PARAMETER.matcher(normalized);
        while (matcher.find())
        {
            parameters.add(matcher.group(1));
        }
        if (!parameters.equals(Set.of("fromTime", "toTime")))
        {
            throw error("SYNC_QUERY_BINDINGS_INVALID", "增量查询只能使用 fromTime 和 toTime 参数");
        }
        return sql;
    }

    private void validateTemplateVariables(JsonNode node, Map<String, String> constants, List<ReadQueryStep> steps)
    {
        if (node.isObject())
        {
            node.elements().forEachRemaining(value -> validateTemplateVariables(value, constants, steps));
            return;
        }
        if (node.isArray())
        {
            node.elements().forEachRemaining(value -> validateTemplateVariables(value, constants, steps));
            return;
        }
        if (!node.isTextual())
        {
            return;
        }
        String value = node.textValue();
        if (!value.contains("{{") && !value.contains("}}"))
        {
            return;
        }
        if (!value.matches("\\{\\{[A-Za-z][A-Za-z0-9_.]{0,199}\\}\\}"))
        {
            throw error("VARIABLE_NOT_ALLOWED", "OA模板变量必须独占一个文本值");
        }
        validateVariable(value.substring(2, value.length() - 2), constants, cardinalities(steps), Integer.MAX_VALUE);
    }

    private void validateVariable(String variable, Map<String, String> constants,
            Map<String, ResultCardinality> dataSteps, int currentOrder)
    {
        if (variable.equals("trigger.masterId") || variable.equals("trigger.formId") || variable.equals("trigger.summaryId"))
        {
            return;
        }
        if (variable.startsWith("task.constants."))
        {
            if (!constants.containsKey(variable.substring("task.constants.".length())))
            {
                throw error("CONSTANT_NOT_FOUND", "任务常量不存在");
            }
            return;
        }
        if (variable.startsWith("data."))
        {
            String remainder = variable.substring("data.".length());
            String stepCode = remainder.contains(".") ? remainder.substring(0, remainder.indexOf('.')) : remainder;
            ResultCardinality cardinality = dataSteps.get(stepCode);
            if (cardinality == null)
            {
                throw error("STEP_REFERENCE_ORDER_INVALID", "只能引用已定义的数据步骤");
            }
            int referencedOrder = new ArrayList<>(dataSteps.keySet()).indexOf(stepCode) + 1;
            if (referencedOrder >= currentOrder && currentOrder != Integer.MAX_VALUE)
            {
                throw error("STEP_REFERENCE_ORDER_INVALID", "只能引用前序数据步骤");
            }
            if (!remainder.contains(".") && currentOrder != Integer.MAX_VALUE
                    && cardinality != ResultCardinality.SCALAR)
            {
                throw error("VARIABLE_NOT_SCALAR", "SQL参数只能引用标量步骤或单行步骤字段");
            }
            return;
        }
        throw error("VARIABLE_NOT_ALLOWED", "配置变量不在允许命名空间内");
    }

    private Map<String, ResultCardinality> cardinalities(List<ReadQueryStep> steps)
    {
        Map<String, ResultCardinality> result = new LinkedHashMap<>();
        steps.forEach(step -> result.put(step.code(), step.cardinality()));
        return result;
    }

    private void validateScalarVariable(String variable, Map<String, String> constants, List<ReadQueryStep> steps)
    {
        validateVariable(variable, constants, cardinalities(steps), Integer.MAX_VALUE);
        if (!variable.startsWith("data.")) return;
        String remainder = variable.substring("data.".length());
        String stepCode = remainder.contains(".") ? remainder.substring(0, remainder.indexOf('.')) : remainder;
        ResultCardinality cardinality = cardinalities(steps).get(stepCode);
        if (cardinality == ResultCardinality.LIST || (cardinality == ResultCardinality.ONE && !remainder.contains(".")))
        {
            throw error("VARIABLE_NOT_SCALAR", "U8主键变量必须指向标量步骤或单行步骤字段");
        }
    }

    private String datasource(JsonNode node)
    {
        String value = text(node, 1, 100, "DATASOURCE_NOT_ALLOWED");
        if (!DATASOURCE.matcher(value).matches() || !readableDatasource.test(value))
        {
            throw error("DATASOURCE_NOT_ALLOWED", "数据源未注册或不是只读数据源");
        }
        return value;
    }

    private String readOnlySql(JsonNode node, String errorCode)
    {
        String sql = text(node, 1, 20000, errorCode).trim();
        if (sql.contains("${"))
        {
            throw error("SQL_PLACEHOLDER_NOT_ALLOWED", "SQL不能使用字符串替换占位符");
        }
        String normalized = COMMENTS.matcher(sql).replaceAll(" ").trim();
        if (normalized.contains(";") || !normalized.matches("(?is)^(SELECT|WITH)\\b.*")
                || WRITE_SQL.matcher(normalized).find())
        {
            throw error("SQL_NOT_READ_ONLY", "SQL只能包含一条 SELECT/WITH 只读查询");
        }
        return sql;
    }

    private ResultCardinality cardinality(JsonNode node)
    {
        try
        {
            return ResultCardinality.valueOf(text(node, 1, 20, "INVALID_CARDINALITY"));
        }
        catch (IllegalArgumentException ex)
        {
            throw error("INVALID_CARDINALITY", "查询结果类型不合法");
        }
    }

    private Map<String, String> textMap(JsonNode node, String errorCode)
    {
        if (!node.isObject())
        {
            throw error(errorCode, "配置对象格式不合法");
        }
        Map<String, String> values = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> {
            if (!CODE.matcher(entry.getKey()).matches() || !entry.getValue().isTextual())
            {
                throw error(errorCode, "配置对象必须由编码和文本值组成");
            }
            values.put(entry.getKey(), entry.getValue().textValue());
        });
        return values;
    }

    private void objectWithOnly(JsonNode node, Set<String> fields, String errorCode)
    {
        if (!node.isObject())
        {
            throw error(errorCode, "配置对象格式不合法");
        }
        node.fieldNames().forEachRemaining(field -> {
            if (!fields.contains(field))
            {
                throw error(errorCode, "配置包含未允许字段");
            }
        });
    }

    private String code(JsonNode node, String errorCode)
    {
        String value = text(node, 1, 50, errorCode);
        if (!CODE.matcher(value).matches())
        {
            throw error(errorCode, "编码格式不合法");
        }
        return value;
    }

    private int integer(JsonNode node, int minimum, int maximum, String errorCode)
    {
        if (!node.isIntegralNumber() || !node.canConvertToInt()
                || node.intValue() < minimum || node.intValue() > maximum)
        {
            throw error(errorCode, "数值超出平台允许范围");
        }
        return node.intValue();
    }

    private String text(JsonNode node, int minimum, int maximum, String errorCode)
    {
        if (!node.isTextual() || node.textValue().length() < minimum || node.textValue().length() > maximum)
        {
            throw error(errorCode, "文本格式不合法");
        }
        return node.textValue();
    }

    private TaskConfigException error(String code, String message)
    {
        return new TaskConfigException(code, message);
    }
}
