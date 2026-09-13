package com.ruoyi.integration.execution.service;

import com.ruoyi.integration.task.TriggerCommand;

public interface ExecutionAcceptor
{
    AcceptanceResult accept(TriggerCommand command);
}
