package com.ruoyi.integration.sync.schedule;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import com.ruoyi.integration.execution.service.ExecutionAcceptor;
import com.ruoyi.integration.execution.service.ExecutionConflictException;
import com.ruoyi.integration.execution.domain.IntegrationExecution;
import com.ruoyi.integration.task.TaskAction;
import com.ruoyi.integration.task.TriggerCommand;
import com.ruoyi.integration.task.TriggerSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduledSyncService
{
    private final ScheduledTaskRegistry registry;
    private final SyncCursorRepository cursors;
    private final SyncEventRepository events;
    private final ExecutionAcceptor acceptor;
    private final Map<String, AtomicBoolean> running = new ConcurrentHashMap<>();

    public ScheduledSyncService(ScheduledTaskRegistry registry, SyncCursorRepository cursors,
            SyncEventRepository events, ExecutionAcceptor acceptor)
    {
        this.registry = registry;
        this.cursors = cursors;
        this.events = events;
        this.acceptor = acceptor;
    }

    @Transactional
    public ScheduledRunResult run(String taskCode)
    {
        ScheduledTaskDefinition definition = registry.require(taskCode);
        AtomicBoolean guard = running.computeIfAbsent(definition.taskCode(), key -> new AtomicBoolean());
        if (!guard.compareAndSet(false, true))
        {
            return ScheduledRunResult.overlapSkipped();
        }
        try
        {
            return runOnce(definition);
        }
        finally
        {
            guard.set(false);
        }
    }

    private ScheduledRunResult runOnce(ScheduledTaskDefinition definition)
    {
        LocalDateTime from = cursors.findForUpdate(definition.taskCode());
        boolean initialized = false;
        if (from == null)
        {
            LocalDateTime initial = definition.initialCursor();
            if (initial == null)
            {
                throw new MissingSyncCursorException(definition.taskCode());
            }
            cursors.initialize(definition.taskCode(), initial);
            from = cursors.findForUpdate(definition.taskCode());
            if (from == null)
            {
                throw new IllegalStateException("同步游标初始化失败: " + definition.taskCode());
            }
            initialized = true;
        }
        LocalDateTime upper = definition.captureUpperBound();
        if (upper == null)
        {
            throw new IllegalStateException("无法获取数据源当前时间: " + definition.taskCode());
        }
        boolean forward = upper.isAfter(from);
        if (forward)
        {
            Duration overlap = definition.overlapWindow();
            if (overlap == null || overlap.isNegative())
            {
                throw new IllegalStateException("增量重叠窗口配置无效: " + definition.taskCode());
            }
            LocalDateTime scanFrom = initialized ? from : from.minus(overlap);
            events.register(definition.taskCode(), definition.findCandidates(scanFrom, upper));
        }
        List<SyncEvent> candidates = coalesceEvents(events.findPendingForUpdate(definition.taskCode()));
        int accepted = 0;
        int conflicts = 0;
        for (SyncEvent candidate : candidates)
        {
            TriggerCommand command = new TriggerCommand(definition.taskCode(), candidate.documentNo(),
                    candidate.sourceEventId(),
                    null, candidate.action(), TriggerSource.SCHEDULED, false);
            try
            {
                var result = acceptor.accept(command);
                events.markAccepted(candidate.sourceEventId(), result.executionId());
                accepted++;
            }
            catch (ExecutionConflictException ex)
            {
                conflicts++;
                if (representsSameEvent(ex.getExistingExecution(), command))
                {
                    events.markAccepted(candidate.sourceEventId(), ex.getExistingExecution().getExecutionId());
                }
            }
        }
        if (forward)
        {
            cursors.advance(definition.taskCode(), upper);
        }
        return new ScheduledRunResult(accepted, conflicts, from, upper, false);
    }

    private List<SyncEvent> coalesceEvents(List<SyncEvent> values)
    {
        Map<String, SyncEvent> byDocument = new LinkedHashMap<>();
        for (SyncEvent value : values)
        {
            SyncEvent previous = byDocument.putIfAbsent(value.documentNo(), value);
            if (previous == null)
            {
                continue;
            }
            SyncEvent winner = higherPriority(previous, value);
            SyncEvent loser = winner == previous ? value : previous;
            events.markSuperseded(loser.sourceEventId());
            byDocument.put(value.documentNo(), winner);
        }
        List<SyncEvent> result = new ArrayList<>(byDocument.values());
        result.sort(Comparator.comparing(SyncEvent::documentNo));
        return result;
    }

    private SyncEvent higherPriority(SyncEvent first, SyncEvent second)
    {
        if (first.action() == TaskAction.DELETE || second.action() == TaskAction.DELETE)
        {
            return first.action() == TaskAction.DELETE ? first : second;
        }
        int timeOrder = second.changedAt().compareTo(first.changedAt());
        if (timeOrder > 0)
        {
            return second;
        }
        if (timeOrder < 0)
        {
            return first;
        }
        int firstPriority = priority(first.action());
        int secondPriority = priority(second.action());
        if (secondPriority > firstPriority)
        {
            return second;
        }
        if (secondPriority < firstPriority)
        {
            return first;
        }
        return first;
    }

    private boolean representsSameEvent(IntegrationExecution existing, TriggerCommand command)
    {
        return existing != null
                && Objects.equals(existing.getTaskCode(), command.taskCode())
                && Objects.equals(existing.getMasterId(), command.masterId())
                && Objects.equals(existing.getFormId(), command.formId())
                && Objects.equals(existing.getOperation(), command.action().name());
    }

    private int priority(TaskAction action)
    {
        return switch (action)
        {
            case DELETE -> 3;
            case CREATE -> 2;
            case CANCEL_RECREATE -> 1;
        };
    }
}
