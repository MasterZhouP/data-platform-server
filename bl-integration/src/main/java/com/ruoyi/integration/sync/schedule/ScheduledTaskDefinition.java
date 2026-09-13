package com.ruoyi.integration.sync.schedule;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;

public interface ScheduledTaskDefinition
{
    String taskCode();

    LocalDateTime initialCursor();

    LocalDateTime captureUpperBound();

    default Duration overlapWindow()
    {
        return Duration.ofHours(24);
    }

    List<SyncCandidate> findCandidates(LocalDateTime fromExclusive, LocalDateTime toInclusive);
}
