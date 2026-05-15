package org.alphalius.examproject.config;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 统一管理所有 ChatModel 实例
 * <p>
 * 根据不同用途创建不同的 ChatModel 配置：
 * - SUMMARY: 摘要生成 (temperature=0.5)
 * - EVOLUTION: Prompt 自进化 (temperature=0.3)
 * - EXECUTION: 任务执行 (temperature=0.1)
 * <p>
 */
@Component
public class ChatModelProvider {

    private final String apiKey;
    private final String model;
    private final String baseUrl;

    public ChatModelProvider(
            @Value("${agent.api-key}") String apiKey,
            @Value("${agent.model}") String model,
            @Value("${agent.base-url}") String baseUrl
    ) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
    }

    /**
     * 获取摘要模型 (temperature=0.5)
     */
    public ChatModel getSummaryModel() {
        return AnthropicChatModel.builder()
                .options(AnthropicChatOptions.builder()
                        .model(model)
                        .baseUrl(baseUrl)
                        .apiKey(apiKey)
                        .temperature(0.5)
                        .maxTokens(8192)
                        .build())
                .build();
    }

    /**
     * 获取进化分析模型 (temperature=0.3)
     */
    public ChatModel getEvolutionModel() {
        return AnthropicChatModel.builder()
                .options(AnthropicChatOptions.builder()
                        .model(model)
                        .baseUrl(baseUrl)
                        .apiKey(apiKey)
                        .temperature(0.3)
                        .maxTokens(8192)
                        .build())
                .build();
    }

    /**
     * 获取任务执行模型 (temperature=0.1)
     */
    public ChatModel getExecutionModel() {
        return AnthropicChatModel.builder()
                .options(AnthropicChatOptions.builder()
                        .model(model)
                        .baseUrl(baseUrl)
                        .apiKey(apiKey)
                        .temperature(0.1)
                        .maxTokens(8192)
                        .build())
                .build();
    }
}