package com.ruoyi.integration.execution.domain;

public enum ExecutionStatus
{
    PENDING,
    RUNNING,
    SUCCESS,
    /** U8 已确认成功，但规定的结果查询/后处理尚未完成，只能从检查点续跑。 */
    PARTIAL_SUCCESS,
    FAILED,
    SKIPPED,
    RESULT_UNKNOWN
}
