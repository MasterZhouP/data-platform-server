package com.ruoyi.integration.sync.schedule;

import java.time.LocalDateTime;

public interface SyncCursorRepository
{
    LocalDateTime findForUpdate(String taskCode);

    void initialize(String taskCode, LocalDateTime initialCursor);

    void advance(String taskCode, LocalDateTime upperBound);
}
