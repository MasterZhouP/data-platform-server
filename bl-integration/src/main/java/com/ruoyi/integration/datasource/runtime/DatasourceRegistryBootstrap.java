package com.ruoyi.integration.datasource.runtime;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.configuration.RevisionToken;
import com.ruoyi.integration.datasource.catalog.DatasourceCatalog;
import com.ruoyi.integration.datasource.catalog.DatasourceRevision;
import com.ruoyi.integration.datasource.catalog.DatasourceRow;
import com.ruoyi.integration.datasource.catalog.DatasourceSecretStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Rehydrates enabled managed datasource pools so reference and OA-to-U8 reads survive an application restart. */
@Component
public class DatasourceRegistryBootstrap
{
    private static final Logger log = LoggerFactory.getLogger(DatasourceRegistryBootstrap.class);

    private final DatasourceCatalog catalog;
    private final DatasourceSecretStore secrets;
    private final DatasourcePoolFactory pools;
    private final DatasourceProbe probe;
    private final DatasourceRegistry registry;

    public DatasourceRegistryBootstrap(DatasourceCatalog catalog, DatasourceSecretStore secrets,
            DatasourcePoolFactory pools, DatasourceProbe probe, DatasourceRegistry registry)
    {
        this.catalog = catalog;
        this.secrets = secrets;
        this.pools = pools;
        this.probe = probe;
        this.registry = registry;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void restoreActiveDatasources()
    {
        try
        {
            for (DatasourceRow datasource : catalog.list())
            {
                if (!datasource.enabled() || datasource.activeRevisionId() == null || datasource.activeRevisionId().isBlank())
                {
                    continue;
                }
                restore(datasource.datasourceKey());
            }
        }
        catch (RuntimeException failure)
        {
            log.warn("无法读取活动数据源目录 type={}", failure.getClass().getSimpleName());
        }
    }

    private void restore(String datasourceKey)
    {
        PreparedDatasource prepared = null;
        try
        {
            DatasourceRevision revision = catalog.activeRevision(datasourceKey);
            char[] password = secrets.read(datasourceKey, new RevisionToken(revision.revisionId()), revision.secretId());
            prepared = pools.create(datasourceKey, new RevisionToken(revision.revisionId()),
                    catalog.getActive(datasourceKey), password);
            ObjectNode result = probe.test(prepared);
            catalog.recordTest(datasourceKey, new RevisionToken(revision.revisionId()), result);
            if (!"SUCCESS".equals(result.path("status").asText()))
            {
                log.warn("无法恢复活动数据源 key={} code={}", datasourceKey, result.path("code").asText("UNKNOWN"));
                return;
            }
            registry.activate(prepared);
            prepared = null;
        }
        catch (RuntimeException failure)
        {
            log.warn("无法恢复活动数据源 key={} type={}", datasourceKey, failure.getClass().getSimpleName());
        }
        finally
        {
            close(prepared);
        }
    }

    private static void close(PreparedDatasource prepared)
    {
        if (prepared != null && prepared.dataSource() instanceof AutoCloseable closeable)
        {
            try
            {
                closeable.close();
            }
            catch (Exception ignored)
            {
                // A failed bootstrap candidate was never registered, so cleanup cannot affect active requests.
            }
        }
    }
}
