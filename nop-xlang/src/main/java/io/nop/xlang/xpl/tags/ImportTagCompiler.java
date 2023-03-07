/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpl.tags;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.reflect.IClassModel;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.ast.Identifier;
import io.nop.xlang.ast.ImportAsDeclaration;
import io.nop.xlang.ast.Literal;
import io.nop.xlang.ast.QualifiedName;
import io.nop.xlang.ast.XLangASTBuilder;
import io.nop.xlang.ast.definition.ImportClassDefinition;
import io.nop.xlang.xpl.IXplCompiler;
import io.nop.xlang.xpl.IXplTagCompiler;
import io.nop.xlang.xpl.IXplTagLib;
import io.nop.xlang.xpl.xlib.XplLibHelper;

import java.util.List;

import static io.nop.xlang.XLangErrors.ARG_NODE;
import static io.nop.xlang.XLangErrors.ERR_XPL_IMPORT_FROM_ADN_CLASS_BOTH_NULL;
import static io.nop.xlang.XLangErrors.ERR_XPL_IMPORT_NOT_ALLOW_BOTH_FROM_AND_CLASS_ATTR;
import static io.nop.xlang.xpl.XplConstants.AS_NAME;
import static io.nop.xlang.xpl.XplConstants.CLASS_NAME;
import static io.nop.xlang.xpl.XplConstants.FROM_NAME;
import static io.nop.xlang.xpl.utils.XplParseHelper.checkArgNames;
import static io.nop.xlang.xpl.utils.XplParseHelper.checkNotSysVar;
import static io.nop.xlang.xpl.utils.XplParseHelper.getAttrClass;
import static io.nop.xlang.xpl.utils.XplParseHelper.getAttrIdentifier;
import static io.nop.xlang.xpl.utils.XplParseHelper.getAttrVPath;
import static java.util.Arrays.asList;

public class ImportTagCompiler implements IXplTagCompiler {
    public static final ImportTagCompiler INSTANCE = new ImportTagCompiler();

    static final List<String> ATTR_NAMES = asList(FROM_NAME, AS_NAME, CLASS_NAME);

    @Override
    public ImportAsDeclaration parseTag(XNode node, IXplCompiler cp, IXLangCompileScope scope) {
        checkArgNames(node, ATTR_NAMES);

        String from = getAttrVPath(node, FROM_NAME, cp, scope);
        Identifier as = getAttrIdentifier(node, AS_NAME, cp, scope);
        checkNotSysVar(node, AS_NAME, as);

        String className = getAttrClass(node, CLASS_NAME, cp, scope);
        if (from != null && className != null)
            throw new NopEvalException(ERR_XPL_IMPORT_NOT_ALLOW_BOTH_FROM_AND_CLASS_ATTR).param(ARG_NODE, node);

        if (from == null && className == null)
            throw new NopEvalException(ERR_XPL_IMPORT_FROM_ADN_CLASS_BOTH_NULL).param(ARG_NODE, node);

        if (from != null) {
            String ns = as == null ? XplLibHelper.getNamespaceFromLibPath(from) : as.getName();
            IXplTagLib lib = cp.loadLib(node.getLocation(), ns, from, scope);
            scope.addLib(ns, lib);

            return XLangASTBuilder.importLib(node.getLocation(),
                    Literal.valueOf(node.attrValueLoc(FROM_NAME).getLocation(), from), as);
        } else {
            String name = as == null ? StringHelper.lastPart(className, '.') : as.getName();
            IClassModel classModel = scope.getClassModelLoader().loadClassModel(className);
            SourceLocation loc = node.attrValueLoc(CLASS_NAME).getLocation();
            scope.addImportedClass(name, new ImportClassDefinition(classModel));

            return XLangASTBuilder.importClass(node.getLocation(), QualifiedName.valueOf(loc, className), as);
        }
    }
}