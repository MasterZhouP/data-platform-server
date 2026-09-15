package com.ruoyi.integration.datasource.admin;

import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.datasource.catalog.DatasourceCatalog;
import com.ruoyi.integration.datasource.catalog.DatasourceRow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatasourceReadServiceTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void exposesOnlySafeRevisionStateInTheListAndDetailViews() {
        DatasourceCatalog catalog = mock(DatasourceCatalog.class);
        DatasourceRow row = new DatasourceRow("u8", "U8 正式库", true, "active-2", "draft-3", 7L);
        ObjectNode draft = json.createObjectNode().put("host", "u8-db").put("secretConfigured", true);
        ObjectNode active = json.createObjectNode().put("host", "u8-old").put("secretConfigured", true);
        ObjectNode lastTest = json.createObjectNode().put("status", "SUCCESS").put("revisionId", "draft-3");
        when(catalog.list()).thenReturn(List.of(row));
        when(catalog.row("u8")).thenReturn(row);
        when(catalog.getDraft("u8")).thenReturn(draft);
        when(catalog.getActive("u8")).thenReturn(active);
        when(catalog.lastTest("u8", "draft-3")).thenReturn(lastTest);

        DatasourceReadService service = new DatasourceReadService(catalog, json);

        assertEquals("u8", service.list().get(0).path("datasourceKey").asText());
        ObjectNode detail = service.detail("u8");
        assertEquals("u8-db", detail.path("draftConfig").path("host").asText());
        assertEquals("SUCCESS", detail.path("lastTest").path("status").asText());
        assertFalse(detail.toString().contains("password"));
        assertFalse(detail.toString().contains("ciphertext"));
    }
}
