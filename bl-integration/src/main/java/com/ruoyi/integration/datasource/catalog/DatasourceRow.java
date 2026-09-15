package com.ruoyi.integration.datasource.catalog;

/** Mutable directory pointer state. Revision content is intentionally kept elsewhere. */
public record DatasourceRow(String datasourceKey, String datasourceName, boolean enabled,
                            String activeRevisionId, String draftRevisionId, long rowVersion) {
}
