package com.ruoyi.integration.client.oa.runtime;

import com.ruoyi.integration.client.oa.JdkOaHttpTransport;
import com.ruoyi.integration.client.oa.OaHttpTransport;
import com.ruoyi.integration.client.oa.OaRestSettings;
import org.springframework.stereotype.Component;

@Component
public class DefaultOaHttpTransportFactory implements OaHttpTransportFactory {
    @Override
    public OaHttpTransport create(OaRestSettings settings) {
        return new JdkOaHttpTransport(settings);
    }
}
