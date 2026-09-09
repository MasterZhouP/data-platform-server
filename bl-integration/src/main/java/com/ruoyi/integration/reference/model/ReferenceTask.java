package com.ruoyi.integration.reference.model;

import com.fasterxml.jackson.databind.node.ObjectNode;

public record ReferenceTask(String taskCode, String taskName, boolean enabled, String datasourceKey,
                            String sqlResource, ObjectNode metadata) { }
