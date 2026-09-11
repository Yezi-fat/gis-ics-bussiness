package com.example.mapchange.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 网关入口（设计 §2.4）：统一对外门面——路由、JWT 鉴权、限流、审计日志、健康聚合。
 * WebFlux 栈：仅依赖 common-core，不依赖 common-web（评审 P-05）。
 */
@SpringBootApplication
@EnableDiscoveryClient
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
