package com.ruoyi.integration.reference.catalog;

import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.integration.reference.model.ReferenceException;
import com.ruoyi.integration.reference.model.ReferenceTask;

/** Validates configurable contracts without assuming a particular business result schema. */
final class ReferenceMetadataValidator {
    private static final Set<String> TYPES = Set.of("STRING", "INTEGER", "DECIMAL", "BOOLEAN", "DATE", "DATETIME");
    private static final Set<String> OPERATORS = Set.of("eq", "ne", "gt", "ge", "lt", "le", "contains", "startsWith", "endsWith", "between", "in", "isNull", "isNotNull");

    static void validate(ReferenceTask task) {
        require(task != null, "缺少任务配置");
        require(task.taskCode() != null && task.taskCode().matches("[A-Za-z][A-Za-z0-9_]{0,99}"), "任务编码格式错误");
        require(task.taskName() != null && !task.taskName().isBlank() && task.taskName().length() <= 100, "任务名称不能为空或过长");
        require(task.datasourceKey() != null && task.datasourceKey().matches("[A-Za-z][A-Za-z0-9_-]{0,99}"), "数据源配置键错误");
        validateReadOnlySql(task.sqlText());
        JsonNode metadata = task.metadata();
        object(metadata, Set.of("taskCode", "taskName", "taskType", "executionMode", "metadataVersion", "resultSets"));
        require(task.taskCode().equals(text(metadata, "taskCode", 100)), "元数据任务编码不一致");
        text(metadata, "taskName", 100);
        require("REFERENCE".equals(text(metadata, "taskType", 20)) && "SYNC_QUERY".equals(text(metadata, "executionMode", 20)), "仅支持同步参照任务");
        text(metadata, "metadataVersion", 100);
        JsonNode sets = metadata.path("resultSets");
        require(sets.isArray() && sets.size() == 1, "V1 每条任务必须配置一个结果集");
        validateResultSet(sets.get(0));
        require(metadata.toString().length() <= 60000, "元数据过大");
    }

    private static void validateResultSet(JsonNode set) {
        object(set, Set.of("resultSetCode", "resultSetName", "selectionMode", "fields", "parameters", "defaults", "limits"));
        identifier(set, "resultSetCode");
        text(set, "resultSetName", 100);
        require("SINGLE".equals(text(set, "selectionMode", 20)), "V1 仅支持单选");
        JsonNode fields = set.path("fields");
        require(fields.isArray() && !fields.isEmpty() && fields.size() <= 256, "必须配置完整字段列表（最多 256 项）");
        Map<String, JsonNode> names = new LinkedHashMap<>();
        Set<Integer> orders = new HashSet<>();
        for (JsonNode field : fields) {
            object(field, Set.of("name", "label", "order", "dataType", "nullable", "filterOperators", "sortable"));
            String name = text(field, "name", 128);
            require(name.chars().noneMatch(Character::isISOControl), "字段名不能包含控制字符");
            require(names.put(name, field) == null, "字段名不能重复");
            text(field, "label", 100);
            int order = number(field, "order", 1, 10000);
            require(orders.add(order), "字段顺序不能重复");
            String type = text(field, "dataType", 20);
            require(TYPES.contains(type), "不支持的字段类型");
            bool(field, "nullable"); bool(field, "sortable");
            JsonNode operators = field.path("filterOperators");
            require(operators.isArray(), "筛选操作符必须为数组");
            Set<String> unique = new HashSet<>();
            for (JsonNode op : operators) {
                require(op.isTextual() && OPERATORS.contains(op.asText()) && unique.add(op.asText()), "筛选操作符错误或重复");
                if (Set.of("contains", "startsWith", "endsWith").contains(op.asText())) require("STRING".equals(type), "模糊筛选仅支持字符串");
                if ("BOOLEAN".equals(type)) require(Set.of("eq", "ne", "in", "isNull", "isNotNull").contains(op.asText()), "布尔字段不支持该操作符");
            }
        }
        JsonNode parameters = set.path("parameters");
        require(parameters.isArray() && parameters.size() <= 50, "受控参数必须为数组（最多 50 项）");
        Set<String> parameterNames = new HashSet<>();
        for (JsonNode param : parameters) {
            object(param, Set.of("name", "label", "dataType", "nullable", "required"));
            require(parameterNames.add(identifier(param, "name")), "参数名不能重复");
            text(param, "label", 100);
            require(TYPES.contains(text(param, "dataType", 20)), "不支持的参数类型");
            bool(param, "nullable"); bool(param, "required");
        }
        JsonNode limits = set.path("limits");
        object(limits, Set.of("maxPageSize", "maxFilterConditions", "maxFilterDepth", "maxSortFields", "maxInValues", "queryTimeoutMs"));
        int pageSize = number(limits, "maxPageSize", 200, 200);
        number(limits, "maxFilterConditions", 20, 20); number(limits, "maxFilterDepth", 3, 3);
        int maxSort = number(limits, "maxSortFields", 5, 5);
        number(limits, "maxInValues", 100, 100); number(limits, "queryTimeoutMs", 10000, 10000);
        JsonNode defaults = set.path("defaults");
        object(defaults, Set.of("displayFields", "filterFields", "sort", "pageSize"));
        fieldList(defaults.path("displayFields"), names, false);
        require(!defaults.path("displayFields").isEmpty(), "至少配置一个默认显示字段");
        fieldList(defaults.path("filterFields"), names, true);
        number(defaults, "pageSize", 1, pageSize);
        JsonNode sort = defaults.path("sort");
        require(sort.isArray() && !sort.isEmpty() && sort.size() <= maxSort, "必须配置有效的默认排序");
        Set<String> sorted = new HashSet<>();
        for (JsonNode item : sort) {
            object(item, Set.of("field", "direction"));
            String name = text(item, "field", 128);
            require(names.containsKey(name) && names.get(name).path("sortable").asBoolean() && sorted.add(name), "默认排序字段不可用或重复");
            require(Set.of("ASC", "DESC").contains(text(item, "direction", 4)), "排序方向错误");
        }
    }

