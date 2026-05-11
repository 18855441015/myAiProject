package org.alphalius.examproject.tools;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import org.alphalius.examproject.config.WorkspaceConfig;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * <p>
 *     文件操作工具
 * </p>
 *
 * @author liushuang
 * @since 2026/5/8 15:21
 **/
@Component
public class FileOperateTool {

    @Resource
    private WorkspaceConfig workspaceConfig;

    @Tool(description = """
            读取文件内容。返回文件的完整文本内容。
            用法: 读取代码文件、配置文件、日志文件等。
            参数filePath可以是绝对路径，也可以是相对于工作目录的相对路径。
            """)
    public String readFile(
        @ToolParam(description = "文件路径") String filePath
    ) {
        Path path = resolvePath(filePath);
        if (!workspaceConfig.isPathAllowed(path)) {
            return "错误: 路径不在允许范围内: " + filePath;
        }
        if (!Files.exists(path)) {
            return "错误: 文件不存在: " + filePath;
        }
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            long lines = content.lines().count();
            return "文件: %s (%d 行)\n\n%s".formatted(path, lines, content);
        } catch (IOException e) {
            return "读取失败: " + e.getMessage();
        }
    }

    @Tool(description = """
            写入文件。如果文件不存在会自动创建（包括父目录）。
            这是Agent最核心的能力——生成代码、写配置文件、创建项目文件。
            可以一次性写入完整文件内容。
            """)
    public String writeFile(
        @ToolParam(description = "文件路径") String filePath,
        @ToolParam(description = "文件内容（完整内容，不是增量）") String content
    ) {
        Path path = resolvePath(filePath);
        if (!workspaceConfig.isPathAllowed(path)) {
            return "错误: 路径不在允许范围内: " + filePath;
        }
        try {
            // 自动创建父目录
            Files.createDirectories(path.getParent());
            Files.writeString(path, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            long lines = content.lines().count();
            return "已写入: %s (%d 行, %d 字节)".formatted(path, lines, content.length());
        } catch (IOException e) {
            return "写入失败: " + e.getMessage();
        }
    }

    @Tool(description = """
            在已有文件的指定位置插入内容。
            用法: 往现有代码文件中追加函数、在配置文件中添加配置项等。
            """)
    public String insertIntoFile(
        @ToolParam(description = "文件路径") String filePath,
        @ToolParam(description = "要插入的内容") String content,
        @ToolParam(description = "插入位置: 在哪一行之后插入(行号从1开始，0表示插入到文件开头)") int afterLine
    ) {
        Path path = resolvePath(filePath);
        if (!workspaceConfig.isPathAllowed(path)) {
            return "错误: 路径不在允许范围内";
        }
        try {
            var lines = new java.util.ArrayList<>(Files.readAllLines(path, StandardCharsets.UTF_8));
            int insertAt = Math.min(afterLine, lines.size());
            lines.add(insertAt, content);
            Files.writeString(path, String.join("\n", lines), StandardCharsets.UTF_8);
            return "已在第 %d 行后插入内容到 %s".formatted(afterLine, filePath);
        } catch (IOException e) {
            return "插入失败: " + e.getMessage();
        }
    }

    @Tool(description = """
            替换文件中的指定文本。
            用法: 修改代码中的某个函数、更新配置项等。
            """)
    public String replaceInFile(
        @ToolParam(description = "文件路径") String filePath,
        @ToolParam(description = "要被替换的原始文本") String oldText,
        @ToolParam(description = "替换后的新文本") String newText
    ) {
        Path path = resolvePath(filePath);
        if (!workspaceConfig.isPathAllowed(path)) {
            return "错误: 路径不在允许范围内";
        }
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (!content.contains(oldText)) {
                return "未找到要替换的文本，请检查原文是否正确";
            }
            String newContent = content.replace(oldText, newText);
            Files.writeString(path, newContent, StandardCharsets.UTF_8);
            return "替换成功: %s".formatted(filePath);
        } catch (IOException e) {
            return "替换失败: " + e.getMessage();
        }
    }

    @Tool(description = """
            删除文件。
            用法: 清理临时文件、删除不需要的文件。
            """)
    public String deleteFile(
        @ToolParam(description = "文件路径") String filePath
    ) {
        Path path = resolvePath(filePath);
        if (!workspaceConfig.isPathAllowed(path)) {
            return "错误: 路径不在允许范围内";
        }
        try {
            boolean deleted = Files.deleteIfExists(path);
            return deleted ? "已删除: " + filePath : "文件不存在: " + filePath;
        } catch (IOException e) {
            return "删除失败: " + e.getMessage();
        }
    }

    /**
     * 将相对路径解析为绝对路径（基于workspace）
     */
    private Path resolvePath(String filePath) {
        Path path = Paths.get(filePath);
        if (path.isAbsolute()) {
            return path.toAbsolutePath().normalize();
        }
        return workspaceConfig.getWorkspacePath().resolve(filePath).toAbsolutePath().normalize();
    }
}
