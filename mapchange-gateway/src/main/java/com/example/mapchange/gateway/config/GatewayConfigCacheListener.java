package com.example.mapchange.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

/**
 * 配置广播监听（评审 R-07）：订阅 RabbitMQ fanout（mapchange.config），
 * 收到变更后经 WebClient（非阻塞）回源 config-service 拉取该组并刷新内存缓存；
 * 启动初值取 L1 yml。gateway 为 WebFlux 栈，不依赖 common-web 的 RemoteConfigCache。
 */
@Component
public class GatewayConfigCacheListener {

    private static final Logger log = LoggerFactory.getLogger(GatewayConfigCacheListener.class);

    private final GatewayConfigCache cache;
    private final WebClient webClient;
    private final String internalToken;

    public GatewayConfigCacheListener(GatewayConfigCache cache, WebClient.Builder webClientBuilder,
                                      @Value("${security.internal-token:dev-internal-token}")
                                      String internalToken) {
        this.cache = cache;
        this.webClient = webClientBuilder.baseUrl("http://config-service").build();
        this.internalToken = internalToken;
    }

    /** 启动初值（回源失败静默——config-service 未就绪时 L1 yml 兜底，评审 T-04） */
    @EventListener(ApplicationReadyEvent.class)
    public void warmup() {
        fetchGroup("security");
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue,
            exchange = @Exchange(value = "mapchange.config", type = "fanout")))
    public void onConfigChanged(String group) {
        fetchGroup(group);
    }

    private void fetchGroup(String group) {
        webClient.get()
                .uri("/internal/config/" + group)
                .header("X-Internal-Token", internalToken)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(3))
                .map(body -> (Map<String, String>) body.get("data"))
                .subscribe(values -> {
                    if (values != null) {
                        cache.putAll(values);
                    }
                }, e -> log.warn("gateway 配置回源失败（沿用旧值）: group={}, {}", group, e.getMessage()));
    }
}
