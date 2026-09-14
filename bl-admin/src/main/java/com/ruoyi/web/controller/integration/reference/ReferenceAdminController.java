package com.ruoyi.web.controller.integration.reference;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.integration.reference.catalog.ReferenceCatalog;
import com.ruoyi.integration.reference.engine.ReferenceEngine;
import com.ruoyi.integration.reference.model.ReferenceTask;
import com.ruoyi.integration.reference.service.ReferenceQueryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/integration/reference")
public class ReferenceAdminController
{
    private final ReferenceCatalog catalog;
    private final ReferenceEngine engine;
    private final ReferenceQueryService queries;
    private final boolean u8Enabled;
    public ReferenceAdminController(ReferenceCatalog catalog, ReferenceEngine engine, ReferenceQueryService queries,
            @Value("${integration.datasource.u8.enabled:false}") boolean u8Enabled)
    {
        this.catalog = catalog; this.engine = engine; this.queries = queries; this.u8Enabled = u8Enabled;
    }
    @GetMapping("/options") @PreAuthorize("@ss.hasPermi('integration:reference:list')")
    public AjaxResult options()
    {
        // 参照 SQL 由任务版本保存并在页面手工编辑；这里不再暴露类路径 SQL 资源选择器。
        return AjaxResult.success(Map.of("datasources", List.of(Map.of("key", "u8", "label", "U8 SQL Server", "enabled", u8Enabled)),
                "sqlEditor", Map.of("enabled", true, "readOnlyOnly", true)));
    }
    @GetMapping("/tasks") @PreAuthorize("@ss.hasPermi('integration:reference:list')")
    public AjaxResult list() { return AjaxResult.success(catalog.list()); }

    @GetMapping("/tasks/{taskCode}") @PreAuthorize("@ss.hasAnyPermi('integration:reference:query,integration:reference:edit')")
    public AjaxResult get(@PathVariable String taskCode) { return AjaxResult.success(catalog.get(taskCode)); }

    @PostMapping("/tasks") @PreAuthorize("@ss.hasPermi('integration:reference:edit')")
    @Log(title = "创建参照任务", businessType = BusinessType.INSERT)
    public AjaxResult create(@RequestBody ReferenceTask task) { return AjaxResult.success(catalog.create(task)); }

    @PutMapping("/tasks/{taskCode}") @PreAuthorize("@ss.hasPermi('integration:reference:edit')")
    @Log(title = "配置参照任务", businessType = BusinessType.UPDATE)
    public AjaxResult save(@PathVariable String taskCode, @RequestBody ReferenceTask task)
    { return AjaxResult.success(catalog.save(taskCode, task)); }

    @PostMapping("/tasks/{taskCode}/inspect") @PreAuthorize("@ss.hasPermi('integration:reference:edit')")
    public AjaxResult inspect(@PathVariable String taskCode)
    { return AjaxResult.success(engine.inspect(catalog.get(taskCode))); }

    @PostMapping("/tasks/{taskCode}/preview") @PreAuthorize("@ss.hasPermi('integration:reference:query')")
    public AjaxResult preview(@PathVariable String taskCode, @RequestBody JsonNode request)
    { return AjaxResult.success(queries.query(catalog.get(taskCode), request, UUID.randomUUID().toString()).data()); }
}
