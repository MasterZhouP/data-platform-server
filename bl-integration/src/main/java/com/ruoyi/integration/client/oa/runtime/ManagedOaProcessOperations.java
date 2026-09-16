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
    private final OaRestSessionRegistry registry;

    public ManagedOaProcessOperations(OaRestSessionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public OaProcessRef start(Map<String, Object> payload) {
        return registry.requireActive().operations().start(payload);
    }

    @Override
    public void cancel(OaProcessRef process) {
        registry.requireActive().operations().cancel(process);
    }
}
