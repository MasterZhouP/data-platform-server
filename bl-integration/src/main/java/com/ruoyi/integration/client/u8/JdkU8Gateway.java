package com.ruoyi.integration.client.u8;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * OA→U8 任务共享的唯一业务网关。
 * <p>
 * token 属于公共账户，按有效期缓存；tradeId 是 U8 交易唯一标识，每一次真实业务推送都重新申请。
 * 这样任务配置不再复制认证链路，同时 U8 调用结果不确定时可准确进入人工核验而非重复推单。
 * </p>
 */
@Component
public class JdkU8Gateway implements U8Gateway, U8Client
{
    private final U8HttpTransport transport;
    private final U8GatewayProperties properties;
    private final ObjectMapper json;
    private final Clock clock;
    private volatile CachedToken cachedToken;

    @Autowired
    public JdkU8Gateway(U8HttpTransport transport, U8GatewayProperties properties, ObjectMapper json)
    {
        this(transport, properties, json, Clock.systemUTC());
    }

    JdkU8Gateway(U8HttpTransport transport, U8GatewayProperties properties, ObjectMapper json, Clock clock)
    {
        this.transport = transport;
        this.properties = properties;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public U8CallResult postBusiness(String operationCode, String relativePath, JsonNode payload)
    {
        if (!properties.isConfigured())
        {
            return failure(U8CallStatus.PRE_SEND_FAILURE, "U8_NOT_CONFIGURED", "U8公共账户尚未配置");
        }
        if (!properties.getAllowedOperationCodes().contains(operationCode))
        {
            return failure(U8CallStatus.PRE_SEND_FAILURE, "U8_OPERATION_NOT_ALLOWED", "U8业务操作未注册");
        }
        if (!validRelativePath(relativePath))
        {
            return failure(U8CallStatus.PRE_SEND_FAILURE, "U8_PATH_INVALID", "U8业务接口必须是受控相对路径");
        }
        try
        {
            String body = json.writeValueAsString(payload);
            return postWithTokenRefresh(relativePath, body, false);
        }
        catch (JsonProcessingException ex)
        {
            return failure(U8CallStatus.PRE_SEND_FAILURE, "U8_REQUEST_INVALID", "U8请求JSON无法序列化");
        }
        catch (U8GatewayException ex)
        {
            return failure(U8CallStatus.PRE_SEND_FAILURE, ex.getErrorCode(), ex.getMessage());
        }
        catch (U8TransportException ex)
        {
            return transportFailure(ex);
        }
    }

    /**
     * 兼容已有代码任务的旧接口。新配置任务不能走此分支，仍必须经过操作码校验；
     * 保留它是为了不在本次改造中破坏尚未迁移的代码型调用方。
     */
    @Override
    public U8CallResult post(String endpoint, String requestPayload)
    {
        if (!properties.isConfigured() || !validRelativePath(endpoint))
        {
            return failure(U8CallStatus.PRE_SEND_FAILURE, "U8_LEGACY_REQUEST_INVALID", "U8旧接口请求不合法或网关未配置");
        }
        try
        {
            return postWithTokenRefresh(endpoint, requestPayload == null ? "" : requestPayload, false);
        }
        catch (U8GatewayException ex)
        {
            return failure(U8CallStatus.PRE_SEND_FAILURE, ex.getErrorCode(), ex.getMessage());
        }
        catch (U8TransportException ex)
        {
            return transportFailure(ex);
        }
    }

    @Override
    public U8ConnectionHealth health()
    {
        return new U8ConnectionHealth(properties.isConfigured(), cachedToken != null && cachedToken.expiresAt().isAfter(clock.instant()),
                properties.isConfigured() ? "U8公共账户已配置" : "U8公共账户未配置");
    }

    private U8CallResult postWithTokenRefresh(String relativePath, String body, boolean refreshed)
    {
        String token = token();
        String tradeId = tradeId(token);
        U8HttpResponse response = transport.exchange(new U8HttpRequest("POST", relativePath,
                authenticatedParameters(token, tradeId), Map.of(), body));
        if (response.statusCode() == 401 && !refreshed)
        {
            invalidateToken(token);
            return postWithTokenRefresh(relativePath, body, true);
        }
        if (response.statusCode() >= 200 && response.statusCode() < 300)
        {
            return new U8CallResult(U8CallStatus.SUCCESS, response.body(), null, null);
        }
        if (response.statusCode() >= 500 || response.statusCode() == 408)
        {
            return failure(U8CallStatus.RESULT_UNKNOWN, "U8_RESULT_UNKNOWN", "U8返回服务异常，业务处理结果无法确认");
        }
        return failure(U8CallStatus.BUSINESS_FAILURE, "U8_HTTP_FAILURE", "U8拒绝业务请求，HTTP " + response.statusCode());
    }

    private String token()
    {
        CachedToken cached = cachedToken;
        if (cached != null && cached.expiresAt().isAfter(clock.instant()))
        {
            return cached.value();
        }
        synchronized (this)
        {
            cached = cachedToken;
            if (cached != null && cached.expiresAt().isAfter(clock.instant()))
            {
                return cached.value();
            }
            U8HttpResponse response = transport.exchange(new U8HttpRequest("GET", properties.getTokenPath(),
                    properties.getAccountParameters(), Map.of(), null));
            if (response.statusCode() < 200 || response.statusCode() >= 300)
            {
                throw new U8GatewayException("U8_TOKEN_FAILED", "获取U8公共账户Token失败，HTTP " + response.statusCode());
            }
            String token = requiredJsonText(response.body(), properties.getTokenPointer(), "U8_TOKEN_INVALID", "U8认证响应缺少Token");
            cachedToken = new CachedToken(token, clock.instant().plusSeconds(Math.max(1, properties.getTokenCacheSeconds())));
            return token;
        }
    }

    /**
     * tradeId 不能缓存：即使 token 可共享，每次业务投递也要获得独立交易标识，才可按单据做结果核验与人工追踪。
     */
    private String tradeId(String token)
    {
        U8HttpResponse response = transport.exchange(new U8HttpRequest("GET", properties.getTradeIdPath(),
                authenticatedParameters(token, null), Map.of(), null));
        if (response.statusCode() < 200 || response.statusCode() >= 300)
        {
            throw new U8GatewayException("U8_TRADE_ID_FAILED", "获取U8交易标识失败，HTTP " + response.statusCode());
        }
        return requiredJsonText(response.body(), properties.getTradeIdPointer(), "U8_TRADE_ID_INVALID", "U8认证响应缺少交易标识");
    }

    private String requiredJsonText(String body, String pointer, String errorCode, String errorMessage)
    {
        try
        {
            JsonNode value = json.readTree(body).at(JsonPointer.compile(pointer));
            if (value.isMissingNode() || value.isNull() || value.asText().isBlank())
            {
                throw new U8GatewayException(errorCode, errorMessage);
            }
            return value.asText();
        }
        catch (U8GatewayException ex)
        {
            throw ex;
        }
        catch (Exception ex)
        {
            throw new U8GatewayException(errorCode, errorMessage);
        }
    }

    private Map<String, String> authenticatedParameters(String token, String tradeId)
    {
        Map<String, String> values = new LinkedHashMap<>(properties.getAccountParameters());
        values.put(properties.getTokenParameterName(), token);
        if (tradeId != null)
        {
            values.put(properties.getTradeIdParameterName(), tradeId);
        }
        return Map.copyOf(values);
    }

    private void invalidateToken(String token)
    {
        CachedToken cached = cachedToken;
        if (cached != null && cached.value().equals(token))
        {
            cachedToken = null;
        }
    }

    private boolean validRelativePath(String value)
    {
        return value != null && value.startsWith("/") && !value.startsWith("//") && !value.contains("://") && !value.contains("?");
    }

    private U8CallResult transportFailure(U8TransportException exception)
    {
        return exception.requestMayHaveReachedU8()
                ? failure(U8CallStatus.RESULT_UNKNOWN, "U8_RESULT_UNKNOWN", "U8调用结果无法确认")
                : failure(U8CallStatus.PRE_SEND_FAILURE, "U8_UNAVAILABLE", "U8连接不可用");
    }

    private U8CallResult failure(U8CallStatus status, String code, String message)
    {
        return new U8CallResult(status, null, code, message);
    }

    private record CachedToken(String value, Instant expiresAt)
    {
    }
}
