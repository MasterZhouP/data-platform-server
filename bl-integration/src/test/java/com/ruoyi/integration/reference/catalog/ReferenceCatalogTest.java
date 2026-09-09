package com.ruoyi.integration.reference.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.reference.model.ReferenceException;
import com.ruoyi.integration.reference.model.ReferenceTask;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReferenceCatalogTest {
    private final ObjectMapper json = new ObjectMapper();
    private final ReferenceTaskMapper mapper = mock(ReferenceTaskMapper.class);
    private final ReferenceCatalog catalog = new ReferenceCatalog(mapper, json);

    private ReferenceTask task() throws Exception {
        ObjectNode metadata = (ObjectNode) json.readTree("""
            {"taskCode":"DEMO","taskName":"示例","taskType":"REFERENCE","executionMode":"SYNC_QUERY",
             "metadataVersion":"old","resultSets":[{"resultSetCode":"tou","resultSetName":"示例","selectionMode":"SINGLE",
             "fields":[{"name":"code","label":"编码","order":1,"dataType":"STRING","nullable":true,"filterOperators":["eq"],"sortable":true}],
             "parameters":[],"defaults":{"displayFields":["code"],"filterFields":["code"],"sort":[{"field":"code","direction":"ASC"}],"pageSize":20},
             "limits":{"maxPageSize":200,"maxFilterConditions":20,"maxFilterDepth":3,"maxSortFields":5,"maxInValues":100,"queryTimeoutMs":10000}}]}
            """);
        return new ReferenceTask("DEMO", "示例", false, "u8", "integration/reference/material.sql", metadata);
    }

    @Test void createsGenericTaskWithServerGeneratedVersionAndPersistsMetadata() throws Exception {
        var task = task();
        var created = catalog.create(task);
        assertNotEquals("old", created.metadata().path("metadataVersion").asText());
        assertEquals("old", task.metadata().path("metadataVersion").asText());
        verify(mapper).insert(argThat(row -> row.taskCode().equals("DEMO") && row.metadataJson().contains("code")));
    }

    @Test void rejectsInvalidDefaultsAndUntrustedSqlBeforeWriting() throws Exception {
        var task = task();
        ((ObjectNode) task.metadata().withArray("resultSets").get(0).path("defaults")).withArray("displayFields").add("unknown");
        assertThrows(ReferenceException.class, () -> catalog.create(task));
        var original = task();
        assertThrows(ReferenceException.class, () -> catalog.create(new ReferenceTask("DEMO", "示例", false, "u8", "../secret.sql", original.metadata())));
        verify(mapper, never()).insert(any());
    }

    @Test void optimisticUpdateRejectsConcurrentSaveAndTaskCodeMismatch() throws Exception {
        var task = task();
        when(mapper.find("DEMO")).thenReturn(new ReferenceTaskRow("DEMO", "示例", false, "u8", task.sqlResource(), task.metadata().toString(), "old"));
        when(mapper.update(any(), eq("old"))).thenReturn(0);
        var error = assertThrows(ReferenceException.class, () -> catalog.save("DEMO", task));
        assertEquals("METADATA_VERSION_MISMATCH", error.code());
        assertThrows(ReferenceException.class, () -> catalog.save("demo", task));
    }

    @Test void acceptsCompleteSqlColumnLabelsIncludingChineseAndClosingBracket() throws Exception {
        var original = task();
        var metadata = (ObjectNode) json.readTree(original.metadata().toString().replace("\"code\"", "\"库存]编码\""));
        var task = new ReferenceTask(original.taskCode(), original.taskName(), false, "u8", original.sqlResource(), metadata);
        assertEquals("库存]编码", catalog.create(task).metadata().path("resultSets").get(0).path("fields").get(0).path("name").asText());
        var fields = ((ObjectNode) metadata.path("resultSets").get(0)).withArray("fields");
        fields.add(fields.get(0).deepCopy());
        assertThrows(ReferenceException.class, () -> catalog.create(task));
    }

    @Test void rejectsControlCharactersInSqlColumnLabels() throws Exception {
        var task = task();
        ((ObjectNode) task.metadata().path("resultSets").get(0).path("fields").get(0)).put("name", "bad\nname");
        assertThrows(ReferenceException.class, () -> catalog.create(task));
    }
}
