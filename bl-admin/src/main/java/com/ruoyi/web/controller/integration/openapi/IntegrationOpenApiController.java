package com.ruoyi.web.controller.integration.openapi;

import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.integration.execution.service.AcceptanceResult;
import com.ruoyi.integration.execution.service.ExecutionAcceptor;
import com.ruoyi.integration.task.TaskAction;
import com.ruoyi.integration.task.TriggerCommand;
import com.ruoyi.integration.task.TriggerSource;
import com.ruoyi.web.controller.integration.reference.ReferenceApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * OA 插件的统一受理入口。
 * 每个单据类型都通过 taskCode 定位已发布任务，平台只创建待执行账本记录并异步处理，不在 HTTP 请求内直接推送 U8。
 */
@RestController
@RequestMapping("/integration/openapi/v1")
public class IntegrationOpenApiController
{
    private final ExecutionAcceptor executionAcceptor;

    public IntegrationOpenApiController(ExecutionAcceptor executionAcceptor)
    {
        this.executionAcceptor = executionAcceptor;
    }

    @PostMapping(value = "/executions", consumes = "application/json", produces = "application/json")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> accept(@RequestBody JsonNode body, HttpServletRequest servletRequest)
    {
        OpenApiExecutionRequest request = OpenApiExecutionRequest.from(body);
        TriggerCommand command = new TriggerCommand(required(request.taskCode(), "taskCode", 100),
                required(request.masterId(), "masterId", 200), optional(request.formId(), "formId", 200),
                optional(request.summaryId(), "summaryId", 200), TaskAction.CREATE, TriggerSource.OA_API, false);
        // 受理成功只代表平台已持久化待执行记录；U8 结果由执行记录和阶段日志后续查询。
        AcceptanceResult accepted = executionAcceptor.accept(command);
        return ReferenceApiResponses.success(ReferenceApiResponses.requestId(servletRequest),
                String.valueOf(accepted.executionId()), Map.of("executionId", accepted.executionId(), "status", accepted.status()));
    }

    private String required(String value, String field, int maximum)
    {
        String normalized = optional(value, field, maximum);
        if (normalized == null)
        {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return normalized;
    }

    private String optional(String value, String field, int maximum)
    {
        if (value == null)
        {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty())
        {
            return null;
        }
        if (normalized.length() > maximum)
        {
            throw new IllegalArgumentException(field + " 长度超过允许范围");
        }
        return normalized;
    }
}
