/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.scope;

import io.nop.api.core.annotations.core.NoReflection;
import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.EvalScopeImpl;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.IFunctionModel;
import io.nop.xlang.api.DefaultFunctionProvider;
import io.nop.xlang.api.IFunctionProvider;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.ast.XLangIdentifierDefinition;
import io.nop.xlang.ast.XLangOutputMode;
import io.nop.xlang.ast.definition.ImportClassDefinition;
import io.nop.xlang.ast.definition.LocalVarDeclaration;
import io.nop.xlang.ast.definition.ScopeVarDefinition;
import io.nop.xlang.xpl.IXplCompiler;
import io.nop.xlang.xpl.IXplTag;
import io.nop.xlang.xpl.IXplTagCompiler;
import io.nop.xlang.xpl.IXplTagLib;
import io.nop.xlang.xpl.XplConstants;
import io.nop.xlang.xpl.xlib.XplLibHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.xlang.XLangErrors.ARG_TAG_NAME;
import static io.nop.xlang.XLangErrors.ARG_VAR_DECL1;
import static io.nop.xlang.XLangErrors.ARG_VAR_DECL2;
import static io.nop.xlang.XLangErrors.ARG_VAR_NAME;
import static io.nop.xlang.XLangErrors.ERR_XLANG_SCOPE_VAR_DEFINITION_CONFLICTS;
import static io.nop.xlang.XLangErrors.ERR_XPL_THIS_LIB_NS_ONLY_ALLOWED_IN_TAG_IMPL;

@NoReflection
public class XLangCompileScope extends EvalScopeImpl implements IXLangCompileScope {
    private final IXplCompiler compiler;

    private final List<XLangBlockScope> scopes = new ArrayList<>();
    private final Set<String> sealedNamespaces = new HashSet<>();

    private int nextVarSeq = 1;

    // 记录blockStatement的层次
    private int blockLevel;

    private IXplTagLib currentLib;
    private IXplTag currentTag;

    private boolean allowUnknownTag;
    private boolean ignoreExpr;
    private boolean ignoreTag;
    private boolean allowUnregisteredScopeVar;

    private XLangOutputMode outputMode = XLangOutputMode.none;

    private int macroLevel;

    private IFunctionProvider functionProvider;

    private Map<String, ScopeVarDefinition> scopeVarDefs = new HashMap<>();

    private Map<String, ScopeVarDefinition> macroScopeVarDefs = new HashMap<>();

    // private Map<String, XNode> slotDefaults;

    public XLangCompileScope(IXplCompiler compiler) {
        this.compiler = compiler;
    }

    public XLangCompileScope(IEvalScope parentScope, Map<String, Object> variables, boolean inheritParentVars,
                             boolean inheritOutput, IXplCompiler compiler) {
        super(parentScope, variables, inheritParentVars, inheritOutput);
        this.compiler = compiler;
    }

    public IXplCompiler getCompiler() {
        return compiler;
    }

    XLangBlockScope topScope() {
        if (scopes.isEmpty()) {
            return null;
        }
        return scopes.get(scopes.size() - 1);
    }

    /**
     * 每一个{}都会产生一个局部变量作用域，这里选择延迟创建blockScope。如果在该scope范围内没有声明新的变量，则实际上不会创建对应的scope
     */
    XLangBlockScope makeScope(boolean functionScope) {
        XLangBlockScope scope = topScope();
        if (scope == null) {
            scope = new XLangBlockScope(null, blockLevel, functionScope);
            scopes.add(scope);
        } else {
            if (blockLevel != scope.getBlockLevel()) {
                scope = new XLangBlockScope(scope, blockLevel, functionScope);
                scopes.add(scope);
            }
        }
        return scope;
    }

    XLangBlockScope makeScope() {
        return makeScope(false);
    }

    @Override
    public IXLangCompileScope getParentScope() {
        return (IXLangCompileScope) super.getParentScope();
    }

    @Override
    public IXLangCompileScope newChildScope(boolean inheritParentVars, boolean inheritParentOut) {
        XLangCompileScope scope = new XLangCompileScope(this, new HashMap<>(), inheritParentVars, inheritParentOut,
                this.compiler);
        return scope;
    }

    @Override
    public void enterBlock(boolean functionScope) {
        blockLevel++;
        if (functionScope) {
            makeScope(true);
        }
    }

