package com.ruoyi.integration.task;

/** Stable context accepted from a future authenticated OA adapter. */
public record TriggerCommand(String taskCode, String masterId, String formId, String summaryId)
{
}
