package org.alphalius.examproject.core.supervisoragents;

import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.agent.flow.agent.FlowAgent;
import com.alibaba.cloud.ai.graph.agent.flow.builder.FlowAgentBuilder;
import com.alibaba.cloud.ai.graph.agent.flow.builder.FlowGraphBuilder;
import com.alibaba.cloud.ai.graph.agent.flow.builder.FlowGraphBuilder.FlowGraphConfig;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import org.springframework.ai.chat.model.ChatModel;

/**
 * <p>
 * 监督者agent,用于分析用户输入问题、协调各agent完成用户工作
 * 参考csdn
 * </p>
 *
 * @author liushuang
 * @since 2026/4/29 14:49
 **/
public class CoreSupervisorAgent extends FlowAgent {

    private final ChatModel chatModel;

    private final int maxIterations;

    private final String systemPrompt;



    public CoreSupervisorAgent(SupervisorAgentBuilder builder) {
         super(builder.name, builder.description,builder.compileConfig,builder.subAgents,builder.stateSerializer,builder.executor,builder.hooks);
         this.chatModel = builder.chatModel;
         this.maxIterations = builder.maxIterations;
         this.systemPrompt = builder.systemPrompt;
    }

    @Override
    protected StateGraph buildSpecificGraph(FlowGraphConfig config) throws GraphStateException {
        // 将自定义参数注入 config
        config.setChatModel(this.chatModel);
        config.customProperty("maxIterations", this.maxIterations);
        config.customProperty("systemPrompt", this.systemPrompt);

        // 委托给注册的 SUPERVISOR 策略
        return FlowGraphBuilder.buildGraph("SUPERVISOR", config);
    }


    // ========== Builder ==========

    public static class SupervisorAgentBuilder
        extends FlowAgentBuilder<CoreSupervisorAgent, SupervisorAgentBuilder> {

        private ChatModel chatModel;
        private int maxIterations = 10;
        private String systemPrompt;

        /** 设置用于 Supervisor 决策的 ChatModel（必填） */
        public SupervisorAgentBuilder model(ChatModel chatModel) {
            this.chatModel = chatModel;
            return self();
        }

        /** 设置最大迭代次数（默认 10） */
        public SupervisorAgentBuilder maxIterations(int maxIterations) {
            this.maxIterations = maxIterations;
            return self();
        }

        /** 设置 Supervisor 的系统提示词 */
        public SupervisorAgentBuilder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return self();
        }

        @Override
        protected SupervisorAgentBuilder self() {
            return this;
        }

        @Override
        protected void validate() {
            super.validate();  // 校验 name 非空、subAgents 非空
            if (chatModel == null) {
                throw new IllegalArgumentException(
                    "ChatModel must be provided for SupervisorAgent");
            }
            if (subAgents != null && subAgents.size() < 2) {
                throw new IllegalArgumentException(
                    "SupervisorAgent requires at least 2 sub-agents "
                        + "(1 supervisor + at least 1 worker)");
            }
        }

        @Override
        public CoreSupervisorAgent doBuild() {
            validate();
            return new CoreSupervisorAgent(this);
        }
    }
}
