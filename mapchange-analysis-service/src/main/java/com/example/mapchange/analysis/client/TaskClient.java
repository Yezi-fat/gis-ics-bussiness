package com.example.mapchange.analysis.client;

import com.example.mapchange.common.core.api.ApiResponse;
import com.example.mapchange.common.core.dto.ProgressUpdate;
import com.example.mapchange.common.core.dto.StatusUpdate;
import com.example.mapchange.common.core.dto.TaskCreateRequest;
import com.example.mapchange.common.core.dto.TaskDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

/** task-service 客户端：创建任务、状态流转、进度上报（同 TaskInternalController 签名） */
@FeignClient(name = "task-service", path = "/internal/tasks", configuration = com.example.mapchange.common.web.config.CommonFeignConfiguration.class)
public interface TaskClient {

    @PostMapping
    ApiResponse<TaskDto> create(@RequestBody TaskCreateRequest req);

    /** 内部按 ID 读取（Worker 竞态兜底回查，评审 P-08） */
    @GetMapping("/{id}")
    ApiResponse<TaskDto> get(@PathVariable UUID id);

    @PutMapping("/{id}/status")
    ApiResponse<Void> updateStatus(@PathVariable UUID id, @RequestBody StatusUpdate u);

    @PutMapping("/{id}/progress")
    ApiResponse<Void> updateProgress(@PathVariable UUID id, @RequestBody ProgressUpdate u);

    @GetMapping("/{id}/storage-keys")
    ApiResponse<List<String>> storageKeys(@PathVariable UUID id);
}
