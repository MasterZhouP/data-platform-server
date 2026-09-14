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

    /**
     * 运行器按受理时已写入执行账本的修订读取配置。
     * 任务后来被停用、或旧修订被归档，都不能中断已经接受的单据；这里只校验任务归属、校验和与不可变修订内容。
     */
    public PublishedTaskRevision resolvePinned(String taskCode, Long revisionId, String checksum)
    {
        String normalized = normalize(taskCode);
        if (revisionId == null || checksum == null || checksum.isBlank())
        {
            throw new TaskDefinitionNotFoundException(normalized);
        }
        IntegrationTaskDefinition task = repository.findTask(normalized);
        TaskRevision revision = repository.findRevision(revisionId);
        if (task == null || !normalized.equals(task.taskCode()) || revision == null
                || !normalized.equals(revision.taskCode()) || !checksum.equals(revision.checksum())
                || (revision.status() != RevisionStatus.PUBLISHED && revision.status() != RevisionStatus.ARCHIVED))
        {
            throw new TaskDefinitionNotFoundException(normalized);
        }
        return new PublishedTaskRevision(task.taskCode(), revision.revisionId(), revision.checksum(),
                task.taskType(), revision.config(), revision.dependencyRevisions());
    }

    /**
     * 区分“尚未纳入任务目录的旧代码任务”和“已经建档但未发布的配置任务”。
     * 后者必须在受理阶段报错，不能因为恰好同名而回退到遗留 Handler。
     */
    public boolean isCatalogTask(String taskCode)
    {
        if (taskCode == null || taskCode.isBlank())
        {
            return false;
        }
        String normalized = taskCode.trim();
        IntegrationTaskDefinition task = repository.findTask(normalized);
        return task != null && normalized.equals(task.taskCode());
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
