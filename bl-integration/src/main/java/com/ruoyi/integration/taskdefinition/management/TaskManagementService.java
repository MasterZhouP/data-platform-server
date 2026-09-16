package com.ruoyi.integration.taskdefinition.management;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.oatou8.config.OaToU8TaskConfig;
import com.ruoyi.integration.oatou8.config.ResultQueryStep;
import com.ruoyi.integration.oatou8.config.TaskConfigValidator;
import com.ruoyi.integration.reference.catalog.ReferenceTaskConfigValidator;
import com.ruoyi.integration.reference.model.ReferenceTask;
import com.ruoyi.integration.u8tooa.config.U8ToOaTaskConfig;
import com.ruoyi.integration.u8tooa.config.U8ToOaTaskConfigValidator;
import com.ruoyi.integration.taskdefinition.IntegrationTaskDefinition;
import com.ruoyi.integration.taskdefinition.RevisionStatus;
import com.ruoyi.integration.taskdefinition.TaskDefinitionNotFoundException;
import com.ruoyi.integration.taskdefinition.TaskRevision;
import com.ruoyi.integration.taskdefinition.TaskType;
import com.ruoyi.integration.taskdefinition.mapper.IntegrationTaskMapper;
import com.ruoyi.integration.taskdefinition.mapper.IntegrationTaskRow;
import com.ruoyi.integration.taskdefinition.mapper.TaskRevisionRow;
import com.ruoyi.integration.taskdefinition.mapper.TaskRevisionWriteRow;
import com.ruoyi.integration.sync.schedule.IntegrationTaskScheduler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 集成任务的控制面状态机。
 * <p>运行中的单据只读取已锁定的修订；这里仅管理草稿、校验和发布，不直接执行 SQL 或调用 U8。</p>
 */
@Service
public class TaskManagementService
{
    private final IntegrationTaskMapper mapper;
    private final ObjectMapper json;
    private final TaskConfigValidator oaToU8Validator;
    private final ReferenceTaskConfigValidator referenceValidator;
    private final U8ToOaTaskConfigValidator u8ToOaValidator;
    private final IntegrationTaskScheduler scheduler;

    public TaskManagementService(IntegrationTaskMapper mapper, ObjectMapper json,
            TaskConfigValidator oaToU8Validator)
    {
        this(mapper, json, oaToU8Validator, new ReferenceTaskConfigValidator(),
                new U8ToOaTaskConfigValidator(json), null);
    }

    public TaskManagementService(IntegrationTaskMapper mapper, ObjectMapper json,
            TaskConfigValidator oaToU8Validator, ReferenceTaskConfigValidator referenceValidator)
    {
        this(mapper, json, oaToU8Validator, referenceValidator, new U8ToOaTaskConfigValidator(json));
    }

    public TaskManagementService(IntegrationTaskMapper mapper, ObjectMapper json,
            TaskConfigValidator oaToU8Validator, ReferenceTaskConfigValidator referenceValidator,
            U8ToOaTaskConfigValidator u8ToOaValidator)
    {
        this(mapper, json, oaToU8Validator, referenceValidator, u8ToOaValidator, null);
    }

    @Autowired
    public TaskManagementService(IntegrationTaskMapper mapper, ObjectMapper json,
            TaskConfigValidator oaToU8Validator, ReferenceTaskConfigValidator referenceValidator,
            U8ToOaTaskConfigValidator u8ToOaValidator, ObjectProvider<IntegrationTaskScheduler> scheduler)
    {
        this.mapper = mapper;
        this.json = json;
        this.oaToU8Validator = oaToU8Validator;
        this.referenceValidator = referenceValidator;
        this.u8ToOaValidator = u8ToOaValidator;
        this.scheduler = scheduler == null ? null : scheduler.getIfAvailable();
    }

    @Transactional
    public IntegrationTaskDefinition create(String taskCode, TaskDraftCommand command)
    {
        if (mapper.findTask(taskCode) != null)
        {
            throw new IllegalArgumentException("任务编码已存在: " + taskCode);
        }
        PreparedDraft prepared = prepare(taskCode, command);
        // 先建立稳定任务身份，再绑定数据库生成的第一份草稿，避免把版本号交给浏览器控制。
        mapper.insertTask(new IntegrationTaskRow(taskCode, command.taskName(), command.taskType().name(),
                command.enabled(), null, null, 0L));
        TaskRevisionWriteRow draft = newDraft(taskCode, mapper.nextRevisionNo(taskCode), prepared, command.changeNote());
        mapper.insertGeneratedRevision(draft);
        if (mapper.updateTaskDraft(taskCode, command.taskName(), command.enabled(), draft.getRevisionId(), 0L) != 1)
        {
            throw new TaskVersionConflictException(taskCode);
        }
        return new IntegrationTaskDefinition(taskCode, command.taskName(), command.taskType(), command.enabled(),
                null, draft.getRevisionId(), 1L);
    }

