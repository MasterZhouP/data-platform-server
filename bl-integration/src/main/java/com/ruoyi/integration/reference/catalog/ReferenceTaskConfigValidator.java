package com.ruoyi.integration.reference.catalog;

import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.reference.model.ReferenceException;
import com.ruoyi.integration.reference.model.ReferenceTask;
import org.springframework.stereotype.Component;

/**
 * 参照任务进入统一任务目录前的配置边界。
 * <p>
 * 统一修订只保存数据源、只读 SQL 和完整查询元数据；运行时仍由同步参照引擎处理分页、筛选和字段规则，
 * 因此不能把 OA→U8 的发送配置混入参照任务。
 * </p>
 */
@Component
public class ReferenceTaskConfigValidator
{
    private static final Set<String> CONFIG_FIELDS = Set.of("datasourceKey", "sqlText", "metadata");
    private static final Set<String> READABLE_DATASOURCES = Set.of("u8");

    public ReferenceTask parseAndValidate(String taskCode, String taskName, boolean enabled, JsonNode config)
    {
        if (config == null || !config.isObject())
        {
            throw new IllegalArgumentException("参照任务配置必须是 JSON 对象");
        }
        config.fieldNames().forEachRemaining(name -> {
            if (!CONFIG_FIELDS.contains(name))
            {
                throw new IllegalArgumentException("参照任务包含未知配置字段: " + name);
            }
        });
        if (!config.has("datasourceKey") || !config.has("sqlText") || !config.has("metadata")
                || !config.path("datasourceKey").isTextual() || !config.path("sqlText").isTextual()
                || !config.path("metadata").isObject())
        {
            throw new IllegalArgumentException("参照任务必须填写数据源、SQL 和字段元数据");
        }
        if (!READABLE_DATASOURCES.contains(config.path("datasourceKey").asText()))
        {
            // 参照运行时只登记了 U8 只读数据源，配置不能借名称尝试访问任意连接。
            throw new IllegalArgumentException("参照任务只能使用已登记的 U8 只读数据源");
        }
        // 深拷贝元数据，避免校验或控制面版本写入时意外修改调用方正在编辑的对象。
        ObjectNode metadata = ((ObjectNode) config.path("metadata")).deepCopy();
        ReferenceTask task = new ReferenceTask(taskCode, taskName, enabled, config.path("datasourceKey").asText(),
                config.path("sqlText").asText(), metadata);
        try
        {
            ReferenceMetadataValidator.validate(task);
            return task;
        }
        catch (ReferenceException ex)
        {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }
}
