package com.ruoyi.integration.sync.schedule;

import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisSyncEventRepository implements SyncEventRepository
{
    private final SyncEventMapper mapper;

    public MyBatisSyncEventRepository(SyncEventMapper mapper)
    {
        this.mapper = mapper;
    }

    @Override
    public void register(String taskCode, List<SyncCandidate> candidates)
    {
        for (SyncCandidate candidate : candidates)
        {
            if (candidate == null || candidate.documentNo() == null || candidate.documentNo().isBlank()
                    || candidate.action() == null || candidate.changedAt() == null)
            {
                throw new IllegalArgumentException("增量事件缺少单据编号、动作或变更时间");
            }
            mapper.insertIgnore(SyncEvent.from(taskCode, candidate));
        }
    }

    @Override public List<SyncEvent> findPendingForUpdate(String taskCode)
    {
        return mapper.selectPendingForUpdate(taskCode);
    }

    @Override public void markAccepted(String sourceEventId, Long executionId)
    {
        requireOne(mapper.markAccepted(sourceEventId, executionId), sourceEventId);
    }

    @Override public void markSuperseded(String sourceEventId)
    {
        requireOne(mapper.markSuperseded(sourceEventId), sourceEventId);
    }

    private void requireOne(int rows, String sourceEventId)
    {
        if (rows != 1)
        {
            throw new IllegalStateException("更新增量事件状态失败: " + sourceEventId);
        }
    }
}
