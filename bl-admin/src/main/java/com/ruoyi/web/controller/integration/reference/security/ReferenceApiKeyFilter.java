package com.ruoyi.web.controller.integration.reference.security;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.web.controller.integration.reference.ReferenceApiResponses;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Service-only boundary; intentionally not a servlet @Component outside its security chain. */
public class ReferenceApiKeyFilter extends OncePerRequestFilter
{
    private final ReferenceApiProperties properties;
    private final ObjectMapper mapper;
    public ReferenceApiKeyFilter(ReferenceApiProperties properties, ObjectMapper mapper)
    {
        this.properties = properties; this.mapper = mapper;
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException
    {
        String suppliedId = request.getHeader("X-Request-Id");
        boolean validId = suppliedId != null && suppliedId.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
        String id = validId ? suppliedId : UUID.randomUUID().toString();
        request.setAttribute(ReferenceApiResponses.REQUEST_ID, id);
        response.setHeader("X-Request-Id", id);
        response.setHeader("Cache-Control", "no-store");
        if (!validId)
        {
            fail(response, id, 400, "INVALID_ARGUMENT", "X-Request-Id 必须为 UUID"); return;
        }
        String suppliedKey = request.getHeader("X-Integration-Key");
        ReferenceApiProperties.Client client = null;
        if (suppliedKey != null && suppliedKey.length() <= 4096 && properties.getClients() != null)
        {
            for (var candidate : properties.getClients())
            {
                String key = candidate.getKey();
                if (key != null && key.length() >= 32 && MessageDigest.isEqual(
                        key.getBytes(StandardCharsets.UTF_8), suppliedKey.getBytes(StandardCharsets.UTF_8)))
                    client = candidate;
            }
        }
        if (client == null || client.getClientId() == null || client.getClientId().isBlank())
        {
            fail(response, id, 401, "UNAUTHORIZED", "服务凭据缺失或无效"); return;
        }
        if (!client.isEnabled())
        {
            fail(response, id, 403, "FORBIDDEN", "服务身份已停用"); return;
        }
        HttpServletRequest effectiveRequest = request;
        if ("POST".equals(request.getMethod()))
        {
            boolean json;
            try { json = request.getContentType() != null && MediaType.APPLICATION_JSON.isCompatibleWith(
                    MediaType.parseMediaType(request.getContentType())); }
            catch (IllegalArgumentException ex) { json = false; }
            if (!json) { fail(response, id, 415, "UNSUPPORTED_MEDIA_TYPE", "请使用 application/json"); return; }
            byte[] body = request.getInputStream().readNBytes(65537);
            if (body.length > 65536) { fail(response, id, 413, "REQUEST_TOO_LARGE", "请求体超过 64 KiB"); return; }
            effectiveRequest = new HttpServletRequestWrapper(request)
            {
                @Override public ServletInputStream getInputStream()
                {
                    var input = new ByteArrayInputStream(body);
                    return new ServletInputStream()
                    {
                        @Override public int read() { return input.read(); }
                        @Override public boolean isFinished() { return input.available() == 0; }
                        @Override public boolean isReady() { return true; }
                        @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
                    };
                }
                @Override public java.io.BufferedReader getReader()
                {
                    return new java.io.BufferedReader(new java.io.InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
                }
                @Override public int getContentLength() { return body.length; }
                @Override public long getContentLengthLong() { return body.length; }
            };
        }
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(client, null, List.of()));
        chain.doFilter(effectiveRequest, response);
    }

    private void fail(HttpServletResponse response, String id, int status, String code, String message) throws IOException
    {
        response.setStatus(status); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), ReferenceApiResponses.error(id, null, code, message, false));
    }
}
