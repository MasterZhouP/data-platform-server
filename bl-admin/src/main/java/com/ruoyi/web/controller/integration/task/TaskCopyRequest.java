package com.ruoyi.web.controller.integration.task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Target identity used when copying a configured task into a new disabled draft. */
@JsonIgnoreProperties(ignoreUnknown = false)
public record TaskCopyRequest(String taskCode, String taskName, String changeNote)
{
}
