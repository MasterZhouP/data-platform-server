package com.ruoyi.integration.datasource.runtime;

import javax.sql.DataSource;
import com.ruoyi.integration.configuration.ConfigurationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DatasourceRegistryTest {
    @Test
    void pinsAnInFlightLeaseToItsRevisionUntilItCloses() {
        DatasourceRegistry registry = new DatasourceRegistry();
        CloseableDataSource first = mock(CloseableDataSource.class);
        CloseableDataSource second = mock(CloseableDataSource.class);
        registry.activate(new PreparedDatasource("u8", "revision-1", first));

        DatasourceLease original = registry.acquire("u8");
        registry.activate(new PreparedDatasource("u8", "revision-2", second));

        assertEquals("revision-1", original.revisionId());
        assertSame(first, original.dataSource());
        verify(first, org.mockito.Mockito.never()).close();
        try (DatasourceLease current = registry.acquire("u8")) {
            assertEquals("revision-2", current.revisionId());
            assertSame(second, current.dataSource());
        }
        original.close();
        verify(first).close();
    }

    @Test
    void disablesNewLeasesWhileLettingTheCurrentRequestFinish() {
        DatasourceRegistry registry = new DatasourceRegistry();
        CloseableDataSource source = mock(CloseableDataSource.class);
        registry.activate(new PreparedDatasource("oa", "revision-3", source));
        DatasourceLease lease = registry.acquire("oa");

        registry.disable("oa");

        assertEquals("DATASOURCE_UNAVAILABLE", assertThrows(ConfigurationException.class,
                () -> registry.acquire("oa")).code());
        verify(source, org.mockito.Mockito.never()).close();
        lease.close();
        verify(source).close();
    }

    interface CloseableDataSource extends DataSource, AutoCloseable {
        @Override
        void close();
    }
}
