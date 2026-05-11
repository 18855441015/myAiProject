package org.alphalius.examproject.tools;

import jakarta.annotation.Resource;
import org.alphalius.examproject.config.WorkspaceConfig;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Path;
import java.util.concurrent.*;

@Component
public class ShellExecutionTool {

    @Resource
    private WorkspaceConfig workspaceConfig;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ShellExecutionTool(WorkspaceConfig workspaceConfig) {
        this.workspaceConfig = workspaceConfig;
    }

    @Tool(description = """
            在终端中执行命令并返回输出。
            这是Agent的核心能力——可以运行任何命令行工具。

            常见用法:
            - 编译运行: "mvn clean install", "npm run build", "python main.py"
            - 包管理: "npm install express", "pip install flask", "mvn dependency:resolve"
            - Git操作: "git init", "git add .", "git commit -m 'init'"
            - 项目脚手架: "mvn archetype:generate ...", "npx create-react-app my-app"
            - 查看结果: "cat output.log", "ls -la"
            - 编译检查: "javac Main.java", "tsc --noEmit"

            返回: stdout + stderr 的完整输出。
            """)
    public String executeCommand(
            @ToolParam(description = "要执行的命令") String command,
            @ToolParam(description = "工作目录(可选，默认为Agent工作空间)") String workingDir
    ) {
        // 安全检查
        if (workspaceConfig.isCommandBlocked(command)) {
            return "错误: 该命令被安全策略禁止: " + command;
        }

        Path workDir = workingDir != null
                ? resolvePath(workingDir)
                : workspaceConfig.getWorkspacePath();

        if (!workspaceConfig.isPathAllowed(workDir)) {
            return "错误: 工作目录不在允许范围内";
        }

        try {
            ProcessBuilder pb = new ProcessBuilder();

            // 根据操作系统设置shell
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                pb.command("cmd.exe", "/c", command);
            } else {
                pb.command("bash", "-c", command);
            }

            pb.directory(workDir.toFile());
            pb.redirectErrorStream(false); // 分别捕获stdout和stderr

            // 设置环境变量
            pb.environment().put("TERM", "dumb");
            pb.environment().put("CI", "true");          // 避免交互式提示
            pb.environment().put("DEBIAN_FRONTEND", "noninteractive");

            Process process = pb.start();

            // 并行读取stdout和stderr
            Future<String> stdoutFuture = executor.submit(() -> readStream(process.getInputStream()));
            Future<String> stderrFuture = executor.submit(() -> readStream(process.getErrorStream()));

            // 等待执行完成（带超时）
            boolean finished = process.waitFor(
                    workspaceConfig.getCommandTimeout(), TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                return "错误: 命令执行超时(%d秒): %s"
                        .formatted(workspaceConfig.getCommandTimeout(), command);
            }

            String stdout = stdoutFuture.get(5, TimeUnit.SECONDS);
            String stderr = stderrFuture.get(5, TimeUnit.SECONDS);
            int exitCode = process.exitValue();

            StringBuilder result = new StringBuilder();
            result.append("命令: ").append(command).append("\n");
            result.append("工作目录: ").append(workDir).append("\n");
            result.append("退出码: ").append(exitCode).append("\n");

            if (!stdout.isBlank()) {
                result.append("\n--- stdout ---\n").append(truncate(stdout, 5000));
            }
            if (!stderr.isBlank()) {
                result.append("\n--- stderr ---\n").append(truncate(stderr, 3000));
            }
            if (exitCode != 0) {
                result.append("\n⚠ 命令执行失败 (exit code: ").append(exitCode).append(")");
            }

            return result.toString();

        } catch (Exception e) {
            return "执行异常: " + e.getMessage();
        }
    }

    @Tool(description = """
            启动一个长期运行的进程（如Web服务器），等待指定秒数后返回输出。
            用法: 启动Spring Boot应用、开发服务器等，查看启动是否成功。
            用完后记得用 killProcess 停掉。
            """)
    public String startBackgroundProcess(
            @ToolParam(description = "要执行的命令") String command,
            @ToolParam(description = "等待秒数(观察输出)") int waitSeconds,
            @ToolParam(description = "工作目录") String workingDir
    ) {
        if (workspaceConfig.isCommandBlocked(command)) {
            return "错误: 该命令被安全策略禁止";
        }

        Path workDir = workingDir != null
                ? resolvePath(workingDir)
                : workspaceConfig.getWorkspacePath();

        try {
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
            pb.directory(workDir.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // 读取输出
            Future<String> outputFuture = executor.submit(() -> readStream(process.getInputStream()));

            // 等待指定时间
            Thread.sleep(Math.min(waitSeconds, 30) * 1000L);

            // 检查进程是否还活着
            boolean alive = process.isAlive();
            String output = outputFuture.isDone() ? outputFuture.get() : "（进程仍在运行...）";

            return """
                    命令: %s
                    进程状态: %s
                    PID: %d

                    --- 输出 ---
                    %s
                    """.formatted(
                    command,
                    alive ? "运行中" : "已结束",
                    process.pid(),
                    truncate(output, 4000)
            );
        } catch (Exception e) {
            return "启动失败: " + e.getMessage();
        }
    }

    private String readStream(InputStream is) throws IOException {
        try (var reader = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
                if (sb.length() > 20000) { // 防止输出过大
                    sb.append("... (输出截断)\n");
                    break;
                }
            }
            return sb.toString();
        }
    }

    private String truncate(String text, int maxLen) {
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "\n... (截断，共 " + text.length() + " 字符)";
    }

    private Path resolvePath(String pathStr) {
        var path = java.nio.file.Paths.get(pathStr);
        if (path.isAbsolute()) return path.toAbsolutePath().normalize();
        return workspaceConfig.getWorkspacePath().resolve(pathStr).toAbsolutePath().normalize();
    }
}
