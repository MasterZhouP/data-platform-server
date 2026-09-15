package com.ruoyi.integration.reference.engine;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import com.ruoyi.integration.datasource.runtime.DatasourceLease;
import com.ruoyi.integration.datasource.runtime.DatasourceRegistry;
import org.springframework.jdbc.datasource.AbstractDataSource;

/** Exposes only managed datasource revisions and releases the matching pool lease with each query connection. */
public final class ManagedReferenceDataSources implements ReferenceDataSources {
    private final DatasourceRegistry registry;

    public ManagedReferenceDataSources(DatasourceRegistry registry) {
        this.registry = registry;
    }

    @Override
    public DataSource get(String key) {
        return new LeasedDataSource(key);
    }

    private final class LeasedDataSource extends AbstractDataSource {
        private final String key;

        private LeasedDataSource(String key) {
            this.key = key;
        }

        @Override
        public Connection getConnection() throws SQLException {
            DatasourceLease lease = registry.acquire(key);
            try {
                return leased(lease.dataSource().getConnection(), lease);
            } catch (SQLException | RuntimeException failure) {
                lease.close();
                throw failure;
            }
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }
    }

    private static Connection leased(Connection physical, DatasourceLease lease) {
        AtomicBoolean closed = new AtomicBoolean();
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("close".equals(method.getName())) {
                        if (closed.compareAndSet(false, true)) {
                            try {
                                physical.close();
                            } finally {
                                lease.close();
                            }
                        }
                        return null;
                    }
                    if ("unwrap".equals(method.getName()) && args != null && args.length == 1
                            && args[0] instanceof Class<?> type && type.isInstance(physical)) {
                        return physical;
                    }
                    if ("isWrapperFor".equals(method.getName()) && args != null && args.length == 1
                            && args[0] instanceof Class<?> type) {
                        return type.isInstance(physical);
                    }
                    try {
                        return method.invoke(physical, args);
                    } catch (InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                });
    }
}
