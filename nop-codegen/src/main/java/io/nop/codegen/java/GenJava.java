/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.codegen.java;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.util.SourceLocation;
import io.nop.codegen.common.AbstractGenCode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.codegen.CodeGenErrors.ARG_CLASS_NAME_1;
import static io.nop.codegen.CodeGenErrors.ARG_CLASS_NAME_2;
import static io.nop.codegen.CodeGenErrors.ARG_LOC_1;
import static io.nop.codegen.CodeGenErrors.ARG_STATIC_IMPORT_1;
import static io.nop.codegen.CodeGenErrors.ARG_STATIC_IMPORT_2;
import static io.nop.codegen.CodeGenErrors.ERR_CODE_GEN_IMPORT_CLASS_CONFLICTED;
import static io.nop.codegen.CodeGenErrors.ERR_CODE_GEN_STATIC_IMPORT_CONFLICTED;

public class GenJava extends AbstractGenCode {
    private Map<String, ImportClassBlock> imports = new HashMap<>();
    private Map<String, StaticImportBlock> staticImports = new HashMap<>();

    public List<ImportClassBlock> getImports() {
        if (imports.isEmpty())
            return Collections.emptyList();

        List<ImportClassBlock> list = new ArrayList<>(imports.values());
        Collections.sort(list);
        return list;
    }

    public List<StaticImportBlock> getStaticImports() {
        if (staticImports.isEmpty())
            return Collections.emptyList();

        List<StaticImportBlock> list = new ArrayList<>(staticImports.values());
        Collections.sort(list);
        return list;
    }

    public GenJava addImport(SourceLocation loc, String className) {
        ImportClassBlock block = new ImportClassBlock(loc, className);
        ImportClassBlock old = imports.put(block.getShortName(), block);
        if (old != null)
            throw new NopEvalException(ERR_CODE_GEN_IMPORT_CLASS_CONFLICTED).param(ARG_CLASS_NAME_1, old.getClassName())
                    .param(ARG_CLASS_NAME_2, className).param(ARG_LOC_1, old.getLoc()).loc(loc);
        return this;
    }

    public GenJava addStaticImport(SourceLocation loc, String staticImport) {
        StaticImportBlock block = new StaticImportBlock(loc, staticImport);
        StaticImportBlock old = staticImports.put(block.getShortName(), block);
        if (old != null)
            throw new NopEvalException(ERR_CODE_GEN_STATIC_IMPORT_CONFLICTED)
                    .param(ARG_STATIC_IMPORT_1, old.getImportName()).param(ARG_STATIC_IMPORT_2, staticImport)
                    .param(ARG_LOC_1, old.getLoc()).loc(loc);
        return this;
    }
}
