package org.alphalius.examproject.core.evolution;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import lombok.extern.slf4j.Slf4j;
import org.alphalius.examproject.config.ChatModelProvider;
import org.alphalius.examproject.tools.FileOperateTool;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Prompt自进化服务
 *
 * 核心功能：
 * 1. 记录任务执行结果（由对话层调用）
 * 2. 分析失败原因，生成改进prompt（由Hook调用）
 * 3. 写入进化文件
 */
@Slf4j
@Service
public class PromptEvolutionService {

    private final ReactAgent promptEvolutionAgent;
    private final EvolutionFileManager fileManager;

    // 记录最近的任务执行历史（内存存储）
    private final List<TaskRecord> taskHistory = new ArrayList<>();

    // 成功案例记录
    private final List<String> successfulPatterns = new ArrayList<>();

    public PromptEvolutionService(EvolutionFileManager fileManager, ChatModelProvider chatModelProvider,FileOperateTool fileTool) {
        this.promptEvolutionAgent = ReactAgent.builder()
            .name("PromptEvolutionAgent")
            .model(chatModelProvider.getEvolutionModel())
            .tools(ToolCallbacks.from(fileTool))
            .build();
        this.fileManager = fileManager;
    }

    /**
     * 记录任务执行结果（仅记录，不触发LLM分析）
     * 由对话层在每次任务结束后调用
     */
    public void recordTask(String taskDescription, boolean success, String finalOutput) {
        TaskRecord record = TaskRecord.builder()
                .taskId(UUID.randomUUID().toString())
                .taskDescription(taskDescription)
                .success(success)
                .finalOutput(finalOutput)
                .executedAt(LocalDateTime.now())
                .build();

        taskHistory.add(record);
        log.info("任务记录已保存: success={}, taskId={}", success, record.getTaskId());
    }

    /**
     * 生成进化摘要（触发LLM分析）
     * 由SelfEvolveHook在AFTER_AGENT时调用
     */
    public void generateEvolutionSummary() {
        if (taskHistory.isEmpty()) {
            return;
        }

        // 获取最近一条记录进行分析
        TaskRecord lastRecord = taskHistory.get(taskHistory.size() - 1);
        if (lastRecord.isSuccess()) {
            // 提取成功模式
            extractSuccessfulPattern(lastRecord);
        } else {
            // 分析失败原因并生成改进提示
            generateImprovementFromFailure(lastRecord);
        }
    }

    /**
     * 获取进化提示文件内容（用于beforeAgent）
     */
    public String getEvolutionHints() {
        return fileManager.readEvolutionHints();
    }

    /**
     * 提取成功模式
     */
    private void extractSuccessfulPattern(TaskRecord record) {
        String prompt = String.format("""
            从以下成功执行的任务中，提取1-2条有效的行为模式或技巧：

            任务：%s

            请用一句话描述提取的成功模式，只返回模式描述。
            """, record.getTaskDescription());

        try {
            AssistantMessage response = promptEvolutionAgent.call(new UserMessage(prompt));
            String pattern = response.getText();
            if (StringUtils.isNotEmpty(pattern)) {
                successfulPatterns.add(pattern);
                // 追加到文件
                fileManager.appendEvolutionHint("[成功模式] " + pattern);
                log.info("成功模式已提取: {}", pattern);
            }
        } catch (Exception e) {
            log.error("提取成功模式时出错", e);
        }
    }

    /**
     * 分析失败并生成改进提示
     */
    private void generateImprovementFromFailure(TaskRecord record) {
        String prompt = String.format("""
            请分析以下任务执行失败的原因，并生成一条改进提示。

            任务：%s

            最终输出/错误：
            %s

            对话历史：
            %s

            请从以下失败原因中选择最符合的（只返回一个）：PROMPT_UNCLEAR, TOOL_MISUSE, LOGIC_ERROR, VERIFICATION_MISS, CONTEXT_OVERFLOW, TIMEOUT, UNKNOWN

            然后生成一条改进提示（不超过100字），格式如下：
            原因: XXX
            改进: XXX
            """, record.getTaskDescription(),
            record.getFinalOutput() != null ? record.getFinalOutput() : "无",
            record.getTaskDescription());

        try {
            AssistantMessage response = promptEvolutionAgent.call(new UserMessage(prompt));
            String analysis = response.getText();

            // 提取原因和改进提示
            String reason = extractReason(analysis);
            String improvement = extractImprovement(analysis);

            record.setFailureReason(reason);
            record.setImprovementHint(improvement);

            // 追加到文件
            String hintEntry = String.format("[失败-%s] %s", reason, improvement);
            fileManager.appendEvolutionHint(hintEntry);

            log.info("失败分析已生成: reason={}, hint={}", reason, improvement);
        } catch (Exception e) {
            log.error("生成失败分析时出错", e);
        }
    }

    private String extractReason(String text) {
        for (FailureReason reason : FailureReason.values()) {
            if (text.contains(reason.name())) {
                return reason.name();
            }
        }
        return "UNKNOWN";
    }

    private String extractImprovement(String text) {
        // 简单提取"改进:"后面的内容
        int idx = text.indexOf("改进:");
        if (idx >= 0) {
            return text.substring(idx + 3).trim();
        }
        idx = text.indexOf("改进：");
        if (idx >= 0) {
            return text.substring(idx + 3).trim();
        }
        return text;
    }

    /**
     * 获取任务历史统计
     */
    public String getStatistics() {
        long total = taskHistory.size();
        long successCount = taskHistory.stream().filter(TaskRecord::isSuccess).count();
        long failCount = total - successCount;
        return String.format("总任务数: %d, 成功: %d, 失败: %d, 成功率: %.1f%%",
                total, successCount, failCount, total > 0 ? (successCount * 100.0 / total) : 0);
    }
}