    /** Copies a task's current draft, or its published revision when no draft exists, into a disabled new draft. */
    @Transactional
    public IntegrationTaskDefinition copy(String sourceTaskCode, String targetTaskCode,
            String targetName, String changeNote)
    {
        validateTaskCode(targetTaskCode);
        if (sourceTaskCode == null || sourceTaskCode.isBlank() || sourceTaskCode.trim().equals(targetTaskCode))
        {
            throw new IllegalArgumentException("复制目标编码不能与来源任务相同");
        }
        IntegrationTaskRow source = requiredTask(sourceTaskCode.trim());
        if (TaskType.REFERENCE_QUERY.name().equals(source.taskType()))
        {
            throw new IllegalArgumentException("参照任务请在参照配置页中复制");
        }
        Long revisionId = source.draftRevisionId() != null ? source.draftRevisionId() : source.activeRevisionId();
        if (revisionId == null)
        {
            throw new IllegalArgumentException("来源任务没有可复制的配置版本");
        }
        TaskRevisionRow revision = requiredRevision(revisionId);
        String name = targetName == null ? null : targetName.trim();
        if (name == null || name.isBlank() || name.length() > 100)
        {
            throw new IllegalArgumentException("复制任务名称不能为空且不能超过100个字符");
        }
        return create(targetTaskCode, new TaskDraftCommand(name, TaskType.valueOf(source.taskType()), false,
                null, readConfig(revision), changeNote));
    }

    @Transactional
    public IntegrationTaskDefinition saveDraft(String taskCode, TaskDraftCommand command)
    {
        IntegrationTaskRow task = requiredTask(taskCode);
        requireExpectedVersion(taskCode, task, command.configVersion());
        requireMatchingType(task, command.taskType());
        PreparedDraft prepared = prepare(taskCode, command);
        Long draftRevisionId = task.draftRevisionId();
        if (draftRevisionId == null)
        {
            TaskRevisionWriteRow draft = newDraft(taskCode, mapper.nextRevisionNo(taskCode), prepared, command.changeNote());
            mapper.insertGeneratedRevision(draft);
            draftRevisionId = draft.getRevisionId();
        }
        else
        {
            // 编辑会使原有“已校验”结论失效，必须回到草稿状态并重新校验后才允许发布。
            TaskRevisionWriteRow draft = newDraft(taskCode, null, prepared, command.changeNote());
            draft.setRevisionId(draftRevisionId);
            if (mapper.replaceDraftRevision(draft) != 1)
            {
                throw new TaskVersionConflictException(taskCode);
            }
        }
        if (mapper.updateTaskDraft(taskCode, command.taskName(), command.enabled(), draftRevisionId,
                task.configVersion()) != 1)
        {
            throw new TaskVersionConflictException(taskCode);
        }
        return new IntegrationTaskDefinition(taskCode, command.taskName(), command.taskType(), command.enabled(),
                task.activeRevisionId(), draftRevisionId, task.configVersion() + 1);
    }

    @Transactional
    public IntegrationTaskDefinition validateDraft(String taskCode, Long expectedVersion)
    {
        IntegrationTaskRow task = requiredTask(taskCode);
        requireExpectedVersion(taskCode, task, expectedVersion);
        if (task.draftRevisionId() == null)
        {
            throw new IllegalArgumentException("当前任务没有待校验草稿");
        }
        TaskRevisionRow draft = requiredRevision(task.draftRevisionId());
        TaskDraftCommand command = new TaskDraftCommand(task.taskName(), TaskType.valueOf(task.taskType()), task.enabled(),
                task.configVersion(), readConfig(draft), null);
        PreparedDraft prepared = prepare(taskCode, command);
        if (mapper.markRevisionValidated(task.draftRevisionId(), validationJson(prepared)) != 1
                || mapper.advanceConfigVersion(taskCode, task.configVersion()) != 1)
        {
            throw new TaskVersionConflictException(taskCode);
        }
        return new IntegrationTaskDefinition(taskCode, task.taskName(), TaskType.valueOf(task.taskType()), task.enabled(),
                task.activeRevisionId(), task.draftRevisionId(), task.configVersion() + 1);
    }

