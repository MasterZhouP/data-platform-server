package com.ruoyi.integration.datasource.runtime;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Set;
import javax.sql.DataSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.integration.datasource.validation.ReadonlyPermissionVerifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatasourceProbeTest {
    @Test
    void returnsTheUiSuccessStateAfterAReadOnlyPermissionProbe() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement databasePermissions = mock(PreparedStatement.class);
        PreparedStatement objectPermissions = mock(PreparedStatement.class);
        ResultSet databaseRows = mock(ResultSet.class);
        ResultSet objectRows = mock(ResultSet.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("SELECT permission_name FROM sys.fn_my_permissions(NULL, 'DATABASE')"))
                .thenReturn(databasePermissions);
        when(connection.prepareStatement("SELECT permission_name FROM sys.fn_my_permissions(?, 'OBJECT')"))
                .thenReturn(objectPermissions);
        when(databasePermissions.executeQuery()).thenReturn(databaseRows);
        when(databaseRows.next()).thenReturn(true, false);
        when(databaseRows.getString(1)).thenReturn("SELECT");
        when(objectPermissions.executeQuery()).thenReturn(objectRows);
        when(objectRows.next()).thenReturn(true, false);
        when(objectRows.getString(1)).thenReturn("SELECT");

        var result = new DatasourceProbe(new ObjectMapper(), new ReadonlyPermissionVerifier())
                .test(new PreparedDatasource("u8", "rev-1", source), Set.of("dbo.Inventory"));

        assertEquals("SUCCESS", result.path("status").asText());
    }
}