    private static void fieldList(JsonNode list, Map<String, JsonNode> names, boolean filter) {
        require(list.isArray(), "默认字段必须为数组");
        Set<String> seen = new HashSet<>();
        for (JsonNode item : list) {
            require(item.isTextual() && names.containsKey(item.asText()) && seen.add(item.asText()), "默认字段不存在或重复");
            if (filter) require(!names.get(item.asText()).path("filterOperators").isEmpty(), "默认筛选字段不允许筛选");
        }
    }

    private static void object(JsonNode node, Set<String> keys) {
        require(node != null && node.isObject(), "配置必须是 JSON 对象");
        node.fieldNames().forEachRemaining(name -> require(keys.contains(name), "未知配置字段：" + name));
        for (String key : keys) require(node.has(key), "缺少配置字段：" + key);
    }
    private static String text(JsonNode node, String key, int max) {
        JsonNode value = node.path(key);
        require(value.isTextual() && !value.asText().isBlank() && value.asText().length() <= max, "配置字段格式错误：" + key);
        return value.asText();
    }
    private static String identifier(JsonNode node, String key) {
        String value = text(node, key, 128);
        require(value.matches("[A-Za-z_][A-Za-z0-9_]{0,127}"), "配置标识格式错误：" + key);
        return value;
    }
    private static int number(JsonNode node, String key, int min, int max) {
        JsonNode value = node.path(key);
        require(value.isIntegralNumber() && value.canConvertToInt() && value.intValue() >= min && value.intValue() <= max, "配置数值超出范围：" + key);
        return value.intValue();
    }
    private static void bool(JsonNode node, String key) { require(node.path(key).isBoolean(), "配置必须为布尔值：" + key); }
    /**
     * 参照 SQL 改由已发布配置保存，不能再通过资源路径间接选择代码包内脚本。
     * 这里维持同步参照的单条只读查询边界，不接受任意多语句或写库指令。
     */
    private static void validateReadOnlySql(String value) {
        require(value != null && !value.isBlank() && value.length() <= 60000, "参照 SQL 不能为空或过长");
        String sql = value.replaceAll("(?s)/\\*.*?\\*/|--[^\\r\\n]*", " ").trim();
        require(!sql.contains("${") && !sql.contains(";") && sql.matches("(?is)^(SELECT|WITH\\b).*"), "参照 SQL 必须是一条只读查询");
        // SQL Server 的 SELECT ... INTO 同样会创建结果表；它不能因为以 SELECT 开头而越过只读边界。
        require(!sql.matches("(?is).*\\b(UPDATE|INSERT|DELETE|MERGE|REPLACE|CALL|EXEC|CREATE|ALTER|DROP|TRUNCATE|GRANT|REVOKE|INTO)\\b.*"), "参照 SQL 不能包含写库或管理指令");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new ReferenceException("INVALID_ARGUMENT", 400, message, false);
    }
}
