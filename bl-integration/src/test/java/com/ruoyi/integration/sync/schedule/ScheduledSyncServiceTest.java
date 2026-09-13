package com.ruoyi.integration.sync.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.ruoyi.integration.execution.service.AcceptanceResult;
import com.ruoyi.integration.execution.service.ExecutionAcceptor;
import com.ruoyi.integration.execution.service.ExecutionConflictException;
import com.ruoyi.integration.execution.service.IntegrationExecutionService;
import com.ruoyi.integration.execution.repository.MyBatisExecutionRepository;
import com.ruoyi.integration.execution.domain.IntegrationExecution;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.integration.task.TaskAction;
import com.ruoyi.integration.task.TriggerCommand;
import com.ruoyi.integration.task.TriggerSource;

class ScheduledSyncServiceTest
{
    @Test
    void caughtDedupConflictsDoNotPoisonTheOuterCursorTransaction() throws Exception
    {
        Transactional accept = IntegrationExecutionService.class
                .getMethod("accept", TriggerCommand.class).getAnnotation(Transactional.class);
        Transactional insert = MyBatisExecutionRepository.class
                .getMethod("insert", com.ruoyi.integration.execution.domain.IntegrationExecution.class)
                .getAnnotation(Transactional.class);

        assertTrue(List.of(accept.noRollbackFor()).contains(ExecutionConflictException.class));
        assertTrue(List.of(insert.noRollbackFor()).contains(ExecutionConflictException.class));
    }

    @Test
    void coalescesCandidatesByDeleteThenCreatePriorityAndAdvancesCursorAfterAcceptance()
    {
        LocalDateTime from = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime upper = LocalDateTime.of(2026, 9, 12, 12, 0);
        TestDefinition definition = new TestDefinition(from, upper, List.of(
                new SyncCandidate("CK-001", TaskAction.CANCEL_RECREATE, from.plusDays(1)),
                new SyncCandidate("CK-001", TaskAction.CREATE, from.plusDays(2)),
                new SyncCandidate("CK-002", TaskAction.CANCEL_RECREATE, from.plusDays(1)),
                new SyncCandidate("CK-002", TaskAction.DELETE, from.plusDays(3))));
        InMemoryCursorRepository cursors = new InMemoryCursorRepository();
        CapturingAcceptor acceptor = new CapturingAcceptor();
        ScheduledSyncService service = service(definition, cursors, acceptor, new InMemorySyncEvents());

        ScheduledRunResult result = service.run("TEST_SCHEDULED");

        assertEquals(2, result.acceptedCount());
        assertEquals(TaskAction.CREATE, acceptor.commands.get(0).action());
        assertEquals(TaskAction.DELETE, acceptor.commands.get(1).action());
        assertEquals(TriggerSource.SCHEDULED, acceptor.commands.get(0).triggerSource());
        assertEquals(upper, cursors.value);
    }

    @Test
    void initializesFirstCursorFromExplicitTaskConfiguration()
    {
        LocalDateTime initial = LocalDateTime.of(2026, 9, 10, 0, 0);
        LocalDateTime upper = initial.plusDays(1);
        TestDefinition definition = new TestDefinition(initial, upper, List.of());
        InMemoryCursorRepository cursors = new InMemoryCursorRepository();
        ScheduledSyncService service = service(definition, cursors,
                command -> new AcceptanceResult(1L, "PENDING"), new InMemorySyncEvents());

        service.run("TEST_SCHEDULED");

        assertEquals(upper, cursors.value);
        assertEquals(1, cursors.initializations);
    }

    @Test
    void advancesPastConflictOnlyWhenDurableExecutionRepresentsTheSameSourceEvent()
    {
        LocalDateTime from = LocalDateTime.of(2026, 9, 10, 0, 0);
        LocalDateTime upper = from.plusHours(1);
        SyncCandidate candidate = new SyncCandidate("CK-001", TaskAction.CANCEL_RECREATE, from.plusMinutes(5));
        TestDefinition definition = new TestDefinition(from, upper, List.of(candidate));
        InMemoryCursorRepository cursors = new InMemoryCursorRepository();
        cursors.value = from;
        ExecutionAcceptor acceptor = command -> {
            IntegrationExecution existing = existing(command, command.formId());
            throw new ExecutionConflictException("same event", existing);
        };

        ScheduledRunResult result = service(definition, cursors, acceptor, new InMemorySyncEvents())
                .run("TEST_SCHEDULED");

        assertEquals(1, result.conflictCount());
        assertEquals(upper, cursors.value);
    }

