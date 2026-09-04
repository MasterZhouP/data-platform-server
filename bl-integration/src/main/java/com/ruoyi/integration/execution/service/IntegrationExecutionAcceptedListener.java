package com.ruoyi.integration.execution.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class IntegrationExecutionAcceptedListener
{
    private final IntegrationPendingDispatcher dispatcher;

    public IntegrationExecutionAcceptedListener(IntegrationPendingDispatcher dispatcher)
    {
        this.dispatcher = dispatcher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(ExecutionAcceptedEvent event)
    {
        dispatcher.dispatch(event.executionId());
    }
}