    @Transactional
    public IntegrationTaskDefinition publish(String taskCode, Long expectedVersion)
    {
        IntegrationTaskRow task = requiredTask(taskCode);
        requireExpectedVersion(taskCode, task, expectedVersion);
        if (task.draftRevisionId() == null || !RevisionStatus.VALIDATED.name().equals(
                requiredRevision(task.draftRevisionId()).status()))
        {
            throw new TaskDraftNotValidatedException(taskCode);
        }
        // 旧生产版本只归档，不删除；已受理执行仍可按锁定修订读取它。
        if (task.activeRevisionId() != null && mapper.archiveRevision(task.activeRevisionId()) != 1)
        {
            throw new TaskVersionConflictException(taskCode);
        }
        if (mapper.publishRevision(task.draftRevisionId()) != 1
                || mapper.publishTask(taskCode, task.draftRevisionId(), task.configVersion()) != 1)
        {
            throw new TaskVersionConflictException(taskCode);
        }
        if (scheduler != null && TaskType.U8_TO_OA.name().equals(task.taskType()))
        {
            U8ToOaTaskConfig config = u8ToOaValidator.parseAndValidate(readConfig(requiredRevision(task.draftRevisionId())));
            scheduler.synchronize(taskCode, task.taskName(), task.enabled(), config.sync().cronExpression());
        }
        return new IntegrationTaskDefinition(taskCode, task.taskName(), TaskType.valueOf(task.taskType()), task.enabled(),
                task.draftRevisionId(), null, task.configVersion() + 1);
    }

    public List<IntegrationTaskDefinition> list()
    {
        return mapper.listTasks().stream().map(this::definition).toList();
    }

    public IntegrationTaskDefinition detail(String taskCode)
    {
        return definition(requiredTask(taskCode));
    }

    public List<TaskRevision> revisions(String taskCode)
    {
        requiredTask(taskCode);
        return mapper.listRevisions(taskCode).stream().map(this::revision).toList();
    }

    public TaskRevision revision(String taskCode, Long revisionId)
    {
        TaskRevisionRow row = requiredRevision(revisionId);
        if (!taskCode.equals(row.taskCode()))
        {
            throw new TaskDefinitionNotFoundException(taskCode);
        }
        return revision(row);
    }

    /**
     * 预览只能读取当前草稿，并要求携带控制面版本。
     * 这样管理员不会在不知情时用已被他人替换的配置访问 OA/U8 只读数据源。
     */
    public JsonNode draftConfig(String taskCode, Long expectedVersion)
    {
        IntegrationTaskRow task = requiredTask(taskCode);
        requireExpectedVersion(taskCode, task, expectedVersion);
        if (task.draftRevisionId() == null)
        {
            throw new IllegalArgumentException("当前任务没有可预览草稿");
        }
        return readConfig(requiredRevision(task.draftRevisionId())).deepCopy();
    }

    private PreparedDraft prepare(String taskCode, TaskDraftCommand command)
    {
        if (command == null || command.config() == null || command.taskName() == null || command.taskName().isBlank()
                || command.taskName().length() > 100 || command.taskType() == null)
        {
            throw new IllegalArgumentException("任务草稿的名称、类型或配置不合规");
        }
        if (command.taskType() == TaskType.OA_TO_U8)
        {
            // 发送任务和参照任务共用版本账本，但各自只接受对应的固定配置合同。
            OaToU8TaskConfig config = oaToU8Validator.parseAndValidate(command.config());
            return prepared(command.config(), dependencies(config));
        }
        if (command.taskType() == TaskType.REFERENCE_QUERY)
        {
            // 同步参照只登记 SQL 与字段元数据，不能夹带 U8 网关参数或异步推送步骤。
            ReferenceTask config = referenceValidator.parseAndValidate(taskCode, command.taskName(), command.enabled(),
                    command.config());
            return prepared(command.config(), dependencies(config));
        }
        if (command.taskType() == TaskType.U8_TO_OA)
        {
            U8ToOaTaskConfig config = u8ToOaValidator.parseAndValidate(command.config());
            return prepared(command.config(), dependencies(config));
        }
        throw new IllegalArgumentException("暂不支持页面配置 " + command.taskType() + " 类型任务");
    }

    private PreparedDraft prepared(JsonNode config, Map<String, String> dependencies)
    {
        return new PreparedDraft(CanonicalJsonChecksum.canonicalize(config, json),
                CanonicalJsonChecksum.sha256(config, json), dependencies);
    }

    private TaskRevisionWriteRow newDraft(String taskCode, Integer revisionNo, PreparedDraft prepared, String changeNote)
    {
        TaskRevisionWriteRow row = new TaskRevisionWriteRow();
        row.setTaskCode(taskCode);
        row.setRevisionNo(revisionNo);
        row.setStatus(RevisionStatus.DRAFT.name());
        row.setConfigJson(write(prepared.config()));
        row.setChecksum(prepared.checksum());
        row.setDependencyRevisionsJson(write(json.valueToTree(prepared.dependencies())));
        row.setValidationJson(null);
        row.setChangeNote(changeNote == null || changeNote.isBlank() ? null : changeNote.trim());
        return row;
    }

