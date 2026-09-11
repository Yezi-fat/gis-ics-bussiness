package com.example.mapchange.result;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/** 结果管理服务入口（设计 §2.7）：layer/region 表、结果组装与实时重签、GeoJSON 导出、懒矢量化回填 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class ResultServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResultServiceApplication.class, args);
    }
}
