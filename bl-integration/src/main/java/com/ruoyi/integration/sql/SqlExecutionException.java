package com.ruoyi.integration.sql;

/** Stable runtime error for a declared read-only SQL step. */
public class SqlExecutionException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    private final String code;

    public SqlExecutionException(String code, String message)
    {
        super(message);
        this.code = code;
    }

    public String code()
    {
        return code;
    }
}
