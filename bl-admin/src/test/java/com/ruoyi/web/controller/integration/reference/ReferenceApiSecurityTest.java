package com.ruoyi.web.controller.integration.reference;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.web.controller.integration.reference.security.ReferenceApiKeyFilter;
import com.ruoyi.web.controller.integration.reference.security.ReferenceApiProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class ReferenceApiSecurityTest
{
    private static final String ID = "83c87e30-8aba-4a82-b5d4-9233f52ead48";
    private static final String KEY = "test-only-012345678901234567890123456789";

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void missingKeyContinuesThroughTemporaryAuthenticationBypass() throws Exception
    {
        MockHttpServletResponse response = invoke(request(null), new ReferenceApiProperties(), true);
        // 鉴权恢复时重新启用以下断言：无服务 Key 必须返回 401。
        // assertEquals(401, response.getStatus());
        // assertEquals("UNAUTHORIZED", new ObjectMapper().readTree(response.getContentAsByteArray()).path("code").asText());
        assertEquals(200, response.getStatus());
        assertEquals(ID, response.getHeader("X-Request-Id"));
        assertEquals("no-store", response.getHeader("Cache-Control"));
    }

    // 鉴权暂时禁用：保留原服务身份测试，恢复 X-Integration-Key 时一并恢复。
    // @Test void validDedicatedKeyEstablishesOnlyItsConfiguredTaskIdentity() throws Exception
    // {
    //     invoke(request(KEY), properties(true), true);
    //     var auth = SecurityContextHolder.getContext().getAuthentication();
    //     assertNotNull(auth);
    //     assertTrue(auth.isAuthenticated());
    //     assertEquals("oa-test", auth.getName());
    //     assertTrue(auth.getAuthorities().isEmpty());
    // }

    // @Test void disabledClientCannotUseOtherwiseCorrectKey() throws Exception
    // {
    //     assertEquals(403, invoke(request(KEY), properties(false), false).getStatus());
    // }

    @Test void malformedRequestIdGetsReplacementAndReadable400() throws Exception
    {
        var request = request(null);
        request.removeHeader("X-Request-Id");
        request.addHeader("X-Request-Id", "bad-id");
        var response = invoke(request, properties(true), false);
        assertEquals(400, response.getStatus());
        assertDoesNotThrow(() -> java.util.UUID.fromString(response.getHeader("X-Request-Id")));
    }

    @Test void chunkedOversizeBodyIsRejectedEvenWithoutContentLength() throws Exception
    {
        var request = request(null);
        request.setMethod("POST");
        request.setContentType("application/json");
        request.setContent(new byte[65537]);
        assertEquals(413, invoke(request, properties(true), false).getStatus());
    }

    private MockHttpServletRequest request(String key)
    {
        var request = new MockHttpServletRequest("GET", "/integration/openapi/v1/status")
        {
            @Override public int getContentLength() { return -1; }
            @Override public long getContentLengthLong() { return -1; }
        };
        request.addHeader("X-Request-Id", ID);
        if (key != null) request.addHeader("X-Integration-Key", key);
        return request;
    }

    private ReferenceApiProperties properties(boolean enabled)
    {
        var p = new ReferenceApiProperties();
        var c = new ReferenceApiProperties.Client();
        c.setClientId("oa-test"); c.setKey(KEY); c.setEnabled(enabled);
        c.setTaskCodes(List.of("U8_MATERIAL_REFERENCE"));
        p.setClients(List.of(c));
        return p;
    }

    private MockHttpServletResponse invoke(MockHttpServletRequest request, ReferenceApiProperties props,
            boolean expectedChain) throws Exception
    {
        var response = new MockHttpServletResponse();
        boolean[] called = { false };
        new ReferenceApiKeyFilter(props, new ObjectMapper()).doFilter(request, response, (req, res) -> called[0] = true);
        assertEquals(expectedChain, called[0]);
        return response;
    }
}
