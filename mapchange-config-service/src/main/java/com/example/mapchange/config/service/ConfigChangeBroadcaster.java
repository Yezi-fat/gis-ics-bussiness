package com.example.mapchange.config.service;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 配置变更广播（设计 §3.3/§5.4）：RabbitMQ fanout（exchange=mapchange.config），
 * 消息体为变更的组名；各服务 RemoteConfigCache / gateway GatewayConfigCacheListener 监听刷新。
 */
@Component
public class ConfigChangeBroadcaster {

    public static final String EXCHANGE_CONFIG = "mapchange.config";

    private final RabbitTemplate rabbitTemplate;

    public ConfigChangeBroadcaster(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void broadcast(String group) {
        rabbitTemplate.convertAndSend(EXCHANGE_CONFIG, "", group);
    }
}
