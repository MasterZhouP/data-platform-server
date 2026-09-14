package com.ruoyi.integration.datasource.runtime;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.configuration.ConfigurationException;
import com.ruoyi.integration.configuration.RevisionToken;
import com.ruoyi.integration.datasource.catalog.DatasourceCatalog;
import com.ruoyi.integration.datasource.catalog.DatasourceRevision;
import com.ruoyi.integration.datasource.catalog.DatasourceSecretStore;
import org.springframework.stereotype.Service;

/** Keeps draft verification isolated from active pools and promotes only a successful probe. */
@Service
public class DatasourceRuntimeService {
    private final DatasourceCatalog catalog;
    private final DatasourceSecretStore secrets;
    private final DatasourcePoolFactory pools;
    private final DatasourceProbe probe;
    private final DatasourceActivationService activation;

    public DatasourceRuntimeService(DatasourceCatalog catalog, DatasourceSecretStore secrets,
                                    DatasourcePoolFactory pools, DatasourceProbe probe,
                                    DatasourceActivationService activation) {
        this.catalog = catalog;
        this.secrets = secrets;
        this.pools = pools;
        this.probe = probe;
        this.activation = activation;
    }

    public ObjectNode testDraft(String key, String expectedRevisionId) {
        PreparedDatasource prepared = prepareDraft(key, expectedRevisionId);
        try {
            ObjectNode result = probe.test(prepared, allowedObjects(catalog.getDraft(key)));
            catalog.recordTest(key, new RevisionToken(prepared.revisionId()), result);
            return result;
        } finally {
            close(prepared);
        }
    }

    public ObjectNode activate(String key, String expectedRevisionId, String expectedActiveRevisionId) {
        PreparedDatasource prepared = prepareDraft(key, expectedRevisionId);
        ObjectNode result = probe.test(prepared, allowedObjects(catalog.getDraft(key)));
        catalog.recordTest(key, new RevisionToken(prepared.revisionId()), result);
        if (!"SUCCESS".equals(result.path("status").asText())) {
            close(prepared);
            throw new ConfigurationException("DATASOURCE_TEST_FAILED", 409, "数据源检测未通过，不能启用");
        }
        boolean activated = activation.activate(key, new RevisionToken(prepared.revisionId()), expectedActiveRevisionId, prepared);
        if (!activated) {
            close(prepared);
            throw conflict();
        }
        return result.put("activeRevisionId", prepared.revisionId());
    }

    public void disable(String key) {
        if (!catalog.disable(key)) {
            throw new ConfigurationException("CONFIGURATION_NOT_FOUND", 404, "数据源不存在");
        }
        activation.disable(key);
    }

    private PreparedDatasource prepareDraft(String key, String expectedRevisionId) {
        DatasourceRevision revision = catalog.draftRevision(key);
        if (hasText(expectedRevisionId) && !expectedRevisionId.equals(revision.revisionId())) {
            throw conflict();
        }
        ObjectNode config = catalog.getDraft(key);
        char[] password = secrets.read(key, new RevisionToken(revision.revisionId()), revision.secretId());
        try {
            return pools.create(key, new RevisionToken(revision.revisionId()), config, password);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private static Set<String> allowedObjects(ObjectNode config) {
        Set<String> values = new LinkedHashSet<>();
        config.path("allowedObjects").forEach(node -> {
            if (node.isTextual()) values.add(node.asText());
        });
        return Set.copyOf(values);
    }

    private static void close(PreparedDatasource prepared) {
        if (prepared.dataSource() instanceof AutoCloseable closable) {
            try {
                closable.close();
            } catch (Exception ignored) {
                // The candidate was never made active; a failed cleanup must not mask the probe result.
            }
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static ConfigurationException conflict() {
        return new ConfigurationException("CONFIGURATION_VERSION_CONFLICT", 409, "配置已更新，请重新加载后再操作");
    }
}
