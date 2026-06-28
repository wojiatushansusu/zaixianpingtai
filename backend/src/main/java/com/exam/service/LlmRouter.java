package com.exam.service;

import com.exam.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * LLM路由服务
 * 根据任务类型自动选择最合适的模型和参数，统一管理所有LLM调用。
 *
 * 策略：
 * - 轻量任务（错题讲解、简单对话）→ 使用轻量模型，快速响应
 * - 标准任务（多轮对话、知识问答）→ 使用标准模型，平衡质量与速度
 * - 重度任务（生成试卷、复杂分析）→ 使用高能力模型，保证质量
 *
 * 每个级别可配置不同的模型名称、max_tokens、temperature、timeout。
 * 如果某级别未配置，自动降级到默认配置。
 */
@Slf4j
@Service
public class LlmRouter {

    @Value("${app.ai.enabled:false}")
    private boolean enabled;

    @Value("${app.ai.default-model:qwen3:4b}")
    private String defaultModel;

    @Value("${app.ai.default-api-url:http://localhost:11434/v1/chat/completions}")
    private String defaultApiUrl;

    @Value("${app.ai.default-api-key:ollama}")
    private String defaultApiKey;

    @Value("${app.ai.default-timeout:60}")
    private int defaultTimeout;

    // === 轻量级 ===
    @Value("${app.ai.light.model:${app.ai.default-model}}")
    private String lightModel;

    @Value("${app.ai.light.max-tokens:1024}")
    private int lightMaxTokens;

    @Value("${app.ai.light.temperature:0.7}")
    private double lightTemperature;

    @Value("${app.ai.light.timeout:${app.ai.default-timeout}}")
    private int lightTimeout;

    // === 标准级 ===
    @Value("${app.ai.standard.model:${app.ai.default-model}}")
    private String standardModel;

    @Value("${app.ai.standard.max-tokens:2048}")
    private int standardMaxTokens;

    @Value("${app.ai.standard.temperature:0.7}")
    private double standardTemperature;

    @Value("${app.ai.standard.timeout:${app.ai.default-timeout}}")
    private int standardTimeout;

    // === 高能力 ===
    @Value("${app.ai.heavy.model:${app.ai.default-model}}")
    private String heavyModel;

    @Value("${app.ai.heavy.max-tokens:4096}")
    private int heavyMaxTokens;

    @Value("${app.ai.heavy.temperature:0.8}")
    private double heavyTemperature;

    @Value("${app.ai.heavy.timeout:300}")
    private int heavyTimeout;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 任务级别枚举
     */
    public enum TaskLevel {
        /** 轻量：错题讲解、简单问答 */
        LIGHT,
        /** 标准：多轮对话、知识问答 */
        STANDARD,
        /** 重度：生成试卷、复杂分析 */
        HEAVY
    }

    /**
     * 模型调用结果
     */
    @Data
    public static class LlmResult {
        private boolean success;
        private String content;
        private String error;
        private String usedModel;
        private long elapsedMs;

        public static LlmResult ok(String content, String model, long elapsedMs) {
            LlmResult r = new LlmResult();
            r.success = true;
            r.content = content;
            r.usedModel = model;
            r.elapsedMs = elapsedMs;
            return r;
        }

        public static LlmResult fail(String error) {
            LlmResult r = new LlmResult();
            r.success = false;
            r.error = error;
            return r;
        }
    }

