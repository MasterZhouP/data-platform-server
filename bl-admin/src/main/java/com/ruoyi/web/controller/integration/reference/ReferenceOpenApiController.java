package com.ruoyi.web.controller.integration.reference;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.integration.reference.catalog.ReferenceCatalog;
import com.ruoyi.integration.reference.model.ReferenceException;
import com.ruoyi.integration.reference.model.ReferenceTask;
import com.ruoyi.integration.reference.service.ReferenceQueryService;
// import com.ruoyi.web.controller.integration.reference.security.ReferenceApiProperties;
import jakarta.servlet.http.HttpServletRequest;
// import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/integration/openapi/v1")
public class ReferenceOpenApiController
{
    private final ReferenceCatalog catalog;
    private final ReferenceQueryService queries;
    private final ObjectMapper mapper;
    public ReferenceOpenApiController(ReferenceCatalog catalog, ReferenceQueryService queries, ObjectMapper mapper)
    {
        this.catalog = catalog; this.queries = queries; this.mapper = mapper;
    }

    @GetMapping("/status")
    public Map<String, Object> status(HttpServletRequest request)
    {
        allowedParameters(request, Set.of());
        // 鉴权暂时禁用，保留 clientId 响应字段以避免破坏 OA 插件现有解析结构。
        // return ok(request, Map.of("status", "UP", "apiVersion", "1.0.0", "clientId", client().getClientId()));
        return ok(request, Map.of("status", "UP", "apiVersion", "1.0.0", "clientId", "AUTHENTICATION_DISABLED"));
    }

    @GetMapping("/reference/tasks")
    public Map<String, Object> tasks(HttpServletRequest request)
    {
        allowedParameters(request, Set.of("keyword", "pageNum", "pageSize"));
        int page = number(request, "pageNum", 1, 100000);
        int size = number(request, "pageSize", 20, 100);
        String keyword = request.getParameter("keyword");
        if (keyword != null && keyword.length() > 100) throw invalid("keyword 最长 100 字符");
        String search = keyword == null ? "" : keyword.toLowerCase(Locale.ROOT);
        // 鉴权暂时禁用：目录仍只返回已启用任务，但不再按服务身份的 taskCodes 二次过滤。
        // var identity = client();
        List<ReferenceTask> tasks = catalog.list().stream().filter(ReferenceTask::enabled)
                // .filter(t -> identity.allows(t.taskCode()))
                .filter(t -> t.taskCode().toLowerCase(Locale.ROOT).contains(search)
                        || t.taskName().toLowerCase(Locale.ROOT).contains(search))
                .sorted(Comparator.comparing(ReferenceTask::taskCode)).toList();
        var data = mapper.createObjectNode();
        data.put("pageNum", page).put("pageSize", size).put("total", tasks.size())
                .put("totalPages", (tasks.size() + size - 1) / size);
        var items = data.putArray("items");
        tasks.stream().skip((long) (page - 1) * size).limit(size).forEach(t -> items.addObject()
                .put("taskCode", t.taskCode()).put("taskName", t.taskName())
                .put("metadataVersion", t.metadata().path("metadataVersion").asText()));
        return ok(request, data);
    }

    @GetMapping("/reference/tasks/{taskCode}/metadata")
    public Map<String, Object> metadata(@PathVariable String taskCode, HttpServletRequest request)
    {
        allowedParameters(request, Set.of());
        return ok(request, accessibleTask(taskCode).metadata());
    }

    @PostMapping(value = "/reference/tasks/{taskCode}/query", consumes = "application/json")
    public Map<String, Object> query(@PathVariable String taskCode, @RequestBody JsonNode body,
            HttpServletRequest request)
    {
        allowedParameters(request, Set.of());
        var result = queries.query(accessibleTask(taskCode), body, ReferenceApiResponses.requestId(request));
        return ReferenceApiResponses.success(ReferenceApiResponses.requestId(request), result.executionId(), result.data());
    }

    private ReferenceTask accessibleTask(String taskCode)
    {
        // 鉴权暂时禁用：取消客户端 taskCodes 授权，但 taskCode 仍必须在任务目录中精确匹配。
        // if (!client().allows(taskCode)) throw new ReferenceException("TASK_NOT_FOUND", 404, "参照任务不存在或无访问权限");
        ReferenceTask task = catalog.get(taskCode);
        if (!task.enabled()) throw new ReferenceException("TASK_DISABLED", 409, "参照任务已停用");
        return task;
    }

    // 鉴权暂时禁用：保留原服务身份读取逻辑，后续恢复 X-Integration-Key 时取消注释。
    // private ReferenceApiProperties.Client client()
    // {
    //     var auth = SecurityContextHolder.getContext().getAuthentication();
    //     if (auth == null || !(auth.getPrincipal() instanceof ReferenceApiProperties.Client client))
    //         throw new ReferenceException("UNAUTHORIZED", 401, "服务凭据缺失或无效");
    //     return client;
    // }
    private Map<String, Object> ok(HttpServletRequest request, Object data)
    {
        return ReferenceApiResponses.success(ReferenceApiResponses.requestId(request), null, data);
    }
    private int number(HttpServletRequest request, String key, int fallback, int maximum)
    {
        String raw = request.getParameter(key);
        if (raw == null) return fallback;
        try
        {
            if (!raw.matches("[0-9]{1,6}")) throw invalid(key + " 格式不合法");
            int value = Integer.parseInt(raw);
            if (value < 1 || value > maximum) throw invalid(key + " 超出允许范围");
            return value;
        }
        catch (NumberFormatException ex) { throw invalid(key + " 格式不合法"); }
    }
    private void allowedParameters(HttpServletRequest request, Set<String> allowed)
    {
        request.getParameterMap().forEach((key, values) -> {
            if (!allowed.contains(key) || values.length != 1) throw invalid("存在未知或重复查询参数");
        });
    }
    private ReferenceException invalid(String message) { return new ReferenceException("INVALID_ARGUMENT", 400, message); }
}