    @Test
    void doesNotAdvanceCursorWhenConflictBelongsToAnOlderEvent()
    {
        LocalDateTime from = LocalDateTime.of(2026, 9, 10, 0, 0);
        LocalDateTime upper = from.plusHours(1);
        SyncCandidate candidate = new SyncCandidate("CK-001", TaskAction.CANCEL_RECREATE, from.plusMinutes(5));
        TestDefinition definition = new TestDefinition(from, upper, List.of(candidate));
        InMemoryCursorRepository cursors = new InMemoryCursorRepository();
        cursors.value = from;
        ExecutionAcceptor acceptor = command -> {
            IntegrationExecution existing = existing(command, "CREATE@2026-09-09T23:00");
            throw new ExecutionConflictException("older event", existing);
        };

        InMemorySyncEvents events = new InMemorySyncEvents();
        ScheduledRunResult first = service(definition, cursors, acceptor, events).run("TEST_SCHEDULED");

        assertEquals(1, first.conflictCount());
        assertEquals(upper, cursors.value);
        assertEquals(1, events.pendingCount());
    }

    @Test
    void locksCursorBeforeCapturingUpperBoundAndIgnoresNonForwardBounds()
    {
        LocalDateTime cursor = LocalDateTime.of(2026, 9, 10, 1, 0);
        List<String> order = new ArrayList<>();
        InMemoryCursorRepository cursors = new InMemoryCursorRepository()
        {
            @Override public LocalDateTime findForUpdate(String taskCode)
            {
                order.add("lock");
                return cursor;
            }
            @Override public void advance(String taskCode, LocalDateTime upperBound)
            {
                order.add("advance");
                super.advance(taskCode, upperBound);
            }
        };
        TestDefinition definition = new TestDefinition(cursor, cursor.minusMinutes(1), List.of())
        {
            @Override public LocalDateTime captureUpperBound()
            {
                order.add("upper");
                return super.captureUpperBound();
            }
        };

        ScheduledRunResult result = service(definition, cursors,
                command -> new AcceptanceResult(1L, "PENDING"), new InMemorySyncEvents())
                .run("TEST_SCHEDULED");

        assertEquals(List.of("lock", "upper"), order);
        assertEquals(cursor, result.from());
        assertEquals(cursor.minusMinutes(1), result.to());
    }

    @Test
    void retriesDeferredDocumentWithoutBlockingLaterCursorWindows()
    {
        LocalDateTime from = LocalDateTime.of(2026, 9, 10, 0, 0);
        LocalDateTime firstUpper = from.plusHours(1);
        SyncCandidate candidate = new SyncCandidate("CK-001", TaskAction.CANCEL_RECREATE, from.plusMinutes(5));
        TestDefinition firstDefinition = new TestDefinition(from, firstUpper, List.of(candidate));
        InMemoryCursorRepository cursors = new InMemoryCursorRepository();
        cursors.value = from;
        InMemorySyncEvents events = new InMemorySyncEvents();
        int[] calls = { 0 };
        ExecutionAcceptor acceptor = command -> {
            calls[0]++;
            if (calls[0] == 1)
            {
                throw new ExecutionConflictException("older event", existing(command, "older-event"));
            }
            return new AcceptanceResult(22L, "PENDING");
        };

        ScheduledRunResult first = service(firstDefinition, cursors, acceptor, events).run("TEST_SCHEDULED");
        assertEquals(firstUpper, cursors.value);
        assertEquals(1, events.pendingCount());

        LocalDateTime secondUpper = firstUpper.plusHours(1);
        TestDefinition secondDefinition = new TestDefinition(from, secondUpper, List.of());
        ScheduledRunResult second = service(secondDefinition, cursors, acceptor, events).run("TEST_SCHEDULED");

        assertEquals(1, second.acceptedCount());
        assertEquals(secondUpper, cursors.value);
        assertEquals(0, events.pendingCount());
    }