    /**
     * 根据任务级别调用LLM
     *
     * @param level        任务级别
     * @param messages     消息列表（含system/user/assistant）
     * @param systemPrompt 系统提示词（可选，会插入到消息最前面）
     * @return 调用结果
     */
    public LlmResult call(TaskLevel level, List<Map<String, String>> messages, String systemPrompt) {
        if (!enabled) {
            return LlmResult.fail("AI功能未启用");
        }

        ModelConfig config = resolveConfig(level);
        log.info("LLM路由: level={}, model={}, maxTokens={}, temperature={}",
                level, config.model, config.maxTokens, config.temperature);

        long start = System.currentTimeMillis();
        try {
            String content = doCall(config, messages, systemPrompt);
            long elapsed = System.currentTimeMillis() - start;
            log.info("LLM调用成功: level={}, model={}, elapsed={}ms, contentLen={}",
                    level, config.model, elapsed, content.length());
            return LlmResult.ok(content, config.model, elapsed);
        } catch (BusinessException e) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("LLM调用业务异常: level={}, model={}, elapsed={}ms, error={}",
                    level, config.model, elapsed, e.getMessage());
            return LlmResult.fail(e.getMessage());
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("LLM调用失败: level={}, model={}, elapsed={}ms",
                    level, config.model, elapsed, e);
            return LlmResult.fail("AI服务调用失败: " + e.getMessage());
        }
    }

    /**
     * 根据任务级别调用LLM（简化版，单条用户消息）
     */
    public LlmResult call(TaskLevel level, String systemPrompt, String userMessage) {
        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        return call(level, List.of(userMsg), systemPrompt);
    }

    /**
     * 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    // ==================== 内部方法 ====================

    /**
     * 解析任务级别对应的模型配置
     */
    private ModelConfig resolveConfig(TaskLevel level) {
        ModelConfig config = new ModelConfig();
        config.apiUrl = defaultApiUrl;
        config.apiKey = defaultApiKey;

        switch (level) {
            case LIGHT -> {
                config.model = isEmpty(lightModel) ? defaultModel : lightModel;
                config.maxTokens = lightMaxTokens;
                config.temperature = lightTemperature;
                config.timeout = lightTimeout > 0 ? lightTimeout : defaultTimeout;
            }
            case STANDARD -> {
                config.model = isEmpty(standardModel) ? defaultModel : standardModel;
                config.maxTokens = standardMaxTokens;
                config.temperature = standardTemperature;
                config.timeout = standardTimeout > 0 ? standardTimeout : defaultTimeout;
            }
            case HEAVY -> {
                config.model = isEmpty(heavyModel) ? defaultModel : heavyModel;
                config.maxTokens = heavyMaxTokens;
                config.temperature = heavyTemperature;
                config.timeout = heavyTimeout > 0 ? heavyTimeout : defaultTimeout;
            }
        }
        return config;
    }

    /**
     * 实际调用LLM API
     */
    private String doCall(ModelConfig config, List<Map<String, String>> messages, String systemPrompt) throws Exception {
        // 构建消息列表
        List<Map<String, String>> allMessages = new ArrayList<>();

        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            Map<String, String> sysMsg = new HashMap<>();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);
            allMessages.add(sysMsg);
        }

        // 对话历史（最多保留最近20条消息）
        int start = Math.max(0, messages.size() - 20);
        allMessages.addAll(messages.subList(start, messages.size()));

        // 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.model);
        requestBody.put("max_tokens", config.maxTokens);
        requestBody.put("temperature", config.temperature);
        requestBody.put("messages", allMessages);

        // 设置超时
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(15000);
        factory.setReadTimeout(config.timeout * 1000);
        RestTemplate restTemplate = new RestTemplate(factory);

        // 构建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.apiKey);

        HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);

        // 发送请求
        ResponseEntity<String> response = restTemplate.exchange(
                config.apiUrl, HttpMethod.POST, entity, String.class);

        // 解析响应
        JsonNode root = objectMapper.readTree(response.getBody());
        JsonNode msgNode = root.path("choices").path(0).path("message");

        // 优先取 content；若为空则取 reasoning（兼容 Qwen3 思维链模型）
        String content = msgNode.path("content").asText("");
        if (content == null || content.isEmpty()) {
            content = msgNode.path("reasoning").asText("");
        }

        if (content.isEmpty()) {
            throw new BusinessException("AI模型返回为空，请检查模型是否正常运行");
        }

        return content;
    }

    private boolean isEmpty(String s) {
        return s == null || s.isEmpty() || s.startsWith("${");
    }

    @Data
    private static class ModelConfig {
        private String model;
        private String apiUrl;
        private String apiKey;
        private int maxTokens;
        private double temperature;
        private int timeout;
    }
}
