/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.codegen.graalvm;

import io.nop.codegen.maven.model.PomArtifactKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestGraalvmConfigGeneratorPaths {

    @TempDir
    Path tempDir;

    @Test
    public void testNativeImageConfigDirLayout() {
        File projectDir = tempDir.toFile();
        PomArtifactKey artifact = new PomArtifactKey("io.github.entropy-cloud", "nop-test");

        File dir = new GraalvmConfigGenerator().getNativeImageConfigDir(projectDir, artifact);

        // GraalVM native-image 配置目录约定：src/main/resources/META-INF/native-image/{groupId}/{artifactId}
        File expected = new File(projectDir,
                "src/main/resources/META-INF/native-image/io.github.entropy-cloud/nop-test");
        assertEquals(expected.getAbsolutePath(), dir.getAbsolutePath());
    }
}
