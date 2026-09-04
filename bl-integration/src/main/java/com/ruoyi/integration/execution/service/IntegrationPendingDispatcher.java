package com.ruoyi.integration.execution.service;

import java.util.Date;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import com.ruoyi.integration.execution.repository.ExecutionRepository;
import com.ruoyi.integration.pipeline.push.PushPipelineRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class IntegrationPendingDispatcher
{
    private static final Logger log = LoggerFactory.getLogger(IntegrationPendingDispatcher.class);

    private final ExecutionRepository repository;
    private final Executor executor;
    private final PushPipelineRunner runner;
    private final int scanLimit;
    private final long staleRunningMillis;

    @Autowired
    public IntegrationPendingDispatcher(ExecutionRepository repository,
            @Qualifier("integrationTaskExecutor") Executor executor,
            PushPipelineRunner runner,
            @Value("${integration.execution.scan-limit:100}") int scanLimit,
            @Value("${integration.execution.stale-running-ms:600000}") long staleRunningMillis)
    {
        this.repository = repository;
        this.executor = executor;
        this.runner = runner;
        this.scanLimit = scanLimit;
        this.staleRunningMillis = staleRunningMillis;
    }

    public IntegrationPendingDispatcher(ExecutionRepository repository, Executor executor,
            PushPipelineRunner runner, int scanLimit)
    {
        this(repository, executor, runner, scanLimit, 600000L);
    }

    public void dispatch(Long executionId)
    {
        try
        {
            executor.execute(() -> runner.run(executionId));
        }
        catch (RejectedExecutionException ex)
        {
            log.warn("集成执行器队列已满，执行 {} 保持 PENDING 等待补扫", executionId);
        }
    }

    @Scheduled(fixedDelayString = "${integration.execution.scan-delay-ms:5000}")
    public void dispatchPending()
    {
        repository.findPendingIds(scanLimit).forEach(this::dispatch);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady()
    {
        Date staleBefore = new Date(System.currentTimeMillis() - staleRunningMillis);
        int recovered = repository.failStaleRunning(staleBefore, "U8_REQUEST_SENDING",
                "PROCESS_INTERRUPTED", "EXTERNAL_RESULT_UNKNOWN");
        if (recovered > 0)
        {
            log.warn("已将 {} 条进程中断的 RUNNING 记录转为 FAILED，未自动重放", recovered);
        }
        dispatchPending();
    }
}
