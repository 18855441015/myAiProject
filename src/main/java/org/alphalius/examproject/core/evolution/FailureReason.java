package org.alphalius.examproject.core.evolution;

/**
 * 任务失败原因枚举
 */
public enum FailureReason {
    PROMPT_UNCLEAR("prompt不明确", "用户需求描述不够清晰或具体"),
    TOOL_MISUSE("工具使用错误", "错误地使用了工具或工具参数"),
    LOGIC_ERROR("执行逻辑错误", "执行步骤顺序或逻辑有问题"),
    VERIFICATION_MISS("缺少验证步骤", "没有验证上一步的结果就继续执行"),
    CONTEXT_OVERFLOW("上下文超限", "对话历史过长导致信息丢失"),
    TIMEOUT("执行超时", "任务步数超过限制或执行时间过长"),
    UNKNOWN("未知原因", "无法确定的失败原因");

    private final String label;
    private final String description;

    FailureReason(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }
}