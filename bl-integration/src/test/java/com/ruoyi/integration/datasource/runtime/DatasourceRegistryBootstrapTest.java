package com.ruoyi.integration.datasource.runtime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import javax.sql.DataSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.configuration.RevisionToken;
import com.ruoyi.integration.datasource.catalog.DatasourceCatalog;
import com.ruoyi.integration.datasource.catalog.DatasourceRevision;
import com.ruoyi.integration.datasource.catalog.DatasourceRow;
import com.ruoyi.integration.datasource.catalog.DatasourceSecretStore;
import org.junit.jupiter.api.Test;

class DatasourceRegistryBootstrapTest
{
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void restoresAnEnabledDatasourceIntoTheSharedRegistry()
    {
        DatasourceCatalog catalog = mock(DatasourceCatalog.class);
        DatasourceSecretStore secrets = mock(DatasourceSecretStore.class);
        DatasourcePoolFactory pools = mock(DatasourcePoolFactory.class);
        DatasourceProbe probe = mock(DatasourceProbe.class);
        DatasourceRegistry registry = mock(DatasourceRegistry.class);
        DatasourceRevision revision = new DatasourceRevision("rev-u8", "u8", "{}", "secret-u8", "checksum");
        PreparedDatasource prepared = new PreparedDatasource("u8", "rev-u8", mock(DataSource.class));
        ObjectNode result = json.createObjectNode().put("status", "SUCCESS");
        ObjectNode config = json.createObjectNode().put("type", "SQLSERVER");
        when(catalog.list()).thenReturn(List.of(new DatasourceRow("u8", "U8", true, "rev-u8", null, 1L)));
        when(catalog.activeRevision("u8")).thenReturn(revision);
        when(catalog.getActive("u8")).thenReturn(config);
        when(secrets.read("u8", new RevisionToken("rev-u8"), "secret-u8")).thenReturn("secret".toCharArray());
        when(pools.create(eq("u8"), any(RevisionToken.class), eq(config), any(char[].class))).thenReturn(prepared);
        when(probe.test(prepared)).thenReturn(result);

        new DatasourceRegistryBootstrap(catalog, secrets, pools, probe, registry).restoreActiveDatasources();

        verify(catalog).recordTest("u8", new RevisionToken("rev-u8"), result);
        verify(registry).activate(prepared);
    }

    @Test
    void leavesAnUnhealthyDatasourceOutOfTheRegistry()
    {
        DatasourceCatalog catalog = mock(DatasourceCatalog.class);
        DatasourceSecretStore secrets = mock(DatasourceSecretStore.class);
        DatasourcePoolFactory pools = mock(DatasourcePoolFactory.class);
        DatasourceProbe probe = mock(DatasourceProbe.class);
        DatasourceRegistry registry = mock(DatasourceRegistry.class);
        DatasourceRevision revision = new DatasourceRevision("rev-oa", "oa", "{}", "secret-oa", "checksum");
        TrackingDataSource source = new TrackingDataSource();
        PreparedDatasource prepared = new PreparedDatasource("oa", "rev-oa", source);
        ObjectNode result = json.createObjectNode().put("status", "FAILED").put("code", "DATASOURCE_CONNECTION_FAILED");
        ObjectNode config = json.createObjectNode().put("type", "SQLSERVER");
        when(catalog.list()).thenReturn(List.of(new DatasourceRow("oa", "OA", true, "rev-oa", null, 1L)));
        when(catalog.activeRevision("oa")).thenReturn(revision);
        when(catalog.getActive("oa")).thenReturn(config);
        when(secrets.read("oa", new RevisionToken("rev-oa"), "secret-oa")).thenReturn("secret".toCharArray());
        when(pools.create(eq("oa"), any(RevisionToken.class), eq(config), any(char[].class))).thenReturn(prepared);
        when(probe.test(prepared)).thenReturn(result);

        new DatasourceRegistryBootstrap(catalog, secrets, pools, probe, registry).restoreActiveDatasources();

        verify(registry, never()).activate(any());
        org.junit.jupiter.api.Assertions.assertTrue(source.closed);
    }

    private static final class TrackingDataSource implements DataSource, AutoCloseable
    {
        private boolean closed;

        @Override public void close() { closed = true; }
        @Override public java.sql.Connection getConnection() { throw new UnsupportedOperationException(); }
        @Override public java.sql.Connection getConnection(String username, String password) { throw new UnsupportedOperationException(); }
        @Override public <T> T unwrap(Class<T> iface) { throw new UnsupportedOperationException(); }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
        @Override public java.io.PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(java.io.PrintWriter out) { }
        @Override public void setLoginTimeout(int seconds) { }
        @Override public int getLoginTimeout() { return 0; }
        @Override public java.util.logging.Logger getParentLogger() { return java.util.logging.Logger.getGlobal(); }
    }
}
