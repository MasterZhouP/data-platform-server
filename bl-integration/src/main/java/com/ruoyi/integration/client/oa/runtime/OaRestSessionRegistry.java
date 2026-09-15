package com.ruoyi.integration.client.oa.runtime;

import java.util.HashMap;
import java.util.Map;
import com.ruoyi.integration.configuration.ConfigurationException;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

/** Holds the currently enabled OA REST session and atomically retires its predecessor. */
@Component
public class OaRestSessionRegistry {
    private final Map<String, OaRestSession> active = new HashMap<>();

    public synchronized void activate(OaRestSession session) {
        if (session == null || session.connectionKey() == null || session.connectionKey().isBlank()) {
            throw new ConfigurationException("INVALID_ARGUMENT", 400, "待启用 OA REST 会话不完整");
        }
        OaRestSession previous = active.put(session.connectionKey(), session);
        if (previous != session) close(previous);
    }

    public synchronized OaRestSession require(String connectionKey) {
        OaRestSession session = active.get(connectionKey);
        if (session == null) {
            throw new ConfigurationException("OA_REST_UNAVAILABLE", 503, "OA REST 账户尚未启用");
        }
        return session;
    }

    public synchronized void disable(String connectionKey) {
        close(active.remove(connectionKey));
    }

    @PreDestroy
    public synchronized void closeAll() {
        for (OaRestSession session : active.values()) {
            close(session);
        }
        active.clear();
    }

    private static void close(OaRestSession session) {
        if (session == null) return;
        try {
            session.close();
        } catch (RuntimeException ignored) {
            // The replacement session is already live; an old token cache failing to clear must not interrupt it.
        }
    }
}
