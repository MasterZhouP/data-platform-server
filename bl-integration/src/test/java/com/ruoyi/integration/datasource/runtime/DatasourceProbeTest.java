package com.ruoyi.integration.datasource.runtime;

import java.sql.Connection;
import javax.sql.DataSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatasourceProbeTest {
    @Test
    void returnsSuccessWhenTheDatabaseConnectionIsAvailableWithoutPermissionMetadata() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);

        var result = new DatasourceProbe(new ObjectMapper())
                .test(new PreparedDatasource("u8", "rev-1", source));

        assertEquals("SUCCESS", result.path("status").asText());
    }
}
