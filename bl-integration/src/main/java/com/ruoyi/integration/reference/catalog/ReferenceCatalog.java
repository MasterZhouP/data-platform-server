package com.ruoyi.integration.reference.catalog;

import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.reference.model.ReferenceException;
import com.ruoyi.integration.reference.model.ReferenceTask;
import com.ruoyi.integration.taskdefinition.IntegrationTaskDefinition;
import com.ruoyi.integration.taskdefinition.TaskDefinitionNotFoundException;
import com.ruoyi.integration.taskdefinition.TaskRevision;
import com.ruoyi.integration.taskdefinition.TaskType;
import com.ruoyi.integration.taskdefinition.management.TaskDraftCommand;
import com.ruoyi.integration.taskdefinition.management.TaskManagementService;
import com.ruoyi.integration.taskdefinition.management.TaskVersionConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 同步参照任务的兼容门面。
 * <p>
 * 对外仍保留原有参照接口和查询语义，配置的唯一事实来源已经切换到统一任务目录的已发布修订；
 * 旧表只在升级脚本中作为迁移输入，不再决定生产查询使用的 SQL。
 * </p>
 */
@Service
public class ReferenceCatalog
{
    private final TaskManagementService tasks;
    private final ReferenceTaskConfigValidator validator;
    private final ObjectMapper json;

    public ReferenceCatalog(TaskManagementService tasks, ReferenceTaskConfigValidator validator, ObjectMapper json)
    {
        this.tasks = tasks;
        this.validator = validator;
        this.json = json;
    }

    public List<ReferenceTask> list()
    {
        return tasks.list().stream()
                .filter(task -> task.taskType() == TaskType.REFERENCE_QUERY && task.activeRevisionId() != null)
                .map(this::publishedTask)
                .toList();
    }

    public ReferenceTask get(String taskCode)
    {
        try
        {
            IntegrationTaskDefinition task = tasks.detail(taskCode);
            // 即使底层旧库是不区分大小写的，参照 URL 的 taskCode 仍必须保持稳定、精确匹配。
            if (!task.taskCode().equals(taskCode) || task.taskType() != TaskType.REFERENCE_QUERY
                    || task.activeRevisionId() == null)
            {
                throw notFound();
            }
            return publishedTask(task);
        }
        catch (TaskDefinitionNotFoundException ex)
        {
            throw notFound();
        }
    }

    /**
     * 兼容旧参照页面“一次保存即可生效”的交互，但后台仍完整经历草稿、校验、发布三个状态。
     * 任一步失败都会回滚，避免未校验 SQL 被同步查询链路使用。
     */
    @Transactional
    public ReferenceTask create(ReferenceTask input)
    {
        ReferenceTask versioned = versioned(input);
        validate(versioned);
        try
        {
            IntegrationTaskDefinition draft = tasks.create(versioned.taskCode(), command(versioned, null, "创建参照任务"));
            IntegrationTaskDefinition validated = tasks.validateDraft(versioned.taskCode(), draft.configVersion());
            tasks.publish(versioned.taskCode(), validated.configVersion());
            return get(versioned.taskCode());
        }
        catch (TaskVersionConflictException ex)
        {
            throw versionMismatch();
        }
        catch (IllegalArgumentException ex)
        {
            throw new ReferenceException("INVALID_ARGUMENT", 400, ex.getMessage(), false);
        }
    }

    /**
     * 参照配置保存也生成新修订，而不覆盖正在使用的生产版本。
     * 页面携带的 metadataVersion 用于先发现并发编辑，再使用统一控制面的 configVersion 完成最终乐观锁。
     */
    @Transactional
    public ReferenceTask save(String taskCode, ReferenceTask input)
    {
        if (!taskCode.equals(input.taskCode()))
        {
            throw new ReferenceException("INVALID_ARGUMENT", 400, "任务编码不能修改", false);
        }
        ReferenceTask current = get(taskCode);
        if (!current.metadata().path("metadataVersion").asText().equals(input.metadata().path("metadataVersion").asText()))
        {
            throw versionMismatch();
        }
        ReferenceTask versioned = versioned(input);
        validate(versioned);
        try
        {
            IntegrationTaskDefinition latest = tasks.detail(taskCode);
            IntegrationTaskDefinition draft = tasks.saveDraft(taskCode,
                    command(versioned, latest.configVersion(), "更新参照任务"));
            IntegrationTaskDefinition validated = tasks.validateDraft(taskCode, draft.configVersion());
            tasks.publish(taskCode, validated.configVersion());
            return get(taskCode);
        }
        catch (TaskVersionConflictException ex)
        {
            throw versionMismatch();
        }
        catch (IllegalArgumentException ex)
        {
            throw new ReferenceException("INVALID_ARGUMENT", 400, ex.getMessage(), false);
        }
    }

    private ReferenceTask publishedTask(IntegrationTaskDefinition task)
    {
        TaskRevision revision = tasks.revision(task.taskCode(), task.activeRevisionId());
        try
        {
            return validator.parseAndValidate(task.taskCode(), task.taskName(), task.enabled(), revision.config());
        }
        catch (IllegalArgumentException ex)
        {
            // 已发布配置不应在运行时失效；若发生，宁可拒绝参照请求也不能回退到旧表或资源文件。
            throw new ReferenceException("SERVICE_UNAVAILABLE", 503, "参照任务已发布配置无法读取，请联系管理员", false);
        }
    }

    private void validate(ReferenceTask task)
    {
        validator.parseAndValidate(task.taskCode(), task.taskName(), task.enabled(), config(task));
    }

    private TaskDraftCommand command(ReferenceTask task, Long configVersion, String changeNote)
    {
        return new TaskDraftCommand(task.taskName(), TaskType.REFERENCE_QUERY, task.enabled(), configVersion,
                config(task), changeNote);
    }

    private ObjectNode config(ReferenceTask task)
    {
        ObjectNode config = json.createObjectNode();
        config.put("datasourceKey", task.datasourceKey());
        config.put("sqlText", task.sqlText());
        config.set("metadata", task.metadata().deepCopy());
        return config;
    }

    private ReferenceTask versioned(ReferenceTask input)
    {
        if (input == null || input.metadata() == null)
        {
            throw new ReferenceException("INVALID_ARGUMENT", 400, "缺少参照任务配置", false);
        }
        ObjectNode metadata = input.metadata().deepCopy();
        metadata.put("metadataVersion", UUID.randomUUID().toString());
        metadata.put("taskCode", input.taskCode());
        metadata.put("taskName", input.taskName());
        return new ReferenceTask(input.taskCode(), input.taskName(), input.enabled(), input.datasourceKey(),
                input.sqlText(), metadata);
    }

    private ReferenceException notFound()
    {
        return new ReferenceException("TASK_NOT_FOUND", 404, "参照任务不存在", false);
    }

    private ReferenceException versionMismatch()
    {
        return new ReferenceException("METADATA_VERSION_MISMATCH", 409, "配置已更新，请刷新后再保存", false);
    }
}