    @Override
    public LexicalScope leaveBlock(boolean functionScope) {
        LexicalScope fnScope = null;
        XLangBlockScope scope = topScope();
        if (scope != null && scope.getBlockLevel() == blockLevel) {
            XLangBlockScope blockScope = scopes.remove(scopes.size() - 1);
            if (functionScope)
                fnScope = buildLexicalScope(blockScope);
            if (blockScope != null)
                blockScope.cleanup();
        }
        blockLevel--;
        return fnScope;
    }

    @Override
    public void addBlockCleanupAction(Runnable action) {
        makeScope().addCleanupAction(action);
    }

    private LexicalScope buildLexicalScope(XLangBlockScope blockScope) {
        if (isInMacro())
            return blockScope.buildMacroLexicalScope();
        return blockScope.buildLexicalScope();
    }

    @Override
    public String generateVarName(String prefix) {
        return prefix + (nextVarSeq++);
    }

    @Override
    public IXplTagLib getCurrentLib() {
        return currentLib;
    }

    @Override
    public void setCurrentLib(IXplTagLib lib) {
        this.currentLib = lib;
    }

    // @Override
    // public Map<String, XNode> getSlotDefaults() {
    // return slotDefaults;
    // }
    //
    // @Override
    // public void setSlotDefaults(Map<String, XNode> slotDefaults) {
    // this.slotDefaults = slotDefaults;
    // }

    @Override
    public void addLib(String namespace, IXplTagLib lib) {
        XLangBlockScope scope = makeScope();
        scope.addLibNamespace(namespace);

        for (IXplTag tag : lib.getTags().values()) {
            String tagName = XplLibHelper.buildFullTagName(namespace, tag.getTagName());
            scope._addTagCompiler(tagName, tag.getTagCompiler());
        }
    }

    @Override
    public void addTagCompiler(String tagName, IXplTagCompiler tagCp) {
        makeScope().addTagCompiler(tagName, tagCp);
    }

    @Override
    public IXplTagCompiler getTagCompiler(String tagName) {
        // thisLib:XXX 对应于当前标签库中的标签
        if (StringHelper.startsWithNamespace(tagName, XplConstants.XPL_THIS_LIB_NS)) {
            if (currentLib == null)
                throw new NopEvalException(ERR_XPL_THIS_LIB_NS_ONLY_ALLOWED_IN_TAG_IMPL).param(ARG_TAG_NAME, tagName);
            IXplTag tag = currentLib.getTag(tagName.substring(XplConstants.XPL_THIS_LIB_NS.length() + 1));
            if (tag == null)
                return null;
            return tag.getTagCompiler();
        }

        for (int i = scopes.size() - 1; i >= 0; i--) {
            IXplTagCompiler tagCp = scopes.get(i).getTagCompiler(tagName);
            if (tagCp != null)
                return tagCp;
        }

        // IXLangCompileScope parentScope = getParentScope();
        // if (parentScope != null)
        // return parentScope.getTagCompiler(tagName);
        return null;
    }

    @Override
    public void setFunctionProvider(IFunctionProvider functionProvider) {
        this.functionProvider = functionProvider;
    }

    @Override
    public IFunctionProvider getFunctionProvider() {
        return functionProvider;
    }

    @Override
    public void registerFunction(String funcName, IFunctionModel fn) {
        if (functionProvider == null)
            functionProvider = new DefaultFunctionProvider();
        functionProvider.registerFunction(funcName, fn);
    }

    @Override
    public IFunctionModel getRegisteredFunction(String funcName) {
        if (functionProvider != null) {
            IFunctionModel fn = functionProvider.getRegisteredFunction(funcName);
            if (fn != null)
                return fn;
        }

        IXLangCompileScope parentScope = getParentScope();
        if (parentScope != null)
            return parentScope.getRegisteredFunction(funcName);
        return null;
    }

    @Override
    public void registerScopeVarDefinition(ScopeVarDefinition varDef, boolean macro) {
        ScopeVarDefinition oldDef;
        if (macro) {
            oldDef = macroScopeVarDefs.put(varDef.getVarName(), varDef);
        } else {
            oldDef = scopeVarDefs.put(varDef.getVarName(), varDef);
        }
        if (oldDef != null) {
            throw new NopEvalException(ERR_XLANG_SCOPE_VAR_DEFINITION_CONFLICTS)
                    .param(ARG_VAR_NAME, varDef.getVarName()).param(ARG_VAR_DECL1, oldDef.getLocation()).source(varDef)
                    .param(ARG_VAR_DECL2, varDef.getLocation());
        }
    }

