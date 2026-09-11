package com.example.mapchange.analysis.orchestration;

import com.example.mapchange.common.core.dto.TaskCancelledEvent;
import com.example.mapchange.common.core.dto.TaskMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 队列消费者（FR-2.4，J-020/J-024）：消费 task.inference；
 * 取消竞态兜底（评审 P-08）：消费时先回查任务状态（tryStart 内含），已 CANCELLED 直接丢弃；
 * PROCESSING 不提供取消。重试 3 次耗尽后由 AnalysisRabbitConfig 的 Recoverer 标记 FAILED 并进入 DLQ。
 */
@Component
public class TaskWorker {

    private static final Logger log = LoggerFactory.getLogger(TaskWorker.class);

    private final AnalysisOrchestrator orchestrator;

    public TaskWorker(AnalysisOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @RabbitListener(queues = "task.inference")
    public void onMessage(TaskMessage msg) {
        log.info("worker received task: id={}, type={}", msg.taskId(), msg.type());
        if (!orchestrator.tryStart(msg.taskId())) {
            return;   // 竞态兜底：已取消/状态冲突，丢弃
        }
        orchestrator.execute(msg.taskId(), msg.type());
    }

    /** 竞态兜底：回查任务状态，已 CANCELLED 则丢弃（设计 §3.1.2） */
    @RabbitListener(queues = "task.cancelled")
    public void onCancelled(TaskCancelledEvent e) {
        // 无需动作：状态机已置 CANCELLED，Worker 消费任务消息时会回查状态丢弃
        log.info("cancelled event received（竞态兜底确认）: taskId={}", e.taskId());
    }
}
