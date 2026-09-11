package com.example.mapchange.common.web.config;

import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

/**
 * 配置变更广播监听（设计 §5.4）：订阅 fanout exchange=mapchange.config（匿名独占队列，
 * 每实例一份），消息体为变更的组名，收到后回源刷新本地缓存。
 */
public class ConfigBroadcastListener {

    private final RemoteConfigCache cache;

    public ConfigBroadcastListener(RemoteConfigCache cache) {
        this.cache = cache;
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue,   // 匿名独占队列
            exchange = @Exchange(value = "mapchange.config", type = "fanout")))
    public void onConfigChanged(String group) {
        cache.refreshQuietly(group);
    }
}
