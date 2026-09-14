package com.ruoyi.integration.oatou8;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.integration.client.u8.U8Gateway;
import com.ruoyi.integration.client.u8.U8GatewayProperties;
import com.ruoyi.integration.execution.repository.ExecutionRepository;
import com.ruoyi.integration.oatou8.config.TaskConfigValidator;
import com.ruoyi.integration.oatou8.template.JsonResponseEvaluator;
import com.ruoyi.integration.oatou8.template.JsonTemplateRenderer;
import com.ruoyi.integration.sql.JdbcReadOnlySqlExecutor;
import com.ruoyi.integration.sql.ReadOnlySqlExecutor;
import com.ruoyi.integration.sql.SqlVariableResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * OA→U8 配置任务的受控运行时装配。
 * 仅把部署环境已注册的 OA/U8 只读数据源交给任务执行器，页面配置既不能新增连接，也不能改变公共 U8 账户。
 */
@Configuration
public class OaToU8RuntimeConfiguration
{
    @Bean
    public ReadOnlySqlExecutor integrationReadOnlySqlExecutor(
            @Qualifier("oaJdbcTemplate") ObjectProvider<NamedParameterJdbcTemplate> oa,
            @Qualifier("u8JdbcTemplate") ObjectProvider<NamedParameterJdbcTemplate> u8, ObjectMapper json)
    {
        Map<String, NamedParameterJdbcTemplate> templates = new LinkedHashMap<>();
        addIfPresent(templates, "oa", oa);
        addIfPresent(templates, "u8", u8);
        return new JdbcReadOnlySqlExecutor(templates, json);
    }

    @Bean
    public TaskConfigValidator oaToU8TaskConfigValidator(
            @Qualifier("oaJdbcTemplate") ObjectProvider<NamedParameterJdbcTemplate> oa,
            @Qualifier("u8JdbcTemplate") ObjectProvider<NamedParameterJdbcTemplate> u8Datasource,
            U8GatewayProperties u8, ObjectMapper json)
    {
        // 校验白名单从网关和部署注册的数据源生成，不能由页面提交值扩大权限。
        Set<String> readableSources = new LinkedHashSet<>();
        if (oa.getIfAvailable() != null)
        {
            readableSources.add("oa");
        }
        if (u8Datasource.getIfAvailable() != null)
        {
            readableSources.add("u8");
        }
        return new TaskConfigValidator(json, readableSources, u8.getAllowedOperationCodes());
    }

    @Bean
    public SqlVariableResolver sqlVariableResolver()
    {
        return new SqlVariableResolver();
    }

    @Bean
    public JsonTemplateRenderer jsonTemplateRenderer(ObjectMapper json)
    {
        return new JsonTemplateRenderer(json);
    }

    @Bean
    public JsonResponseEvaluator jsonResponseEvaluator()
    {
        return new JsonResponseEvaluator();
    }

    @Bean
    public OaToU8TaskExecutor oaToU8TaskExecutor(TaskConfigValidator oaToU8TaskConfigValidator,
            ReadOnlySqlExecutor integrationReadOnlySqlExecutor, SqlVariableResolver sqlVariableResolver,
            JsonTemplateRenderer jsonTemplateRenderer, U8Gateway u8Gateway,
            JsonResponseEvaluator jsonResponseEvaluator, ExecutionRepository repository, ObjectMapper json)
    {
        return new OaToU8TaskExecutor(oaToU8TaskConfigValidator::parseAndValidate, integrationReadOnlySqlExecutor,
                sqlVariableResolver, jsonTemplateRenderer, u8Gateway, jsonResponseEvaluator, repository, json);
    }

    @Bean
    public OaToU8PreviewService oaToU8PreviewService(TaskConfigValidator oaToU8TaskConfigValidator,
            ReadOnlySqlExecutor integrationReadOnlySqlExecutor, SqlVariableResolver sqlVariableResolver,
            JsonTemplateRenderer jsonTemplateRenderer, ObjectMapper json)
    {
        return new OaToU8PreviewService(oaToU8TaskConfigValidator, integrationReadOnlySqlExecutor,
                sqlVariableResolver, jsonTemplateRenderer, json);
    }

    private void addIfPresent(Map<String, NamedParameterJdbcTemplate> templates, String key,
            ObjectProvider<NamedParameterJdbcTemplate> provider)
    {
        NamedParameterJdbcTemplate template = provider.getIfAvailable();
        if (template != null)
        {
            templates.put(key, template);
        }
    }
}
