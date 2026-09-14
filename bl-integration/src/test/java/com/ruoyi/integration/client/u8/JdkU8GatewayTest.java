package com.ruoyi.integration.client.u8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;

class JdkU8GatewayTest
{
    private final ObjectMapper json = new ObjectMapper();
    private FakeTransport transport;
    private JdkU8Gateway gateway;

    @BeforeEach
    void setUp()
    {
        U8GatewayProperties properties = new U8GatewayProperties();
        properties.setBaseUrl("https://u8.example.test");
        properties.setAllowedOperationCodes(Set.of("VOUCHER_ADD"));
        properties.setAccountParameters(Map.of("from_account", "shared-account", "app_key", "shared-secret"));
        transport = new FakeTransport();
        gateway = new JdkU8Gateway(transport, properties, json, Clock.systemUTC());
    }

    @Test
    void reusesTheSharedAccountTokenButObtainsATradeIdForEveryBusinessPost() throws Exception
    {
        transport.respond(200, "{\"token\":{\"id\":\"token-1\"}}");
        transport.respond(200, "{\"trade\":{\"id\":\"trade-1\"}}");
        transport.respond(200, "{\"code\":0}");
        transport.respond(200, "{\"trade\":{\"id\":\"trade-2\"}}");
        transport.respond(200, "{\"code\":0}");

        U8CallResult first = gateway.postBusiness("VOUCHER_ADD", "/api/voucher/add", json.readTree("{\"id\":\"BX-1\"}"));
        U8CallResult second = gateway.postBusiness("VOUCHER_ADD", "/api/voucher/add", json.readTree("{\"id\":\"BX-2\"}"));

        assertEquals(U8CallStatus.SUCCESS, first.status());
        assertEquals(U8CallStatus.SUCCESS, second.status());
        assertEquals(1, transport.count("/system/token"));
        assertEquals(2, transport.count("/system/tradeid"));
        assertEquals("token-1", transport.requests.get(2).queryParameters().get("token"));
        assertEquals("trade-2", transport.requests.get(4).queryParameters().get("tradeid"));
        assertTrue(transport.requests.get(2).body().contains("BX-1"));
    }

    @Test
    void refreshesTokenOnlyAfterDefinitiveAuthenticationRejection() throws Exception
    {
        transport.respond(200, "{\"token\":{\"id\":\"token-1\"}}");
        transport.respond(200, "{\"trade\":{\"id\":\"trade-1\"}}");
        transport.respond(401, "{\"message\":\"expired\"}");
        transport.respond(200, "{\"token\":{\"id\":\"token-2\"}}");
        transport.respond(200, "{\"trade\":{\"id\":\"trade-2\"}}");
        transport.respond(200, "{\"code\":0}");

        U8CallResult result = gateway.postBusiness("VOUCHER_ADD", "/api/voucher/add", json.readTree("{\"id\":\"BX-1\"}"));

        assertEquals(U8CallStatus.SUCCESS, result.status());
        assertEquals(2, transport.count("/system/token"));
        assertEquals("token-2", transport.requests.get(5).queryParameters().get("token"));
    }

    @Test
    void classifiesTimeoutAfterBusinessSendAsResultUnknown() throws Exception
    {
        transport.respond(200, "{\"token\":{\"id\":\"token-1\"}}");
        transport.respond(200, "{\"trade\":{\"id\":\"trade-1\"}}");
        transport.fail(new U8TransportException("U8请求超时", true));

        U8CallResult result = gateway.postBusiness("VOUCHER_ADD", "/api/voucher/add", json.readTree("{\"id\":\"BX-1\"}"));

        assertEquals(U8CallStatus.RESULT_UNKNOWN, result.status());
        assertEquals("U8_RESULT_UNKNOWN", result.errorCode());
    }

    @Test
    void rejectsAnOperationThatWasNotRegisteredWithoutOpeningANetworkConnection() throws Exception
    {
        U8CallResult result = gateway.postBusiness("INVENTORY_ADD", "/api/inventory/add", json.readTree("{}"));

        assertEquals(U8CallStatus.PRE_SEND_FAILURE, result.status());
        assertEquals("U8_OPERATION_NOT_ALLOWED", result.errorCode());
        assertTrue(transport.requests.isEmpty());
    }

    private static final class FakeTransport implements U8HttpTransport
    {
        private final Queue<Object> responses = new ArrayDeque<>();
        private final List<U8HttpRequest> requests = new ArrayList<>();

        void respond(int status, String body)
        {
            responses.add(new U8HttpResponse(status, body));
        }

        void fail(U8TransportException exception)
        {
            responses.add(exception);
        }

        int count(String path)
        {
            return (int) requests.stream().filter(request -> path.equals(request.path())).count();
        }

        @Override
        public U8HttpResponse exchange(U8HttpRequest request)
        {
            requests.add(request);
            Object next = responses.remove();
            if (next instanceof U8TransportException exception)
            {
                throw exception;
            }
            return (U8HttpResponse) next;
        }
    }
}
