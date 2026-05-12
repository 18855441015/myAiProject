package org.alphalius.examproject.core.supervisoragents;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.alphalius.examproject.config.WorkspaceConfig;
import org.alphalius.examproject.hooks.ToolObserver;
import org.alphalius.examproject.interceptor.ToolMonitorInterceptor;
import org.alphalius.examproject.tools.DirectoryTool;
import org.alphalius.examproject.tools.FileOperateTool;
import org.alphalius.examproject.tools.GitTool;
import org.alphalius.examproject.tools.ShellExecutionTool;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 任务执行Agent - ReAct循环引擎
 *
 * 核心机制:
 * 1. 接收用户任务
 * 2. LLM思考下一步该做什么
 * 3. 如果需要调用工具 → 调用 → 把结果反馈给LLM → 回到2
 * 4. 如果LLM认为任务完成 → 返回最终结果
 * 5. 循环直到任务完成或达到最大步数
 */
@Slf4j
@Component
public class TaskExecutionAgent {

    private ReactAgent chatClient;
    private final WorkspaceConfig workspaceConfig;
    private String currentApiKey = "your api key";
    private String currentModel = "MiniMax-M2.7";

    // 保存工具引用，用于重建 chatClient
    private final ToolCallback[] toolCallbacks;

    // 当前会话的消息历史
    private final List<Message> conversationHistory = new ArrayList<>();

    public TaskExecutionAgent(
            // 注入所有自定义工具
            FileOperateTool fileTool,
            DirectoryTool directoryTool,
            ShellExecutionTool shellTool,
            GitTool gitTool,
            WorkspaceConfig workspaceConfig
    ) {
        this.workspaceConfig = workspaceConfig;

        // 保存工具引用
        this.toolCallbacks = ToolCallbacks.from(fileTool, directoryTool, shellTool, gitTool);

        // 初始化 chatClient
        rebuildChatClient();
    }

    /**
     * 执行用户任务 - 同步模式
     * Spring AI的ChatClient内部已经实现了tool call循环
     */
    public String execute(String userTask) {
        log.info("=== 开始执行任务: {} ===", userTask);

        // 将用户消息加入历史
        conversationHistory.add(new UserMessage(userTask));

        try {
            String result = chatClient.call(conversationHistory)
                .toString();

            // 将助手回复加入历史
            conversationHistory.add(new AssistantMessage(result));

            log.info("=== 任务执行完成 ===");
            return result;

        } catch (Exception e) {
            log.error("任务执行失败", e);
            return "任务执行失败: " + e.getMessage();
        }
    }

