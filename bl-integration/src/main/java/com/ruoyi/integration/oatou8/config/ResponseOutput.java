package com.ruoyi.integration.oatou8.config;

/** A declared result value that may be persisted after a confirmed U8 response. */
public record ResponseOutput(String name, String pointer, boolean required)
{
}
