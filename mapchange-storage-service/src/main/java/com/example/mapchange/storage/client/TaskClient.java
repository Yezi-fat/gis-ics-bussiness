package com.example.mapchange.storage.client;

import com.example.mapchange.common.core.api.ApiResponse;
import com.example.mapchange.common.core.dto.CleanupReport;
import com.example.mapchange.common.core.dto.CleanupRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** task-service 客户端（J-010 骨架）：空间超限时触发"最旧优先"清理（FR-4.5） */
@FeignClient(name = "task-service", path = "/internal/tasks", configuration = com.example.mapchange.common.web.config.CommonFeignConfiguration.class)
public interface TaskClient {

    @PostMapping("/cleanup")
    ApiResponse<CleanupReport> cleanup(@RequestBody CleanupRequest req);
}
