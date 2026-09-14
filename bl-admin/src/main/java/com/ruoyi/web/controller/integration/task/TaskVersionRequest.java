package com.ruoyi.web.controller.integration.task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 校验和发布都必须基于页面读取到的控制面版本。 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record TaskVersionRequest(Long configVersion)
{
}
