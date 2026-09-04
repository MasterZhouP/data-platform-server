package com.ruoyi.web.controller.integration;

import java.util.List;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.integration.execution.domain.IntegrationExecution;
import com.ruoyi.integration.execution.service.AcceptanceResult;
import com.ruoyi.integration.execution.service.ExecutionNotFoundException;
import com.ruoyi.integration.execution.service.IntegrationExecutionService;
import com.ruoyi.integration.execution.service.RetryRejectedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/integration/execution")
public class IntegrationExecutionController extends BaseController
{
    private final IntegrationExecutionService executionService;

    public IntegrationExecutionController(IntegrationExecutionService executionService)
    {
        this.executionService = executionService;
    }

    @PreAuthorize("@ss.hasPermi('integration:execution:list')")
    @GetMapping("/list")
    public TableDataInfo list(IntegrationExecution query)
    {
        startPage();
        List<IntegrationExecution> values = executionService.findList(query);
        return getDataTable(values);
    }

    @PreAuthorize("@ss.hasPermi('integration:execution:query')")
    @GetMapping("/{executionId}")
    public AjaxResult getInfo(@PathVariable Long executionId)
    {
        try
        {
            return success(executionService.findDetail(executionId));
        }
        catch (ExecutionNotFoundException ex)
        {
            return AjaxResult.error(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    @Log(title = "集成执行人工重试", businessType = BusinessType.OTHER)
    @PreAuthorize("@ss.hasPermi('integration:execution:retry')")
    @PostMapping("/{executionId}/retry")
    public AjaxResult retry(@PathVariable Long executionId)
    {
        try
        {
            AcceptanceResult accepted = executionService.retry(executionId);
            return AjaxResult.success("已创建新的执行记录", accepted);
        }
        catch (ExecutionNotFoundException ex)
        {
            return AjaxResult.error(HttpStatus.NOT_FOUND, ex.getMessage());
        }
        catch (RetryRejectedException ex)
        {
            return AjaxResult.error(HttpStatus.CONFLICT, ex.getMessage());
        }
    }
}
