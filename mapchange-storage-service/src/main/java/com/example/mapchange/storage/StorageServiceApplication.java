package com.example.mapchange.storage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/** 存储服务入口（设计 §2.8）：对象存储抽象（OSS/OBS/local）、签名 URL / 文件下载、用量监控与空间保护 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class StorageServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(StorageServiceApplication.class, args);
    }
}
