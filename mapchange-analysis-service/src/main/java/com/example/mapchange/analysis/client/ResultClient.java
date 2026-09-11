package com.example.mapchange.analysis.client;

import com.example.mapchange.common.core.api.ApiResponse;
import com.example.mapchange.common.core.dto.LayerWriteRequest;
import com.example.mapchange.common.core.dto.RegionWriteRequest;
import com.example.mapchange.common.core.dto.RegionsFileRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

/** result-service 客户端：图层/区域/汇总写入（设计 §3.5） */
@FeignClient(name = "result-service", path = "/internal/results", configuration = com.example.mapchange.common.web.config.CommonFeignConfiguration.class)
public interface ResultClient {

    @PostMapping("/{taskId}/layers")
    ApiResponse<Void> writeLayers(@PathVariable UUID taskId, @RequestBody List<LayerWriteRequest> layers);

    /** 组织 FeatureCollection → regions.geojson 存存储层，返回 key */
    @PostMapping("/{taskId}/regions-file")
    ApiResponse<String> writeRegionsFile(@PathVariable UUID taskId, @RequestBody RegionsFileRequest req);

    /** 仅 persist.regions=db 时调用 */
    @PostMapping("/{taskId}/regions")
    ApiResponse<Void> writeRegions(@PathVariable UUID taskId, @RequestBody List<RegionWriteRequest> regions);
}
