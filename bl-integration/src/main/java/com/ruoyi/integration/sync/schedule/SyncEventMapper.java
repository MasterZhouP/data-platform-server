package com.ruoyi.integration.sync.schedule;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SyncEventMapper
{
    int insertIgnore(SyncEvent event);

    List<SyncEvent> selectPendingForUpdate(String taskCode);

    int markAccepted(@Param("sourceEventId") String sourceEventId, @Param("executionId") Long executionId);

    int markSuperseded(String sourceEventId);
}
