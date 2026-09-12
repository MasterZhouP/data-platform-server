package com.ruoyi.web.controller.integration.reference;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.web.controller.integration.reference.security.ReferenceApiProperties;
import com.ruoyi.web.controller.integration.reference.security.ReferenceApiSecurityConfig;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class ReferenceSecurityChainTest
{
    @Test void openApiBypassesAuthenticationWithoutOpeningManagementApi() throws Exception
    {
        try (var context = new AnnotationConfigWebApplicationContext())
        {
            context.setServletContext(new MockServletContext());
            context.register(Config.class, ReferenceApiSecurityConfig.class); context.refresh();
            // 鉴权暂时禁用：恢复时重新配置专用服务身份和允许访问的 taskCode。
            // var client = new ReferenceApiProperties.Client(); client.setClientId("test"); client.setEnabled(true);
            // client.setKey("test-key-012345678901234567890123456789"); client.setTaskCodes(List.of("A"));
            // context.getBean(ReferenceApiProperties.class).setClients(List.of(client));
            var mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
            String id = "83c87e30-8aba-4a82-b5d4-9233f52ead48";
            mvc.perform(get("/integration/openapi/v1/status").header("X-Request-Id", id))
                    .andExpect(status().isOk());
            mvc.perform(get("/integration/reference/options").header("X-Request-Id", id))
                    .andExpect(status().isUnauthorized());
            // 鉴权恢复时重新验证管理端 JWT 不能替代 X-Integration-Key。
            // mvc.perform(get("/integration/openapi/v1/status").header("Authorization", "Bearer management-jwt").header("X-Request-Id", id))
            //         .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }

    @Configuration @EnableWebSecurity @EnableWebMvc
    static class Config
    {
        @Bean ObjectMapper mapper() { return new ObjectMapper(); }
        @Bean Endpoints endpoints() { return new Endpoints(); }
        @Bean @Order(2) SecurityFilterChain managementChain(HttpSecurity http) throws Exception
        {
            return http.csrf(csrf -> csrf.disable()).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                    .exceptionHandling(e -> e.authenticationEntryPoint((q, r, x) -> r.setStatus(401))).build();
        }
    }
    @RestController static class Endpoints
    {
        @GetMapping({"/integration/openapi/v1/status", "/integration/reference/options"})
        public String status() { return "ok"; }
    }
}
