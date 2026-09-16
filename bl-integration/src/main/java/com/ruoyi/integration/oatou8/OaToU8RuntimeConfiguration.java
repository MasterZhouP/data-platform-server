package com.ruoyi.integration.oatou8;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.integration.client.u8.runtime.U8GatewayRegistry;
import com.ruoyi.integration.datasource.runtime.DatasourceRegistry;
import com.ruoyi.integration.execution.repository.ExecutionRepository;
import com.ruoyi.integration.oatou8.config.TaskConfigValidator;
import com.ruoyi.integration.oatou8.template.JsonResponseEvaluator;
import com.ruoyi.integration.oatou8.template.JsonTemplateRenderer;
import com.ruoyi.integration.sql.JdbcReadOnlySqlExecutor;
import com.ruoyi.integration.sql.ReadOnlySqlExecutor;
import com.ruoyi.integration.sql.SqlVariableResolver;
import com.ruoyi.integration.u8tooa.config.U8ToOaTaskConfigValidator;
import com.ruoyi.integration.client.oa.OaProcessOperations;
import com.ruoyi.integration.sync.salesoutbound.link.OaProcessLinkRepository;
import com.ruoyi.integration.u8tooa.U8ToOaTaskExecutor;
import com.ruoyi.integration.u8tooa.U8ToOaPreviewService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OA→U8 配置任务的受控运行时装配。
 * 只从受管数据源注册表取得 OA/U8 只读连接；页面配置既不能新增连接，也不能改变公共 U8 账户。
 */
@Configuration
public class OaToU8RuntimeConfiguration
{
    @Bean
    public ReadOnlySqlExecutor integrationReadOnlySqlExecutor(DatasourceRegistry datasourceRegistry, ObjectMapper json)
    {
        return new JdbcReadOnlySqlExecutor(datasourceRegistry, json);
    }

    @Bean
    public TaskConfigValidator oaToU8TaskConfigValidator(ObjectMapper json)
    {
        return new TaskConfigValidator(json);
    }

    @Bean
    public U8ToOaTaskConfigValidator u8ToOaTaskConfigValidator(ObjectMapper json)
    {
        return new U8ToOaTaskConfigValidator(json);
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
            JsonTemplateRenderer jsonTemplateRenderer,
            JsonResponseEvaluator jsonResponseEvaluator, ExecutionRepository repository, ObjectMapper json,
            U8GatewayRegistry gatewayRegistry)
    {
        return new OaToU8TaskExecutor(oaToU8TaskConfigValidator::parseAndValidate, integrationReadOnlySqlExecutor,
                sqlVariableResolver, jsonTemplateRenderer, gatewayRegistry, jsonResponseEvaluator, repository, json);
    }

    @Bean
    public OaToU8PreviewService oaToU8PreviewService(TaskConfigValidator oaToU8TaskConfigValidator,
            ReadOnlySqlExecutor integrationReadOnlySqlExecutor, SqlVariableResolver sqlVariableResolver,
            JsonTemplateRenderer jsonTemplateRenderer, ObjectMapper json)
    {
        return new OaToU8PreviewService(oaToU8TaskConfigValidator, integrationReadOnlySqlExecutor,
                sqlVariableResolver, jsonTemplateRenderer, json);
    }

    @Bean
    public U8ToOaTaskExecutor u8ToOaTaskExecutor(U8ToOaTaskConfigValidator validator,
            ReadOnlySqlExecutor sql, SqlVariableResolver sqlVariables, JsonTemplateRenderer templates,
            OaProcessOperations oa, OaProcessLinkRepository links, ObjectMapper json)
    {
        return new U8ToOaTaskExecutor(validator::parseAndValidate, sql, sqlVariables, templates, oa, links, json);
    }

    @Bean
    public U8ToOaPreviewService u8ToOaPreviewService(U8ToOaTaskConfigValidator validator,
            ReadOnlySqlExecutor sql, SqlVariableResolver sqlVariables, JsonTemplateRenderer templates, ObjectMapper json)
    {
        return new U8ToOaPreviewService(validator::parseAndValidate, sql, sqlVariables, templates, json);
    }
}
