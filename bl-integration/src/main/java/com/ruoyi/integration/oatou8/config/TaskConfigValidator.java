package com.ruoyi.integration.oatou8.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Validates the deliberately small configuration language before it can become a published revision.
 * SQL, network targets and execution order are checked here so the runtime executor can stay fixed and safe.
 */
public class TaskConfigValidator
{
    private static final Pattern CODE = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,49}");
    // SQL Server 的 SELECT ... INTO 可写出新表，必须与其他写语句一起在配置保存前拦截。
    private static final Pattern WRITE_SQL = Pattern.compile("(?is)\\b(UPDATE|INSERT|DELETE|MERGE|REPLACE|CALL|EXEC|CREATE|ALTER|DROP|TRUNCATE|GRANT|REVOKE|INTO)\\b");
    private static final Pattern COMMENTS = Pattern.compile("(?s)/\\*.*?\\*/|--[^\\r\\n]*");
    private static final Set<String> ROOT_FIELDS = Set.of("constants", "dataSteps", "u8", "resultQueries");
    private static final Set<String> QUERY_FIELDS = Set.of("code", "order", "datasourceKey", "sql", "cardinality", "parameterBindings");
    private static final Set<String> RESULT_QUERY_FIELDS = Set.of("code", "order", "datasourceKey", "sql", "cardinality",
            "parameterBindings", "required", "initialDelayMs", "intervalMs", "maxAttempts", "outputMappings");

    private final ObjectMapper json;
    private final Set<String> readableDatasourceKeys;
    private final Set<String> allowedOperations;

    public TaskConfigValidator(ObjectMapper json, Set<String> readableDatasourceKeys, Set<String> allowedOperations)
    {
        this.json = json;
        this.readableDatasourceKeys = Set.copyOf(readableDatasourceKeys);
        this.allowedOperations = Set.copyOf(allowedOperations);
    }

    public OaToU8TaskConfig parseAndValidate(JsonNode source)
    {
        objectWithOnly(source, ROOT_FIELDS, "INVALID_CONFIG");
        Map<String, String> constants = textMap(source.path("constants"), "INVALID_CONSTANTS");
        List<ReadQueryStep> dataSteps = readDataSteps(source.path("dataSteps"));
        validateDataStepBindings(dataSteps, constants);
        U8BusinessRequest u8 = readU8(source.path("u8"), constants, dataSteps);
        List<ResultQueryStep> resultQueries = readResultQueries(source.path("resultQueries"), constants, dataSteps);
        return new OaToU8TaskConfig(Map.copyOf(constants), List.copyOf(dataSteps), u8, List.copyOf(resultQueries));
    }

    private List<ReadQueryStep> readDataSteps(JsonNode source)
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
            objectWithOnly(node, QUERY_FIELDS, "INVALID_DATA_STEP");
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
            String sql = readOnlySql(node.path("sql"));
            ResultCardinality cardinality = cardinality(node.path("cardinality"));
            steps.add(new ReadQueryStep(code, order, datasourceKey, sql, cardinality,
                    Map.copyOf(textMap(node.path("parameterBindings"), "INVALID_PARAMETER_BINDINGS"))));
        }
        return steps;
    }

    private void validateDataStepBindings(List<ReadQueryStep> steps, Map<String, String> constants)
    {
        Map<String, ResultCardinality> allSteps = new LinkedHashMap<>();
        steps.forEach(step -> allSteps.put(step.code(), step.cardinality()));
        for (ReadQueryStep step : steps)
        {
            for (String variable : step.parameterBindings().values())
            {
                validateVariable(variable, constants, allSteps, step.order(), false);
            }
        }
    }

    private U8BusinessRequest readU8(JsonNode source, Map<String, String> constants, List<ReadQueryStep> dataSteps)
    {
        objectWithOnly(source, Set.of("operationCode", "path", "requestJsonTemplate", "successRule", "errorMessagePointer", "outputs"),
                "INVALID_U8_REQUEST");
        String operationCode = code(source.path("operationCode"), "INVALID_U8_REQUEST");
        if (!allowedOperations.contains(operationCode))
        {
            throw error("U8_OPERATION_NOT_ALLOWED", "U8业务接口未注册");
        }
        String path = text(source.path("path"), 1, 2000, "INVALID_U8_REQUEST");
        if (!path.startsWith("/") || path.startsWith("//") || path.contains("?") || path.contains("://"))
        {
            throw error("U8_PATH_INVALID", "U8接口只能使用受控相对路径");
        }
        String template = text(source.path("requestJsonTemplate"), 2, 20000, "INVALID_U8_REQUEST");
        try
        {
            JsonNode templateNode = json.readTree(template);
            if (!templateNode.isObject())
            {
                throw error("U8_TEMPLATE_INVALID", "U8请求模板必须是JSON对象");
            }
            validateTemplateVariables(templateNode, constants, dataSteps);
        }
        catch (TaskConfigException ex)
        {
            throw ex;
        }
        catch (Exception ex)
        {
            throw error("U8_TEMPLATE_INVALID", "U8请求模板不是有效JSON");
        }
        JsonNode success = source.path("successRule");
        objectWithOnly(success, Set.of("pointer", "allowedValues"), "INVALID_U8_SUCCESS_RULE");
        String pointer = pointer(success.path("pointer"), "INVALID_U8_SUCCESS_RULE");
        Set<String> allowedValues = textSet(success.path("allowedValues"), "INVALID_U8_SUCCESS_RULE");
        if (allowedValues.isEmpty())
        {
            throw error("INVALID_U8_SUCCESS_RULE", "U8成功规则必须包含允许值");
        }
        String errorMessagePointer = source.has("errorMessagePointer")
                ? pointer(source.path("errorMessagePointer"), "INVALID_U8_REQUEST") : null;
        List<ResponseOutput> outputs = new ArrayList<>();
        JsonNode outputNodes = source.path("outputs");
        if (!outputNodes.isArray())
        {
            throw error("INVALID_U8_REQUEST", "U8响应输出必须是数组");
        }
        Set<String> outputNames = new LinkedHashSet<>();
        for (JsonNode output : outputNodes)
        {
            objectWithOnly(output, Set.of("name", "pointer", "required"), "INVALID_U8_OUTPUT");
            String name = code(output.path("name"), "INVALID_U8_OUTPUT");
            if (!outputNames.add(name))
            {
                throw error("DUPLICATE_OUTPUT_NAME", "U8响应输出名称重复");
            }
            if (!output.path("required").isBoolean())
            {
                throw error("INVALID_U8_OUTPUT", "U8响应输出必须声明是否必填");
            }
            outputs.add(new ResponseOutput(name, pointer(output.path("pointer"), "INVALID_U8_OUTPUT"), output.path("required").asBoolean()));
        }
        return new U8BusinessRequest(operationCode, path, template, new SuccessRule(pointer, Set.copyOf(allowedValues)),
                errorMessagePointer, List.copyOf(outputs));
    }

    private void validateTemplateVariables(JsonNode node, Map<String, String> constants, List<ReadQueryStep> dataSteps)
    {
        if (node.isObject())
        {
            node.elements().forEachRemaining(value -> validateTemplateVariables(value, constants, dataSteps));
            return;
        }
        if (node.isArray())
        {
            node.elements().forEachRemaining(value -> validateTemplateVariables(value, constants, dataSteps));
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
            throw error("VARIABLE_NOT_ALLOWED", "U8模板变量必须独占一个文本值");
        }
        String variable = value.substring(2, value.length() - 2);
        if (variable.equals("trigger.masterId") || variable.equals("trigger.formId") || variable.equals("trigger.summaryId"))
        {
            return;
        }
        if (variable.startsWith("task.constants.") && constants.containsKey(variable.substring("task.constants.".length())))
        {
            return;
        }
        if (variable.startsWith("data."))
        {
            String remainder = variable.substring("data.".length());
            String stepCode = remainder.contains(".") ? remainder.substring(0, remainder.indexOf('.')) : remainder;
            if (dataSteps.stream().anyMatch(step -> step.code().equals(stepCode)))
            {
                return;
            }
        }
        throw error("VARIABLE_NOT_ALLOWED", "U8模板变量不在固定执行上下文内");
    }

    private List<ResultQueryStep> readResultQueries(JsonNode source, Map<String, String> constants,
            List<ReadQueryStep> dataSteps)
    {
        if (!source.isArray())
        {
            throw error("INVALID_RESULT_QUERIES", "结果查询必须是数组");
        }
        Map<String, ResultCardinality> dataCardinality = new LinkedHashMap<>();
        dataSteps.forEach(step -> dataCardinality.put(step.code(), step.cardinality()));
        List<ResultQueryStep> steps = new ArrayList<>();
        Set<String> codes = new LinkedHashSet<>();
        int expectedOrder = 1;
        for (JsonNode node : source)
        {
            objectWithOnly(node, RESULT_QUERY_FIELDS, "INVALID_RESULT_QUERY");
            String code = code(node.path("code"), "INVALID_RESULT_QUERY");
            if (!codes.add(code))
            {
                throw error("DUPLICATE_STEP_CODE", "结果查询步骤编码重复");
            }
            int order = integer(node.path("order"), 1, 100, "INVALID_RESULT_QUERY");
            if (order != expectedOrder++)
            {
                throw error("STEP_ORDER_INVALID", "结果查询顺序必须从 1 连续递增");
            }
            Map<String, String> bindings = textMap(node.path("parameterBindings"), "INVALID_PARAMETER_BINDINGS");
            for (String variable : bindings.values())
            {
                validateVariable(variable, constants, dataCardinality, Integer.MAX_VALUE, true);
            }
            if (!node.path("required").isBoolean())
            {
                throw error("INVALID_RESULT_QUERY", "结果查询必须声明是否必填");
            }
            int initialDelay = integer(node.path("initialDelayMs"), 0, 30000, "RESULT_POLL_LIMIT_INVALID");
            int interval = integer(node.path("intervalMs"), 100, 30000, "RESULT_POLL_LIMIT_INVALID");
            int attempts = integer(node.path("maxAttempts"), 1, 20, "RESULT_POLL_LIMIT_INVALID");
            Map<String, String> mappings = textMap(node.path("outputMappings"), "INVALID_OUTPUT_MAPPINGS");
            if (mappings.isEmpty())
            {
                throw error("INVALID_OUTPUT_MAPPINGS", "结果查询至少需要一个输出映射");
            }
            steps.add(new ResultQueryStep(code, order, datasource(node.path("datasourceKey")), readOnlySql(node.path("sql")),
                    cardinality(node.path("cardinality")), Map.copyOf(bindings), node.path("required").asBoolean(),
                    initialDelay, interval, attempts, Map.copyOf(mappings)));
        }
        return steps;
    }

    private void validateVariable(String variable, Map<String, String> constants,
            Map<String, ResultCardinality> dataSteps, int currentOrder, boolean allowU8AndResult)
    {
        if (variable.equals("trigger.masterId") || variable.equals("trigger.formId") || variable.equals("trigger.summaryId"))
        {
            return;
        }
        if (variable.startsWith("task.constants."))
        {
            if (constants.containsKey(variable.substring("task.constants.".length())))
            {
                return;
            }
            throw error("CONSTANT_NOT_FOUND", "任务常量不存在");
        }
        if (variable.startsWith("data."))
        {
            String stepCode = variable.substring("data.".length());
            ResultCardinality cardinality = dataSteps.get(stepCode);
            if (cardinality == null)
            {
                throw error("STEP_REFERENCE_ORDER_INVALID", "只能引用已定义的前序数据步骤");
            }
            if (cardinality != ResultCardinality.SCALAR)
            {
                throw error("STEP_OUTPUT_NOT_SCALAR", "SQL参数只能引用标量数据步骤");
            }
            int referencedOrder = dataSteps.keySet().stream().toList().indexOf(stepCode) + 1;
            if (referencedOrder >= currentOrder)
            {
                throw error("STEP_REFERENCE_ORDER_INVALID", "SQL参数只能引用前序数据步骤");
            }
            return;
        }
        if (allowU8AndResult && (variable.startsWith("u8.response.") || variable.startsWith("result.")))
        {
            if (variable.indexOf('.', variable.indexOf('.') + 1) > 0)
            {
                return;
            }
        }
        throw error("VARIABLE_NOT_ALLOWED", "配置变量不在允许命名空间内");
    }

    private String datasource(JsonNode node)
    {
        String key = code(node, "DATASOURCE_NOT_ALLOWED");
        if (!readableDatasourceKeys.contains(key))
        {
            throw error("DATASOURCE_NOT_ALLOWED", "数据源未注册或不是只读数据源");
        }
        return key;
    }

    private String readOnlySql(JsonNode node)
    {
        String sql = text(node, 1, 20000, "INVALID_SQL").trim();
        if (sql.contains("${"))
        {
            throw error("SQL_PLACEHOLDER_NOT_ALLOWED", "SQL不能使用字符串替换占位符");
        }
        String normalized = COMMENTS.matcher(sql).replaceAll(" ").trim();
        if (normalized.contains(";") || !normalized.matches("(?is)^(SELECT|WITH)\\b.*"))
        {
            throw error("SQL_NOT_READ_ONLY", "SQL只能包含一条只读查询");
        }
        if (WRITE_SQL.matcher(normalized).find())
        {
            throw error("SQL_NOT_READ_ONLY", "SQL不能包含写入或结构变更语句");
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

    private Set<String> textSet(JsonNode node, String errorCode)
    {
        if (!node.isArray())
        {
            throw error(errorCode, "配置值必须是文本数组");
        }
        Set<String> values = new LinkedHashSet<>();
        for (JsonNode value : node)
        {
            if (!value.isTextual() || value.textValue().isBlank() || !values.add(value.textValue()))
            {
                throw error(errorCode, "配置值必须是唯一非空文本");
            }
        }
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

    private String pointer(JsonNode node, String errorCode)
    {
        String value = text(node, 0, 500, errorCode);
        if (!value.isEmpty() && !value.startsWith("/") || value.contains("//"))
        {
            throw error(errorCode, "JSON路径格式不合法");
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
