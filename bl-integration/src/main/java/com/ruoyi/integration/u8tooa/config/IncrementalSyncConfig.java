package com.ruoyi.integration.u8tooa.config;

import java.time.LocalDateTime;

/** Read-only change-feed queries and cursor policy for one U8-to-OA task. */
public record IncrementalSyncConfig(String datasourceKey, String upperBoundSql, String createSql,
        String updateSql, String deleteSql, LocalDateTime initialCursor, int overlapMinutes,
        String cronExpression)
{
    public IncrementalSyncConfig(String datasourceKey, String upperBoundSql, String createSql,
            String updateSql, String deleteSql, LocalDateTime initialCursor, int overlapMinutes)
    {
        this(datasourceKey, upperBoundSql, createSql, updateSql, deleteSql, initialCursor, overlapMinutes,
                "0 0/5 * * * ?");
    }
}
