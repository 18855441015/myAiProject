package org.alphalius.examproject.core.strategy.contextshortstrategy;

import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.JumpTo;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.alphalius.examproject.config.ChatModelProvider;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 精简上下文策略 - 生成摘要
 *
 * 当对话历史超过指定 token 阈值时，将旧消息压缩为摘要，
 * 只保留最近一条消息和摘要信息，大幅减少 token 消耗。
 *
 * @author liushuang
 * @since 2026/5/12 14:22
 */
@Slf4j
@Component
@HookPositions({HookPosition.BEFORE_MODEL})
public class SummaryMessageHook extends MessagesModelHook {

    private static final int MAX_SUMMARY_LENGTH = 2000;

    @Value("${agent.max-summary-token}")
    private Integer maxToken;

    private final ChatModel summarizationModel;

    public SummaryMessageHook(ChatModelProvider chatModelProvider) {
        this.summarizationModel = chatModelProvider.getSummaryModel();
    }

    @Override
    public String getName() {
        return "SummaryMessageHook";
    }

    @Override
    public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
        if (previousMessages == null || previousMessages.isEmpty()) {
            return new AgentCommand(previousMessages);
        }

        // 统计 token 数量，未超阈值则直接返回
        int tokenCount = estimateTokens(previousMessages);
        if (tokenCount <= maxToken) {
            return new AgentCommand(previousMessages);
        }

        // 分离最近消息和历史消息
        Message recentMessage = previousMessages.getLast();
        List<Message> oldMessages = previousMessages.subList(0, previousMessages.size() - 1);

        // 生成摘要
        String summary = generateSummarization(oldMessages);
        log.info("================生成对话摘要：{}",summary);
        SystemMessage summaryMessage = new SystemMessage("【对话摘要】" + summary);

        // 用摘要替换旧消息，只保留摘要 + 最近消息
        return new AgentCommand(List.of(summaryMessage, recentMessage), UpdatePolicy.REPLACE);
    }

    /**
     * 生成对话摘要
     */
    private String generateSummarization(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "无历史对话";
        }

        // 格式化对话内容
        String conversation = formatConversation(messages);

        // 截断过长的内容
        if (conversation.length() > MAX_SUMMARY_LENGTH) {
            conversation = conversation.substring(0, MAX_SUMMARY_LENGTH) + "...";
        }

        // 调用模型生成摘要
        String prompt = buildSystemPrompt() + conversation;
        ChatResponse response = summarizationModel.call(new Prompt(new UserMessage(prompt)));

        return response.getResult().getOutput().getText();
    }

    private String buildSystemPrompt() {
        return """
            你是一个助手，请简洁地总结以下对话的核心内容，保留关键信息和结论，生成一个有效的摘要,
            请勿生成任何多余的文本，只返回摘要。
            """;
    }

    /**
     * 格式化对话内容
     */
    private String formatConversation(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        for (Message msg : messages) {
            String role;
            if (msg instanceof AssistantMessage) {
                role = "助手";
            } else if (msg instanceof UserMessage) {
                role = "用户";
            } else if (msg instanceof SystemMessage) {
                role = "系统";
            } else {
                role = msg.getMessageType().name();
            }
            sb.append(role).append("：").append(msg.getText()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 估算 token 数量
     */
    private int estimateTokens(List<Message> messages) {
        int total = 0;
        for (Message msg : messages) {
            total += msg.getText().length() / 4;
        }
        return total;
    }

    @Override
    public List<ModelInterceptor> getModelInterceptors() {
        return super.getModelInterceptors();
    }

    @Override
    public List<ToolInterceptor> getToolInterceptors() {
        return super.getToolInterceptors();
    }

    @Override
    public List<ToolCallback> getTools() {
        return super.getTools();
    }

    @Override
    public List<JumpTo> canJumpTo() {
        return super.canJumpTo();
    }

    @Override
    public Map<String, KeyStrategy> getKeyStrategys() {
        return super.getKeyStrategys();
    }

    @Override
    public HookPosition[] getHookPositions() {
        return super.getHookPositions();
    }
}