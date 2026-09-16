package com.ruoyi.integration.client.oa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OaProcessClientTest
{
    private ScriptedTransport transport;
    private OaProcessClient client;

    @BeforeEach
    void setUp()
    {
        OaRestSettings settings = new OaRestSettings("http://oa.test:8081", "rest-user", "rest-password",
                "bladmin", 5000, 15000);
        transport = new ScriptedTransport();
        OaTokenProvider tokens = new OaTokenProvider(transport, settings);
        client = new OaProcessClient(transport, tokens, settings);
    }

    @Test
    void authenticatesByPostAndStartsProcessWithTokenHeader()
    {
        transport.respond(200, "{\"id\":\"token-1\"}");
        transport.respond(200, successResponse());

        OaProcessRef result = client.start(Map.of("appName", "collaboration"));

        assertEquals("S-1", result.summaryId());
        assertEquals("A-1", result.affairId());
        assertEquals("P-1", result.processId());
        assertEquals("/seeyon/rest/token", transport.requests.get(0).path());
        assertTrue(transport.requests.get(0).body().contains("rest-user"));
        assertTrue(transport.requests.get(0).body().contains("bladmin"));
        assertEquals("/seeyon/rest/bpm/process/start", transport.requests.get(1).path());
        assertEquals("token-1", transport.requests.get(1).headers().get("token"));
    }

    @Test
    void refreshesTokenOnlyOnceAfterUnauthorizedResponse()
    {
        transport.respond(200, "{\"id\":\"token-1\"}");
        transport.respond(401, "invalid token");
        transport.respond(200, "{\"id\":\"token-2\"}");
        transport.respond(200, successResponse());

        OaProcessRef result = client.start(Map.of("appName", "collaboration"));

        assertEquals("S-1", result.summaryId());
        assertEquals("token-2", transport.requests.get(3).headers().get("token"));
        assertEquals(4, transport.requests.size());
    }

    @Test
    void marksStartTimeoutAsResultUnknownAndRejectsMalformedSuccess()
    {
        transport.respond(200, "{\"id\":\"token-1\"}");
        transport.fail(new OaTransportException("timeout", true));

        OaClientException timeout = assertThrows(OaClientException.class,
                () -> client.start(Map.of("appName", "collaboration")));
        assertTrue(timeout.isResultUnknown());

        setUp();
        transport.respond(200, "{\"id\":\"token-1\"}");
        transport.respond(200, "{\"code\":0,\"data\":{\"processId\":\"P-1\"}}");
        OaClientException malformed = assertThrows(OaClientException.class,
                () -> client.start(Map.of("appName", "collaboration")));
        assertTrue(malformed.isResultUnknown());
    }

    @Test
    void emptySuccessfulStartResponseIsResultUnknownInsteadOfAnUnexpectedFailure()
    {
        transport.respond(200, "{\"id\":\"token-1\"}");
        transport.respond(200, "");

        OaClientException failure = assertThrows(OaClientException.class,
                () -> client.start(Map.of("appName", "collaboration")));

        assertTrue(failure.isResultUnknown());
        assertEquals("OA_INVALID_RESPONSE", failure.getErrorCode());
    }

    @Test
    void emptyOrMalformedTokenResponseIsSafelyRetryableBecauseNoBusinessRequestWasSent()
    {
        transport.respond(200, "");
        OaClientException empty = assertThrows(OaClientException.class,
                () -> client.start(Map.of("appName", "collaboration")));
        assertTrue(empty.isRetryable());
        assertEquals("OA_AUTH_INVALID_RESPONSE", empty.getErrorCode());

        setUp();
        transport.respond(200, "not-json");
        OaClientException malformed = assertThrows(OaClientException.class,
                () -> client.start(Map.of("appName", "collaboration")));
        assertTrue(malformed.isRetryable());
        assertEquals("OA_AUTH_INVALID_RESPONSE", malformed.getErrorCode());

        setUp();
        transport.respond(200, "{\"id\":{}}");
        OaClientException wrongType = assertThrows(OaClientException.class,
                () -> client.start(Map.of("appName", "collaboration")));
        assertTrue(wrongType.isRetryable());
        assertEquals("OA_AUTH_INVALID_RESPONSE", wrongType.getErrorCode());
    }

    @Test
    void cancellationRequiresAnExplicitSuccessfulResponse()
    {
        transport.respond(200, "{\"id\":\"token-1\"}");
        transport.respond(200, "false");
        assertThrows(OaClientException.class,
                () -> client.cancel(new OaProcessRef("S-1", "A-1", "P-1")));

        setUp();
        transport.respond(200, "{\"id\":\"token-1\"}");
        transport.respond(200, "{\"success\":true}");
        client.cancel(new OaProcessRef("S-1", "A-1", "P-1"));
        assertTrue(transport.requests.get(1).body().contains("bladmin"));
    }

    @Test
    void ambiguousHttpOrResponseFailuresAreNeverMarkedRetryable()
    {
        transport.respond(200, "{\"id\":\"token-1\"}");
        transport.respond(500, "server error");
        OaClientException serverError = assertThrows(OaClientException.class,
                () -> client.start(Map.of("appName", "collaboration")));
        assertTrue(serverError.isResultUnknown());

        setUp();
        transport.respond(200, "{\"id\":\"token-1\"}");
        transport.respond(200, "not-json");
        OaClientException malformedCancel = assertThrows(OaClientException.class,
                () -> client.cancel(new OaProcessRef("S-1", "A-1", "P-1")));
        assertTrue(malformedCancel.isResultUnknown());
    }

    @Test
    void wrongStartResponseFieldTypesAreResultUnknown()
    {
        assertWrongStartTypeIsUnknown("{\"code\":{},\"data\":{}}");
        assertWrongStartTypeIsUnknown("{\"code\":0,\"data\":[]}");
        assertWrongStartTypeIsUnknown("{\"code\":0,\"data\":{\"processId\":{},\"app_bussiness_data\":[]}}");
    }

    private void assertWrongStartTypeIsUnknown(String body)
    {
        setUp();
        transport.respond(200, "{\"id\":\"token-1\"}");
        transport.respond(200, body);
        OaClientException failure = assertThrows(OaClientException.class,
                () -> client.start(Map.of("appName", "collaboration")));
        assertTrue(failure.isResultUnknown());
    }

    private String successResponse()
    {
        return "{\"code\":0,\"data\":{\"app_bussiness_data\":\"{\\\"affairId\\\":\\\"A-1\\\",\\\"summaryId\\\":\\\"S-1\\\"}\",\"processId\":\"P-1\"}}";
    }

    private static final class ScriptedTransport implements OaHttpTransport
    {
        private final Deque<Object> script = new ArrayDeque<>();
        private final List<OaHttpRequest> requests = new ArrayList<>();

        void respond(int status, String body)
        {
            script.addLast(new OaHttpResponse(status, body));
        }

        void fail(OaTransportException failure)
        {
            script.addLast(failure);
        }

        @Override
        public OaHttpResponse exchange(OaHttpRequest request)
        {
            requests.add(request);
            Object next = script.removeFirst();
            if (next instanceof OaTransportException failure)
            {
                throw failure;
            }
            return (OaHttpResponse) next;
        }
    }
}
