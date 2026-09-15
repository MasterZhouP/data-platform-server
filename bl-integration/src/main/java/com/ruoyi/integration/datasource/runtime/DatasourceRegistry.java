package com.ruoyi.integration.datasource.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import com.ruoyi.integration.configuration.ConfigurationException;
import org.springframework.stereotype.Component;

/**
 * Swaps active pools atomically for new requests while retired pools drain in-flight leases.
 * It does not expose a mutable routing datasource, so a request can never switch revisions midway.
 */
@Component
public class DatasourceRegistry {
    private final Map<String, PoolSlot> active = new HashMap<>();

    public synchronized void activate(PreparedDatasource prepared) {
        PoolSlot replacement = new PoolSlot(prepared.datasourceKey(), prepared.revisionId(), prepared.dataSource());
        PoolSlot previous = active.put(prepared.datasourceKey(), replacement);
        retire(previous);
    }

    public synchronized void disable(String datasourceKey) {
        retire(active.remove(datasourceKey));
    }

    public synchronized DatasourceLease acquire(String datasourceKey) {
        PoolSlot slot = active.get(datasourceKey);
        if (slot == null || slot.retired) {
            throw new ConfigurationException("DATASOURCE_UNAVAILABLE", 503, "数据源尚未启用或正在切换");
        }
        slot.leases++;
        return new Lease(slot);
    }

    private synchronized void release(PoolSlot slot) {
        if (slot.leases > 0) slot.leases--;
        closeWhenDrained(slot);
    }

    private void retire(PoolSlot slot) {
        if (slot == null) return;
        slot.retired = true;
        closeWhenDrained(slot);
    }

    private void closeWhenDrained(PoolSlot slot) {
        if (!slot.retired || slot.leases != 0 || slot.closed) return;
        slot.closed = true;
        if (slot.dataSource instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // Pool shutdown is best effort; callers already hold no lease at this point.
            }
        }
    }

    private final class Lease implements DatasourceLease {
        private final PoolSlot slot;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Lease(PoolSlot slot) {
            this.slot = slot;
        }

        @Override
        public String datasourceKey() {
            return slot.datasourceKey;
        }

        @Override
        public String revisionId() {
            return slot.revisionId;
        }

        @Override
        public DataSource dataSource() {
            return slot.dataSource;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) release(slot);
        }
    }

    private static final class PoolSlot {
        private final String datasourceKey;
        private final String revisionId;
        private final DataSource dataSource;
        private int leases;
        private boolean retired;
        private boolean closed;

        private PoolSlot(String datasourceKey, String revisionId, DataSource dataSource) {
            this.datasourceKey = datasourceKey;
            this.revisionId = revisionId;
            this.dataSource = dataSource;
        }
    }
}
