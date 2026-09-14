package com.ruoyi.integration.oatou8.template;

import java.util.List;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.oatou8.config.ResponseOutput;
import com.ruoyi.integration.oatou8.config.SuccessRule;

/**
 * 根据任务版本声明的成功规则确认 U8 是否已受理，并截取供后续阶段使用的输出。
 * <p>
 * 只有成功规则命中才会产生输出快照；明确的业务拒绝与网络超时由不同异常路径处理，后者不能被误判为可重推。
 * </p>
 */
public class JsonResponseEvaluator
{
    public ResponseEvaluation evaluate(JsonNode response, SuccessRule successRule, List<ResponseOutput> outputs)
    {
        return evaluate(response, successRule, outputs, null);
    }

    public ResponseEvaluation evaluate(JsonNode response, SuccessRule successRule, List<ResponseOutput> outputs,
            String errorMessagePointer)
    {
        JsonNode successValue = at(response, successRule.pointer());
        if (successValue.isMissingNode() || !successRule.allowedValues().contains(successValue.asText()))
        {
            throw new ResponseEvaluationException("U8_BUSINESS_REJECTED", errorMessage(response, errorMessagePointer));
        }
        ObjectNode extracted = JsonNodeFactory.instance.objectNode();
        for (ResponseOutput output : outputs)
        {
            JsonNode value = at(response, output.pointer());
            if (value.isMissingNode() || value.isNull())
            {
                if (output.required())
                {
                    throw new ResponseEvaluationException("U8_RESPONSE_OUTPUT_MISSING", "U8响应缺少必填输出: " + output.name());
                }
                continue;
            }
            extracted.set(output.name(), value.deepCopy());
        }
        return new ResponseEvaluation(extracted);
    }

    private JsonNode at(JsonNode response, String pointer)
    {
        return response.at(JsonPointer.compile(pointer));
    }

    private String errorMessage(JsonNode response, String errorMessagePointer)
    {
        if (errorMessagePointer == null)
        {
            return "U8业务响应未满足任务定义的成功规则";
        }
        JsonNode message = at(response, errorMessagePointer);
        return message.isMissingNode() || message.isNull() ? "U8业务响应未满足任务定义的成功规则" : message.asText();
    }
}
