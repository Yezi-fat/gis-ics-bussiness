package com.example.mapchange.result.client;

import com.example.mapchange.common.core.api.ApiResponse;
import com.example.mapchange.common.core.dto.TaskDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/** task-service 客户端（评审 S-01 补位）：读 task.created_by 供导出权属校验（R-10③） */
@FeignClient(name = "task-service", path = "/internal/tasks", configuration = com.example.mapchange.common.web.config.CommonFeignConfiguration.class)
public interface TaskClient {

    // TODO J-031：内部按 ID 读取任务（created_by）的端点与签名随实现补充
    @GetMapping("/{id}")
    ApiResponse<TaskDto> get(@PathVariable UUID id);
}
