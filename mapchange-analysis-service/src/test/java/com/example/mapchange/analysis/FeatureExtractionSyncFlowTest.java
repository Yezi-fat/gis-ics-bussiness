package com.example.mapchange.analysis;

import com.example.mapchange.analysis.client.ConfigClient;
import com.example.mapchange.analysis.client.ResultClient;
import com.example.mapchange.analysis.client.StorageClient;
import com.example.mapchange.analysis.client.TaskClient;
import com.example.mapchange.common.core.api.ApiResponse;
import com.example.mapchange.common.core.dto.ElementDto;
import com.example.mapchange.common.core.dto.SignUrlResponse;
import com.example.mapchange.common.core.dto.TaskDto;
import com.example.mapchange.common.web.config.RemoteConfigCache;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 同步要素识别全链路集成测试（J-023）：multipart 收图 → 存图（打桩）→ 预签名 URL →
 * Python infer/compute（WireMock 打桩）→ 蒙版回传 → layer 写入 → async=false 登记 → 响应。
 */
@SpringBootTest(properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "security.auth.enabled=false"
})
@AutoConfigureMockMvc
class FeatureExtractionSyncFlowTest {

    /** 1x1 红色 PNG */
    private static final byte[] PNG_1PX = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    private static WireMockServer pythonStub;

    @MockBean
    private StorageClient storageClient;
    @MockBean
    private TaskClient taskClient;
    @MockBean
    private ResultClient resultClient;
    @MockBean
    private ConfigClient configClient;
    @MockBean
    private RemoteConfigCache configCache;

    @Autowired
    private MockMvc mockMvc;

    @BeforeAll
    static void startStub() {
        pythonStub = new WireMockServer(0);
        pythonStub.start();
        pythonStub.stubFor(post(urlEqualTo("/infer/segmentation")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"task_id":"t","masks":{"forest":"%s"},"combined_mask":"%s",
                         "statistics":{"forest":{"area_px":1,"area_m2":2.5,"ratio":1.0,"patch_count":1}},
                         "model_name":"landcover-seg","model_version":"v2.0"}
                        """.formatted(Base64.getEncoder().encodeToString(PNG_1PX),
                        Base64.getEncoder().encodeToString(PNG_1PX)))));
        pythonStub.stubFor(post(urlEqualTo("/compute/vectorize")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"task_id\":\"t\",\"features\":[]}")));
    }

    @AfterAll
    static void stopStub() {
        pythonStub.stop();
    }

    @DynamicPropertySource
    static void pythonUrls(DynamicPropertyRegistry registry) {
        registry.add("python.infer.url", () -> "http://localhost:" + pythonStub.port());
        registry.add("python.compute.url", () -> "http://localhost:" + pythonStub.port());
        registry.add("python.nlp.url", () -> "http://localhost:" + pythonStub.port());
    }

    @Test
    void syncExtractionFullChain() throws Exception {
        when(configCache.getOrDefault(anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(1));
        when(configClient.elementCatalog()).thenReturn(ApiResponse.ok(List.of(
                new ElementDto("forest", "森林", "#228B22", 1, true))));
        when(storageClient.put(anyString(), any())).thenReturn(ApiResponse.ok(null));
        when(storageClient.signUrl(any())).thenAnswer(inv ->
                ApiResponse.ok(new SignUrlResponse("http://files.local/x", 0)));
        when(taskClient.create(any())).thenAnswer(inv ->
                ApiResponse.ok(new TaskDto(UUID.randomUUID(), null, null, null, null, null,
                        null, null, null, null, false, null, null, null, "u", null, null)));
        when(resultClient.writeLayers(any(), any())).thenReturn(ApiResponse.ok(null));
        when(resultClient.writeRegionsFile(any(), any())).thenReturn(ApiResponse.ok("k/regions.geojson"));

        MockMultipartFile image = new MockMultipartFile("image", "img.png",
                MediaType.IMAGE_PNG_VALUE, PNG_1PX);

        mockMvc.perform(multipart("/api/v1/feature-extraction")
                        .file(image)
                        .param("elements", "forest")
                        .param("tile_range",
                                "{\"z\":1,\"x_min\":0,\"x_max\":0,\"y_min\":0,\"y_max\":0}")
                        .param("geo_extent", "[-180,-90,0,90]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.inference.model_name").value("landcover-seg"))
                .andExpect(jsonPath("$.data.inference.degraded").value(false))
                .andExpect(jsonPath("$.data.layers[0].element").value("forest"))
                .andExpect(jsonPath("$.data.layers[0].mask_url").exists())
                .andExpect(jsonPath("$.data.combined_mask_url").exists());

        // Python 两服务确实被调用（WireMock 打桩验证）
        pythonStub.verify(postRequestedFor(urlEqualTo("/infer/segmentation")));
        pythonStub.verify(postRequestedFor(urlEqualTo("/compute/vectorize")));
    }

    @Test
    void unsupportedElementRejected() throws Exception {
        when(configCache.getOrDefault(anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(1));
        when(configClient.elementCatalog()).thenReturn(ApiResponse.ok(List.of(
                new ElementDto("forest", "森林", "#228B22", 1, true))));

        MockMultipartFile image = new MockMultipartFile("image", "img.png",
                MediaType.IMAGE_PNG_VALUE, PNG_1PX);
        mockMvc.perform(multipart("/api/v1/feature-extraction")
                        .file(image)
                        .param("elements", "water")
                        .param("geo_extent", "[-180,-90,0,90]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_ELEMENTS"));
    }

    @Test
    void extentMismatchRejected() throws Exception {
        when(configCache.getOrDefault(anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(1));
        when(configClient.elementCatalog()).thenReturn(ApiResponse.ok(List.of(
                new ElementDto("forest", "森林", "#228B22", 1, true))));

        MockMultipartFile image = new MockMultipartFile("image", "img.png",
                MediaType.IMAGE_PNG_VALUE, PNG_1PX);
        mockMvc.perform(multipart("/api/v1/feature-extraction")
                        .file(image)
                        .param("elements", "forest")
                        .param("tile_range",
                                "{\"z\":1,\"x_min\":0,\"x_max\":0,\"y_min\":0,\"y_max\":0}")
                        .param("geo_extent", "[-170,-80,10,80]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
}
