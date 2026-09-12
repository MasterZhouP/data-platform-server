package com.ruoyi.web.controller.integration.reference.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.web.controller.integration.reference.ReferenceApiResponses;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableConfigurationProperties(ReferenceApiProperties.class)
public class ReferenceApiSecurityConfig
{
    @Bean @Order(1)
    public SecurityFilterChain referenceApiChain(HttpSecurity http, ReferenceApiProperties properties,
            ObjectMapper mapper) throws Exception
    {
        return http.securityMatcher("/integration/openapi/v1/**")
                .csrf(csrf -> csrf.disable()).requestCache(cache -> cache.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // OA 插件与平台当前同包、同服务器部署，暂时关闭开放接口鉴权。
                // 恢复服务凭据能力时，将 permitAll 改回下面保留的 authenticated 配置。
                // .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                // 鉴权暂时禁用：保留原未认证响应，后续恢复 X-Integration-Key 时重新启用。
                // .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) -> {
                //     response.setStatus(401); response.setContentType("application/json;charset=UTF-8");
                //     mapper.writeValue(response.getOutputStream(), ReferenceApiResponses.error(
                //             ReferenceApiResponses.requestId(request), null, "UNAUTHORIZED", "服务凭据缺失或无效", false));
                // }))
                // 过滤器仍保留 requestId、Content-Type 和请求体大小等协议校验；其中鉴权代码已注释。
                .addFilterBefore(new ReferenceApiKeyFilter(properties, mapper), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
