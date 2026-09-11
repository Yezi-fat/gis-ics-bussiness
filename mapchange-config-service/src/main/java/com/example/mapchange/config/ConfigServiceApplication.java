package com.example.mapchange.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/** 配置服务入口（设计 §2.9）：运行配置（校验/审计/热更新广播）、要素目录、模型注册、capabilities 聚合 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class ConfigServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServiceApplication.class, args);
    }
}
