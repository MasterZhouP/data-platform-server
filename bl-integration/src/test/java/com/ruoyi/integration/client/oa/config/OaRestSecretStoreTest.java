package com.ruoyi.integration.client.oa.config;

import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.spec.SecretKeySpec;
import com.ruoyi.integration.configuration.RevisionToken;
import com.ruoyi.integration.configuration.security.KeyProvider;
import com.ruoyi.integration.configuration.security.SecretCipher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OaRestSecretStoreTest {
    @Test
    void encryptsThePasswordAgainstTheOaConnectionAndRevision() {
        OaRestMapper mapper = mock(OaRestMapper.class);
        AtomicReference<OaRestSecret> saved = new AtomicReference<>();
        doAnswer(call -> { saved.set(call.getArgument(0)); return 1; }).when(mapper).insertSecret(any());
        when(mapper.findSecret(any())).thenAnswer(call -> saved.get());
        KeyProvider keys = id -> new SecretKeySpec(new byte[32], "AES");
        OaRestSecretStore store = new OaRestSecretStore(mapper, new SecretCipher(keys, "test-key"));

        String secretId = store.store("oa-default", new RevisionToken("revision-1"), "test-only-password".toCharArray());

        assertArrayEquals("test-only-password".toCharArray(),
                store.read("oa-default", new RevisionToken("revision-1"), secretId));
        assertThrows(RuntimeException.class,
                () -> store.read("other-oa", new RevisionToken("revision-1"), secretId));
    }
}
