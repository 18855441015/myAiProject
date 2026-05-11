package org.alphalius.examproject.tools;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import org.alphalius.examproject.config.WorkspaceConfig;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class DirectoryTool {

    @Resource
    private WorkspaceConfig workspaceConfig;

    public DirectoryTool(WorkspaceConfig workspaceConfig) {
        this.workspaceConfig = workspaceConfig;
    }

    @Tool(description = """
            列出目录下的文件和子目录。
            返回树形结构，帮助Agent了解项目结构。
            depth参数控制递归深度（1=只看当前层，2=看两层，3=看三层）。
            """)
    public String listDirectory(
            @ToolParam(description = "目录路径") String dirPath,
            @ToolParam(description = "递归深度(1-5)，默认2") int depth
    ) {
        Path path = resolvePath(dirPath);
        if (!workspaceConfig.isPathAllowed(path)) {
            return "错误: 路径不在允许范围内";
        }
        if (!Files.isDirectory(path)) {
            return "错误: 不是目录: " + dirPath;
        }
        try {
            return buildTree(path, path, Math.min(depth > 0 ? depth : 2, 5), 0);
        } catch (IOException e) {
            return "列目录失败: " + e.getMessage();
        }
    }

    @Tool(description = """
            创建目录（包括所有不存在的父目录）。
            用法: 创建项目结构、新建包目录等。
            """)
    public String createDirectory(
            @ToolParam(description = "目录路径") String dirPath
    ) {
        Path path = resolvePath(dirPath);
        if (!workspaceConfig.isPathAllowed(path)) {
            return "错误: 路径不在允许范围内";
        }
        try {
            Files.createDirectories(path);
            return "已创建目录: " + path;
        } catch (IOException e) {
            return "创建失败: " + e.getMessage();
        }
    }

    @Tool(description = """
            搜索文件。根据文件名模式在目录树中查找文件。
            支持通配符: *.java, **/*.xml, pom.xml 等。
            """)
    public String findFiles(
            @ToolParam(description = "搜索根目录") String dirPath,
            @ToolParam(description = "文件名模式(支持通配符)") String pattern
    ) {
        Path path = resolvePath(dirPath);
        if (!workspaceConfig.isPathAllowed(path)) {
            return "错误: 路径不在允许范围内";
        }
        try {
            var matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            var results = Files.walk(path, 10)
                    .filter(Files::isRegularFile)
                    .filter(p -> matcher.matches(p.getFileName()))
                    .map(p -> path.relativize(p).toString())
                    .collect(Collectors.toList());
            if (results.isEmpty()) {
                return "未找到匹配的文件: " + pattern;
            }
            return "找到 %d 个文件:\n%s".formatted(results.size(), String.join("\n", results));
        } catch (IOException e) {
            return "搜索失败: " + e.getMessage();
        }
    }

    private String buildTree(Path root, Path current, int maxDepth, int depth) throws IOException {
        StringBuilder sb = new StringBuilder();
        String indent = "  ".repeat(depth);
        String prefix = depth == 0 ? "" : "├── ";

        if (depth == 0) {
            sb.append(current.getFileName()).append("/\n");
        }

        try (var stream = Files.list(current)) {
            var entries = stream
                    .sorted((a, b) -> {
                        boolean aDir = Files.isDirectory(a);
                        boolean bDir = Files.isDirectory(b);
                        if (aDir != bDir) return aDir ? -1 : 1;
                        return a.getFileName().toString().compareTo(b.getFileName().toString());
                    })
                    .limit(50) // 防止目录太大
                    .toList();

            for (Path entry : entries) {
                String name = entry.getFileName().toString();
                // 跳过常见的无关目录
                if (name.startsWith(".") || name.equals("node_modules") || name.equals("target")) {
                    continue;
                }
                if (Files.isDirectory(entry)) {
                    sb.append(indent).append(prefix).append(name).append("/\n");
                    if (depth + 1 < maxDepth) {
                        sb.append(buildTree(root, entry, maxDepth, depth + 1));
                    }
                } else {
                    long size = Files.size(entry);
                    sb.append(indent).append(prefix).append(name)
                            .append(" (").append(formatSize(size)).append(")\n");
                }
            }
        }
        return sb.toString();
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + "KB";
        return String.format("%.1fMB", bytes / (1024.0 * 1024));
    }

    private Path resolvePath(String dirPath) {
        Path path = Paths.get(dirPath);
        if (path.isAbsolute()) return path.toAbsolutePath().normalize();
        return workspaceConfig.getWorkspacePath().resolve(dirPath).toAbsolutePath().normalize();
    }
}
