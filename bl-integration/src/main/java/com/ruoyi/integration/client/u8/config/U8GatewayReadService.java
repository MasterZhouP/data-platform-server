package com.ruoyi.integration.client.u8.config;

import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.configuration.ConfigurationException;
import com.ruoyi.integration.taskdefinition.TaskType;
import com.ruoyi.integration.taskdefinition.mapper.IntegrationTaskMapper;
import org.springframework.stereotype.Service;

/** Projects U8 account state and operation options without exposing credentials. */
@Service
public class U8GatewayReadService
{
    public static final String DEFAULT_KEY = "u8-default";
    private final U8GatewayCatalog catalog;
    private final IntegrationTaskMapper tasks;
    private final ObjectMapper json;

    public U8GatewayReadService(U8GatewayCatalog catalog, IntegrationTaskMapper tasks, ObjectMapper json)
    {
        this.catalog = catalog;
        this.tasks = tasks;
        this.json = json;
    }

    public List<ObjectNode> list()
    {
        return catalog.list().stream().map(this::summary).toList();
    }

    public ObjectNode detail(String key)
    {
        U8GatewayConnection connection = catalog.row(key);
        ObjectNode result = summary(connection);
        if (hasText(connection.draftRevisionId())) result.set("draftConfig", configuration(catalog.draft(key, null), connection.connectionName()));
        if (hasText(connection.activeRevisionId())) result.set("activeConfig", configuration(catalog.active(key), connection.connectionName()));
        String tested = hasText(connection.draftRevisionId()) ? connection.draftRevisionId() : connection.activeRevisionId();
        if (hasText(tested))
        {
            ObjectNode lastTest = catalog.lastTest(key, tested);
            if (lastTest.size() > 0)
            {
                result.set("lastTest", lastTest.deepCopy());
                result.put("health", "SUCCESS".equals(lastTest.path("status").asText()) ? "HEALTHY" : "DANGER");
                result.put("checkedAt", lastTest.path("checkedAt").asText(""));
            }
        }
        return result;
    }

    public ObjectNode options()
    {
        ObjectNode result = json.createObjectNode().put("connectionKey", DEFAULT_KEY).put("configured", false);
        try
        {
            U8GatewayConnection connection = catalog.row(DEFAULT_KEY);
            result.put("configured", connection.enabled() && hasText(connection.activeRevisionId()));
            if (hasText(connection.activeRevisionId()))
            {
                ObjectNode config = configuration(catalog.active(DEFAULT_KEY), connection.connectionName());
                result.set("operations", config.path("operations").deepCopy());
            }
        }
        catch (ConfigurationException absent)
        {
            if (!"CONFIGURATION_NOT_FOUND".equals(absent.code())) throw absent;
        }
        if (!result.has("operations")) result.set("operations", json.createArrayNode());
        return result;
    }

    public List<ObjectNode> usages(String key)
    {
        if (!DEFAULT_KEY.equals(key)) throw new ConfigurationException("CONFIGURATION_NOT_FOUND", 404, "U8账户不存在");
        return tasks.listTasks().stream().filter(task -> TaskType.OA_TO_U8.name().equals(task.taskType())).map(task ->
                json.createObjectNode().put("taskCode", task.taskCode()).put("taskName", task.taskName())
                        .put("enabled", task.enabled())).toList();
    }

    private ObjectNode summary(U8GatewayConnection connection)
    {
        return json.createObjectNode().put("connectionKey", connection.connectionKey()).put("name", connection.connectionName())
                .put("enabled", connection.enabled()).put("activeRevisionId", emptyToNull(connection.activeRevisionId()))
                .put("draftRevisionId", emptyToNull(connection.draftRevisionId())).put("rowVersion", connection.rowVersion());
    }

    private ObjectNode configuration(U8GatewayRevision revision, String connectionName)
    {
        ObjectNode result = catalog.config(revision).put("revisionId", revision.revisionId())
                .put("secretConfigured", hasText(revision.secretId()));
        result.put("connectionName", connectionName);
        return result;
    }

    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
    private static String emptyToNull(String value) { return hasText(value) ? value : null; }
}
