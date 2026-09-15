package com.ruoyi.integration.datasource.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.integration.configuration.RevisionToken;
import com.ruoyi.integration.datasource.catalog.DatasourceCatalog;
import com.ruoyi.integration.datasource.catalog.DatasourceRevision;
import com.ruoyi.integration.datasource.catalog.DatasourceSecretStore;
import com.ruoyi.integration.datasource.validation.DatasourcePolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatasourceAdminServiceTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void acceptsOnlyWhitelistedFieldsAndKeepsPasswordOutOfTheConfigurationJson() {
        DatasourceCatalog catalog = mock(DatasourceCatalog.class);
        DatasourceSecretStore secrets = mock(DatasourceSecretStore.class);
        when(secrets.store(eq("u8"), any(), any())).thenReturn("secret-9");
        when(catalog.saveDraft(eq("u8"), eq(null), any(), eq("secret-9"), any())).thenAnswer(invocation -> invocation.getArgument(4));
        DatasourceAdminService service = new DatasourceAdminService(catalog, secrets,
                new DatasourcePolicy(), json);
        var request = json.createObjectNode();
        request.put("name", "U8 生产库");
        request.put("host", "u8.internal");
        request.put("port", 1433);
        request.put("databaseName", "U8Data");
        request.put("username", "readonly");
        request.put("passwordUpdate", "test-only-password");

        RevisionToken saved = service.saveDraft("u8", request);

        assertEquals(36, saved.value().length());
        verify(catalog).saveDraft(eq("u8"), eq(null), argThat(config ->
                !config.has("passwordUpdate") && !config.has("password") && "SQLSERVER".equals(config.path("type").asText())),
                eq("secret-9"), any());
    }

    @Test
    void reencryptsTheExistingSecretForEveryNewRevisionWhenPasswordIsBlank() {
        DatasourceCatalog catalog = mock(DatasourceCatalog.class);
        DatasourceSecretStore secrets = mock(DatasourceSecretStore.class);
        when(catalog.currentSecretRevision("u8")).thenReturn(new DatasourceRevision("revision-2", "u8", "{}", "secret-2", "hash"));
        when(secrets.rebind(eq("u8"), eq(new RevisionToken("revision-2")), eq("secret-2"), any())).thenReturn("secret-3");
        when(catalog.saveDraft(eq("u8"), eq("revision-2"), any(), eq("secret-3"), any())).thenAnswer(invocation -> invocation.getArgument(4));
        DatasourceAdminService service = new DatasourceAdminService(catalog, secrets,
                new DatasourcePolicy(), json);
        var request = json.createObjectNode();
        request.put("host", "u8.internal");
        request.put("port", 1433);
        request.put("databaseName", "U8Data");
        request.put("username", "readonly");
        request.put("expectedRevision", "revision-2");

        service.saveDraft("u8", request);

        verify(secrets).rebind(eq("u8"), eq(new RevisionToken("revision-2")), eq("secret-2"), any());
    }
}
