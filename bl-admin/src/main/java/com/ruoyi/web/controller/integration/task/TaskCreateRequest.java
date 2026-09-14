package com.ruoyi.web.controller.integration.task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.integration.taskdefinition.TaskType;

/** 创建时一次性确定稳定 taskCode 和任务类型，后续只能编辑其草稿版本。 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record TaskCreateRequest(String taskCode, String taskName, TaskType taskType, Boolean enabled,
        JsonNode config, String changeNote)
{
}
