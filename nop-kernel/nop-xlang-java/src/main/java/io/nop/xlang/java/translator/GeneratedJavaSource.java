/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.java.translator;

import io.nop.api.core.util.Guard;

/**
 * 转译产物：一个编译单元一个生成类（类名从 resourcePath 确定性派生），
 * 文件头含 {@code // source: <resourcePath>} 注释（仅辅助人工排查，不承载逻辑）。
 */
public final class GeneratedJavaSource {
    private final String resourcePath;
    private final String className;
    private final String code;

    public GeneratedJavaSource(String resourcePath, String className, String code) {
        this.resourcePath = Guard.notEmpty(resourcePath, "resourcePath");
        this.className = Guard.notEmpty(className, "className");
        this.code = Guard.notNull(code, "code");
    }

    public String getResourcePath() {
        return resourcePath;
    }

    /**
     * 全限定类名（含包名）。
     */
    public String getClassName() {
        return className;
    }

    public String getCode() {
        return code;
    }
}