    private Map<String, String> dependencies(OaToU8TaskConfig config)
    {
        LinkedHashSet<String> sources = new LinkedHashSet<>();
        config.dataSteps().forEach(step -> sources.add(step.datasourceKey()));
        config.resultQueries().forEach(step -> sources.add(step.datasourceKey()));
        Map<String, String> result = new LinkedHashMap<>();
        sources.forEach(source -> result.put("datasource:" + source, "registered-readonly"));
        // 任务只引用唯一受管网关键；账户、token、tradeId 和连接密钥均不进入版本数据。
        result.put("u8Gateway", "u8-default");
        return Map.copyOf(result);
    }

    private Map<String, String> dependencies(ReferenceTask config)
    {
        // 参照链路只依赖已登记的只读数据源，不借用 U8 网关、token 或任何写库权限。
        return Map.of("datasource:" + config.datasourceKey(), "registered-readonly");
    }

    private Map<String, String> dependencies(U8ToOaTaskConfig config)
    {
        LinkedHashSet<String> sources = new LinkedHashSet<>();
        config.dataSteps().forEach(step -> sources.add(step.datasourceKey()));
        sources.add(config.sync().datasourceKey());
        Map<String, String> result = new LinkedHashMap<>();
        sources.forEach(source -> result.put("datasource:" + source, "registered-readonly"));
        result.put("oaGateway", "oa-default");
        return Map.copyOf(result);
    }

    private String validationJson(PreparedDraft prepared)
    {
        ObjectNode result = json.createObjectNode();
        result.put("valid", true);
        result.put("checksum", prepared.checksum());
        result.put("validatedBy", "server");
        result.set("dependencies", json.valueToTree(prepared.dependencies()));
        return write(result);
    }

    private void requireExpectedVersion(String taskCode, IntegrationTaskRow task, Long expectedVersion)
    {
        if (expectedVersion == null || !expectedVersion.equals(task.configVersion()))
        {
            throw new TaskVersionConflictException(taskCode);
        }
    }

    private void requireMatchingType(IntegrationTaskRow task, TaskType type)
    {
        if (type == null || !task.taskType().equals(type.name()))
        {
            throw new IllegalArgumentException("任务类型创建后不能变更");
        }
    }

    private IntegrationTaskRow requiredTask(String taskCode)
    {
        IntegrationTaskRow row = mapper.findTask(taskCode);
        if (row == null)
        {
            throw new TaskDefinitionNotFoundException(taskCode);
        }
        return row;
    }

    private void validateTaskCode(String taskCode)
    {
        if (taskCode == null || !taskCode.matches("[A-Z][A-Z0-9_]{0,99}"))
        {
            throw new IllegalArgumentException("任务编码只能使用大写字母、数字和下划线，且必须以字母开头");
        }
    }

    private TaskRevisionRow requiredRevision(Long revisionId)
    {
        TaskRevisionRow row = mapper.findRevision(revisionId);
        if (row == null)
        {
            throw new IllegalStateException("任务修订不存在: " + revisionId);
        }
        return row;
    }

    private IntegrationTaskDefinition definition(IntegrationTaskRow row)
    {
        return new IntegrationTaskDefinition(row.taskCode(), row.taskName(), TaskType.valueOf(row.taskType()),
                row.enabled(), row.activeRevisionId(), row.draftRevisionId(), row.configVersion());
    }

    private TaskRevision revision(TaskRevisionRow row)
    {
        try
        {
            @SuppressWarnings("unchecked")
            Map<String, String> dependencies = row.dependencyRevisionsJson() == null ? Map.of()
                    : json.readValue(row.dependencyRevisionsJson(), Map.class);
            return new TaskRevision(row.revisionId(), row.taskCode(), row.revisionNo(), RevisionStatus.valueOf(row.status()),
                    readConfig(row), row.checksum(), Map.copyOf(dependencies));
        }
        catch (Exception ex)
        {
            throw new IllegalStateException("任务修订配置无法读取: " + row.revisionId(), ex);
        }
    }

    private JsonNode readConfig(TaskRevisionRow row)
    {
        try { return json.readTree(row.configJson()); }
        catch (Exception ex) { throw new IllegalStateException("任务修订配置无法读取: " + row.revisionId(), ex); }
    }

    private String write(JsonNode value)
    {
        try { return json.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalStateException("任务修订配置无法写入", ex); }
    }

    private record PreparedDraft(JsonNode config, String checksum, Map<String, String> dependencies) { }
}
