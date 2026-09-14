package com.ruoyi.integration.sql;

import java.util.Map;
import com.ruoyi.integration.oatou8.config.ResultCardinality;

/** Executes a previously validated read-only SQL statement through named JDBC parameters. */
public interface ReadOnlySqlExecutor
{
    ReadOnlySqlResult execute(String datasourceKey, String sql, ResultCardinality cardinality,
            Map<String, Object> parameters);
}
