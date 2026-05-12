package org.alphalius.examproject.hooks;

import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.JumpTo;
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.ai.tool.ToolCallback;

/**
 * <p>
 *     格式化输出结果,一般来说，hook是动态干预模型的，此处我是加的静态数据，要求模型格式化输出，并不推荐这么做
 * </p>
 *
 * @author liushuang
 * @since 2026/5/11 16:33
 **/
@HookPositions({HookPosition.BEFORE_MODEL})
public class FormatOutputHook extends ModelHook {

    @Override
    public String getName() {
        return "FormatOutputHook";
    }

    public CompletableFuture<Map<String, Object>> beforeModel(OverAllState state, RunnableConfig config) {
        return CompletableFuture.completedFuture(Map.of("system_hint","强制使用 **Markdown** 格式输出，包括标题、列表、代码块、表格等、代码块要指定语言类型，如 ```java, ```bash 等"));
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
