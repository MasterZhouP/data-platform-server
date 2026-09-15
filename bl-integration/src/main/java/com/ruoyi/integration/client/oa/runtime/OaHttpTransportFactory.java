package com.ruoyi.integration.client.oa.runtime;

import com.ruoyi.integration.client.oa.OaHttpTransport;
import com.ruoyi.integration.client.oa.OaRestSettings;

@FunctionalInterface
public interface OaHttpTransportFactory {
    OaHttpTransport create(OaRestSettings settings);
}
