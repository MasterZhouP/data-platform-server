package com.ruoyi.integration.task;

import com.ruoyi.integration.execution.service.ResolvedExecution;
import com.ruoyi.integration.taskdefinition.TaskType;

/**
 * 配置型任务按类型共享的执行入口。
 * taskCode 只负责定位任务修订；同一类型的多个任务复用一个固定业务骨架，而不是各自注册 Spring Handler。
 */
public interface TaskExecutor
{
    TaskType taskType();

    PushResult execute(ResolvedExecution execution, ExecutionStageRecorder recorder);
}
