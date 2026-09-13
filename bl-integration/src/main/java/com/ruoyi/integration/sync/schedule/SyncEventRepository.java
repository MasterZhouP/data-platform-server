package com.ruoyi.integration.sync.schedule;

import java.util.List;

public interface SyncEventRepository
{
    void register(String taskCode, List<SyncCandidate> candidates);

    List<SyncEvent> findPendingForUpdate(String taskCode);

    void markAccepted(String sourceEventId, Long executionId);

    void markSuperseded(String sourceEventId);
}
