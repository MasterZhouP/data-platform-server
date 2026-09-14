package com.ruoyi.integration.reference.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** 参照配置测试共用一份完整最小元数据，确保测试覆盖真实的发布前校验边界。 */
final class ReferenceCatalogTestFixture
{
    private ReferenceCatalogTestFixture() { }

    static ObjectNode config(ObjectMapper json, String datasourceKey)
    {
        ObjectNode config = json.createObjectNode();
        config.put("datasourceKey", datasourceKey);
        config.put("sqlText", "SELECT code FROM inventory");
        ObjectNode metadata = config.putObject("metadata");
        metadata.put("taskCode", "REFERENCE_DEMO");
        metadata.put("taskName", "参照示例");
        metadata.put("taskType", "REFERENCE");
        metadata.put("executionMode", "SYNC_QUERY");
        metadata.put("metadataVersion", "test-1");
        ObjectNode resultSet = metadata.putArray("resultSets").addObject();
        resultSet.put("resultSetCode", "result");
        resultSet.put("resultSetName", "查询结果");
        resultSet.put("selectionMode", "SINGLE");
        ObjectNode field = resultSet.putArray("fields").addObject();
        field.put("name", "code");
        field.put("label", "编码");
        field.put("order", 1);
        field.put("dataType", "STRING");
        field.put("nullable", true);
        field.putArray("filterOperators").add("eq");
        field.put("sortable", true);
        resultSet.putArray("parameters");
        ObjectNode defaults = resultSet.putObject("defaults");
        defaults.putArray("displayFields").add("code");
        defaults.putArray("filterFields").add("code");
        ObjectNode sort = defaults.putArray("sort").addObject();
        sort.put("field", "code");
        sort.put("direction", "ASC");
        defaults.put("pageSize", 20);
        ObjectNode limits = resultSet.putObject("limits");
        limits.put("maxPageSize", 200);
        limits.put("maxFilterConditions", 20);
        limits.put("maxFilterDepth", 3);
        limits.put("maxSortFields", 5);
        limits.put("maxInValues", 100);
        limits.put("queryTimeoutMs", 10000);
        return config;
    }
}
