package com.ruoyi.integration.u8tooa.config;

import com.fasterxml.jackson.databind.JsonNode;

/** OA process payload details; the account, token and host remain outside task JSON. */
public record OaProcessRequest(String u8IdVariable, JsonNode payloadTemplate)
{
}
