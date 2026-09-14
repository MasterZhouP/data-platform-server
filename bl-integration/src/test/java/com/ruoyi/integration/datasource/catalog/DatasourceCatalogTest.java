package com.ruoyi.integration.datasource.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.configuration.ConfigurationException;
import com.ruoyi.integration.configuration.RevisionToken;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatasourceCatalogTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void savesImmutableDraftAndKeepsTheActiveRevisionUntouched() {
        DatasourceMapper mapper = mock(DatasourceMapper.class);
        DatasourceRow row = new DatasourceRow("u8", "U8 正式库", true, "active-1", null, 4L);
        when(mapper.findCatalog("u8")).thenReturn(row);
        when(mapper.updateDraftPointer(eq("u8"), any(), eq(4L))).thenReturn(1);
        when(mapper.findRevision("active-1")).thenReturn(new DatasourceRevision("active-1", "u8", "{\"host\":\"old\"}", "secret-1", "checksum-1"));

        DatasourceCatalog catalog = new DatasourceCatalog(mapper, json);
        ObjectNode candidate = json.createObjectNode().put("name", "U8 正式库").put("host", "new");

        RevisionToken saved = catalog.saveDraft("u8", null, candidate, "secret-2");

        assertEquals("active-1", catalog.getActive("u8").path("revisionId").asText());
        verify(mapper).insertRevision(org.mockito.ArgumentMatchers.argThat(revision ->
                revision.datasourceKey().equals("u8") && revision.secretId().equals("secret-2")
                        && revision.revisionId().equals(saved.value())));
    }

    @Test
    void rejectsAStaleDraftWithoutReplacingTheExistingRevision() {
        DatasourceMapper mapper = mock(DatasourceMapper.class);
        when(mapper.findCatalog("u8")).thenReturn(new DatasourceRow("u8", "U8", true, "1", "2", 8L));
        when(mapper.updateDraftPointer(eq("u8"), any(), eq(8L))).thenReturn(0);
        DatasourceCatalog catalog = new DatasourceCatalog(mapper, json);

        ConfigurationException error = assertThrows(ConfigurationException.class,
                () -> catalog.saveDraft("u8", "2", json.createObjectNode().put("name", "U8"), "secret-3"));

        assertEquals("CONFIGURATION_VERSION_CONFLICT", error.code());
    }
}
