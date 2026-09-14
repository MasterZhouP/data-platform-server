package com.ruoyi.integration.oatou8.config;

/** Stable configuration error shown by draft validation and publish checks. */
public class TaskConfigException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    private final String code;

    public TaskConfigException(String code, String message)
    {
        super(message);
        this.code = code;
    }

    public String code()
    {
        return code;
    }
}
