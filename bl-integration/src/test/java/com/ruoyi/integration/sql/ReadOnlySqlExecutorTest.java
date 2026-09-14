package com.ruoyi.integration.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import com.ruoyi.integration.oatou8.config.ResultCardinality;

class ReadOnlySqlExecutorTest
{
    @Test
    void bindsRuntimeValuesAsParametersRatherThanSqlText()
    {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:read-only-sql-bind;MODE=MySQL;DB_CLOSE_DELAY=-1");
        ReadOnlySqlExecutor executor = new JdbcReadOnlySqlExecutor(Map.of("oa", new NamedParameterJdbcTemplate(dataSource)));

        ReadOnlySqlResult result = executor.execute("oa", "select :masterId AS source_id, :period AS period", ResultCardinality.ONE,
                Map.of("masterId", "A' OR 1=1", "period", "2026-09"));

        assertEquals("A' OR 1=1", result.one().path("SOURCE_ID").asText());
        assertEquals("2026-09", result.one().path("PERIOD").asText());
    }

    @Test
    void rejectsOneResultWhenQueryReturnsMoreThanOneRow()
    {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:read-only-sql-cardinality;MODE=MySQL;DB_CLOSE_DELAY=-1");
        ReadOnlySqlExecutor executor = new JdbcReadOnlySqlExecutor(Map.of("oa", new NamedParameterJdbcTemplate(dataSource)));

        SqlExecutionException error = assertThrows(SqlExecutionException.class,
                () -> executor.execute("oa", "select 1 AS id union all select 2 AS id", ResultCardinality.ONE, Map.of()));

        assertEquals("RESULT_CARDINALITY_MISMATCH", error.code());
    }
}
