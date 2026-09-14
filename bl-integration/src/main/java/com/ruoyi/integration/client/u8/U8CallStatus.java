package com.ruoyi.integration.client.u8;

public enum U8CallStatus
{
    /** HTTP 已响应，等待任务定义的 JSON 成功规则做最终业务确认。 */
    SUCCESS,
    /** U8 已明确拒绝本次业务，不应按网络故障盲目重推。 */
    BUSINESS_FAILURE,
    /** 业务请求尚未可能到达 U8，可按平台重试规则完整重试。 */
    PRE_SEND_FAILURE,
    /** 请求可能已到达 U8，但未能确认结果，必须人工核验。 */
    RESULT_UNKNOWN
}
