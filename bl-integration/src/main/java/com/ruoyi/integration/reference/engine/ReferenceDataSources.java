package com.ruoyi.integration.reference.engine;

import javax.sql.DataSource;

@FunctionalInterface
public interface ReferenceDataSources {
    /** Return null for an unknown or disabled source. Never initialize another source implicitly. */
    DataSource get(String key);
}
