package com.ruoyi.integration.client.oa.runtime;

import java.util.Map;
import com.ruoyi.integration.client.oa.OaProcessOperations;
import com.ruoyi.integration.client.oa.OaProcessRef;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Routes OA process actions through the operator-approved managed account rather than a mutable YAML client. */
@Component
@Primary
public class ManagedOaProcessOperations implements OaProcessOperations {
    public static final String DEFAULT_CONNECTION_KEY = "oa-default";
    private final OaRestSessionRegistry registry;

    public ManagedOaProcessOperations(OaRestSessionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public OaProcessRef start(Map<String, Object> payload) {
        return registry.require(DEFAULT_CONNECTION_KEY).operations().start(payload);
    }

    @Override
    public void cancel(OaProcessRef process) {
        registry.require(DEFAULT_CONNECTION_KEY).operations().cancel(process);
    }
}
