package com.ruoyi.integration.datasource.runtime;

import javax.sql.DataSource;

/** A request-scoped immutable reference to one datasource pool revision. */
public interface DatasourceLease extends AutoCloseable {
    String datasourceKey();
    String revisionId();
    DataSource dataSource();

    @Override
    void close();
}
