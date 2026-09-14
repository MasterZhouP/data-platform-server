package com.ruoyi.integration.taskdefinition;

import org.springframework.stereotype.Service;

@Service
public class TaskDefinitionResolver
{
    private final TaskDefinitionRepository repository;

    public TaskDefinitionResolver(TaskDefinitionRepository repository)
    {
        this.repository = repository;
    }

    public PublishedTaskRevision resolvePublished(String taskCode)
    {
        String normalized = normalize(taskCode);
        IntegrationTaskDefinition task = repository.findTask(normalized);
        // 业务入口只允许精确匹配的稳定任务编码，避免数据库不区分大小写时路由到错误任务。
        if (task == null || !normalized.equals(task.taskCode()) || task.activeRevisionId() == null)
        {
            throw new TaskDefinitionNotFoundException(normalized);
        }
        // 停用在受理阶段阻断，已受理执行则继续使用自己的快照，不会被后续启停影响。
        if (!task.enabled())
        {
            throw new TaskDisabledException(normalized);
        }

        TaskRevision revision = repository.findRevision(task.activeRevisionId());
        if (revision == null || !normalized.equals(revision.taskCode())
                || !RevisionStatus.PUBLISHED.equals(revision.status()))
        {
            throw new TaskDefinitionNotFoundException(normalized);
        }
        return new PublishedTaskRevision(task.taskCode(), revision.revisionId(), revision.checksum(),
                task.taskType(), revision.config(), revision.dependencyRevisions());
    }

    private String normalize(String taskCode)
    {
        if (taskCode == null || taskCode.isBlank())
        {
            throw new TaskDefinitionNotFoundException("");
        }
        return taskCode.trim();
    }
}
