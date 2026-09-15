package com.ruoyi.integration.client.oa.config;

/** Mutable directory pointers for a managed OA REST connection. */
public record OaRestConnection(String connectionKey, String connectionName, boolean enabled,
                               String activeRevisionId, String draftRevisionId, long rowVersion,
                               String targetId) {
}
