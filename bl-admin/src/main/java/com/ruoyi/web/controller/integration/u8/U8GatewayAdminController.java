package com.ruoyi.web.controller.integration.u8;

import java.util.Map;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.integration.client.u8.config.U8GatewayAdminService;
import com.ruoyi.integration.client.u8.config.U8GatewayReadService;
import com.ruoyi.integration.client.u8.runtime.U8GatewayRuntimeService;
import com.ruoyi.integration.configuration.ConfigurationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** U8 public-account administration; credentials are never included in a response or operation log. */
@RestController
@RequestMapping("/integration/u8-account")
public class U8GatewayAdminController
{
    private final U8GatewayAdminService editor;
    private final U8GatewayReadService reads;
    private final U8GatewayRuntimeService runtime;

    public U8GatewayAdminController(U8GatewayAdminService editor, U8GatewayReadService reads,
            U8GatewayRuntimeService runtime)
    {
        this.editor = editor;
        this.reads = reads;
        this.runtime = runtime;
    }

    @GetMapping
    @PreAuthorize("@ss.hasPermi('integration:u8-account:list')")
    public AjaxResult list() { return AjaxResult.success(reads.list()); }

    @GetMapping("/options")
    @PreAuthorize("@ss.hasAnyPermi('integration:u8-account:view,integration:task:edit,integration:task:preview')")
    public AjaxResult options() { return AjaxResult.success(reads.options()); }

    @GetMapping("/{key}")
    @PreAuthorize("@ss.hasPermi('integration:u8-account:view')")
    public AjaxResult detail(@PathVariable String key) { return AjaxResult.success(reads.detail(key)); }

    @PutMapping("/{key}/draft")
    @PreAuthorize("@ss.hasPermi('integration:u8-account:edit')")
    @Log(title = "保存U8开放平台账户草稿", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    public AjaxResult save(@PathVariable String key, @RequestBody ObjectNode request)
    {
        editor.saveDraft(key, request);
        return AjaxResult.success(reads.detail(key));
    }

    @PostMapping("/{key}/test")
    @PreAuthorize("@ss.hasPermi('integration:u8-account:test')")
    public AjaxResult test(@PathVariable String key, @RequestBody ObjectNode request)
    {
        return AjaxResult.success(runtime.testDraft(key, expected(request, "expectedRevision")));
    }

    @PostMapping("/{key}/activate")
    @PreAuthorize("@ss.hasPermi('integration:u8-account:activate')")
    @Log(title = "启用U8开放平台账户修订", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    public AjaxResult activate(@PathVariable String key, @RequestBody ObjectNode request)
    {
        return AjaxResult.success(runtime.activate(key, expected(request, "expectedRevision"), expected(request, "expectedActiveId")));
    }

    @PostMapping("/{key}/disable")
    @PreAuthorize("@ss.hasPermi('integration:u8-account:activate')")
    @Log(title = "停用U8开放平台账户", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    public AjaxResult disable(@PathVariable String key)
    {
        runtime.disable(key);
        return AjaxResult.success();
    }

    @GetMapping("/{key}/usages")
    @PreAuthorize("@ss.hasPermi('integration:u8-account:view')")
    public AjaxResult usages(@PathVariable String key)
    {
        return AjaxResult.success(Map.of("items", reads.usages(key), "blocking", false));
    }

    private static String expected(ObjectNode request, String name)
    {
        if (request == null || !request.has(name) || request.path(name).isNull()) return null;
        if (!request.path(name).isTextual()) throw new ConfigurationException("INVALID_ARGUMENT", 400, "修订标识格式不正确");
        String value = request.path(name).asText().trim();
        return value.isEmpty() ? null : value;
    }
}
