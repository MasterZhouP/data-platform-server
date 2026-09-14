package com.ruoyi.integration.reference.catalog;

/** Persisted task configuration. SQL text is versioned configuration; datasource secrets never belong here. */
public record ReferenceTaskRow(String taskCode, String taskName, boolean enabled,
        String datasourceKey, String sqlText, String metadataJson, String metadataVersion) { }
