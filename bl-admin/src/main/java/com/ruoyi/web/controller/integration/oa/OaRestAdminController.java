package com.ruoyi.web.controller.integration.oa;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.integration.client.oa.config.OaRestAdminService;
import com.ruoyi.integration.client.oa.config.OaRestReadService;
import com.ruoyi.integration.client.oa.runtime.OaRestRuntimeService;
import com.ruoyi.integration.configuration.ConfigurationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** OA REST account administration endpoints. Credentials are excluded from all responses and operation logs. */
@RestController
@RequestMapping("/integration/oa-rest")
public class OaRestAdminController {
    private final OaRestAdminService editor;
    private final OaRestReadService reads;
    private final OaRestRuntimeService runtime;

    public OaRestAdminController(OaRestAdminService editor, OaRestReadService reads, OaRestRuntimeService runtime) {
        this.editor = editor;
        this.reads = reads;
        this.runtime = runtime;
    }

    @GetMapping
    @PreAuthorize("@ss.hasPermi('integration:oa-rest:list')")
    public AjaxResult list() {
        return AjaxResult.success(reads.list());
    }

    @GetMapping("/{key}")
    @PreAuthorize("@ss.hasPermi('integration:oa-rest:view')")
    public AjaxResult detail(@PathVariable String key) {
        return AjaxResult.success(reads.detail(key));
    }

    @PutMapping("/{key}/draft")
    @PreAuthorize("@ss.hasPermi('integration:oa-rest:edit')")
    @Log(title = "保存OA REST账户草稿", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    public AjaxResult save(@PathVariable String key, @RequestBody ObjectNode request) {
        editor.saveDraft(key, request);
        return AjaxResult.success(reads.detail(key));
    }

    @PostMapping("/{key}/test")
    @PreAuthorize("@ss.hasPermi('integration:oa-rest:test')")
    public AjaxResult test(@PathVariable String key, @RequestBody ObjectNode request) {
        return AjaxResult.success(runtime.testDraft(key, expectedRevision(request)));
    }

    @PostMapping("/{key}/activate")
    @PreAuthorize("@ss.hasPermi('integration:oa-rest:activate')")
    @Log(title = "启用OA REST账户修订", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    public AjaxResult activate(@PathVariable String key, @RequestBody ObjectNode request) {
        return AjaxResult.success(runtime.activate(key, expectedRevision(request), expectedActiveId(request)));
    }

    @PostMapping("/{key}/disable")
    @PreAuthorize("@ss.hasPermi('integration:oa-rest:activate')")
    @Log(title = "停用OA REST账户", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    public AjaxResult disable(@PathVariable String key) {
        runtime.disable(key);
        return AjaxResult.success();
    }

    @GetMapping("/{key}/usages")
    @PreAuthorize("@ss.hasPermi('integration:oa-rest:view')")
    public AjaxResult usages(@PathVariable String key) {
        reads.detail(key);
        return AjaxResult.success(Map.of("items", List.of(), "blocking", false));
    }

    private static String expectedRevision(ObjectNode request) {
        return optionalText(request, "expectedRevision");
    }

    private static String expectedActiveId(ObjectNode request) {
        return optionalText(request, "expectedActiveId");
    }

    private static String optionalText(ObjectNode request, String name) {
        if (request == null || !request.has(name) || request.path(name).isNull()) return null;
        if (!request.path(name).isTextual()) {
            throw new ConfigurationException("INVALID_ARGUMENT", 400, "修订标识格式不正确");
        }
        String value = request.path(name).asText().trim();
        return value.isEmpty() ? null : value;
    }
}
