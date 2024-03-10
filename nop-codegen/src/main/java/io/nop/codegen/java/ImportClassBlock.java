/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.codegen.java;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;

public class ImportClassBlock implements Comparable<ImportClassBlock> {
    private final SourceLocation loc;
    private final String className;
    private final String shortName;

    public ImportClassBlock(SourceLocation loc, String className, String shortName) {
        this.loc = loc;
        this.className = className;
        this.shortName = shortName;
    }

    public ImportClassBlock(SourceLocation loc, String className) {
        this(loc, className, StringHelper.lastPart(className, '.'));
    }

    @Override
    public int compareTo(ImportClassBlock o) {
        return className.compareTo(o.getClassName());
    }

    public SourceLocation getLoc() {
        return loc;
    }

    public String getClassName() {
        return className;
    }

    public String getShortName() {
        return shortName;
    }
}
