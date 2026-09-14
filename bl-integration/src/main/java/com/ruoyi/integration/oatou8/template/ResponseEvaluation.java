package com.ruoyi.integration.oatou8.template;

import com.fasterxml.jackson.databind.node.ObjectNode;

/** 已确认成功的 U8 响应中可供后续查询、记录和人工核验使用的最小输出快照。 */
public record ResponseEvaluation(ObjectNode outputs)
{
}
