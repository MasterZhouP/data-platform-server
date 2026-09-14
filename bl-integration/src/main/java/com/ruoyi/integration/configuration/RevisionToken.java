package com.ruoyi.integration.configuration;

/** Immutable identifier for one configuration revision. */
public record RevisionToken(String value) {
    public RevisionToken {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("revision token is required");
        }
    }
}