    @Test
    void scansAnOverlapWindowWhileTheEventLedgerPreventsReplay()
    {
        LocalDateTime from = LocalDateTime.of(2026, 9, 10, 12, 0);
        LocalDateTime upper = from.plusHours(1);
        SyncCandidate late = new SyncCandidate("CK-LATE", TaskAction.CREATE, from.minusMinutes(30));
        TestDefinition definition = new TestDefinition(from, upper, List.of(late));
        InMemoryCursorRepository cursors = new InMemoryCursorRepository();
        cursors.value = from;
        InMemorySyncEvents events = new InMemorySyncEvents();
        CapturingAcceptor acceptor = new CapturingAcceptor();

        service(definition, cursors, acceptor, events).run("TEST_SCHEDULED");

        assertEquals(from.minus(Duration.ofHours(24)), definition.queriedFrom);
        assertEquals(1, acceptor.commands.size());
        assertEquals(0, events.pendingCount());
    }

    @Test
    void eventLedgerWriteFailurePreventsCursorAdvance()
    {
        LocalDateTime from = LocalDateTime.of(2026, 9, 10, 12, 0);
        LocalDateTime upper = from.plusMinutes(5);
        TestDefinition definition = new TestDefinition(from, upper,
                List.of(new SyncCandidate("CK-001", TaskAction.CREATE, upper)));
        InMemoryCursorRepository cursors = new InMemoryCursorRepository();
        cursors.value = from;
        InMemorySyncEvents events = new InMemorySyncEvents()
        {
            @Override public void register(String taskCode, List<SyncCandidate> candidates)
            {
                throw new IllegalStateException("ledger unavailable");
            }
        };

        assertThrows(IllegalStateException.class,
                () -> service(definition, cursors, command -> new AcceptanceResult(1L, "PENDING"), events)
                        .run("TEST_SCHEDULED"));
        assertEquals(from, cursors.value);
    }

    @Test
    void acceptedCreateInsideOverlapDoesNotSupersedeALaterUpdate()
    {
        LocalDateTime initial = LocalDateTime.of(2026, 9, 10, 0, 0);
        LocalDateTime createdAt = initial.plusMinutes(5);
        InMemoryCursorRepository cursors = new InMemoryCursorRepository();
        InMemorySyncEvents events = new InMemorySyncEvents();
        CapturingAcceptor acceptor = new CapturingAcceptor();
        TestDefinition createWindow = new TestDefinition(initial, initial.plusHours(1),
                List.of(new SyncCandidate("CK-001", TaskAction.CREATE, createdAt, null)));

        service(createWindow, cursors, acceptor, events).run("TEST_SCHEDULED");

        LocalDateTime modifiedAt = initial.plusHours(1).plusMinutes(5);
        TestDefinition updateWindow = new TestDefinition(initial, initial.plusHours(2), List.of(
                new SyncCandidate("CK-001", TaskAction.CREATE, createdAt, null),
                new SyncCandidate("CK-001", TaskAction.CANCEL_RECREATE, modifiedAt, "0000000000000002")));
        service(updateWindow, cursors, acceptor, events).run("TEST_SCHEDULED");

        assertEquals(2, acceptor.commands.size());
        assertEquals(TaskAction.CREATE, acceptor.commands.get(0).action());
        assertEquals(TaskAction.CANCEL_RECREATE, acceptor.commands.get(1).action());
    }

    @Test
    void createDeferredByAManualExecutionDoesNotSupersedeALaterUpdate()
    {
        LocalDateTime initial = LocalDateTime.of(2026, 9, 10, 0, 0);
        LocalDateTime createdAt = initial.plusMinutes(5);
        InMemoryCursorRepository cursors = new InMemoryCursorRepository();
        InMemorySyncEvents events = new InMemorySyncEvents();
        List<TriggerCommand> accepted = new ArrayList<>();
        int[] calls = { 0 };
        ExecutionAcceptor acceptor = command -> {
            calls[0]++;
            if (calls[0] == 1)
            {
                throw new ExecutionConflictException("manual create running", existing(command, null));
            }
            accepted.add(command);
            return new AcceptanceResult(33L, "PENDING");
        };
        TestDefinition createWindow = new TestDefinition(initial, initial.plusHours(1),
                List.of(new SyncCandidate("CK-001", TaskAction.CREATE, createdAt, null)));
        service(createWindow, cursors, acceptor, events).run("TEST_SCHEDULED");
        assertEquals(1, events.pendingCount());

        LocalDateTime modifiedAt = initial.plusHours(1).plusMinutes(5);
        TestDefinition updateWindow = new TestDefinition(initial, initial.plusHours(2), List.of(
                new SyncCandidate("CK-001", TaskAction.CREATE, createdAt, null),
                new SyncCandidate("CK-001", TaskAction.CANCEL_RECREATE, modifiedAt, "0000000000000002")));
        service(updateWindow, cursors, acceptor, events).run("TEST_SCHEDULED");

        assertEquals(1, accepted.size());
        assertEquals(TaskAction.CANCEL_RECREATE, accepted.get(0).action());
        assertEquals(0, events.pendingCount());
    }

