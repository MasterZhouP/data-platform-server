package com.ruoyi.web.controller.integration.task;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.integration.oatou8.config.TaskConfigException;
import com.ruoyi.integration.sql.SqlExecutionException;
import com.ruoyi.integration.taskdefinition.TaskDefinitionNotFoundException;
import com.ruoyi.integration.taskdefinition.management.TaskDraftNotValidatedException;
import com.ruoyi.integration.taskdefinition.management.TaskVersionConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 管理端用结构化业务码提示配置问题，不把 SQL、连接或堆栈细节返回浏览器。 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = IntegrationTaskAdminController.class)
public class IntegrationTaskApiExceptionHandler
{
    private static final Logger log = LoggerFactory.getLogger(IntegrationTaskApiExceptionHandler.class);

    @ExceptionHandler(TaskVersionConflictException.class)
    public AjaxResult conflict(TaskVersionConflictException exception)
    {
        return AjaxResult.error(409, exception.getMessage()).put("integrationCode", "TASK_VERSION_CONFLICT");
    }

    @ExceptionHandler(TaskDraftNotValidatedException.class)
    public AjaxResult draft(TaskDraftNotValidatedException exception)
    {
        return AjaxResult.error(400, exception.getMessage()).put("integrationCode", "DRAFT_NOT_VALIDATED");
    }

    @ExceptionHandler(TaskConfigException.class)
    public AjaxResult config(TaskConfigException exception)
    {
        return AjaxResult.error(400, exception.getMessage()).put("integrationCode", exception.code());
    }

    @ExceptionHandler(SqlExecutionException.class)
    public AjaxResult sql(SqlExecutionException exception)
    {
        int status = switch (exception.code())
        {
            case "DATASOURCE_UNAVAILABLE", "READ_QUERY_FAILED", "QUERY_ROW_LIMIT_EXCEEDED" -> 503;
            default -> 400;
        };
        return AjaxResult.error(status, exception.getMessage())
                .put("integrationCode", exception.code()).put("httpStatus", status);
    }

    @ExceptionHandler(RuntimeException.class)
    public AjaxResult unexpected(RuntimeException exception)
    {
        log.error("集成任务请求执行异常", exception);
        return AjaxResult.error(500, "任务预览或校验失败，请检查数据源、SQL 和任务配置")
                .put("integrationCode", "TASK_RUNTIME_ERROR").put("httpStatus", 500);
    }

    @ExceptionHandler({ IllegalArgumentException.class, TaskDefinitionNotFoundException.class })
    public AjaxResult argument(RuntimeException exception)
    {
        return AjaxResult.error(400, exception.getMessage()).put("integrationCode", "INVALID_TASK_ARGUMENT");
    }
}
