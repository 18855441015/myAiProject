package org.alphalius.examproject.hooks;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;

/**
 * <p>
 *     拦截工具调用，用于记录工具调用信息
 * </p>
 *
 * @author liushuang
 * @since 2026/5/8 15:37
 **/
public class ToolObserver extends ToolInterceptor {

    @Override
    public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
        String toolName = request.getToolName();
        System.out.println("[ToolObserver] 拦截工具调用=======: " + toolName);
        return handler.call(request);
    }

    @Override
    public String getName() {
        return "toolObserver";
    }
}
