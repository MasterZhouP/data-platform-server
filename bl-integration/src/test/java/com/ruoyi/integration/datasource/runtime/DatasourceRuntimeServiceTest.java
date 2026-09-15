package com.ruoyi.integration.datasource.runtime;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;
import javax.sql.DataSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.configuration.RevisionToken;
import com.ruoyi.integration.datasource.catalog.DatasourceCatalog;
import com.ruoyi.integration.datasource.catalog.DatasourceRevision;
import com.ruoyi.integration.datasource.catalog.DatasourceSecretStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatasourceRuntimeServiceTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void testsOnlyTheDraftPoolAndClosesItAfterTheProbe() {
        DatasourceCatalog catalog = mock(DatasourceCatalog.class);
        DatasourceSecretStore secrets = mock(DatasourceSecretStore.class);
        DatasourcePoolFactory pools = mock(DatasourcePoolFactory.class);
        DatasourceProbe probe = mock(DatasourceProbe.class);
        DatasourceActivationService activation = mock(DatasourceActivationService.class);
        DatasourceRevision revision = new DatasourceRevision("draft-1", "u8", "{}", "secret-1", "checksum");
        ObjectNode config = json.createObjectNode().put("host", "db.example.com");
        TrackingDataSource source = new TrackingDataSource();
        ObjectNode inspected = json.createObjectNode().put("status", "SUCCESS");
        when(catalog.draftRevision("u8")).thenReturn(revision);
        when(catalog.getDraft("u8")).thenReturn(config);
        when(secrets.read("u8", new RevisionToken("draft-1"), "secret-1")).thenReturn("secret".toCharArray());
        when(pools.create(eq("u8"), any(), eq(config), any())).thenReturn(new PreparedDatasource("u8", "draft-1", source));
        when(probe.test(any(PreparedDatasource.class))).thenReturn(inspected);

        ObjectNode result = new DatasourceRuntimeService(catalog, secrets, pools, probe, activation)
                .testDraft("u8", "draft-1");

        assertEquals("SUCCESS", result.path("status").asText());
        assertTrue(source.closed);
        verify(probe).test(any(PreparedDatasource.class));
    }

    private static final class TrackingDataSource implements DataSource, AutoCloseable {
        private boolean closed;

        @Override public void close() { closed = true; }
        @Override public Connection getConnection() throws SQLException { throw new SQLException("not used"); }
        @Override public Connection getConnection(String username, String password) throws SQLException { throw new SQLException("not used"); }
        @Override public <T> T unwrap(Class<T> iface) { throw new UnsupportedOperationException(); }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
        @Override public PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(PrintWriter out) { }
        @Override public void setLoginTimeout(int seconds) { }
        @Override public int getLoginTimeout() { return 0; }
        @Override public Logger getParentLogger() { return Logger.getGlobal(); }
    }
}
