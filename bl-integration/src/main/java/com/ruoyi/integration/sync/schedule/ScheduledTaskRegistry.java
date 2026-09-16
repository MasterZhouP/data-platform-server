package com.ruoyi.integration.sync.schedule;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.ruoyi.integration.task.UnknownTaskException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ScheduledTaskRegistry
{
    private final Map<String, ScheduledTaskDefinition> definitions;
    private final ScheduledTaskDefinitionProvider provider;

    @Autowired
    public ScheduledTaskRegistry(List<ScheduledTaskDefinition> values,
            ObjectProvider<ScheduledTaskDefinitionProvider> provider)
    {
        this(values, provider.getIfAvailable());
    }

    public ScheduledTaskRegistry(List<ScheduledTaskDefinition> values)
    {
        this(values, (ScheduledTaskDefinitionProvider) null);
    }

    public ScheduledTaskRegistry(List<ScheduledTaskDefinition> values, ScheduledTaskDefinitionProvider provider)
    {
        Map<String, ScheduledTaskDefinition> registered = new LinkedHashMap<>();
        for (ScheduledTaskDefinition value : values)
        {
            String code = normalize(value.taskCode());
            if (registered.putIfAbsent(code, value) != null)
            {
                throw new IllegalStateException("重复的定时集成任务编码: " + code);
            }
        }
        definitions = Collections.unmodifiableMap(registered);
        this.provider = provider;
    }

    public ScheduledTaskDefinition require(String taskCode)
    {
        if (provider != null)
        {
            ScheduledTaskDefinition dynamic = provider.resolve(taskCode);
            if (dynamic != null)
            {
                return dynamic;
            }
        }
        ScheduledTaskDefinition value = definitions.get(normalize(taskCode));
        if (value == null)
        {
            throw new UnknownTaskException(taskCode);
        }
        return value;
    }

    private String normalize(String value)
    {
        return value == null ? "" : value.trim();
    }
}
