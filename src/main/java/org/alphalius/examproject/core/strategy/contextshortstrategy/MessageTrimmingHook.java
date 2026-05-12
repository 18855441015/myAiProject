package org.alphalius.examproject.core.strategy.contextshortstrategy;

import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.RunnableConfig;
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
import java.util.stream.Collectors;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.ToolCallback;

/**
 * <p>
 *     精简上下文策略-截取消息
 * </p>
 *
 * @author liushuang
 * @since 2026/5/12 13:44
 **/
@HookPositions(value = {HookPosition.BEFORE_MODEL})
public class MessageTrimmingHook extends MessagesModelHook {

    private static final int MAX_MESSAGES = 5;

    @Override
    public String getName() {
        return "MessageTrimmingHook";
    }

    public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
        if (previousMessages.size() <= MAX_MESSAGES) {
            return new AgentCommand(previousMessages);
        }
        // 截取消息
        List<Message> trimmedMessages = previousMessages.stream()
            .skip(previousMessages.size() - MAX_MESSAGES).collect(Collectors.toList());
        return new AgentCommand(trimmedMessages, UpdatePolicy.REPLACE);
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
