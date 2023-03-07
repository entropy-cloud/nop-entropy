/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.debugger;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;

@DataBean
public class StackTraceElement implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String sourcePath;
    private final int line;
    private final String funcName;

    @JsonCreator
    public StackTraceElement(@JsonProperty("sourcePath") String sourcePath, @JsonProperty("line") int line,
                             @JsonProperty("funcName") String funcName) {
        this.sourcePath = sourcePath;
        this.line = line;
        this.funcName = funcName;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(funcName).append(':').append(line);
        if (sourcePath != null) {
            sb.append(',');
            int pos = sourcePath.lastIndexOf('/');
            if (pos < 0) {
                sb.append(sourcePath);
            } else {
                sb.append(sourcePath.substring(pos + 1));
                sb.append('(');
                sb.append(sourcePath.substring(0, pos));
                sb.append(')');
            }
        }
        return sb.toString();
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public int getLine() {
        return line;
    }

    public String getFuncName() {
        return funcName;
    }
}
