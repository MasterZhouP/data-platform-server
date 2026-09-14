package com.ruoyi.integration.datasource.runtime;

import javax.sql.DataSource;

/** A tested candidate pool that can be made active without rebuilding it. */
public record PreparedDatasource(String datasourceKey, String revisionId, DataSource dataSource) {
    public PreparedDatasource {
        if (datasourceKey == null || datasourceKey.isBlank() || revisionId == null || revisionId.isBlank() || dataSource == null) {
            throw new IllegalArgumentException("prepared datasource is incomplete");
        }
    }
}
