package com.example.mapchange.common.web.config;

import org.springframework.context.ApplicationEvent;

/** 配置组刷新完成事件（RemoteConfigCache 刷新后发布，供本服务组件热重建，如推理路由/限流器） */
public class ConfigGroupRefreshedEvent extends ApplicationEvent {

    private final String group;

    public ConfigGroupRefreshedEvent(Object source, String group) {
        super(source);
        this.group = group;
    }

    public String getGroup() {
        return group;
    }
}
