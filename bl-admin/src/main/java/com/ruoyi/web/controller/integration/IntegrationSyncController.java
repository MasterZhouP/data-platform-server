package com.ruoyi.web.controller.integration;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.integration.execution.service.ExecutionConflictException;
import com.ruoyi.integration.sync.ManualSyncNotSupportedException;
import com.ruoyi.integration.sync.ManualSyncService;
import com.ruoyi.integration.sync.salesoutbound.DuplicateOaProcessException;
import com.ruoyi.integration.sync.salesoutbound.SourceValidationException;
import com.ruoyi.integration.task.UnknownTaskException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/integration/sync")
public class IntegrationSyncController extends BaseController
{
    private final ManualSyncService service;

    public IntegrationSyncController(ManualSyncService service)
    {
        this.service = service;
    }

    @PreAuthorize("@ss.hasPermi('integration:sync:preview')")
    @GetMapping("/{taskCode}/preview/{documentNo}")
    public AjaxResult preview(@PathVariable String taskCode, @PathVariable String documentNo)
    {
        try
        {
            return success(service.preview(taskCode, documentNo));
        }
        catch (UnknownTaskException | ManualSyncNotSupportedException ex)
        {
            return AjaxResult.error(HttpStatus.NOT_FOUND, ex.getMessage());
        }
        catch (IllegalArgumentException | SourceValidationException ex)
        {
            return AjaxResult.error(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @Log(title = "集成任务手动推送", businessType = BusinessType.OTHER)
    @PreAuthorize("@ss.hasPermi('integration:sync:push')")
    @PostMapping("/{taskCode}/push/{documentNo}")
    public AjaxResult push(@PathVariable String taskCode, @PathVariable String documentNo)
    {
        return accept(taskCode, documentNo, false);
    }

    @Log(title = "集成任务强制重推", businessType = BusinessType.OTHER)
    @PreAuthorize("@ss.hasPermi('integration:sync:force')")
    @PostMapping("/{taskCode}/force-repush/{documentNo}")
    public AjaxResult forceRepush(@PathVariable String taskCode, @PathVariable String documentNo)
    {
        return accept(taskCode, documentNo, true);
    }

    private AjaxResult accept(String taskCode, String documentNo, boolean force)
    {
        try
        {
            return AjaxResult.success(force ? "已受理强制重推" : "已受理手动推送",
                    service.push(taskCode, documentNo, force));
        }
        catch (UnknownTaskException | ManualSyncNotSupportedException ex)
        {
            return AjaxResult.error(HttpStatus.NOT_FOUND, ex.getMessage());
        }
        catch (DuplicateOaProcessException | ExecutionConflictException | IllegalStateException ex)
        {
            return AjaxResult.error(HttpStatus.CONFLICT, ex.getMessage());
        }
        catch (IllegalArgumentException | SourceValidationException ex)
        {
            return AjaxResult.error(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }
}
