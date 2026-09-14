package com.ruoyi.integration.task;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import com.ruoyi.integration.execution.service.ResolvedExecution;
import com.ruoyi.integration.taskdefinition.TaskType;

class TaskExecutorRegistryTest
{
    @Test
    void routesAllConfiguredTaskCodesOfTheSameTypeToOneExecutor()
    {
        TaskExecutor executor = new TaskExecutor()
        {
            @Override public TaskType taskType() { return TaskType.OA_TO_U8; }
            @Override public PushResult execute(ResolvedExecution execution, ExecutionStageRecorder recorder) { return null; }
        };

        assertSame(executor, new TaskExecutorRegistry(List.of(executor)).require(TaskType.OA_TO_U8));
        assertThrows(UnknownTaskExecutorException.class,
                () -> new TaskExecutorRegistry(List.of(executor)).require(TaskType.REFERENCE_QUERY));
    }
}
