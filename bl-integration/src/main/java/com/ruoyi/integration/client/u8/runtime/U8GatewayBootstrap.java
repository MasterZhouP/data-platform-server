package com.ruoyi.integration.client.u8.runtime;

import java.util.Map;
import java.util.LinkedHashMap;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.client.u8.config.U8GatewayCatalog;
import com.ruoyi.integration.client.u8.config.U8GatewayConnection;
import com.ruoyi.integration.client.u8.config.U8GatewayRevision;
import com.ruoyi.integration.client.u8.config.U8GatewaySecretStore;
import com.ruoyi.integration.configuration.RevisionToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Restores the enabled U8 account after restart without falling back to deployment YAML credentials. */
@Component
public class U8GatewayBootstrap
{
    private static final Logger log = LoggerFactory.getLogger(U8GatewayBootstrap.class);
    private final U8GatewayCatalog catalog;
    private final U8GatewaySecretStore secrets;
    private final U8GatewayFactory factory;
    private final U8GatewayRegistry registry;
    private final ObjectMapper json;

    public U8GatewayBootstrap(U8GatewayCatalog catalog, U8GatewaySecretStore secrets,
            U8GatewayFactory factory, U8GatewayRegistry registry, ObjectMapper json)
    {
        this.catalog = catalog;
        this.secrets = secrets;
        this.factory = factory;
        this.registry = registry;
        this.json = json;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE + 10)
    public void restoreActiveAccounts()
    {
        try
        {
            for (U8GatewayConnection account : catalog.list())
            {
                if (account.enabled() && account.activeRevisionId() != null && !account.activeRevisionId().isBlank())
                {
                    restore(account.connectionKey());
                }
            }
        }
        catch (RuntimeException failure)
        {
            log.warn("无法读取U8账户目录 type={}", failure.getClass().getSimpleName());
        }
    }

    private void restore(String key)
    {
        U8GatewaySession session = null;
        try
        {
            U8GatewayRevision revision = catalog.active(key);
            Map<String, String> parameters = new LinkedHashMap<>(secrets.read(key,
                    new RevisionToken(revision.revisionId()), revision.secretId()));
            try
            {
                session = factory.create(revision, parameters);
            }
            finally
            {
                parameters.clear();
            }
            session.authenticate();
            ObjectNode result = success(revision.revisionId());
            catalog.recordTest(key, new RevisionToken(revision.revisionId()), result);
            registry.activate(session);
            session = null;
        }
        catch (RuntimeException failure)
        {
            log.warn("无法恢复活动U8账户 key={} type={}", key, failure.getClass().getSimpleName());
        }
        finally
        {
            if (session != null) try { session.close(); } catch (RuntimeException ignored) { }
        }
    }

    private ObjectNode success(String revisionId)
    {
        return json.createObjectNode()
                .put("status", "SUCCESS").put("message", "U8账户认证通过").put("revisionId", revisionId)
                .put("checkedAt", java.time.Instant.now().toString());
    }
}
