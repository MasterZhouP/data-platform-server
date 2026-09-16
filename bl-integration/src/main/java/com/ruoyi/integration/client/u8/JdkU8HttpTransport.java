package com.ruoyi.integration.client.u8;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * JDK HTTP 实现只负责把已组装的网关请求送至固定 U8 主机。
 * 网络超时和连接中断会保留“请求可能已到达”的事实，供上层阻止盲目重推。
 */
public class JdkU8HttpTransport implements U8HttpTransport
{
    private final U8GatewayProperties properties;
    private final HttpClient client;

    public JdkU8HttpTransport(U8GatewayProperties properties)
    {
        this.properties = properties;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis())).build();
    }

    @Override
    public U8HttpResponse exchange(U8HttpRequest request)
    {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(trimSlash(properties.getBaseUrl()) + request.path() + query(request.queryParameters())))
                .timeout(Duration.ofMillis(properties.getReadTimeoutMillis()))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json;charset=UTF-8");
        request.headers().forEach(builder::header);
        if ("POST".equalsIgnoreCase(request.method()))
        {
            builder.POST(HttpRequest.BodyPublishers.ofString(request.body() == null ? "" : request.body(), StandardCharsets.UTF_8));
        }
        else
        {
            builder.method(request.method(), HttpRequest.BodyPublishers.noBody());
        }
        try
        {
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new U8HttpResponse(response.statusCode(), response.body());
        }
        catch (HttpConnectTimeoutException | ConnectException ex)
        {
            throw new U8TransportException("U8连接失败", false, ex);
        }
        catch (HttpTimeoutException ex)
        {
            throw new U8TransportException("U8请求超时", true, ex);
        }
        catch (InterruptedException ex)
        {
            Thread.currentThread().interrupt();
            throw new U8TransportException("U8请求被中断", true, ex);
        }
        catch (IOException ex)
        {
            throw new U8TransportException("U8连接中断", true, ex);
        }
        catch (IllegalArgumentException ex)
        {
            throw new U8TransportException("U8连接配置无效", false, ex);
        }
    }

    private String query(Map<String, String> values)
    {
        if (values.isEmpty())
        {
            return "";
        }
        return values.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .reduce((left, right) -> left + "&" + right).map(value -> "?" + value).orElse("");
    }

    private String trimSlash(String value)
    {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
