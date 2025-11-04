/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.codegen.antlr;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;

import java.util.List;

public class AntlrParserConfig {
    private String antlrLibDirPath;
    private boolean antlrGenCode;
    private String antlrOutputPath;

    private String antlrModelPath;
    private String antlrPackage;

    private String mainRule;

    private String astModelPath;
    private String packageName;

    private List<String> primaryExpectedTokens;

    public String getAntlrLibDirPath() {
        return antlrLibDirPath;
    }

    public void setAntlrLibDirPath(String antlrLibDirPath) {
        this.antlrLibDirPath = antlrLibDirPath;
    }

    public boolean isAntlrGenCode() {
        return antlrGenCode;
    }

    public void setAntlrGenCode(boolean antlrGenCode) {
        this.antlrGenCode = antlrGenCode;
    }

    public String getAntlrOutputPath() {
        return antlrOutputPath;
    }

    public void setAntlrOutputPath(String antlrOutputPath) {
        this.antlrOutputPath = antlrOutputPath;
    }

    public String getAntlrModelPath() {
        return antlrModelPath;
    }

    public void setAntlrModelPath(String antlrModelPath) {
        this.antlrModelPath = antlrModelPath;
    }

    public String getMainRule() {
        return mainRule;
    }

    public void setMainRule(String mainRule) {
        this.mainRule = mainRule;
    }

    public String getAstModelPath() {
        return astModelPath;
    }

    public void setAstModelPath(String astModelPath) {
        this.astModelPath = astModelPath;
    }

    public List<String> getPrimaryExpectedTokens() {
        return primaryExpectedTokens;
    }

    public void setPrimaryExpectedTokens(List<String> primaryExpectedTokens) {
        this.primaryExpectedTokens = primaryExpectedTokens;
    }

    public String getAntlrPackage() {
        return antlrPackage;
    }

    public void setAntlrPackage(String antlrPackage) {
        this.antlrPackage = antlrPackage;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getPackagePath() {
        return StringHelper.replace(packageName, ".", "/");
    }

    public void resolveAbsolutePath(String currentPath) {
        if (StringHelper.isEmpty(currentPath))
            return;

        this.antlrLibDirPath = StringHelper.absolutePath(currentPath, antlrLibDirPath);
        this.antlrModelPath = StringHelper.absolutePath(currentPath, antlrModelPath);
        this.astModelPath = StringHelper.absolutePath(currentPath, astModelPath);
    }

    public static AntlrParserConfig loadFromPath(String path) {
        return JsonTool.loadBean(path, AntlrParserConfig.class);
    }
}