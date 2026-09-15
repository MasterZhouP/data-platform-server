package com.ruoyi.integration.datasource.runtime;

import java.util.Arrays;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.configuration.ConfigurationException;
import com.ruoyi.integration.configuration.RevisionToken;
import com.ruoyi.integration.datasource.validation.DatasourcePolicy;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.stereotype.Component;

/** Builds a pool only from validated structure; raw JDBC URLs are never accepted. */
@Component
public class DatasourcePoolFactory {
    private final DatasourcePolicy policy;

    public DatasourcePoolFactory(DatasourcePolicy policy) {
        this.policy = policy;
    }

    public PreparedDatasource create(String key, RevisionToken revision, ObjectNode config, char[] password) {
        ObjectNode valid = policy.validate(config);
        if (password == null || password.length == 0) {
            throw new ConfigurationException("SECRET_REQUIRED", 409, "数据源尚未配置密码");
        }
        int poolSize = valid.path("maximumPoolSize").asInt(3);
        if (poolSize < 1 || poolSize > 10) {
            throw new ConfigurationException("DATASOURCE_POLICY_REJECTED", 400, "连接池大小必须在 1 到 10 之间");
        }
        long timeout = valid.path("connectionTimeoutMs").asLong(5000L);
        if (timeout < 1000L || timeout > 30000L) {
            throw new ConfigurationException("DATASOURCE_POLICY_REJECTED", 400, "连接超时必须在 1000 到 30000 毫秒之间");
        }
        HikariDataSource source = new HikariDataSource();
        try {
            source.setPoolName("integration-" + key + "-" + revision.value().substring(0, Math.min(8, revision.value().length())));
            source.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            source.setJdbcUrl(policy.toJdbcUrl(valid));
            source.setUsername(valid.path("username").asText());
            source.setPassword(new String(password));
            source.setReadOnly(true);
            source.setMaximumPoolSize(poolSize);
            source.setConnectionTimeout(timeout);
            source.setInitializationFailTimeout(-1L);
            return new PreparedDatasource(key, revision.value(), source);
        } catch (RuntimeException invalid) {
            source.close();
            throw invalid;
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
