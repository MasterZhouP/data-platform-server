package com.ruoyi.integration.oatou8;

import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;

/** 只读预览结果：展示数据准备结果和待发送 JSON，但绝不产生 U8 调用。 */
public record OaToU8Preview(Map<String, Object> data, JsonNode request)
{
}
