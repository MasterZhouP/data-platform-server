package com.ruoyi.integration.task;

/** 触发来源会与执行快照共同保存，便于区分人工、定时和 OA 插件的统一受理请求。 */
public enum TriggerSource
{
    MANUAL,
    SCHEDULED,
    RETRY,
    OA_API
}
