package com.ruoyi.integration.task;

/** Direction-neutral contract implemented by each registered integration task. */
public interface IntegrationTaskHandler
{
    String taskCode();

    default String dedupKey(TriggerCommand command)
    {
        return command.taskCode() + ":" + command.masterId();
    }

    /** Keep the key after success for one-shot integrations; repeatable sync tasks override this. */
    default boolean retainDedupAfterSuccess()
    {
        return true;
    }

    PushResult execute(PushExecutionContext context, ExecutionStageRecorder recorder);
}
