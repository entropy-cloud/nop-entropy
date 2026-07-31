package io.nop.ai.code_analyzer.stats;

import java.util.HashSet;
import java.util.Set;

/**
 * 统计时忽略的目录与文件扩展名集合（从 FileLanguageStats 中提取）
 */
public class IgnoredItems {

    private static final Set<String> IGNORED_DIRECTORIES = new HashSet<>();
    private static final Set<String> IGNORED_EXTENSIONS = new HashSet<>();

    static {
        initializeIgnoredItems();
    }

    private static void initializeIgnoredItems() {
        // 忽略的目录
        IGNORED_DIRECTORIES.add(".git");
        IGNORED_DIRECTORIES.add(".svn");
        IGNORED_DIRECTORIES.add(".hg");
        IGNORED_DIRECTORIES.add("node_modules");
        IGNORED_DIRECTORIES.add("target");
        IGNORED_DIRECTORIES.add("build");
        IGNORED_DIRECTORIES.add("dist");
        IGNORED_DIRECTORIES.add("out");
        IGNORED_DIRECTORIES.add("bin");
        IGNORED_DIRECTORIES.add(".idea");
        IGNORED_DIRECTORIES.add(".vscode");
        IGNORED_DIRECTORIES.add("__pycache__");
        IGNORED_DIRECTORIES.add(".gradle");
        IGNORED_DIRECTORIES.add("vendor");
        IGNORED_DIRECTORIES.add("coverage");
        IGNORED_DIRECTORIES.add(".next");
        IGNORED_DIRECTORIES.add(".nuxt");

        // 忽略的文件扩展名
        IGNORED_EXTENSIONS.add("class");
        IGNORED_EXTENSIONS.add("jar");
        IGNORED_EXTENSIONS.add("war");
        IGNORED_EXTENSIONS.add("ear");
        IGNORED_EXTENSIONS.add("exe");
        IGNORED_EXTENSIONS.add("dll");
        IGNORED_EXTENSIONS.add("so");
        IGNORED_EXTENSIONS.add("dylib");
        IGNORED_EXTENSIONS.add("o");
        IGNORED_EXTENSIONS.add("obj");
        IGNORED_EXTENSIONS.add("pyc");
        IGNORED_EXTENSIONS.add("pyo");
        IGNORED_EXTENSIONS.add("log");
        IGNORED_EXTENSIONS.add("tmp");
        IGNORED_EXTENSIONS.add("temp");
        IGNORED_EXTENSIONS.add("bak");
        IGNORED_EXTENSIONS.add("swp");
        IGNORED_EXTENSIONS.add("DS_Store");
        IGNORED_EXTENSIONS.add("zip");
        IGNORED_EXTENSIONS.add("tar");
        IGNORED_EXTENSIONS.add("gz");
        IGNORED_EXTENSIONS.add("rar");
        IGNORED_EXTENSIONS.add("7z");
    }

    public static boolean isIgnoredDirectory(String dirName) {
        return IGNORED_DIRECTORIES.contains(dirName);
    }

    public static boolean isIgnoredExtension(String extension) {
        return IGNORED_EXTENSIONS.contains(extension);
    }
}
