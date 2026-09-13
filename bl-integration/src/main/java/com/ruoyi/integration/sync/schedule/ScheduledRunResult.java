package com.ruoyi.integration.sync.schedule;

import java.time.LocalDateTime;

public record ScheduledRunResult(int acceptedCount, int conflictCount, LocalDateTime from,
        LocalDateTime to, boolean alreadyRunning)
{
    public static ScheduledRunResult overlapSkipped()
    {
        return new ScheduledRunResult(0, 0, null, null, true);
    }
}
