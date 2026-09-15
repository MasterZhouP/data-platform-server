package com.ruoyi.integration.client.oa.config;

/** Internal immutable OA REST revision. Never expose the secret identifier outside the service layer. */
public record OaRestRevision(String revisionId, String connectionKey, String targetId, String environment,
                             String baseUrl, String restUsername, String loginName,
                             int connectTimeoutMs, int readTimeoutMs, String secretId, String checksum) {
}
