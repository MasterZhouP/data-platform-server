package com.ruoyi.integration.client.oa;

/** Immutable client settings captured when an OA REST session is created. */
public record OaRestSettings(String baseUrl, String restUsername, String password, String loginName,
                             int connectTimeoutMillis, int readTimeoutMillis) {
}
