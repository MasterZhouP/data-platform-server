package com.ruoyi.integration.datasource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Set;
import com.ruoyi.integration.configuration.ConfigurationException;
import com.ruoyi.integration.datasource.validation.ReadonlyPermissionVerifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatasourcePermissionTest {
    @Test
    void failsClosedWhenSqlServerPermissionMetadataCannotConfirmReadonlyAccess() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(false);

        ConfigurationException error = assertThrows(ConfigurationException.class,
                () -> new ReadonlyPermissionVerifier().verify(connection, Set.of("dbo.Inventory")));

        assertEquals("READONLY_UNVERIFIED", error.code());
        verify(connection, never()).setReadOnly(false);
        verify(connection, never()).createStatement();
    }
}
