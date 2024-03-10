/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.debugger;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.util.SourceLocation;

import java.io.Serializable;

@DataBean
public class Breakpoint implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String sourcePath;
    private final int line;
    private final String condition;

    // 如果设置了logExpr，则执行到断点时只打印log信息，并不停止
    private final String logExpr;

    @JsonCreator
    public Breakpoint(@JsonProperty("sourcePath") String sourcePath, @JsonProperty("line") int line,
                      @JsonProperty("condition") String condition, @JsonProperty("logExpr") String logExpr) {
        this.sourcePath = sourcePath;
        this.line = line;
        this.condition = condition;
        this.logExpr = logExpr;
    }

    public String toString() {
        return "Breakpoint[sourcePath=" + sourcePath + ",line=" + line + ",condition=" + condition + ",logExpr="
                + logExpr + "]";
    }

    public static Breakpoint build(String sourcePath, int line) {
        return new Breakpoint(sourcePath, line, null, null);
    }

    public static Breakpoint build(SourceLocation loc) {
        return new Breakpoint(loc.getCellPath(), loc.getLine(), null, null);
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getSourcePath() {
        return sourcePath;
    }

    public int getLine() {
        return line;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getCondition() {
        return condition;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getLogExpr() {
        return logExpr;
    }
}