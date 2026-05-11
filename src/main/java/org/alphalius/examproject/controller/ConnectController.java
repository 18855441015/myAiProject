package org.alphalius.examproject.controller;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.alphalius.examproject.core.supervisoragents.TaskExecutionAgent;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * <p>
 * </p>
 *
 * @author liushuang
 * @since 2026/4/29 14:53
 **/
@Slf4j
@RestController
@RequestMapping("/api/agent")
public class ConnectController {

    @Resource
    private TaskExecutionAgent reactAgent;

    /**
     * 同步执行 — Agent 跑完整个 ReAct 循环后返回最终结果
     */
    @PostMapping("/execute")
    public Map<String, String> execute(@RequestBody Map<String, String> req) {
        String task = req.get("task");

        // ReactAgent.run() 内部自动执行:
        // LLM → tool_call → 执行工具 → 结果回传 → LLM → ... → 最终回答
        String result = reactAgent.execute(task);

        return Map.of("task", task, "result", result);
    }

    /**
     * 流式执行 — 实时看到 Agent 每一步思考和操作
     * 使用 SSE 格式: data: xxx\n\n
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody Map<String, Object> req) {
        String task = (String) req.get("message");
        log.info("Stream request received for task: {}", task);

        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        reactAgent.executeStream(task)
                .subscribe(
                        data -> {
                            log.info("Sending SSE data: {}", data);
                            try {
                                emitter.send("data: " + data + "\n\n");
                            } catch (IOException e) {
                                log.error("Failed to send SSE data", e);
                                emitter.completeWithError(e);
                            }
                        },
                        error -> {
                            log.error("Stream error", error);
                            emitter.completeWithError(error);
                        },
                        () -> {
                            log.info("Stream completed");
                            emitter.complete();
                        }
                );

        return emitter;
    }
}
