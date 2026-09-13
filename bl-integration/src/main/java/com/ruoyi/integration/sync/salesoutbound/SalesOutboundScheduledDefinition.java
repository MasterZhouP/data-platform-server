package com.ruoyi.integration.sync.salesoutbound;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import com.ruoyi.integration.sync.schedule.ScheduledTaskDefinition;
import com.ruoyi.integration.sync.schedule.SyncCandidate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "integration.datasource.u8", name = "enabled", havingValue = "true")
public class SalesOutboundScheduledDefinition implements ScheduledTaskDefinition
{
    private final SalesOutboundChangeSource source;
    private final LocalDateTime initialCursor;
    private final Duration overlapWindow;

    public SalesOutboundScheduledDefinition(SalesOutboundChangeSource source,
            @Value("${integration.sync.sales-outbound.initial-cursor:}") String initialCursor,
            @Value("${integration.sync.sales-outbound.overlap-minutes:1440}") long overlapMinutes)
    {
        this.source = source;
        this.initialCursor = initialCursor == null || initialCursor.isBlank()
                ? null : LocalDateTime.parse(initialCursor.trim());
        if (overlapMinutes < 0)
        {
            throw new IllegalArgumentException("overlap-minutes 不能小于0");
        }
        this.overlapWindow = Duration.ofMinutes(overlapMinutes);
    }

    @Override public String taskCode() { return SalesOutboundTaskHandler.TASK_CODE; }
    @Override public LocalDateTime initialCursor() { return initialCursor; }
    @Override public LocalDateTime captureUpperBound() { return source.captureUpperBound(); }
    @Override public Duration overlapWindow() { return overlapWindow; }
    @Override public List<SyncCandidate> findCandidates(LocalDateTime fromExclusive, LocalDateTime toInclusive)
    {
        return source.findCandidates(fromExclusive, toInclusive);
    }
}
