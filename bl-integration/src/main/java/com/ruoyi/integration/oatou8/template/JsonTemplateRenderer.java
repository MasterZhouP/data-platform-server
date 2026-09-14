package com.ruoyi.integration.oatou8.template;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.oatou8.ExecutionVariableContext;

/**
 * 将已校验的 U8 JSON 模板展开为本次执行请求。
 * <p>
 * 占位符必须独占一个 JSON 文本节点，替换后保持原有 JSON 类型；因此金额、明细数组和对象不会在推送时退化为字符串。
 * </p>
 */
public class JsonTemplateRenderer
{
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Za-z][A-Za-z0-9_.]{0,199})\\}\\}");

    private final ObjectMapper json;

    public JsonTemplateRenderer(ObjectMapper json)
    {
        this.json = json;
    }

    public JsonNode render(JsonNode template, ExecutionVariableContext context)
    {
        if (template.isObject())
        {
            ObjectNode rendered = json.createObjectNode();
            Iterator<Entry<String, JsonNode>> fields = template.fields();
            while (fields.hasNext())
            {
                Entry<String, JsonNode> field = fields.next();
                rendered.set(field.getKey(), render(field.getValue(), context));
            }
            return rendered;
        }
        if (template.isArray())
        {
            ArrayNode rendered = json.createArrayNode();
            for (JsonNode item : template)
            {
                rendered.add(render(item, context));
            }
            return rendered;
        }
        if (!template.isTextual())
        {
            return template.deepCopy();
        }
        return renderText(template.textValue(), context);
    }

    private JsonNode renderText(String source, ExecutionVariableContext context)
    {
        Matcher matcher = PLACEHOLDER.matcher(source);
        if (!matcher.matches())
        {
            if (source.contains("{{") || source.contains("}}"))
            {
                throw new TemplateRenderException("TEMPLATE_VARIABLE_INVALID", "JSON模板变量必须独占一个文本值");
            }
            return json.getNodeFactory().textNode(source);
        }
        String variable = matcher.group(1);
        ExecutionVariableContext.VariableValue value = context.lookup(variable);
        if (!value.defined())
        {
            throw new TemplateRenderException("TEMPLATE_VARIABLE_NOT_FOUND", "JSON模板引用的任务变量不存在: " + variable);
        }
        return value.value() instanceof JsonNode node ? node.deepCopy() : json.valueToTree(value.value());
    }
}
