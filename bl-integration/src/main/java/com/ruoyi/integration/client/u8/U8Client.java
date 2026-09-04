package com.ruoyi.integration.client.u8;

/** Stage 1 boundary only. A real implementation requires a U8 test account and Stage 2 endpoint facts. */
public interface U8Client
{
    U8CallResult post(String endpoint, String requestPayload);
}
