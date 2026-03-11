/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.core.command;

import io.nop.ai.core.file.IFileOperator;
import io.nop.ai.core.file.LocalFileOperator;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class CallToolsContext {

    private final IFileOperator fileOperator;
    private final Map<String, Object> variables;

    public CallToolsContext(IFileOperator fileOperator) {
        this(fileOperator, new HashMap<>());
    }

    public CallToolsContext(File baseDir) {
        this(new LocalFileOperator(baseDir));
    }

    public CallToolsContext(IFileOperator fileOperator, Map<String, Object> variables) {
        this.fileOperator = fileOperator;
        this.variables = variables != null ? variables : new HashMap<>();
    }

    public IFileOperator getFileOperator() {
        return fileOperator;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public void setVariable(String name, Object value) {
        variables.put(name, value);
    }

    public Object getVariable(String name) {
        return variables.get(name);
    }
}
