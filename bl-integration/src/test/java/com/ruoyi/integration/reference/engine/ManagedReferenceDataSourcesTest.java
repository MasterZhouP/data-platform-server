package com.ruoyi.integration.reference.engine;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;
import javax.sql.DataSource;
import com.ruoyi.integration.datasource.runtime.DatasourceLease;
import com.ruoyi.integration.datasource.runtime.DatasourceRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManagedReferenceDataSourcesTest {
    @Test
    void holdsTheManagedDatasourceLeaseUntilTheReferenceConnectionCloses() throws Exception {
        DatasourceRegistry registry = mock(DatasourceRegistry.class);
        DatasourceLease lease = mock(DatasourceLease.class);
        Connection connection = mock(Connection.class);
        DataSource source = new SingleConnectionDataSource(connection);
        when(lease.dataSource()).thenReturn(source);
        when(registry.acquire("u8")).thenReturn(lease);

        Connection leased = new ManagedReferenceDataSources(registry).get("u8").getConnection();

        assertSame(connection, leased.unwrap(Connection.class));
        leased.close();
        verify(connection).close();
        verify(lease).close();
    }

    private record SingleConnectionDataSource(Connection connection) implements DataSource {
        @Override public Connection getConnection() { return connection; }
        @Override public Connection getConnection(String username, String password) { return connection; }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLException("not supported"); }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
        @Override public PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(PrintWriter out) { }
        @Override public void setLoginTimeout(int seconds) { }
        @Override public int getLoginTimeout() { return 0; }
        @Override public Logger getParentLogger() { return Logger.getGlobal(); }
    }
}
