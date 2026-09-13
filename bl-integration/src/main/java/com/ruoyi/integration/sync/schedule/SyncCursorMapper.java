package com.ruoyi.integration.sync.schedule;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SyncCursorMapper
{
    LocalDateTime selectForUpdate(String taskCode);

    int insert(@Param("taskCode") String taskCode, @Param("cursorTime") LocalDateTime cursorTime);

    int update(@Param("taskCode") String taskCode, @Param("cursorTime") LocalDateTime cursorTime);
}
