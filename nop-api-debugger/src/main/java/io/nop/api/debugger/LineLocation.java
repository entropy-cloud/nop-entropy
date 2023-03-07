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
import io.nop.api.core.util.SourceLocation;

import java.io.Serializable;
import java.util.function.Function;

@DataBean
public class LineLocation implements Serializable {
    private final String sourcePath;
    private final int line;

    @JsonCreator
    public LineLocation(@JsonProperty("sourcePath") String sourcePath, @JsonProperty("line") int line) {
        this.sourcePath = sourcePath;
        this.line = line;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public static LineLocation fromSourcePosition(SourceLocation loc,
                                                  Function<SourceLocation, String> sourcePathGetter) {
        if (loc == null)
            return null;
        LineLocation ret = new LineLocation(sourcePathGetter.apply(loc), loc.getLine());
        return ret;
    }

    public String toString() {
        return sourcePath + '(' + line + ')';
    }

    public int getLine() {
        return line;
    }
}