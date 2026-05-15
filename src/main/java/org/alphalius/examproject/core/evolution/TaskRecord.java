package org.alphalius.examproject.core.evolution;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 任务执行记录 - 用于追踪和分析任务执行历史
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskRecord {

    private String taskId;
    private String taskDescription;
    private boolean success;
    private String failureReason;
    private String failureDetail;
    private int stepsCount;
    private long executionTimeMs;
    private LocalDateTime executedAt;

    // 执行过程中的关键信息
    private String finalOutput;
    private String improvementHint;  // 生成的改进提示
}