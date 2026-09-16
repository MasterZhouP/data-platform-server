package com.ruoyi.integration.u8tooa.schedule;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import com.ruoyi.integration.sync.schedule.ScheduledTaskDefinition;
import com.ruoyi.integration.sync.schedule.SyncCandidate;
import com.ruoyi.integration.u8tooa.config.IncrementalSyncConfig;

/** A scheduled view over one published U8-to-OA task revision. */
public class U8ToOaScheduledDefinition implements ScheduledTaskDefinition
{
    private final String taskCode;
    private final IncrementalSyncConfig sync;
    private final ConfigurableU8ToOaChangeSource source;

    public U8ToOaScheduledDefinition(String taskCode, IncrementalSyncConfig sync,
            ConfigurableU8ToOaChangeSource source)
    {
        this.taskCode = taskCode;
        this.sync = sync;
        this.source = source;
    }

    @Override public String taskCode() { return taskCode; }
    @Override public LocalDateTime initialCursor() { return sync.initialCursor(); }
    @Override public LocalDateTime captureUpperBound() { return source.captureUpperBound(sync); }
    @Override public Duration overlapWindow() { return Duration.ofMinutes(sync.overlapMinutes()); }
    @Override public List<SyncCandidate> findCandidates(LocalDateTime fromExclusive, LocalDateTime toInclusive)
    {
        return source.findCandidates(sync, fromExclusive, toInclusive);
    }
}
