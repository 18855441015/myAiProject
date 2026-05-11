package org.alphalius.examproject.tools;

import jakarta.annotation.Resource;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class GitTool {

    @Resource
    private ShellExecutionTool shell;

    @Tool(description = "初始化Git仓库")
    public String gitInit(
            @ToolParam(description = "项目目录") String dir
    ) {
        return shell.executeCommand("git init", dir);
    }

    @Tool(description = "Git add 文件到暂存区")
    public String gitAdd(
            @ToolParam(description = "项目目录") String dir,
            @ToolParam(description = "文件路径，如 . 表示全部") String filePattern
    ) {
        return shell.executeCommand("git add " + filePattern, dir);
    }

    @Tool(description = "Git commit 提交")
    public String gitCommit(
            @ToolParam(description = "项目目录") String dir,
            @ToolParam(description = "提交信息") String message
    ) {
        return shell.executeCommand("git commit -m \"" + message + "\"", dir);
    }

    @Tool(description = "查看Git状态")
    public String gitStatus(
            @ToolParam(description = "项目目录") String dir
    ) {
        return shell.executeCommand("git status", dir);
    }

    @Tool(description = "查看Git日志")
    public String gitLog(
            @ToolParam(description = "项目目录") String dir
    ) {
        return shell.executeCommand("git log --oneline -10", dir);
    }
}
