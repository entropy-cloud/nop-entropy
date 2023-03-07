/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.api;

import io.nop.api.core.annotations.core.NoReflection;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.IFunctionModel;
import io.nop.xlang.ast.XLangIdentifierDefinition;
import io.nop.xlang.ast.XLangOutputMode;
import io.nop.xlang.ast.definition.ImportClassDefinition;
import io.nop.xlang.ast.definition.LocalVarDeclaration;
import io.nop.xlang.ast.definition.ScopeVarDefinition;
import io.nop.xlang.scope.LexicalScope;
import io.nop.xlang.xpl.IXplCompiler;
import io.nop.xlang.xpl.IXplTag;
import io.nop.xlang.xpl.IXplTagCompiler;
import io.nop.xlang.xpl.IXplTagLib;

import java.util.Set;

@NoReflection
public interface IXLangCompileScope extends IEvalScope {
    IXplCompiler getCompiler();

    /**
     * 生成变量名，确保在整个编译过程中的唯一性，不会与其他生成的变量名或者程序中已存在的变量名重复。
     */
    String generateVarName(String prefix);

    boolean isAllowUnregisteredScopeVar();

    void setAllowUnregisteredScopeVar(boolean b);

    /**
     * 当变量在scope中找不到时，会自动到parentScope中查找
     *
     * @return
     */
    IXLangCompileScope getParentScope();

    /**
     * 新建一个空的变量scope。为了便于维持scope内在限制，这里并没有传入vars参数
     *
     * @param inheritParentVars 新scope在查找变量未找到的时候，是否到父scope中查找
     * @param inheritParentOut  新scope的out和outputStream是否继承父scope的
     * @return
     */
    IXLangCompileScope newChildScope(boolean inheritParentVars, boolean inheritParentOut);

    /**
     * 当前的标签库，在标签实现中可以通过<thisLib:XXX>来调用当前标签库中的标签
     */
    IXplTagLib getCurrentLib();

    void setCurrentLib(IXplTagLib lib);

    // Map<String,XNode> getSlotDefaults();

    // void setSlotDefaults(Map<String, XNode> slotDefaults);

    /**
     * 将标签库引入指定的名字空间
     */
    void addLib(String namespace, IXplTagLib lib);

    void addTagCompiler(String tagName, IXplTagCompiler tagCp);

    IXplTagCompiler getTagCompiler(String tagName);

    /**
     * 名字空间封闭之后不再能够引入新的标签
     */
    boolean isNamespaceSealed(String namespace);

    void sealNamespace(String namespace);

    XLangOutputMode getOutputMode();

    void setOutputMode(XLangOutputMode outputMode);

    /**
     * 判断名字空间是否有可能对应IXplTagCompiler，如果返回false，则具有此名字空间的节点将被作为输出节点对待，忽略它对应的标签库。
     */
    boolean isNsEnabled(String ns);

    void enableNs(Set<String> ignoreNs);

    void disableNs(Set<String> checkNs);

    boolean isIgnoreExpr();

    void setIgnoreExpr(boolean ignoreExpr);

    boolean isIgnoreTag();

    void setIgnoreTag(boolean ignoreTag);

    /**
     * 遇到未识别的标签时是否抛出异常
     */
    boolean isAllowUnknownTag();

    void setAllowUnknownTag(boolean allowUnknownTag);

    IXplTag getCurrentTag();

    void setCurrentTag(IXplTag tag);

    void addImportedClass(String alias, ImportClassDefinition classModel);

    ImportClassDefinition getImportedClass(String alias);

    void addVarDeclaration(LocalVarDeclaration varDecl, boolean macro);

    void setFunctionProvider(IFunctionProvider functionProvider);

    IFunctionProvider getFunctionProvider();

    void registerFunction(String funcName, IFunctionModel fn);

    IFunctionModel getRegisteredFunction(String funcName);

    void registerScopeVarDefinition(ScopeVarDefinition varDef, boolean macro) throws NopException;

    void unregisterScopeVarDefinition(ScopeVarDefinition varDef, boolean macro);

    ScopeVarDefinition getScopeVarDefinition(String varName, boolean macro);

    XLangIdentifierDefinition resolveVar(SourceLocation loc, String varName, boolean macro);

    XLangIdentifierDefinition resolveVarInFunctionScope(SourceLocation loc, String varName, boolean macro);

    void enterMacro();

    void leaveMacro();

    /**
     * 是否处在宏表达式中
     */
    boolean isInMacro();

    /**
     * 进入一个新的代码块，不同代码块中的变量可以重名
     */
    void enterBlock(boolean functionScope);

    LexicalScope leaveBlock(boolean functionScope);

    void addBlockCleanupAction(Runnable action);

    void enterLoop();

    void leaveLoop();

    boolean isInLoop();
}