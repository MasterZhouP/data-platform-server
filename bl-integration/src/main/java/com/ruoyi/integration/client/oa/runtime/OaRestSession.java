package com.ruoyi.integration.client.oa.runtime;

import com.ruoyi.integration.client.oa.OaProcessOperations;

public interface OaRestSession extends AutoCloseable {
    String connectionKey();
    String revisionId();
    String targetId();
    String loginName();
    OaProcessOperations operations();
    void authenticate();
    @Override void close();
}
