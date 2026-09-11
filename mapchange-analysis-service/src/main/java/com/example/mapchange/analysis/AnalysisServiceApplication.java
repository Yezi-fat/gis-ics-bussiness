package com.example.mapchange.analysis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/** 分析编排服务入口（设计 §2.6）：四类分析接口、校验、同步/异步编排、推理提供方三级解析、NLP 转发 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class AnalysisServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalysisServiceApplication.class, args);
    }
}
