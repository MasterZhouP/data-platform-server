package com.ruoyi.integration.taskdefinition;

import java.util.LinkedHashMap;
import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.integration.taskdefinition.mapper.IntegrationTaskMapper;
import com.ruoyi.integration.taskdefinition.mapper.IntegrationTaskRow;
import com.ruoyi.integration.taskdefinition.mapper.TaskRevisionRow;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisTaskDefinitionRepository implements TaskDefinitionRepository
{
    private static final TypeReference<LinkedHashMap<String, String>> DEPENDENCY_REVISIONS = new TypeReference<>() { };

    private final IntegrationTaskMapper mapper;
    private final ObjectMapper json;

    public MyBatisTaskDefinitionRepository(IntegrationTaskMapper mapper, ObjectMapper json)
    {
        this.mapper = mapper;
        this.json = json;
    }

    @Override
    public IntegrationTaskDefinition findTask(String taskCode)
    {
        IntegrationTaskRow row = mapper.findTask(taskCode);
        return row == null ? null : new IntegrationTaskDefinition(row.taskCode(), row.taskName(),
                TaskType.valueOf(row.taskType()), row.enabled(), row.activeRevisionId(),
                row.draftRevisionId(), row.configVersion());
    }

    @Override
    public TaskRevision findRevision(Long revisionId)
    {
        TaskRevisionRow row = mapper.findRevision(revisionId);
        if (row == null)
        {
            return null;
        }
        try
        {
            JsonNode config = json.readTree(row.configJson());
            Map<String, String> dependencies = row.dependencyRevisionsJson() == null
                    ? Map.of() : Map.copyOf(json.readValue(row.dependencyRevisionsJson(), DEPENDENCY_REVISIONS));
            // 这里读取的是发布时保存的内容，运行时不会重新读取草稿或外部连接当前配置。
            return new TaskRevision(row.revisionId(), row.taskCode(), row.revisionNo(),
                    RevisionStatus.valueOf(row.status()), config, row.checksum(), dependencies);
        }
        catch (Exception ex)
        {
            throw new IllegalStateException("集成任务修订配置无法读取: " + row.revisionId(), ex);
        }
    }
}