    @Override
    public void unregisterScopeVarDefinition(ScopeVarDefinition varDef, boolean macro) {
        if (macro) {
            macroScopeVarDefs.remove(varDef.getVarName(), varDef);
        } else {
            scopeVarDefs.remove(varDef.getVarName(), varDef);
        }
    }

    public ScopeVarDefinition getScopeVarDefinition(String varName, boolean macro) {
        if (macro) {
            return macroScopeVarDefs.get(varName);
        } else {
            return scopeVarDefs.get(varName);
        }
    }

    @Override
    public XLangIdentifierDefinition resolveVar(SourceLocation loc, String name, boolean macro) {
        XLangBlockScope scope = topScope();
        if (scope == null)
            return null;
        return scope.resolveVar(loc, name, macro);
    }

    @Override
    public XLangIdentifierDefinition resolveVarInFunctionScope(SourceLocation loc, String name, boolean macro) {
        XLangBlockScope scope = topScope();
        if (scope == null)
            return null;
        return scope.resolveVarInFunctionScope(loc, name, macro);
    }

    @Override
    public void addVarDeclaration(LocalVarDeclaration varDecl, boolean macro) {
        makeScope().addVarDeclaration(varDecl.getIdentifierName(), varDecl, macro);
    }

    @Override
    public void addImportedClass(String alias, ImportClassDefinition classModel) {
        makeScope().addImportedClass(alias, classModel);
    }

    @Override
    public ImportClassDefinition getImportedClass(String name) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            ImportClassDefinition clazz = scopes.get(i).getImportedClass(name);
            if (clazz != null)
                return clazz;
        }

        // IXLangCompileScope parentScope = getParentScope();
        // if (parentScope != null)
        // return parentScope.getImportedClass(name);
        return null;
    }

    @Override
    public boolean isNamespaceSealed(String namespace) {
        return sealedNamespaces.contains(namespace);
    }

    @Override
    public void sealNamespace(String namespace) {
        sealedNamespaces.add(namespace);
    }

    @Override
    public XLangOutputMode getOutputMode() {
        return outputMode;
    }

    @Override
    public void setOutputMode(XLangOutputMode outputMode) {
        this.outputMode = outputMode;
    }

    @Override
    public boolean isNsEnabled(String ns) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            XLangBlockScope scope = scopes.get(i);
            Boolean b = scope.getNsEnabled(ns);
            if (b != null) {
                return b;
            }
        }
        return true;
    }

    @Override
    public void enableNs(Set<String> ignoreNs) {
        makeScope().addEnableNs(ignoreNs);
    }

    @Override
    public void disableNs(Set<String> checkNs) {
        makeScope().addDisableNs(checkNs);
    }

    @Override
    public boolean isIgnoreExpr() {
        return ignoreExpr;
    }

    @Override
    public void setIgnoreExpr(boolean ignoreExpr) {
        this.ignoreExpr = ignoreExpr;
    }

    @Override
    public boolean isIgnoreTag() {
        return ignoreTag;
    }

    @Override
    public void setIgnoreTag(boolean ignoreTag) {
        this.ignoreTag = ignoreTag;
    }

    @Override
    public boolean isAllowUnknownTag() {
        return allowUnknownTag;
    }

    @Override
    public void setAllowUnknownTag(boolean allowUnknownTag) {
        this.allowUnknownTag = allowUnknownTag;
    }

    @Override
    public IXplTag getCurrentTag() {
        return currentTag;
    }

    @Override
    public void setCurrentTag(IXplTag tag) {
        this.currentTag = tag;
    }

    @Override
    public void enterMacro() {
        macroLevel++;
    }

    @Override
    public void leaveMacro() {
        macroLevel--;
    }

    @Override
    public boolean isInMacro() {
        return macroLevel > 0;
    }

    XLangBlockScope functionScope() {
        return topScope().getFunctionScope();
    }

    @Override
    public void enterLoop() {
        functionScope().enterLoop(isInMacro());
        enterBlock(false);
    }

    @Override
    public void leaveLoop() {
        leaveBlock(false);
        functionScope().leaveLoop(isInMacro());
    }

    @Override
    public boolean isInLoop() {
        return functionScope().isInLoop(isInMacro());
    }

    @Override
    public boolean isAllowUnregisteredScopeVar() {
        return allowUnregisteredScopeVar;
    }

    @Override
    public void setAllowUnregisteredScopeVar(boolean allowUnregisteredScopeVar) {
        this.allowUnregisteredScopeVar = allowUnregisteredScopeVar;
    }
}
