package com.ruoyi.integration.reference.catalog;

/** Persisted task configuration. SQL text and datasource secrets never belong here. */
public record ReferenceTaskRow(String taskCode, String taskName, boolean enabled,
        String datasourceKey, String sqlResource, String metadataJson, String metadataVersion) { }
