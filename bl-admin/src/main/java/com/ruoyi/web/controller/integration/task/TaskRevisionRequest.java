package com.ruoyi.web.controller.integration.task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.integration.taskdefinition.TaskType;

/** 管理端保存草稿的请求；任务执行行为只能来自受控 config，不能携带网关账户或任意步骤。 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record TaskRevisionRequest(String taskName, TaskType taskType, Boolean enabled, Long configVersion,
        JsonNode config, String changeNote)
{
}
