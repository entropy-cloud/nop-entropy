/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.scope;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.ast.IdentifierKind;
import io.nop.xlang.ast.XLangIdentifierDefinition;
import io.nop.xlang.ast.definition.ClosureRefDefinition;
import io.nop.xlang.ast.definition.ImportClassDefinition;
import io.nop.xlang.ast.definition.LocalVarDeclaration;
import io.nop.xlang.xpl.IXplTagCompiler;
import io.nop.xlang.xpl.XplConstants;
import io.nop.xlang.xpl.xlib.XplLibHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.xlang.XLangErrors.ARG_ALIAS;
import static io.nop.xlang.XLangErrors.ARG_CLASS1;
import static io.nop.xlang.XLangErrors.ARG_CLASS2;
import static io.nop.xlang.XLangErrors.ARG_DECL_LOC;
import static io.nop.xlang.XLangErrors.ARG_TAG1;
import static io.nop.xlang.XLangErrors.ARG_TAG2;
import static io.nop.xlang.XLangErrors.ARG_USE_LOC;
import static io.nop.xlang.XLangErrors.ARG_VAR_DECL1;
import static io.nop.xlang.XLangErrors.ARG_VAR_DECL2;
import static io.nop.xlang.XLangErrors.ARG_VAR_NAME;
import static io.nop.xlang.XLangErrors.ERR_XLANG_DECLARE_VAR_CONFLICTS;
import static io.nop.xlang.XLangErrors.ERR_XLANG_IMPORT_MULTIPLE_CLASS_CONFLICTS;
import static io.nop.xlang.XLangErrors.ERR_XLANG_IMPORT_MULTIPLE_LIB_CONFLICTS;
import static io.nop.xlang.XLangErrors.ERR_XLANG_USE_VAR_BEFORE_DECLARATION;

public class XLangBlockScope {
    private final XLangBlockScope parentScope;

    private final Set<String> libNamespaces = new HashSet<>();
    private final Map<String, IXplTagCompiler> tagCompilers = new HashMap<>();
    private final Map<String, ImportClassDefinition> importedClasses = new HashMap<>();
    private final Map<String, LocalVarDeclaration> varDecls = new HashMap<>();
    private final Map<String, LocalVarDeclaration> macroVarDecls = new HashMap<>();
    private final Set<String> disabledNs = new HashSet<>();
    private final Set<String> enabledNs = new HashSet<>();

    private final List<LocalVarDeclaration> params = new ArrayList<>();
    private final List<LocalVarDeclaration> macroParams = new ArrayList<>();
    private final List<LocalVarDeclaration> hoistedVars = new ArrayList<>();
    private final List<LocalVarDeclaration> hoistedMacroVars = new ArrayList<>();

    private int blockLevel;
    private final boolean functionScope;
    private int loopLevel;
    private int macrolLoopLevel;

    // 对于FunctionBlock（即functionScope=true）时，记录本函数内部所引用的closure变量有哪些
    private final Map<String, ClosureRefDefinition> closureVars = new LinkedHashMap<>();

    private final Map<String, ClosureRefDefinition> macroClosureVars = new LinkedHashMap<>();

    // 在编译期离开scope时所需要执行的清理函数。主要处理c:macro-var产生的编译期变量定义
    private List<Runnable> cleanupActions;

    public XLangBlockScope(XLangBlockScope parentScope, int blockLevel, boolean functionScope) {
        this.parentScope = parentScope;
        this.blockLevel = blockLevel;
        this.functionScope = functionScope;
    }

    public void addCleanupAction(Runnable action) {
        if (cleanupActions == null)
            cleanupActions = new ArrayList<>();
        cleanupActions.add(action);
    }

    public void cleanup() {
        if (cleanupActions != null) {
            // 注册的时候从前往后，执行的时候从后向前，保持类似堆栈的结构
            for (int i = cleanupActions.size() - 1; i >= 0; i--) {
                cleanupActions.get(i).run();
            }
        }
    }

