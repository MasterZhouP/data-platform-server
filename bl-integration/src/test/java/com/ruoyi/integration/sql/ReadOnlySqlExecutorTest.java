package com.ruoyi.integration.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.integration.configuration.ConfigurationException;
import com.ruoyi.integration.datasource.runtime.DatasourceLease;
import com.ruoyi.integration.datasource.runtime.DatasourceRegistry;
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

    @Test
    void executesOaToU8ReadsThroughTheManagedDatasourceLease()
    {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:managed-read-only-sql;MODE=MySQL;DB_CLOSE_DELAY=-1");
        DatasourceRegistry registry = mock(DatasourceRegistry.class);
        DatasourceLease lease = mock(DatasourceLease.class);
        when(lease.dataSource()).thenReturn(dataSource);
        when(registry.acquire("oa-test")).thenReturn(lease);
        ReadOnlySqlExecutor executor = new JdbcReadOnlySqlExecutor(registry, new ObjectMapper());

        ReadOnlySqlResult result = executor.execute("oa-test", "select :masterId AS source_id", ResultCardinality.ONE,
                Map.of("masterId", "M-1"));

        assertEquals("M-1", result.one().path("SOURCE_ID").asText());
        verify(lease).close();
    }

    @Test
    void exposesUnavailableManagedDatasourceAsAStableSqlError()
    {
        DatasourceRegistry registry = mock(DatasourceRegistry.class);
        when(registry.acquire("oa-test")).thenThrow(new ConfigurationException(
                "DATASOURCE_UNAVAILABLE", 503, "数据源尚未启用或正在切换"));
        ReadOnlySqlExecutor executor = new JdbcReadOnlySqlExecutor(registry, new ObjectMapper());

        SqlExecutionException error = assertThrows(SqlExecutionException.class,
                () -> executor.execute("oa-test", "select 1", ResultCardinality.ONE, Map.of()));

        assertEquals("DATASOURCE_UNAVAILABLE", error.code());
    }
}
