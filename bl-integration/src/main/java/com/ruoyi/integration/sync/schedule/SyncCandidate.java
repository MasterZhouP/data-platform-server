package com.ruoyi.integration.sync.schedule;

import java.time.LocalDateTime;
import com.ruoyi.integration.task.TaskAction;

public record SyncCandidate(String documentNo, TaskAction action, LocalDateTime changedAt, String versionToken)
{
    public SyncCandidate(String documentNo, TaskAction action, LocalDateTime changedAt)
    {
        this(documentNo, action, changedAt, null);
    }
}
