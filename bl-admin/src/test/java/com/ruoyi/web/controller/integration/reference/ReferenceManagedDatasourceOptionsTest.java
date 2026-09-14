package com.ruoyi.web.controller.integration.reference;

import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.integration.datasource.admin.DatasourceReadService;
import com.ruoyi.integration.reference.catalog.ReferenceCatalog;
import com.ruoyi.integration.reference.engine.ReferenceEngine;
import com.ruoyi.integration.reference.service.ReferenceQueryService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReferenceManagedDatasourceOptionsTest {
    @Test
    void offersManagedDatasourceRevisionsInsteadOfLegacyYamlFlags() throws Exception {
        ObjectMapper json = new ObjectMapper();
        DatasourceReadService datasources = mock(DatasourceReadService.class);
        ObjectNode u8 = json.createObjectNode().put("datasourceKey", "u8").put("name", "U8 测试库").put("enabled", true);
        when(datasources.list()).thenReturn(List.of(u8));

        AjaxResult response = new ReferenceAdminController(mock(ReferenceCatalog.class), mock(ReferenceEngine.class),
                mock(ReferenceQueryService.class), datasources).options();
        Object data = response.get(AjaxResult.DATA_TAG);
        var values = (java.util.Map<?, ?>) data;
        var items = (List<?>) values.get("datasources");
        var first = (java.util.Map<?, ?>) items.get(0);

        assertEquals("u8", first.get("key"));
        assertEquals("U8 测试库", first.get("label"));
        assertEquals(true, first.get("enabled"));
    }
}
