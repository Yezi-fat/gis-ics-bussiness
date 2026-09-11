package com.example.mapchange.analysis.orchestration;

import com.example.mapchange.analysis.client.TaskClient;
import com.example.mapchange.common.core.dto.ProgressUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 进度上报（FR-2.4/8.6）：每完成一期/一步骤经 Feign 调 task-service 更新 progress。
 * 上报失败不阻断主流程（进度为尽力而为）。
 */
@Component
public class TaskProgressReporter {

    private static final Logger log = LoggerFactory.getLogger(TaskProgressReporter.class);

    private final TaskClient taskClient;

    public TaskProgressReporter(TaskClient taskClient) {
        this.taskClient = taskClient;
    }

    public void report(UUID taskId, ProgressUpdate p) {
        try {
            taskClient.updateProgress(taskId, p);
        } catch (Exception e) {
            log.warn("进度上报失败（不阻断）: taskId={}, stage={}, 原因={}", taskId, p.stage(), e.getMessage());
        }
    }
}
