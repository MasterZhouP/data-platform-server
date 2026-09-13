package com.ruoyi.integration.sync.salesoutbound;

public class DuplicateOaProcessException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    public DuplicateOaProcessException(String documentNo)
    {
        super("单据 " + documentNo + " 已存在有效OA流程，请使用强制重推");
    }
}
