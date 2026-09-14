package com.ruoyi.integration.sql;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.oatou8.config.ResultCardinality;

/** Normalized, size-bounded SQL value retained by the task executor. */
public record ReadOnlySqlResult(ResultCardinality cardinality, JsonNode value)
{
    public ObjectNode one()
    {
        return value instanceof ObjectNode node ? node : null;
    }

    public ArrayNode list()
    {
        return value instanceof ArrayNode node ? node : null;
    }

    public JsonNode scalar()
    {
        return cardinality == ResultCardinality.SCALAR ? value : null;
    }
}
