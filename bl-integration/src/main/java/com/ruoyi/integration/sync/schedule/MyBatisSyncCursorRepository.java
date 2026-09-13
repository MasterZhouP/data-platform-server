package com.ruoyi.integration.sync.schedule;

import java.time.LocalDateTime;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisSyncCursorRepository implements SyncCursorRepository
{
    private final SyncCursorMapper mapper;

    public MyBatisSyncCursorRepository(SyncCursorMapper mapper)
    {
        this.mapper = mapper;
    }

    @Override
    public LocalDateTime findForUpdate(String taskCode)
    {
        return mapper.selectForUpdate(taskCode);
    }

    @Override
    public void initialize(String taskCode, LocalDateTime initialCursor)
    {
        mapper.insert(taskCode, initialCursor);
    }

    @Override
    public void advance(String taskCode, LocalDateTime upperBound)
    {
        mapper.update(taskCode, upperBound);
    }
}