    public XLangIdentifierDefinition resolveVar(SourceLocation loc, String name, boolean macro) {
        XLangIdentifierDefinition var = getLocalVar(name, macro);
        if (var != null) {
            return var;
        }

        ClosureRefDefinition ref = getClosureVar(name, macro);
        if (ref != null)
            return ref;

        if (parentScope != null) {
            var = parentScope.resolveVar(loc, name, macro);
            if (var != null) {
                if (isFunctionScope()) {
                    ref = new ClosureRefDefinition(loc, toLocalVar(var));
                    addClosureVar(ref, macro);
                    return ref;
                }
            }
        }
        return var;
    }

    LocalVarDeclaration toLocalVar(XLangIdentifierDefinition var) {
        if (var instanceof ClosureRefDefinition)
            return ((ClosureRefDefinition) var).getVarDeclaration();
        return (LocalVarDeclaration) var;
    }

    public LocalVarDeclaration resolveVarInFunctionScope(SourceLocation loc, String name, boolean macro) {
        LocalVarDeclaration var = getLocalVar(name, macro);
        if (var != null) {
            return var;
        }

        if (isFunctionScope())
            return null;

        if (parentScope != null) {
            var = parentScope.resolveVarInFunctionScope(loc, name, macro);
        }
        return var;
    }

    public LexicalScope buildLexicalScope() {
        if (closureVars.isEmpty() && hoistedVars.isEmpty() && params.isEmpty())
            return LexicalScope.EMPTY_SCOPE;
        return new LexicalScope(params, hoistedVars,
                closureVars.isEmpty() ? Collections.emptyList() : new ArrayList<>(closureVars.values()));
    }

    public LexicalScope buildMacroLexicalScope() {
        if (macroClosureVars.isEmpty() && hoistedMacroVars.isEmpty() && macroParams.isEmpty())
            return LexicalScope.EMPTY_SCOPE;
        return new LexicalScope(macroParams, hoistedMacroVars,
                macroClosureVars.isEmpty() ? Collections.emptyList() : new ArrayList<>(macroClosureVars.values()));
    }

    public Map<String, ClosureRefDefinition> getClosureVars() {
        return closureVars;
    }

    public void addClosureVar(ClosureRefDefinition var, boolean macro) {
        if (macro) {
            this.macroClosureVars.put(var.getIdentifierName(), var);
        } else {
            this.closureVars.put(var.getIdentifierName(), var);
        }
    }

    public boolean isInLoop(boolean macro) {
        if (macro)
            return macrolLoopLevel > 0;
        return loopLevel > 0;
    }

    public void enterLoop(boolean macro) {
        if (macro) {
            macrolLoopLevel++;
        } else {
            loopLevel++;
        }
    }

    public void leaveLoop(boolean macro) {
        if (macro) {
            macrolLoopLevel--;
        } else {
            loopLevel--;
        }
    }

    public ClosureRefDefinition getClosureVar(String name, boolean macro) {
        if (macro)
            return macroClosureVars.get(name);
        return closureVars.get(name);
    }

    public boolean isFunctionScope() {
        return functionScope;
    }

    public int getBlockLevel() {
        return blockLevel;
    }

    public ImportClassDefinition getImportedClass(String alias) {
        return importedClasses.get(alias);
    }

    public void addImportedClass(String alias, ImportClassDefinition classDef) {
        ImportClassDefinition imported = importedClasses.put(alias, classDef);
        if (imported != null && imported != classDef)
            throw new NopEvalException(ERR_XLANG_IMPORT_MULTIPLE_CLASS_CONFLICTS)
                    .param(ARG_CLASS1, imported.getClassName()).param(ARG_CLASS2, classDef.getClassName())
                    .param(ARG_ALIAS, alias);
    }

    public IXplTagCompiler getTagCompiler(String tagName) {
        return tagCompilers.get(tagName);
    }

    public void addLibNamespace(String ns) {
        libNamespaces.add(ns);
    }

