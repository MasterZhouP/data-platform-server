package com.ruoyi.integration.taskdefinition.management;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.integration.taskdefinition.TaskType;

/** 页面传入的受控草稿内容，不包含 U8 凭据、主机或任何写库指令。 */
public record TaskDraftCommand(String taskName, TaskType taskType, boolean enabled, Long configVersion,
        JsonNode config, String changeNote)
{
}
