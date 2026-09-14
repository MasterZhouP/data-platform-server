package com.ruoyi.integration.taskdefinition.management;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 相同任务配置即使前端对象字段顺序不同，也必须产生同一个校验和。
 * 该校验和会在受理时锁定到执行记录，用于证明在途单据使用的确切版本。
 */
final class CanonicalJsonChecksum
{
    private CanonicalJsonChecksum() { }

    static String sha256(JsonNode source, ObjectMapper json)
    {
        try
        {
            byte[] bytes = json.writeValueAsBytes(canonicalize(source, json));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (Exception ex)
        {
            throw new IllegalStateException("任务配置校验和无法生成", ex);
        }
    }

    static JsonNode canonicalize(JsonNode source, ObjectMapper json)
    {
        if (source.isObject())
        {
            ObjectNode result = json.createObjectNode();
            Map<String, JsonNode> fields = new TreeMap<>();
            source.fields().forEachRemaining(entry -> fields.put(entry.getKey(), entry.getValue()));
            fields.forEach((key, value) -> result.set(key, canonicalize(value, json)));
            return result;
        }
        if (source.isArray())
        {
            ArrayNode result = json.createArrayNode();
            source.forEach(value -> result.add(canonicalize(value, json)));
            return result;
        }
        return source.deepCopy();
    }
}
