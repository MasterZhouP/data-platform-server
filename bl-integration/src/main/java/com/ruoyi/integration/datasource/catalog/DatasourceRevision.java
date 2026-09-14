package com.ruoyi.integration.datasource.catalog;

/** Immutable, internal datasource revision. Never serialize this type to an HTTP response. */
public record DatasourceRevision(String revisionId, String datasourceKey, String configJson,
                                 String secretId, String checksum) {
}
