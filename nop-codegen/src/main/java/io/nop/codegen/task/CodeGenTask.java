/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.codegen.task;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.util.LogLevel;
import io.nop.codegen.CodeGenConstants;
import io.nop.codegen.XCodeGenerator;
import io.nop.commons.util.FileHelper;
import io.nop.core.CoreConfigs;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.store.DefaultVirtualFileSystem;
import io.nop.log.core.LoggerConfigurator;
import io.nop.xlang.api.XLang;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static io.nop.core.CoreConfigs.CFG_INCLUDE_CURRENT_PROJECT_RESOURCES;

/**
 * 可以在ant脚本中配置执行或者通过exec-maven-plugin插件执行
 */
public class CodeGenTask {
    static final Logger LOG = LoggerFactory.getLogger(CodeGenTask.class);
    private File projectPath;
    private File tplRootPath;
    private File targetRootPath;

    private String tplPath = "/";
    private Map<String, Object> params;
    private LogLevel logLevel;

    private int maxInitializeLevel = CoreConstants.INITIALIZER_PRIORITY_ANALYZE;

    public int getMaxInitializeLevel() {
        return maxInitializeLevel;
    }

    public void setMaxInitializeLevel(int maxInitializeLevel) {
        this.maxInitializeLevel = maxInitializeLevel;
    }

    public void setProjectPath(File projectPath) {
        this.projectPath = projectPath;
    }

    public File getProjectPath() {
        return projectPath;
    }

    public void setLogLevel(LogLevel logLevel) {
        this.logLevel = logLevel;
    }

    public File getTplRootPath() {
        return tplRootPath;
    }

    public void setTplRootPath(File tplRootPath) {
        this.tplRootPath = tplRootPath;
    }

    public File getTargetRootPath() {
        return targetRootPath;
    }

    public void setTargetRootPath(File targetRootPath) {
        this.targetRootPath = targetRootPath;
    }

    public String getTplPath() {
        return tplPath;
    }

    public void setTplPath(String tplPath) {
        this.tplPath = tplPath;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    public void execute() {
        AppConfig.getConfigProvider().updateConfigValue(CoreConfigs.CFG_CORE_MAX_INITIALIZE_LEVEL, maxInitializeLevel);

        CoreInitialization.initialize();

        try {
            if (logLevel != null)
                LoggerConfigurator.instance().changeLogLevel(null, logLevel);

            String tplRootPathUrl = FileHelper.getFileUrl(tplRootPath);
            String targetRootPathUrl = FileHelper.getFileUrl(targetRootPath);
            XCodeGenerator gen = new XCodeGenerator(tplRootPathUrl, targetRootPathUrl);
            if (params == null)
                params = new HashMap<>();
            String projectPathUrl = FileHelper.getFileUrl(projectPath);
            params.put(CodeGenConstants.VAR_CODE_GEN_PROJECT, projectPathUrl);
            gen.execute(tplPath, XLang.newEvalScope(params));
        } finally {
            CoreInitialization.destroy();
        }
    }

    static void debug() {
        System.out.println("========properties==========");
        for (Map.Entry<Object, Object> entry : System.getProperties().entrySet()) {
            System.out.println(entry.getKey() + "=" + entry.getValue());
        }

        System.out.println("==========env=========");
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            System.out.println(entry.getKey() + "=" + entry.getValue());
        }
    }

    static LogLevel getLogLevel() {
        String level = System.getProperty("org.slf4j.simpleLogger.defaultLogLevel");
        if (level == null)
            return null;
        return LogLevel.fromText(level);
    }

    public static void main(String[] args) {
        // args = new String[]{"C:\\can\\entropy-cloud\\nop-config"};
        LogLevel logLevel = getLogLevel();

        if (logLevel != null)
            debug();

        // 禁用远程配置服务
        System.setProperty(CoreConstants.CFG_CONFIG_SERVICE_ENABLED, "false");

        CodeGenTask task = new CodeGenTask();
        task.setLogLevel(logLevel);

        File projectPath = new File(args[0]);

        // 切换当前工作目录到工程目录
        FileHelper.setCurrentDir(projectPath);
        try {
            if (args.length > 1 && args[1].equals("aop")) {
                VirtualFileSystem.registerInstance(new DefaultVirtualFileSystem());
                genAopProxy(projectPath);
                return;
            }
            String tplRoot = "precompile";
            if (args.length > 1) {
                tplRoot = args[1];
            }
            task.setTplRootPath(new File(projectPath, tplRoot));
            boolean dryRun = isPropOn("dryRun");
            System.out.println(
                    "projectDir=" + projectPath.getAbsolutePath() + ",dryRun=" + dryRun + ",tplRoot=" + tplRoot);
            File targetRootPath = new File(projectPath, dryRun ? "target/" + tplRoot : "");
            task.setTargetRootPath(targetRootPath);
            task.setProjectPath(projectPath);

            // 代码生成可能会用到工程中的类，但是不会启动bean容器
            int maxLevel = CoreConstants.INITIALIZER_PRIORITY_ANALYZE;

            if ("precompile".equals(tplRoot)) {
                maxLevel = CoreConstants.INITIALIZER_PRIORITY_PRECOMPILE;
                AppConfig.getConfigProvider().updateConfigValue(CFG_INCLUDE_CURRENT_PROJECT_RESOURCES,false);
            }
            task.setMaxInitializeLevel(maxLevel);

            task.execute();
            System.out.println("end");
        } finally {
            FileHelper.setCurrentDir(null);
        }
    }

    static void genAopProxy(File projectDir) {
        new GenAopProxy().execute(projectDir, false);

        if (System.getProperty("skipTests") == null) {
            try {
                new GenAopProxy().execute(projectDir, true);
            } catch (Exception e) {
                LOG.debug("nop.gen-aop-proxy-fail", e);
            }
        }
    }

    static boolean isPropOn(String propName) {
        String value = System.getProperty(propName);
        return value != null && !"false".equals(value);
    }
}