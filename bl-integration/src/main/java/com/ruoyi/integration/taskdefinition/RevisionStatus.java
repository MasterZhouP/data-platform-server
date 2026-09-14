package com.ruoyi.integration.taskdefinition;

/** A draft can be edited; only a published revision is eligible for a production execution. */
public enum RevisionStatus
{
    DRAFT,
    VALIDATED,
    PUBLISHED,
    ARCHIVED
}
