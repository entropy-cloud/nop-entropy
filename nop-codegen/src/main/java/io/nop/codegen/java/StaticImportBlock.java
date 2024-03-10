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

public class StaticImportBlock implements Comparable<StaticImportBlock> {
    private final SourceLocation loc;
    private final String importName;
    private final String shortName;

    public StaticImportBlock(SourceLocation loc, String importName, String shortName) {
        this.loc = loc;
        this.importName = importName;
        this.shortName = shortName;
    }

    public StaticImportBlock(SourceLocation loc, String importName) {
        this(loc, importName, StringHelper.lastPart(importName, '.'));
    }

    @Override
    public int compareTo(StaticImportBlock o) {
        return importName.compareTo(o.getImportName());
    }

    public SourceLocation getLoc() {
        return loc;
    }

    public String getImportName() {
        return importName;
    }

    public String getShortName() {
        return shortName;
    }
}
