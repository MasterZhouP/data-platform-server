package com.ruoyi.integration.datasource;

import javax.sql.DataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Configuration
@EnableConfigurationProperties
public class IntegrationExternalDataSourceConfig
{
    @Bean("oaDataSourceProperties")
    @ConfigurationProperties("integration.datasource.oa")
    @ConditionalOnProperty(prefix = "integration.datasource.oa", name = "enabled", havingValue = "true")
    public IntegrationSqlServerProperties oaDataSourceProperties()
    {
        IntegrationSqlServerProperties properties = new IntegrationSqlServerProperties();
        properties.setReadOnly(true);
        return properties;
    }

    @Bean("oaDataSource")
    @ConditionalOnProperty(prefix = "integration.datasource.oa", name = "enabled", havingValue = "true")
    public DataSource oaDataSource(@Qualifier("oaDataSourceProperties") IntegrationSqlServerProperties properties)
    {
        return buildDataSource("integration-oa", properties);
    }

    @Bean("oaJdbcTemplate")
    @ConditionalOnProperty(prefix = "integration.datasource.oa", name = "enabled", havingValue = "true")
    public NamedParameterJdbcTemplate oaJdbcTemplate(@Qualifier("oaDataSource") DataSource dataSource)
    {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean("u8DataSourceProperties")
    @ConfigurationProperties("integration.datasource.u8")
    @ConditionalOnProperty(prefix = "integration.datasource.u8", name = "enabled", havingValue = "true")
    public IntegrationSqlServerProperties u8DataSourceProperties()
    {
        return new IntegrationSqlServerProperties();
    }

    @Bean("u8DataSource")
    @ConditionalOnProperty(prefix = "integration.datasource.u8", name = "enabled", havingValue = "true")
    public DataSource u8DataSource(@Qualifier("u8DataSourceProperties") IntegrationSqlServerProperties properties)
    {
        properties.setReadOnly(true);
        return buildDataSource("integration-u8", properties);
    }

    @Bean("u8JdbcTemplate")
    @ConditionalOnProperty(prefix = "integration.datasource.u8", name = "enabled", havingValue = "true")
    public NamedParameterJdbcTemplate u8JdbcTemplate(@Qualifier("u8DataSource") DataSource dataSource)
    {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    private DataSource buildDataSource(String poolName, IntegrationSqlServerProperties properties)
    {
        HikariDataSource dataSource = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .driverClassName(properties.getDriverClassName())
                .url(properties.getUrl())
                .username(properties.getUsername())
                .password(properties.getPassword())
                .build();
        dataSource.setPoolName(poolName);
        dataSource.setReadOnly(properties.isReadOnly());
        dataSource.setMaximumPoolSize(properties.getMaximumPoolSize());
        dataSource.setConnectionTimeout(properties.getConnectionTimeout());
        dataSource.setInitializationFailTimeout(-1L);
        return dataSource;
    }
}
