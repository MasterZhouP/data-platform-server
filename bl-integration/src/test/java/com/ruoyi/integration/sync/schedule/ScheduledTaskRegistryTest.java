package com.ruoyi.integration.sync.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.ruoyi.integration.task.TaskAction;

class ScheduledTaskRegistryTest
{
    @Test
    void resolvesUnknownTaskCodesThroughConfigurationProvider()
    {
        ScheduledTaskDefinition dynamic = new ScheduledTaskDefinition()
        {
            @Override public String taskCode() { return "U8_RECEIPT"; }
            @Override public LocalDateTime initialCursor() { return LocalDateTime.MIN; }
            @Override public LocalDateTime captureUpperBound() { return LocalDateTime.MIN; }
            @Override public List<SyncCandidate> findCandidates(LocalDateTime fromExclusive, LocalDateTime toInclusive)
            { return List.of(new SyncCandidate("DOC-1", TaskAction.CREATE, LocalDateTime.MIN)); }
        };
        ScheduledTaskRegistry registry = new ScheduledTaskRegistry(List.of(), code -> dynamic);

        assertEquals("U8_RECEIPT", registry.require("U8_RECEIPT").taskCode());
    }
}
