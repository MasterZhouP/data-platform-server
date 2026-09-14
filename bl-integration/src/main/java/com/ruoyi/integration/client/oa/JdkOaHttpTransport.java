package com.ruoyi.integration.client.oa;

import java.io.IOException;
import java.net.URI;
import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class JdkOaHttpTransport implements OaHttpTransport
{
    private final OaRestSettings settings;
    private final HttpClient client;

    @Autowired
    public JdkOaHttpTransport(OaRestProperties properties)
    {
        this(properties.snapshot());
    }

    public JdkOaHttpTransport(OaRestSettings settings)
    {
        this.settings = settings;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(settings.connectTimeoutMillis()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public OaHttpResponse exchange(OaHttpRequest request)
    {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(trimSlash(settings.baseUrl()) + request.path()))
                .timeout(Duration.ofMillis(settings.readTimeoutMillis()))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json;charset=UTF-8");
        request.headers().forEach(builder::header);
        if ("POST".equalsIgnoreCase(request.method()))
        {
            builder.POST(HttpRequest.BodyPublishers.ofString(request.body() == null ? "" : request.body(),
                    StandardCharsets.UTF_8));
        }
        else
        {
            builder.method(request.method(), HttpRequest.BodyPublishers.noBody());
        }
        try
        {
            HttpResponse<String> response = client.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new OaHttpResponse(response.statusCode(), response.body());
        }
        catch (HttpConnectTimeoutException | ConnectException ex)
        {
            throw new OaTransportException("OA连接失败: " + ex.getMessage(), false, ex);
        }
        catch (HttpTimeoutException ex)
        {
            throw new OaTransportException("OA请求超时", true, ex);
        }
        catch (InterruptedException ex)
        {
            Thread.currentThread().interrupt();
            throw new OaTransportException("OA请求被中断", true, ex);
        }
        catch (IOException ex)
        {
            throw new OaTransportException("OA连接中断: " + ex.getMessage(), true, ex);
        }
        catch (IllegalArgumentException ex)
        {
            throw new OaTransportException("OA连接失败: " + ex.getMessage(), false, ex);
        }
    }

    private String trimSlash(String value)
    {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
