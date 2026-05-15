package org.alphalius.examproject.hooks;

import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.JumpTo;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesAgentHook;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.alphalius.examproject.core.evolution.EvolutionFileManager;
import org.alphalius.examproject.core.evolution.PromptEvolutionService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * 自进化Hook
 *
 * - beforeAgent: 读取进化提示文件
 * - afterAgent: 触发进化分析，生成新的进化摘要并写入文件
 *
 * @author liushuang
 * @since 2026/5/14 15:32
 **/
@Slf4j
@Component
@HookPositions({HookPosition.BEFORE_AGENT, HookPosition.AFTER_AGENT})
public class SelfEvolveHook extends MessagesAgentHook {

    @Resource
    private PromptEvolutionService promptEvolutionService;

    @Resource
    private EvolutionFileManager evolutionFileManager;

    @Override
    public AgentCommand beforeAgent(List<Message> previousMessages, RunnableConfig config) {
        log.info("BeforeAgent: 读取进化提示文件");
        String hints = evolutionFileManager.readEvolutionHints();
        if (StringUtils.isNotEmpty(hints)) {
            log.info("已读取进化提示文件,文件内容为：{}", hints);
            SystemMessage systemMessage = new SystemMessage(hints);
            previousMessages.add(systemMessage);
        }
        return super.beforeAgent(previousMessages, config);
    }

    @Override
    public AgentCommand afterAgent(List<Message> previousMessages, RunnableConfig config) {
        log.info("AfterAgent: 触发进化分析");
        promptEvolutionService.generateEvolutionSummary();
        return super.afterAgent(previousMessages, config);
    }

    @Override
    public String getName() {
        return "SelfEvolveHook";
    }

    @Override
    public ReactAgent getAgent() {
        return null;
    }

    @Override
    public void setAgent(ReactAgent agent) {

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

    @Override
    public int getOrder() {
        return 0;
    }
}