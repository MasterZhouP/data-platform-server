package com.ruoyi.integration.task;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PushHandlerRegistry
{
    private final Map<String, IntegrationTaskHandler> handlers;

    public PushHandlerRegistry(List<IntegrationTaskHandler> handlerList)
    {
        Map<String, IntegrationTaskHandler> registered = new LinkedHashMap<>();
        for (IntegrationTaskHandler handler : handlerList)
        {
            String taskCode = normalize(handler.taskCode());
            if (registered.putIfAbsent(taskCode, handler) != null)
            {
                throw new IllegalStateException("重复的集成任务编码: " + taskCode);
            }
        }
        this.handlers = Collections.unmodifiableMap(registered);
    }

    public IntegrationTaskHandler require(String taskCode)
    {
        IntegrationTaskHandler handler = handlers.get(normalize(taskCode));
        if (handler == null)
        {
            throw new UnknownTaskException(taskCode);
        }
        return handler;
    }

    private String normalize(String taskCode)
    {
        if (taskCode == null || taskCode.isBlank())
        {
            return "";
        }
        return taskCode.trim();
    }
}
