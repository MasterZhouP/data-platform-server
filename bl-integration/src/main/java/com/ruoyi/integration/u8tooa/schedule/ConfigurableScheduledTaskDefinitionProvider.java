package com.ruoyi.integration.u8tooa.schedule;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.integration.sync.schedule.ScheduledTaskDefinition;
import com.ruoyi.integration.sync.schedule.ScheduledTaskDefinitionProvider;
import com.ruoyi.integration.taskdefinition.PublishedTaskRevision;
import com.ruoyi.integration.taskdefinition.TaskDefinitionNotFoundException;
import com.ruoyi.integration.taskdefinition.TaskDefinitionResolver;
import com.ruoyi.integration.taskdefinition.TaskType;
import com.ruoyi.integration.u8tooa.config.U8ToOaTaskConfig;
import com.ruoyi.integration.u8tooa.config.U8ToOaTaskConfigValidator;
import org.springframework.stereotype.Component;

/** Resolves enabled published U8-to-OA revisions into scheduled definitions on demand. */
@Component
public class ConfigurableScheduledTaskDefinitionProvider implements ScheduledTaskDefinitionProvider
{
    private final TaskDefinitionResolver tasks;
    private final U8ToOaTaskConfigValidator validator;
    private final ConfigurableU8ToOaChangeSource source;

    public ConfigurableScheduledTaskDefinitionProvider(TaskDefinitionResolver tasks,
            U8ToOaTaskConfigValidator validator, ConfigurableU8ToOaChangeSource source)
    {
        this.tasks = tasks;
        this.validator = validator;
        this.source = source;
    }

    @Override
    public ScheduledTaskDefinition resolve(String taskCode)
    {
        final PublishedTaskRevision revision;
        try
        {
            revision = tasks.resolvePublished(taskCode);
        }
        catch (TaskDefinitionNotFoundException ex)
        {
            return null;
        }
        if (revision.taskType() != TaskType.U8_TO_OA)
        {
            return null;
        }
        U8ToOaTaskConfig config = validator.parseAndValidate((JsonNode) revision.config());
        return new U8ToOaScheduledDefinition(revision.taskCode(), config.sync(), source);
    }
}
