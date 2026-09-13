package com.ruoyi.integration.sync;

import com.ruoyi.integration.execution.service.AcceptanceResult;
import com.ruoyi.integration.execution.service.ExecutionAcceptor;
import com.ruoyi.integration.task.IntegrationTaskHandler;
import com.ruoyi.integration.task.ManualSyncTask;
import com.ruoyi.integration.task.PushHandlerRegistry;
import com.ruoyi.integration.task.TriggerCommand;
import com.ruoyi.integration.task.TriggerSource;
import org.springframework.stereotype.Service;

@Service
public class ManualSyncService
{
    private final PushHandlerRegistry registry;
    private final ExecutionAcceptor acceptor;

    public ManualSyncService(PushHandlerRegistry registry, ExecutionAcceptor acceptor)
    {
        this.registry = registry;
        this.acceptor = acceptor;
    }

    public Object preview(String taskCode, String documentNo)
    {
        return manualTask(taskCode).preview(requiredDocumentNo(documentNo));
    }

    public AcceptanceResult push(String taskCode, String documentNo, boolean force)
    {
        ManualSyncTask task = manualTask(taskCode);
        String normalized = requiredDocumentNo(documentNo);
        task.validateManualAcceptance(normalized, force);
        TriggerCommand command = new TriggerCommand(task.taskCode(), normalized, null, null,
                task.manualAction(force), TriggerSource.MANUAL, force);
        return acceptor.accept(command);
    }

    private ManualSyncTask manualTask(String taskCode)
    {
        IntegrationTaskHandler handler = registry.require(taskCode);
        if (handler instanceof ManualSyncTask manual)
        {
            return manual;
        }
        throw new ManualSyncNotSupportedException(taskCode);
    }

    private String requiredDocumentNo(String documentNo)
    {
        if (documentNo == null || documentNo.isBlank())
        {
            throw new IllegalArgumentException("documentNo 不能为空");
        }
        return documentNo.trim();
    }
}
