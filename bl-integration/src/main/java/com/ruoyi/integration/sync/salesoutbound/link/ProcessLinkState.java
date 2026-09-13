package com.ruoyi.integration.sync.salesoutbound.link;

public enum ProcessLinkState
{
    CREATING,
    CREATE_FAILED,
    ACTIVE,
    CANCELING,
    CANCELLED_PENDING_RECREATE,
    CANCELLED
}
