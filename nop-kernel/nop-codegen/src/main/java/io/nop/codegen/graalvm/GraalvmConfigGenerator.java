/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.codegen.graalvm;

import io.nop.api.core.exceptions.NopException;
import io.nop.codegen.maven.MavenModelHelper;
import io.nop.codegen.maven.model.PomArtifactKey;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.MavenDirHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.resource.VirtualFileSystem;

import java.io.File;
import java.util.Set;

import static io.nop.codegen.CodeGenConfigs.CFG_CODEGEN_TRACE_DIR;
import static io.nop.codegen.CodeGenConfigs.CFG_CODEGEN_TRACE_ENABLED;
import static io.nop.codegen.CodeGenErrors.ERR_CODE_GEN_PATH_NOT_ALLOW_STARTS_WITH_OR_ENDS_WITH_BLANK;

public class GraalvmConfigGenerator {
    static final GraalvmConfigGenerator _INSTANCE = new GraalvmConfigGenerator();

    private File configDir;
    private File resourceDir;

    public static GraalvmConfigGenerator instance() {
        return _INSTANCE;
    }

    public boolean isEnabled() {
        return CFG_CODEGEN_TRACE_ENABLED.get();
    }

    private void init() {
        File projectDir = MavenDirHelper.guessProjectDir();
        PomArtifactKey artifact = MavenModelHelper.getProjectArtifactKey(projectDir);

        if (configDir == null) {
            String path = CFG_CODEGEN_TRACE_DIR.get();
            if (!StringHelper.isEmpty(path)) {
                configDir = new File(path);
            }
        }

        if (configDir == null) {
            if (artifact != null) {
                configDir = getNativeImageConfigDir(projectDir, artifact);
            }
        }

        if (configDir == null) {
            configDir = new File(projectDir, "graalvm");
        }

        if (resourceDir == null) {
            if (artifact != null) {
                resourceDir = new File(projectDir, "src/main/resources");
            } else {
                resourceDir = new File(projectDir, "graalvm");
            }
        }

        // JSON序列化的时候需要使用到反射
        ReflectionManager.instance().logReflectClass(ReflectClass.class);
        ReflectionManager.instance().logReflectClass(ReflectField.class);
        ReflectionManager.instance().logReflectClass(ReflectMethod.class);
    }

    File getNativeImageConfigDir(File projectDir, PomArtifactKey artifact) {
        return new File(projectDir,
                "src/main/resources/META-INF/native-image/" + artifact.getGroupId() + '/' + artifact.getArtifactId());
    }

    public File getConfigDir() {
        init();
        return configDir;
    }

    public File getResourceDir() {
        init();
        return resourceDir;
    }

    public void generateVfsIndex() {
        Set<String> files = VirtualFileSystem.instance().getClassPathResources();
        for (String file : files) {
            if (file.startsWith(" ") || file.endsWith(" "))
                throw new NopException(ERR_CODE_GEN_PATH_NOT_ALLOW_STARTS_WITH_OR_ENDS_WITH_BLANK);
        }

        String text = StringHelper.join(files, "\n");
        File indexPath = new File(getResourceDir(), "nop-vfs-index.txt");
        FileHelper.writeText(indexPath, text, null);
    }

    public void generateGraalvmConfig() {
        ReflectConfigGenerator.instance().generateDeltaToDir(getConfigDir());
        ProxyConfigGenerator.instance().generateDeltaToDir(getConfigDir());
    }
}