package org.alphalius.examproject.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "agent")
public class WorkspaceConfig {

    /** Agent的工作根目录 */
    private String workspace = System.getProperty("user.home") + "/agent-workspace";

    /** 最大循环次数 */
    private int maxIterations = 30;

    /** 命令执行超时(秒) */
    private int commandTimeout = 60;

    /** 允许操作的路径白名单 */
    private List<String> allowedPaths = List.of();

    /** 被禁止的命令模式 */
    private List<String> blockedCommands = List.of();

    public Path getWorkspacePath() {
        return Paths.get(workspace).toAbsolutePath().normalize();
    }

    /**
     * 安全检查：路径是否在白名单内
     */
    public boolean isPathAllowed(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        // 始终允许workspace内部操作
        if (normalized.startsWith(getWorkspacePath())) {
            return true;
        }
        return allowedPaths.stream()
                .anyMatch(p -> normalized.startsWith(Paths.get(p).toAbsolutePath().normalize()));
    }

    /**
     * 安全检查：命令是否被禁止
     */
    public boolean isCommandBlocked(String command) {
        String lower = command.toLowerCase().trim();
        return blockedCommands.stream()
                .anyMatch(lower::contains);
    }
}
