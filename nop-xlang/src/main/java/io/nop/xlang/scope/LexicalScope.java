/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.scope;

import io.nop.api.core.util.Guard;
import io.nop.api.core.util.Symbol;
import io.nop.commons.util.StringHelper;
import io.nop.xlang.ast.XLangIdentifierDefinition;
import io.nop.xlang.ast.definition.ClosureRefDefinition;
import io.nop.xlang.ast.definition.LocalVarDeclaration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LexicalScope {
    public static LexicalScope EMPTY_SCOPE = new LexicalScope(Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList());
    public static int[] EMPTY_SLOTS = new int[0];

    private final List<LocalVarDeclaration> params;

    // 函数中所有声明的变量都集中收集到此集合中，可能会存在同名的变量
    private final List<LocalVarDeclaration> localVars;

    // 函数以及函数内子函数所用到的闭包变量和闭包函数
    private final List<ClosureRefDefinition> closureRefs;

    private List<ClosureRefDefinition> allClosureVars;

    private Map<Symbol, XLangIdentifierDefinition> tokenMap;

    private String[] slotNames;
    private int[] closureTargetSlots;

    public LexicalScope(List<LocalVarDeclaration> params, List<LocalVarDeclaration> localVars,
                        List<ClosureRefDefinition> closureRefs) {
        this.params = params;
        this.localVars = Guard.notNull(localVars, "localVars");
        this.closureRefs = Guard.notNull(closureRefs, "closureRefs");

        tokenMap = new HashMap<>();
        for (LocalVarDeclaration param : params) {
            tokenMap.put(param.getToken(), param);
        }

        for (LocalVarDeclaration var : localVars) {
            tokenMap.put(var.getToken(), var);
        }

        for (ClosureRefDefinition ref : closureRefs) {
            tokenMap.put(ref.getToken(), ref);
        }
    }

    /**
     * 内部调用函数如果需要用到闭包变量，则需要提升内部闭包变量到顶层
     */
    public List<ClosureRefDefinition> hoistClosureVars() {
        if (allClosureVars != null)
            return allClosureVars;

        if (closureRefs.isEmpty()) {
            allClosureVars = Collections.emptyList();
            initSlots();
            return allClosureVars;
        }

        allClosureVars = new ArrayList<>();

        for (ClosureRefDefinition ref : closureRefs) {
            LocalVarDeclaration var = ref.getVarDeclaration();

            // 可以内联的常量不用作为闭包传递
            if (var.isInlineVar())
                continue;

            if (var.isFuncDecl()) {
                // 闭包函数所依赖的闭包变量需要被隐式传递
                List<ClosureRefDefinition> allVars = var.getFunctionScope().hoistClosureVars();
                for (ClosureRefDefinition refVar : allVars) {
                    Symbol token = refVar.getVarDeclaration().getToken();
                    // 不同的闭包函数可能引用同一个闭包变量，只需要传递一次
                    if (!tokenMap.containsKey(token)) {
                        ClosureRefDefinition localRef = new ClosureRefDefinition(ref.getLocation(),
                                refVar.getVarDeclaration());
                        allClosureVars.add(localRef);
                        tokenMap.put(token, localRef);
                    }
                }
            } else {
                allClosureVars.add(ref);
                tokenMap.put(ref.getToken(), ref);
            }
        }

        initSlots();
        return allClosureVars;
    }

    private void initSlots() {
        if (slotNames == null)
            assignVarSlots();
    }

    /**
     * 按照参数-闭包变量-局部变量的顺序分配slot编号。
     */
    private void assignVarSlots() {
        int slot = 0;
        List<String> varNames = new ArrayList<>();
        Map<String, LocalVarDeclaration> nameMap = new HashMap<>();
        if (!params.isEmpty()) {
            for (LocalVarDeclaration param : params) {
                String varName = param.getIdentifierName();
                param.setVarSlot(slot);
                varNames.add(varName);
                slot++;

                nameMap.put(varName, param);
            }
        }

        for (ClosureRefDefinition ref : allClosureVars) {
            ref.setVarSlot(slot);
            varNames.add(ref.getIdentifierName());
            slot++;
        }

        initClosureSlots();

        if (!localVars.isEmpty()) {
            for (LocalVarDeclaration var : localVars) {
                String varName = var.getIdentifierName();
                // 同名的变量slot一致
                LocalVarDeclaration old = nameMap.get(varName);
                if (old != null) {
                    var.setVarSlot(old.getVarSlot());
                } else {
                    var.setVarSlot(slot);
                    slot++;
                    varNames.add(varName);
                    nameMap.put(varName, var);
                }
            }
        }

        this.slotNames = StringHelper.toStringArray(varNames);
    }

    private void initClosureSlots() {
        this.closureTargetSlots = new int[allClosureVars.size()];

        for (int i = 0, n = allClosureVars.size(); i < n; i++) {
            ClosureRefDefinition var = allClosureVars.get(i);
            closureTargetSlots[i] = Guard.nonNegativeInt(var.getVarSlot(), "closureTargetSlot");
        }
    }

    public int getParamCount() {
        return params.size();
    }

    public String[] getSlotNames() {
        return slotNames;
    }

    public int getClosureCount() {
        return allClosureVars.size();
    }

    public int getSlotCount() {
        return slotNames.length;
    }

    public List<LocalVarDeclaration> getParams() {
        return params;
    }

    public List<LocalVarDeclaration> getLocalVars() {
        return localVars;
    }

    public List<ClosureRefDefinition> getClosureRefs() {
        return closureRefs;
    }

    public List<ClosureRefDefinition> getAllClosureVars() {
        return allClosureVars;
    }

    public int[] getClosureTargetSlots() {
        return closureTargetSlots;
    }

    public int[] getClosureSlots(List<ClosureRefDefinition> vars) {
        int[] slots = new int[vars.size()];
        for (int i = 0, n = vars.size(); i < n; i++) {
            ClosureRefDefinition ref = vars.get(i);
            int slot = tokenMap.get(ref.getToken()).getVarSlot();
            slots[i] = slot;
        }
        return slots;
    }
}