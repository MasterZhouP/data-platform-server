package com.ruoyi.integration.task;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import com.ruoyi.integration.taskdefinition.TaskType;
import org.springframework.stereotype.Component;

/** 以任务类型路由配置型任务，确保新增 OA→U8 任务不需要新增 Java Handler。 */
@Component
public class TaskExecutorRegistry
{
    private final Map<TaskType, TaskExecutor> executors;

    public TaskExecutorRegistry(List<TaskExecutor> executorList)
    {
        Map<TaskType, TaskExecutor> registered = new EnumMap<>(TaskType.class);
        for (TaskExecutor executor : executorList)
        {
            if (registered.putIfAbsent(executor.taskType(), executor) != null)
            {
                throw new IllegalStateException("重复的集成任务执行器类型: " + executor.taskType());
            }
        }
        this.executors = Collections.unmodifiableMap(registered);
    }

    public TaskExecutor require(TaskType taskType)
    {
        TaskExecutor executor = executors.get(taskType);
        if (executor == null)
        {
            throw new UnknownTaskExecutorException(taskType);
        }
        return executor;
    }
}
