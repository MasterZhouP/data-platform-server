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
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(401); response.setContentType("application/json;charset=UTF-8");
                    mapper.writeValue(response.getOutputStream(), ReferenceApiResponses.error(
                            ReferenceApiResponses.requestId(request), null, "UNAUTHORIZED", "服务凭据缺失或无效", false));
                }))
                .addFilterBefore(new ReferenceApiKeyFilter(properties, mapper), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
