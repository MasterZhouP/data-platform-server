package com.ruoyi.integration.datasource.catalog;

import javax.crypto.spec.SecretKeySpec;
import com.ruoyi.integration.configuration.RevisionToken;
import com.ruoyi.integration.configuration.security.KeyProvider;
import com.ruoyi.integration.configuration.security.SecretCipher;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatasourceSecretStoreTest {
    @Test
    void persistsCiphertextAndRebindsTheSecretToTheNewRevision() throws Exception {
        JdbcDataSource db = new JdbcDataSource();
        db.setURL("jdbc:h2:mem:datasource_secret;MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (var connection = db.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE int_datasource_secret (secret_id VARCHAR(64) PRIMARY KEY, key_id VARCHAR(100), nonce VARBINARY(12), ciphertext BLOB, owner_key VARCHAR(160), owner_revision VARCHAR(64), create_time TIMESTAMP)");
        }
        Configuration configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(), db));
        String path = "mapper/integration/DatasourceMapper.xml";
        try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, configuration, path, configuration.getSqlFragments()).parse();
        }
        var sessions = new SqlSessionFactoryBuilder().build(configuration);
        KeyProvider keys = id -> new SecretKeySpec(new byte[32], "AES");
        try (var session = sessions.openSession(true)) {
            DatasourceSecretStore store = new DatasourceSecretStore(session.getMapper(DatasourceMapper.class), new SecretCipher(keys, "test-key"));
            String initial = store.store("u8", new RevisionToken("7"), "test-only-password".toCharArray());
            String rebound = store.rebind("u8", new RevisionToken("7"), initial, new RevisionToken("8"));

            assertArrayEquals("test-only-password".toCharArray(), store.read("u8", new RevisionToken("8"), rebound));
            assertThrows(RuntimeException.class, () -> store.read("oa", new RevisionToken("8"), rebound));
        }
    }
}