    public void addTagCompiler(String tagName, IXplTagCompiler cp) {
        String ns = XplLibHelper.getNamespaceFromTagName(tagName);
        libNamespaces.add(ns);
        _addTagCompiler(tagName, cp);
    }

    void _addTagCompiler(String tagName, IXplTagCompiler cp) {
        IXplTagCompiler old = tagCompilers.put(tagName, cp);
        if (old != null && old != cp) {
            throw new NopEvalException(ERR_XLANG_IMPORT_MULTIPLE_LIB_CONFLICTS).param(ARG_TAG1, old).param(ARG_TAG2,
                    cp);
        }
    }

    public XLangBlockScope getFunctionScope() {
        if (parentScope == null)
            return this;
        XLangBlockScope scope = this;
        while (!scope.isFunctionScope() && scope.parentScope != null) {
            scope = scope.parentScope;
        }
        return scope;
    }

    public LocalVarDeclaration getLocalVar(String name, boolean macro) {
        return macro ? macroVarDecls.get(name) : varDecls.get(name);
    }

    public void addVarDeclaration(String varName, LocalVarDeclaration varDecl, boolean macro) {
        XLangBlockScope fnScope = getFunctionScope();

        // 在定义变量前访问过同名的闭包变量
        ClosureRefDefinition closure = getClosureVar(varName, macro);
        if (closure != null)
            throw new NopEvalException(ERR_XLANG_USE_VAR_BEFORE_DECLARATION).param(ARG_VAR_NAME, varName)
                    .param(ARG_DECL_LOC, varDecl.getLocation()).param(ARG_USE_LOC, closure.getLocation());

        LocalVarDeclaration old = resolveVarInFunctionScope(varDecl.getLocation(), varName, macro);

        if (old != null && old != varDecl) {
            throw new NopEvalException(ERR_XLANG_DECLARE_VAR_CONFLICTS).param(ARG_VAR_DECL1, old.getLocation())
                    .param(ARG_VAR_DECL2, varDecl.getLocation()).param(ARG_VAR_NAME, varName);
        }

        if (macro) {
            macroVarDecls.put(varName, varDecl);
        } else {
            varDecls.put(varName, varDecl);
        }

        if (varDecl.getIdentifierKind() == IdentifierKind.VAR_DECL) {
            if (macro) {
                fnScope.hoistedMacroVars.add(varDecl);
            } else {
                fnScope.hoistedVars.add(varDecl);
            }
        } else if (varDecl.getIdentifierKind() == IdentifierKind.PARAM_DECL) {
            if (macro) {
                fnScope.macroParams.add(varDecl);
            } else {
                fnScope.params.add(varDecl);
            }
        }
    }

    public void enableNs(String ns) {
        if (XplConstants.XPL_ALL_NS.equals(ns)) {
            disabledNs.clear();
        } else {
            disabledNs.remove(ns);
            enabledNs.add(ns);
        }
    }

    public void setNsEnabled(String ns, boolean b) {
        if (b) {
            enableNs(ns);
        } else {
            disableNs(ns);
        }
    }

    public void addEnableNs(Set<String> nsList) {
        if (nsList != null) {
            for (String ns : nsList) {
                enableNs(ns);
            }
        }
    }

    public void addDisableNs(Set<String> nsList) {
        if (nsList != null) {
            for (String ns : nsList) {
                disableNs(ns);
            }
        }
    }

    public void disableNs(String ns) {
        if (XplConstants.XPL_ALL_NS.equals(ns)) {
            disabledNs.clear();
            enabledNs.clear();
            disabledNs.add(ns);
        } else {
            enabledNs.remove(ns);
            disabledNs.add(ns);
        }
    }

    public Boolean getNsEnabled(String ns) {
        if (enabledNs.contains(ns))
            return true;

        if (disabledNs.contains(XplConstants.XPL_ALL_NS))
            return false;

        if (disabledNs.contains(ns))
            return false;

        return null;
    }
}