package io.nop.metadata.service;

import io.nop.api.core.ApiErrors;
import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2026-07-19-1250-3 Phase 4 Proof：验证模块主代码无 {@code System.currentTimeMillis()} /
 * {@code new Timestamp(System.currentTimeMillis())} 残留（plan 维度20-01）。
 *
 * <p>grep 机械验证：所有 main 源文件中应无 {@code System.currentTimeMillis()} 或
 * {@code new Timestamp(System.currentTimeMillis())} 残留。已统一替换为 {@code CoreMetrics.currentTimeMillis()} /
 * {@code CoreMetrics.currentTimestamp()}（mockable Clock，便于测试时间相关逻辑）。
 *
 * <p>plan 2026-08-04-1543-3 R2.5：扫描范围扩展至 {@code nop-metadata-dao/src/main}（OrmModelImporter
 * 的 2 处残余于本 plan 修复，扩展范围防止 dao 模块再次引入同类回归）。
 */
public class TestCoreMetricsUsage {

    private static final String MAIN_SRC_DIR =
            "src/main/java";

    private static final String[] SCAN_MODULES = {
            "nop-metadata/nop-metadata-service",
            "nop-metadata/nop-metadata-dao"
    };

    @Test
    public void testNoSystemCurrentTimeMillisInMainCode() throws IOException {
        assertTrue(Files.exists(mainDir()), "main src dir must exist: " + mainDir());
        for (String module : SCAN_MODULES) {
            Path moduleMain = resolveModuleMain(module);
            if (!Files.exists(moduleMain)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(moduleMain)) {
                stream.filter(p -> p.toString().endsWith(".java"))
                        .forEach(p -> {
                            try {
                                String content = new String(Files.readAllBytes(p));
                                // 允许在注释或 javadoc 中出现（grep 也会匹配，但人工评估为信息性）
                                // 这里只断言代码内未直接调用 System.currentTimeMillis
                                String noComments = stripJavaCommentsForScan(content);
                                assertFalse(
                                        noComments.contains("System.currentTimeMillis()"),
                                        "File " + p + " must not call System.currentTimeMillis() directly; use CoreMetrics.currentTimeMillis() instead");
                                assertFalse(
                                        noComments.contains("new Timestamp(System.currentTimeMillis())"),
                                        "File " + p + " must not use new Timestamp(System.currentTimeMillis()); use CoreMetrics.currentTimestamp() instead");
                            } catch (IOException e) {
                                throw new NopException(ApiErrors.ERR_WRAP_EXCEPTION, e);
                            }
                        });
            }
        }
    }

    /**
     * P2-35 项 3（plan 2026-08-16-0549-2）：样例单测钉死注释剥离行为——原正则
     * （行注释正则 + 无 DOTALL 的块注释正则）存在两个洞：
     * (a) 多行块注释剥离不全（点号不匹配换行 → 注释体内的 target 调用残留 → 合法代码误报）；
     * (b) 字符串字面量内的双斜线误剥（把 URL 字符串当行注释起点 → 同行后续真实调用被剥掉 → 漏报）。
     * 本测试修复前红 / 修复后绿（证明见当日 daily log）。
     */
    @Test
    public void commentStrippingHandlesMultilineBlockAndStringLiteralSlash() {
        // (a) 多行块注释内的 target 调用（javadoc 记载历史迁移）→ 必须完整剥离，不误报
        String multiLineComment = "class A {\n"
                + "/* plan note:\n"
                + "   legacy code used System.currentTimeMillis() before CoreMetrics\n"
                + "*/\n"
                + "void m() {}\n}\n";
        assertFalse(stripJavaCommentsForScan(multiLineComment).contains("System.currentTimeMillis()"),
                "multi-line block comment content must be fully stripped (was partial with non-DOTALL regex)");

        // (b) 字符串字面量内的 // 不得当作行注释起点 → 同行真实调用保持可检出（假阴性洞）
        String stringLiteralSlash = "class B {\n"
                + "String url = \"http://example.com\"; long t = System.currentTimeMillis();\n"
                + "}\n";
        assertTrue(stripJavaCommentsForScan(stringLiteralSlash).contains("System.currentTimeMillis()"),
                "'//' inside string literal must not strip the rest of line (false negative hole)");

        // (c) 行注释 / 单行块注释仍正常剥离（既有行为不回归）
        assertFalse(stripJavaCommentsForScan("// legacy System.currentTimeMillis()\nvoid m() {}\n")
                .contains("System.currentTimeMillis()"), "line comment must be stripped");
        assertFalse(stripJavaCommentsForScan("/* single-line System.currentTimeMillis() */ void m() {}\n")
                .contains("System.currentTimeMillis()"), "single-line block comment must be stripped");
    }

    /**
     * 剥离 Java 注释（P2-35 项 3 修复形态）：单遍状态机——行注释（到行尾）、块注释（跨行完整剥离，
     * 剥离后按需回填换行保持行号近似）、字符串/字符字面量原文保留（字面量内的双斜线与斜线星号
     * 不作为注释起点）。模块 main 代码无文本块（live 核对 2026-08-16），文本块不在扫描口径内。
     */
    static String stripJavaCommentsForScan(String source) {
        StringBuilder sb = new StringBuilder(source.length());
        int i = 0;
        int n = source.length();
        while (i < n) {
            char c = source.charAt(i);
            if (c == '/' && i + 1 < n && source.charAt(i + 1) == '/') {
                while (i < n && source.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && i + 1 < n && source.charAt(i + 1) == '*') {
                i += 2;
                boolean sawNewline = false;
                while (i + 1 < n && !(source.charAt(i) == '*' && source.charAt(i + 1) == '/')) {
                    if (source.charAt(i) == '\n') {
                        sawNewline = true;
                    }
                    i++;
                }
                i = Math.min(i + 2, n);
                if (sawNewline) {
                    sb.append('\n');
                }
            } else if (c == '"' || c == '\'') {
                char quote = c;
                sb.append(c);
                i++;
                while (i < n && source.charAt(i) != quote) {
                    if (source.charAt(i) == '\\' && i + 1 < n) {
                        sb.append(source.charAt(i));
                        i++;
                    }
                    sb.append(source.charAt(i));
                    i++;
                }
                if (i < n) {
                    sb.append(source.charAt(i));
                    i++;
                }
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    private Path resolveModuleMain(String module) {
        Path baseDir = Paths.get(System.getProperty("user.dir", "."));
        Path mainSrc = baseDir.resolve(MAIN_SRC_DIR);
        if (Files.exists(mainSrc)) {
            // 在子模块根目录运行：仅扫描当前模块
            return mainSrc;
        }
        return baseDir.resolve(module + "/" + MAIN_SRC_DIR);
    }

    private Path mainDir() {
        return resolveModuleMain(SCAN_MODULES[0]);
    }
}
