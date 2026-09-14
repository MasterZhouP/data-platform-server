package com.ruoyi.integration.oatou8;

/** 管理员调试时提供的模拟 OA 上下文；它与 OA 插件受理协议保持同一组业务定位字段。 */
public record OaToU8PreviewInput(String masterId, String formId, String summaryId)
{
}
