package com.example.mapchange.common.core.dto;

import java.util.UUID;

/**
 * 任务取消事件（queue=task.cancelled）。
 * 仅作"用户取消与 Worker 消费之间的竞态兜底"：Worker 消费后回查任务状态，已 CANCELLED 则丢弃；
 * PROCESSING 不提供取消能力（评审 P-08，设计 §3.1.2）。
 */
public record TaskCancelledEvent(UUID taskId) {
}
