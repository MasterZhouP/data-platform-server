package com.ruoyi.web.controller.integration.task;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.integration.oatou8.OaToU8Preview;
import com.ruoyi.integration.oatou8.OaToU8PreviewInput;
import com.ruoyi.integration.oatou8.OaToU8PreviewService;
import com.ruoyi.integration.taskdefinition.IntegrationTaskDefinition;
import com.ruoyi.integration.taskdefinition.TaskRevision;
import com.ruoyi.integration.taskdefinition.TaskType;
import com.ruoyi.integration.taskdefinition.management.TaskDraftCommand;
import com.ruoyi.integration.taskdefinition.management.TaskManagementService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统一任务工作台的管理接口。
 * 这里管理版本化定义；OA 插件仍只调用 /integration/openapi/v1/executions，二者不会混为一套入口。
 */
@RestController
@RequestMapping("/integration/tasks")
public class IntegrationTaskAdminController
{
    private final TaskManagementService tasks;
    private final OaToU8PreviewService preview;

    public IntegrationTaskAdminController(TaskManagementService tasks, OaToU8PreviewService preview)
    {
        this.tasks = tasks;
        this.preview = preview;
    }

    @GetMapping
    @PreAuthorize("@ss.hasPermi('integration:task:list')")
    public AjaxResult list()
    {
        return AjaxResult.success(tasks.list());
    }

    @GetMapping("/{taskCode}")
    @PreAuthorize("@ss.hasPermi('integration:task:list')")
    public AjaxResult detail(@PathVariable String taskCode)
    {
        IntegrationTaskDefinition task = tasks.detail(taskCode);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("task", task);
        data.put("activeRevision", task.activeRevisionId() == null ? null : tasks.revision(taskCode, task.activeRevisionId()));
        data.put("draftRevision", task.draftRevisionId() == null ? null : tasks.revision(taskCode, task.draftRevisionId()));
        return AjaxResult.success(data);
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermi('integration:task:edit')")
    @Log(title = "创建集成任务草稿", businessType = BusinessType.INSERT)
    public AjaxResult create(@RequestBody TaskCreateRequest request)
    {
        String taskCode = taskCode(request.taskCode());
        IntegrationTaskDefinition created = tasks.create(taskCode, command(request.taskName(), request.taskType(), request.enabled(),
                null, request.config(), request.changeNote()));
        return AjaxResult.success(created);
    }

    @PutMapping("/{taskCode}/draft")
    @PreAuthorize("@ss.hasPermi('integration:task:edit')")
    @Log(title = "保存集成任务草稿", businessType = BusinessType.UPDATE)
    public AjaxResult saveDraft(@PathVariable String taskCode, @RequestBody TaskRevisionRequest request)
    {
        IntegrationTaskDefinition existing = tasks.detail(taskCode);
        TaskType type = request.taskType() == null ? existing.taskType() : request.taskType();
        return AjaxResult.success(tasks.saveDraft(taskCode, command(request.taskName(), type, request.enabled(),
                request.configVersion(), request.config(), request.changeNote())));
    }

    @PostMapping("/{taskCode}/validate")
    @PreAuthorize("@ss.hasPermi('integration:task:edit')")
    public AjaxResult validateDraft(@PathVariable String taskCode, @RequestBody TaskVersionRequest request)
    {
        IntegrationTaskDefinition validated = tasks.validateDraft(taskCode, requiredVersion(request.configVersion()));
        return AjaxResult.success(Map.of("task", validated,
                "checklist", List.of(Map.of("code", "CONFIG_VALID", "passed", true, "message", "草稿通过受控配置校验"))));
    }

    @PostMapping("/{taskCode}/preview")
    @PreAuthorize("@ss.hasPermi('integration:task:preview')")
    public AjaxResult preview(@PathVariable String taskCode, @RequestBody TaskPreviewRequest request)
    {
        OaToU8Preview result = preview.preview(tasks.draftConfig(taskCode, requiredVersion(request.configVersion())),
                new OaToU8PreviewInput(requiredText(request.masterId(), "masterId", 200),
                        optionalText(request.formId(), "formId", 200), optionalText(request.summaryId(), "summaryId", 200)));
        return AjaxResult.success(Map.of("data", result.data(), "request", result.request()));
    }

    @PostMapping("/{taskCode}/publish")
    @PreAuthorize("@ss.hasPermi('integration:task:publish')")
    @Log(title = "发布集成任务", businessType = BusinessType.UPDATE)
    public AjaxResult publish(@PathVariable String taskCode, @RequestBody TaskVersionRequest request)
    {
        return AjaxResult.success(tasks.publish(taskCode, requiredVersion(request.configVersion())));
    }

    @GetMapping("/{taskCode}/revisions")
    @PreAuthorize("@ss.hasPermi('integration:task:list')")
    public AjaxResult revisions(@PathVariable String taskCode)
    {
        return AjaxResult.success(tasks.revisions(taskCode));
    }

    private TaskDraftCommand command(String taskName, TaskType taskType, Boolean enabled, Long version,
            com.fasterxml.jackson.databind.JsonNode config, String changeNote)
    {
        return new TaskDraftCommand(requiredText(taskName, "taskName", 100), taskType,
                enabled != null && enabled, version, config, optionalText(changeNote, "changeNote", 500));
    }

    private String taskCode(String value)
    {
        String code = requiredText(value, "taskCode", 100);
        if (!code.matches("[A-Z][A-Z0-9_]{0,99}"))
        {
            throw new IllegalArgumentException("taskCode 只能使用大写字母、数字和下划线，且必须以字母开头");
        }
        return code;
    }

    private Long requiredVersion(Long value)
    {
        if (value == null || value < 0)
        {
            throw new IllegalArgumentException("configVersion 不能为空");
        }
        return value;
    }

    private String requiredText(String value, String field, int maximum)
    {
        String normalized = optionalText(value, field, maximum);
        if (normalized == null)
        {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return normalized;
    }

    private String optionalText(String value, String field, int maximum)
    {
        if (value == null || value.trim().isEmpty())
        {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maximum)
        {
            throw new IllegalArgumentException(field + " 长度超过允许范围");
        }
        return normalized;
    }
}
