package com.example.mapchange.config.config;

import com.example.mapchange.config.service.ConfigChangeBroadcaster;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * config-service RabbitMQ 拓扑：fanout exchange=mapchange.config（配置变更广播，设计 §3.3）。
 * 各服务监听端为匿名独占队列（各自声明绑定），本服务只声明 exchange。
 */
@Configuration
public class RabbitConfig {

    @Bean
    public FanoutExchange configExchange() {
        return new FanoutExchange(ConfigChangeBroadcaster.EXCHANGE_CONFIG, true, false);
    }
}
