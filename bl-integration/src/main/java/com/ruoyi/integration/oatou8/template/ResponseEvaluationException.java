package com.ruoyi.integration.oatou8.template;

/** U8 已有明确业务响应，但该响应不能进入成功后的查询或断点续跑阶段。 */
public class ResponseEvaluationException extends RuntimeException
{
    private final String code;

    public ResponseEvaluationException(String code, String message)
    {
        super(message);
        this.code = code;
    }

    public String code()
    {
        return code;
    }
}
