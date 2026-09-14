package com.ruoyi.integration.oatou8.config;

import java.util.Set;

/** Declares the response field and values that mean U8 has definitely accepted the document. */
public record SuccessRule(String pointer, Set<String> allowedValues)
{
}