    private ScheduledSyncService service(TestDefinition definition, SyncCursorRepository cursors,
            ExecutionAcceptor acceptor, SyncEventRepository events)
    {
        return new ScheduledSyncService(new ScheduledTaskRegistry(List.of(definition)), cursors, events, acceptor);
    }

    private static IntegrationExecution existing(TriggerCommand command, String formId)
    {
        IntegrationExecution value = new IntegrationExecution();
        value.setTaskCode(command.taskCode());
        value.setMasterId(command.masterId());
        value.setFormId(formId);
        value.setOperation(command.action().name());
        return value;
    }

    private static class TestDefinition implements ScheduledTaskDefinition
    {
        private final LocalDateTime initial;
        private final LocalDateTime upper;
        private final List<SyncCandidate> candidates;
        private LocalDateTime queriedFrom;

        private TestDefinition(LocalDateTime initial, LocalDateTime upper, List<SyncCandidate> candidates)
        {
            this.initial = initial;
            this.upper = upper;
            this.candidates = candidates;
        }

        @Override public String taskCode() { return "TEST_SCHEDULED"; }
        @Override public LocalDateTime initialCursor() { return initial; }
        @Override public LocalDateTime captureUpperBound() { return upper; }
        @Override public Duration overlapWindow() { return Duration.ofHours(24); }
        @Override public List<SyncCandidate> findCandidates(LocalDateTime fromExclusive, LocalDateTime toInclusive)
        {
            queriedFrom = fromExclusive;
            return candidates;
        }
    }

    private static class InMemorySyncEvents implements SyncEventRepository
    {
        private final Map<String, SyncEvent> values = new LinkedHashMap<>();

        @Override
        public void register(String taskCode, List<SyncCandidate> candidates)
        {
            for (SyncCandidate candidate : candidates)
            {
                SyncEvent event = SyncEvent.from(taskCode, candidate);
                values.putIfAbsent(event.sourceEventId(), event);
            }
        }

        @Override public List<SyncEvent> findPendingForUpdate(String taskCode)
        {
            return values.values().stream().filter(value -> value.status() == SyncEventStatus.PENDING).toList();
        }

        @Override public void markAccepted(String sourceEventId, Long executionId)
        {
            values.put(sourceEventId, values.get(sourceEventId).withStatus(SyncEventStatus.ACCEPTED, executionId));
        }

        @Override public void markSuperseded(String sourceEventId)
        {
            values.put(sourceEventId, values.get(sourceEventId).withStatus(SyncEventStatus.SUPERSEDED, null));
        }

        int pendingCount()
        {
            return (int) values.values().stream().filter(value -> value.status() == SyncEventStatus.PENDING).count();
        }
    }

    private static class InMemoryCursorRepository implements SyncCursorRepository
    {
        private LocalDateTime value;
        private int initializations;

        @Override public LocalDateTime findForUpdate(String taskCode) { return value; }
        @Override public void initialize(String taskCode, LocalDateTime initialCursor)
        {
            value = initialCursor;
            initializations++;
        }
        @Override public void advance(String taskCode, LocalDateTime upperBound) { value = upperBound; }
    }

    private static final class CapturingAcceptor implements ExecutionAcceptor
    {
        private final List<TriggerCommand> commands = new ArrayList<>();

        @Override
        public AcceptanceResult accept(TriggerCommand command)
        {
            commands.add(command);
            return new AcceptanceResult((long) commands.size(), "PENDING");
        }
    }
}
