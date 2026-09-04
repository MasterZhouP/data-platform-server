package com.ruoyi.integration.task;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PushHandlerRegistry
{
    private final Map<String, OaToU8PushHandler> handlers;

    public PushHandlerRegistry(List<OaToU8PushHandler> handlerList)
    {
        Map<String, OaToU8PushHandler> registered = new LinkedHashMap<>();
        for (OaToU8PushHandler handler : handlerList)
        {
            String taskCode = normalize(handler.taskCode());
            if (registered.putIfAbsent(taskCode, handler) != null)
            {
                throw new IllegalStateException("重复的集成任务编码: " + taskCode);
            }
        }
        this.handlers = Collections.unmodifiableMap(registered);
    }

    public OaToU8PushHandler require(String taskCode)
    {
        OaToU8PushHandler handler = handlers.get(normalize(taskCode));
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
