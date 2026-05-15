package org.alphalius.examproject.core.evolution;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * 自进化文件管理器
 *
 * 管理进化提示文件的读写：
 * - evolution_hints.md: 累积的进化提示（每次beforeAgent读取）
 */
@Slf4j
@Component
public class EvolutionFileManager {

    private static final String EVOLUTION_FILE = "evolution_hints.md";
    private final Path evolutionFilePath;

    public EvolutionFileManager() {
        // 放在项目根目录下，与.claude同级
        String userDir = System.getProperty("user.dir");
        Path projectRoot = Paths.get(userDir).getParent(); // target的上级目录
        this.evolutionFilePath = projectRoot.resolve(EVOLUTION_FILE);
        ensureFileExists();
    }

    public EvolutionFileManager(String baseDir) {
        this.evolutionFilePath = Paths.get(baseDir, EVOLUTION_FILE);
        ensureFileExists();
    }

    private void ensureFileExists() {
        try {
            if (!Files.exists(evolutionFilePath.getParent())) {
                Files.createDirectories(evolutionFilePath.getParent());
            }
            if (!Files.exists(evolutionFilePath)) {
                Files.createFile(evolutionFilePath);
                Files.write(evolutionFilePath, "# 自进化提示文件\n\n（暂无历史经验）\n".getBytes());
            }
        } catch (IOException e) {
            log.error("创建进化文件失败", e);
        }
    }

    /**
     * 读取进化提示文件内容
     */
    public String readEvolutionHints() {
        try {
            if (!Files.exists(evolutionFilePath)) {
                return "";
            }
            String content = Files.readString(evolutionFilePath);
            // 如果是空的或只有默认内容，返回空
            if (content.trim().equals("（暂无历史经验）") || content.trim().isEmpty()) {
                return "";
            }
            return content;
        } catch (IOException e) {
            log.error("读取进化文件失败", e);
            return "";
        }
    }

    /**
     * 追加进化提示到文件
     */
    public void appendEvolutionHint(String hint) {
        try {
            String entry = String.format("\n- %s\n", hint);
            Files.writeString(evolutionFilePath, entry, StandardOpenOption.APPEND);
            log.info("已追加进化提示到文件");
        } catch (IOException e) {
            log.error("写入进化文件失败", e);
        }
    }

    /**
     * 写入完整的进化提示文件
     */
    public void writeEvolutionHints(String content) {
        try {
            Files.writeString(evolutionFilePath, content);
            log.info("已更新进化提示文件");
        } catch (IOException e) {
            log.error("写入进化文件失败", e);
        }
    }

    /**
     * 获取文件路径（用于调试）
     */
    public String getFilePath() {
        return evolutionFilePath.toString();
    }
}