package com.ruoyi.integration.datasource.runtime;

import com.ruoyi.integration.configuration.ConfigurationException;
import com.ruoyi.integration.configuration.RevisionToken;
import com.ruoyi.integration.datasource.catalog.DatasourceCatalog;
import org.springframework.stereotype.Service;

/** Coordinates the durable active pointer with the in-memory pool registry. */
@Service
public class DatasourceActivationService {
    private final DatasourceCatalog catalog;
    private final DatasourceRegistry registry;

    public DatasourceActivationService(DatasourceCatalog catalog, DatasourceRegistry registry) {
        this.catalog = catalog;
        this.registry = registry;
    }

    public boolean activate(String key, RevisionToken candidate, String expectedActiveId, PreparedDatasource prepared) {
        if (prepared == null || !key.equals(prepared.datasourceKey()) || !candidate.value().equals(prepared.revisionId())) {
            throw new ConfigurationException("INVALID_ARGUMENT", 400, "候选连接与待启用修订不一致");
        }
        if (!catalog.activatePointer(key, candidate, expectedActiveId)) {
            return false;
        }
        registry.activate(prepared);
        return true;
    }

    public void disable(String key) {
        registry.disable(key);
    }
}
