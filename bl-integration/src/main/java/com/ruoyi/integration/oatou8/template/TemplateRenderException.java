package com.ruoyi.integration.oatou8.template;

/** JSON 模板未能在固定运行上下文中安全展开时抛出的业务异常。 */
public class TemplateRenderException extends RuntimeException
{
    private final String code;

    public TemplateRenderException(String code, String message)
    {
        super(message);
        this.code = code;
    }

    public String code()
    {
        return code;
    }
}
