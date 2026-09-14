package com.ruoyi.web.controller.integration.task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 预览使用的模拟 OA 单据定位信息，不是 OA 插件对外协议的一部分。 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record TaskPreviewRequest(Long configVersion, String masterId, String formId, String summaryId)
{
}