    /**
     * 流式执行 - 实时看到每一步
     * 过滤掉工具调用日志，只保留 AI 回复
     */
    public Flux<String> executeStream(String userTask) {
        conversationHistory.add(new UserMessage(userTask));
        log.info("executeStream called with task: {}, history size: {}", userTask, conversationHistory.size());

        try {
            StringBuilder modelResp = new StringBuilder();
            Flux<String> flux = chatClient
                    .streamMessages(conversationHistory)
                    .map(message -> {
                        String text = message.getText();
                        log.info("streamMessages got text: {}", text);
                        return text != null ? text : "";
                    })
                    .filter(text -> !text.isEmpty())
                    // 过滤掉工具调用日志
                    .filter(text -> !text.startsWith("执行工具:"))
                    .filter(text -> !text.matches("工具 .* 执行成功.*"))
                    .filter(text -> !text.matches("工具 .* 执行失败.*"))
                    .doOnNext(text -> {
                        log.info("After filters, emitting: {}", text);
                        modelResp.append(text);
                    })
                    .concatWithValues("[DONE]")
                    .doOnError(error -> {
                        log.error("Error in Flux stream", error);
                        conversationHistory.add(new AssistantMessage("Stream error: " + error));
                    })
                    .doOnComplete(() -> {
                        log.info("Flux stream completed");
                        conversationHistory.add(new AssistantMessage(modelResp.toString()));
                    });

            log.info("Flux created, returning");
            return flux;
        } catch (GraphRunnerException e) {
            log.error("GraphRunnerException in executeStream", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 清空对话历史（开始新任务）
     */
    public void reset() {
        conversationHistory.clear();
        log.info("对话历史已清空");
    }

    /**
     * 更新配置（API Key 和模型）
     */
    public void updateConfig(Map<String, String> config) {
        if (config == null) return;

        String apiKey = config.get("apiKey");
        String model = config.get("model");

        if (apiKey != null && !apiKey.isEmpty()) {
            this.currentApiKey = apiKey;
            log.info("API Key 已更新");
        }
        if (model != null && !model.isEmpty()) {
            this.currentModel = model;
            log.info("模型已更新为: {}", model);
        }

        // 如果有新的配置，重新创建 chatClient
        if ((apiKey != null && !apiKey.isEmpty()) || model != null && !model.isEmpty()) {
            rebuildChatClient();
        }
    }

    private void rebuildChatClient() {
        AnthropicChatModel chatModel = AnthropicChatModel.builder()
            .options(AnthropicChatOptions.builder()
                .model(currentModel)
                .baseUrl("https://api.minimaxi.com/anthropic")
                .apiKey(currentApiKey)
                .temperature(0.1)
                .maxTokens(8192)
                .build())
            .build();

        this.chatClient = ReactAgent.builder()
            .name("TaskExecutionAgent")
            .model(chatModel)
            // FormatOutputHook 已禁用，在 system prompt 中已明确要求 Markdown 输出
            // .hooks(new FormatOutputHook())
            .interceptors(new ToolObserver())
            .systemPrompt(buildSystemPrompt())
            .tools(Arrays.stream(toolCallbacks).toList())
            .interceptors(new ToolMonitorInterceptor())
            .build();

        log.info("ChatClient 已重建，模型: {}, API Key: {}", currentModel, currentApiKey != null ? "****" : "null");
    }

    /**
     * 获取对话历史（用于前端展示）
     */
    public List<Message> getHistory() {
        return List.copyOf(conversationHistory);
    }

    private String buildSystemPrompt() {
        return """
                你是一个专业的任务执行Agent。你的核心能力是**动手完成任务**，而不是仅仅给出建议。

                ## 你的能力
                你可以:
                1. **创建和编辑文件**: 编写代码、配置文件、文档等任何文本文件
                2. **执行终端命令**: 运行编译器、包管理器、构建工具、脚本等
                3. **操作目录**: 创建项目结构、查找文件
                4. **Git操作**: 初始化仓库、提交代码
                5. **使用MCP工具**: 通过MCP协议调用外部工具

                ## 工作方式
                你采用 ReAct (Reasoning + Acting) 模式:

                1. **分析任务**: 理解用户要什么，拆解成具体步骤
                2. **逐步执行**: 每一步调用合适的工具来完成
                3. **验证结果**: 执行后检查输出，确认是否正确
                4. **处理错误**: 如果出错，分析原因，换个方式重试
                5. **完成汇报**: 所有步骤完成后，总结做了什么

                ## 重要原则

                **先做再说**: 不要问用户"要不要我帮你创建"，直接创建。你是执行者，不是顾问。

                **完整交付**: 写代码要写完整的可运行代码，不要写省略号或伪代码。

                **验证执行**: 写完代码后，尝试编译或运行它来验证正确性。

                **错误恢复**: 命令失败时，分析错误信息，修复后重试（最多3次）。

                **最小权限**: 只操作完成任务所需的文件和目录。

                ## 工作空间
                你的工作目录: %s

                所有相对路径都基于此目录。创建项目时在此目录下创建子目录。

                ## 输出风格
                - 使用 **Markdown** 格式输出，包括标题、列表、代码块、表格等
                - 代码块必须指定语言类型，如 ```java, ```bash 等
                - 执行过程中简要说明你在做什么（一行即可）
                - 最终给出完整的执行总结
                - 如果有需要用户手动操作的步骤（如配置API密钥），明确列出

                """.formatted(workspaceConfig.getWorkspace());
    }
}