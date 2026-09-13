package com.ruoyi.integration.sync.salesoutbound;

import java.time.LocalDateTime;
import java.util.List;
import com.ruoyi.integration.sync.schedule.SyncCandidate;

public interface SalesOutboundChangeSource
{
    LocalDateTime captureUpperBound();

    List<SyncCandidate> findCandidates(LocalDateTime fromExclusive, LocalDateTime toInclusive);
}
