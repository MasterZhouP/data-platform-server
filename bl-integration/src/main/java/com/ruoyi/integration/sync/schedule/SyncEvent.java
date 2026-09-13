package com.ruoyi.integration.sync.schedule;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import com.ruoyi.integration.task.TaskAction;

public record SyncEvent(String sourceEventId, String taskCode, String documentNo, TaskAction action,
        LocalDateTime changedAt, SyncEventStatus status, Long executionId)
{
    public static SyncEvent from(String taskCode, SyncCandidate candidate)
    {
        String documentNo = candidate.documentNo().trim();
        String canonical = taskCode + "\n" + documentNo + "\n" + candidate.action().name()
                + "\n" + candidate.changedAt() + "\n" + candidate.versionToken();
        return new SyncEvent(sha256(canonical), taskCode, documentNo, candidate.action(),
                candidate.changedAt(), SyncEventStatus.PENDING, null);
    }

    public SyncEvent withStatus(SyncEventStatus newStatus, Long newExecutionId)
    {
        return new SyncEvent(sourceEventId, taskCode, documentNo, action, changedAt, newStatus, newExecutionId);
    }

    private static String sha256(String value)
    {
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException("SHA-256不可用", ex);
        }
    }
}
