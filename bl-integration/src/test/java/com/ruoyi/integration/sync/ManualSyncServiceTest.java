package com.ruoyi.integration.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import com.ruoyi.integration.execution.service.AcceptanceResult;
import com.ruoyi.integration.execution.service.ExecutionAcceptor;
import com.ruoyi.integration.task.ExecutionStageRecorder;
import com.ruoyi.integration.task.ManualSyncTask;
import com.ruoyi.integration.task.PushExecutionContext;
import com.ruoyi.integration.task.PushHandlerRegistry;
import com.ruoyi.integration.task.PushResult;
import com.ruoyi.integration.task.TaskAction;
import com.ruoyi.integration.task.TriggerCommand;
import com.ruoyi.integration.task.TriggerSource;

class ManualSyncServiceTest
{
    @Test
    void routesPreviewAndManualActionsByTaskCode()
    {
        TestManualTask task = new TestManualTask();
        CapturingAcceptor acceptor = new CapturingAcceptor();
        ManualSyncService service = new ManualSyncService(new PushHandlerRegistry(List.of(task)), acceptor);

        assertEquals("preview:CK-001", service.preview("TEST_MANUAL", "CK-001"));
        service.push("TEST_MANUAL", "CK-001", false);
        assertEquals(TaskAction.CREATE, acceptor.command.action());
        assertEquals(TriggerSource.MANUAL, acceptor.command.triggerSource());
        service.push("TEST_MANUAL", "CK-001", true);
        assertEquals(TaskAction.CANCEL_RECREATE, acceptor.command.action());
        assertEquals(true, acceptor.command.force());
    }

    @Test
    void rejectsHandlersThatDoNotExposeManualOperations()
    {
        var registry = new PushHandlerRegistry(List.of(new com.ruoyi.integration.task.IntegrationTaskHandler()
        {
            @Override public String taskCode() { return "NOT_MANUAL"; }
            @Override public PushResult execute(PushExecutionContext context, ExecutionStageRecorder recorder) { return null; }
        }));
        ManualSyncService service = new ManualSyncService(registry, command -> null);
        assertThrows(ManualSyncNotSupportedException.class,
                () -> service.preview("NOT_MANUAL", "CK-001"));
    }

    private static final class TestManualTask implements ManualSyncTask
    {
        @Override public String taskCode() { return "TEST_MANUAL"; }
        @Override public Object preview(String masterId) { return "preview:" + masterId; }
        @Override public void validateManualAcceptance(String masterId, boolean force) { }
        @Override public PushResult execute(PushExecutionContext context, ExecutionStageRecorder recorder) { return null; }
    }

    private static final class CapturingAcceptor implements ExecutionAcceptor
    {
        private TriggerCommand command;

        @Override
        public AcceptanceResult accept(TriggerCommand command)
        {
            this.command = command;
            return new AcceptanceResult(1L, "PENDING");
        }
    }
}
