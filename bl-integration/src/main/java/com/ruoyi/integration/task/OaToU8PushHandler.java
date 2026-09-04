package com.ruoyi.integration.task;

/** One business task implementation. The Handler reloads OA data on every invocation. */
public interface OaToU8PushHandler
{
    String taskCode();

    default String dedupKey(TriggerCommand command)
    {
        return command.taskCode() + ":" + command.masterId();
    }

    PushResult execute(PushExecutionContext context, ExecutionStageRecorder recorder);
}
