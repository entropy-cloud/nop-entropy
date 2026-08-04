/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
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
                                String noComments = content.replaceAll("//[^\\n]*", "")
                                        .replaceAll("/\\*.*?\\*/", "");
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
