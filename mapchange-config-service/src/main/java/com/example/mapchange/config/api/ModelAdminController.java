package com.example.mapchange.config.api;

import com.example.mapchange.common.core.api.ApiResponse;
import com.example.mapchange.common.core.dto.ModelActivateRequest;
import com.example.mapchange.common.core.dto.ModelRegisterRequest;
import com.example.mapchange.common.core.dto.ModelVersionDto;
import com.example.mapchange.common.core.dto.python.InferModelListResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.mapchange.config.service.ModelRegistryService;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

/**
 * 本地模型版本管理（FR-3.5，评审 P-02 补位；管理接口经 gateway 鉴权 ADMIN）。
 * TODO M5（J-034）实现（M1 骨架：501 占位）。
 */
@RestController
@RequestMapping("/api/v1/models")
public class ModelAdminController {

    private final ModelRegistryService modelRegistryService;

    public ModelAdminController(ModelRegistryService modelRegistryService) {
        this.modelRegistryService = modelRegistryService;
    }

    /** 本地模型版本列表 */
    @GetMapping
    public ApiResponse<List<ModelVersionDto>> list() {
        return ApiResponse.ok(modelRegistryService.list());
    }

    /** 推理服务实际可用模型清单（透传 infer /infer/models，对接事项 J-2；供模型管理页对账展示） */
    @GetMapping("/available")
    public ApiResponse<InferModelListResponse> available() {
        return ApiResponse.ok(modelRegistryService.availableModels());
    }

    /** 登记模型版本元数据（REGISTERED；模型文件经 storage-service 取出写入共享卷，设计 §3.7） */
    @PostMapping
    public ApiResponse<ModelVersionDto> register(@RequestBody ModelRegisterRequest r,
                                                 @RequestHeader(value = "X-User-Id", required = false,
                                                         defaultValue = "anonymous") String operator) {
        return ApiResponse.ok(modelRegistryService.register(r, operator));
    }

    /** 切换激活版本（按 model_type 写对应 L2 键对，设计 §3.7） */
    @PutMapping("/active")
    public ApiResponse<Void> activate(@RequestBody ModelActivateRequest r) {
        modelRegistryService.activate(r);
        return ApiResponse.ok(null);
    }
}
