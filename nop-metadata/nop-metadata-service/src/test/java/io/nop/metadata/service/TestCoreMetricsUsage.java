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
 */
public class TestCoreMetricsUsage {

    private static final String MAIN_SRC_DIR =
            "src/main/java";

    @Test
    public void testNoSystemCurrentTimeMillisInMainCode() throws IOException {
        Path baseDir = Paths.get(System.getProperty("user.dir", "."));
        Path mainSrc = baseDir.resolve(MAIN_SRC_DIR);
        if (!Files.exists(mainSrc)) {
            // 测试在子模块根目录运行
            mainSrc = baseDir.resolve("nop-metadata/nop-metadata-service/" + MAIN_SRC_DIR);
        }
        assertTrue(Files.exists(mainDir()), "main src dir must exist: " + mainDir());

        try (Stream<Path> stream = Files.walk(mainDir())) {
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

    private Path mainDir() {
        Path baseDir = Paths.get(System.getProperty("user.dir", "."));
        Path mainSrc = baseDir.resolve(MAIN_SRC_DIR);
        if (!Files.exists(mainSrc)) {
            mainSrc = baseDir.resolve("nop-metadata/nop-metadata-service/" + MAIN_SRC_DIR);
        }
        return mainSrc;
    }
}
