package com.example.mapchange.result.api;

import com.example.mapchange.common.core.api.ApiResponse;
import com.example.mapchange.common.core.api.BizException;
import com.example.mapchange.common.core.api.ErrorCode;
import com.example.mapchange.common.core.dto.ExportResponse;
import com.example.mapchange.result.client.TaskClient;
import com.example.mapchange.result.service.ExportService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * GeoJSON 导出（对外，经 gateway，FR-4.2）。
 * 权属校验（评审 R-10③）：按 task.created_by 校验（ADMIN 例外），auth.enabled=false 时放行。
 */
@RestController
@RequestMapping("/api/v1/tasks")
public class ExportController {

    private final ExportService exportService;
    private final TaskClient taskClient;
    private final boolean authEnabled;

    public ExportController(ExportService exportService, TaskClient taskClient,
                            @Value("${security.auth.enabled:true}") boolean authEnabled) {
        this.exportService = exportService;
        this.taskClient = taskClient;
        this.authEnabled = authEnabled;
    }

    @GetMapping("/{id}/export")
    public ApiResponse<ExportResponse> export(@PathVariable UUID id,
                                              @RequestHeader(value = "X-User-Id", required = false) String userId,
                                              @RequestHeader(value = "X-User-Role", required = false) String role) {
        enforceOwnership(id, userId, role);
        return ApiResponse.ok(exportService.exportGeoJson(id));
    }

    private void enforceOwnership(UUID id, String userId, String role) {
        if (!authEnabled || "ADMIN".equalsIgnoreCase(role)) {
            return;
        }
        var task = taskClient.get(id).data();
        if (task != null && task.createdBy() != null && userId != null
                && !userId.equals(task.createdBy())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权导出他人任务结果");
        }
    }
}
