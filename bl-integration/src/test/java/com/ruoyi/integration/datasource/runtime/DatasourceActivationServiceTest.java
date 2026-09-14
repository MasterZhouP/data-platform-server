package com.ruoyi.integration.datasource.runtime;

import javax.sql.DataSource;
import com.ruoyi.integration.configuration.RevisionToken;
import com.ruoyi.integration.datasource.catalog.DatasourceCatalog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatasourceActivationServiceTest {
    @Test
    void onlyPublishesATestedCandidateWhenTheCatalogPointerWinsItsCas() {
        DatasourceCatalog catalog = mock(DatasourceCatalog.class);
        DatasourceRegistry registry = new DatasourceRegistry();
        DataSource source = mock(DataSource.class);
        RevisionToken candidate = new RevisionToken("revision-9");
        when(catalog.activatePointer("u8", candidate, "revision-8")).thenReturn(true);

        boolean activated = new DatasourceActivationService(catalog, registry)
                .activate("u8", candidate, "revision-8", new PreparedDatasource("u8", "revision-9", source));

        assertTrue(activated);
        try (DatasourceLease lease = registry.acquire("u8")) {
            assertEquals("revision-9", lease.revisionId());
        }
    }
}
