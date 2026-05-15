package org.alphalius.examproject.core.evolution;

import lombok.Builder;
import lombok.Data;

/**
 * Prompt改进建议
 */
@Data
@Builder
public class PromptImprovement {

    private FailureReason reason;
    private String originalPrompt;
    private String improvedPrompt;
    private String improvementRationale;
    private int confidence;  // 0-100 置信度
}