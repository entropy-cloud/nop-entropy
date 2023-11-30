/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.model.loop.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.CoreConstants;
import io.nop.core.model.loop.INestedLoop;
import io.nop.core.model.loop.INestedLoopSupport;
import io.nop.core.model.loop.INestedLoopVar;
import io.nop.core.model.loop.model.NestedLoopModel;
import io.nop.core.model.loop.model.NestedLoopVarModel;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static io.nop.core.CoreErrors.ARG_GLOBAL_VARS;
import static io.nop.core.CoreErrors.ARG_VAR_NAME;
import static io.nop.core.CoreErrors.ERR_LOOP_VAR_NOT_DEFINED;

public class NestedLoop implements INestedLoop {
    private final NestedLoopModel model;
    private final Map<String, Object> globalVars;
    private final String varName;
    private final INestedLoopSupport parent;

    protected NestedLoop(NestedLoopModel model, Map<String, Object> globalVars, String varName,
                         INestedLoopSupport parent) {
        if (StringHelper.isEmpty(varName))
            varName = null;

        this.model = model;
        this.globalVars = globalVars == null ? Collections.emptyMap() : globalVars;
        this.varName = varName;
        this.parent = parent;

        if (varName != null) {
            if (!model.isGlobalVar(varName) && !model.isLoopVar(varName))
                throw new NopException(ERR_LOOP_VAR_NOT_DEFINED)
                        .param(ARG_VAR_NAME, varName).param(ARG_GLOBAL_VARS, this.globalVars.keySet());
        }
    }

    public NestedLoop(NestedLoopModel model, Map<String, Object> globalVars) {
        this(model, globalVars, null, null);
    }

    private class LoopVar implements INestedLoopVar {
        private final String varName;
        private final Object value;
        private final INestedLoopVar parentVar;

        public LoopVar(String varName, Object value, INestedLoopVar parentVar) {
            this.value = value;
            this.parentVar = parentVar;
            this.varName = Guard.notEmpty(varName, "varName");
        }

        public INestedLoopVar getParentVar() {
            return parentVar;
        }

        public String toString() {
            return varName + "=" + getVarValue();
        }

        @Override
        public String getVarName() {
            return varName;
        }

        @Override
        public Object getVarValue() {
            return value;
        }

        @Override
        public Stream<INestedLoopVar> stream() {
            return Stream.of(this);
        }

        @Override
        public INestedLoop loopForVar(String varName) {
            return new NestedLoop(model, globalVars, varName, this);
        }

        @Override
        public Stream<INestedLoopVar> streamForVar(String varName) {
            return varStream(varName, this);
        }
    }

    public boolean isRoot() {
        return parent == null;
    }

    public INestedLoop loopForVar(String varName) {
        if (StringHelper.isEmpty(varName) || Objects.equals(this.varName, varName))
            return this;

        // 产生新一层的嵌套循环
        return new NestedLoop(model, globalVars, varName, this);
    }

    @Override
    public Map<String, Object> getGlobalVars() {
        return globalVars;
    }

    @Override
    public boolean hasVar(String varName) {
        if (Objects.equals(this.varName, varName))
            return true;
        if (parent != null)
            return parent.hasVar(varName);
        return globalVars.containsKey(varName);
    }

    public Stream<INestedLoopVar> stream() {
        return varStream(varName, parent);
    }

    public Iterator<INestedLoopVar> iterator() {
        return stream().iterator();
    }

    Stream<INestedLoopVar> varStream(String varName, INestedLoopSupport parent) {
        if (varName == null) {
            if (parent != null)
                return parent.stream();

            // 如果是根节点，返回一个虚拟变量的值，避免循环为空
            return Stream.of(new LoopVar(CoreConstants.LOOP_ROOT_VAR, null, null));
        }

        // varName不为空时，parent肯定不为null
        return parent.stream().flatMap(p -> generateVarStream(varName, p));
    }

    Stream<INestedLoopVar> generateVarStream(String varName, INestedLoopVar p) {
        // 如果在父层级已经存在此变量
        INestedLoopVar parVar = p.getVar(varName);
        if (parVar != null) {
            return Stream.of(new LoopVar(parVar.getVarName(), parVar.getVarValue(), p));
        }

        // 如果是全局变量
        NestedLoopVarModel varModel = model.getVar(varName);
        if (varModel == null)
            return generateGlobalStream(varName, p);

        // 确保来源变量已经生成
        INestedLoopVar var = p.getVar(varModel.getSrcName());
        if (var != null) {
            return generateVarStream(varModel, new LoopVar(var.getVarName(), var.getVarValue(), p));
        } else {
            return p.streamForVar(varModel.getSrcName()).flatMap(pp -> generateVarStream(varModel, pp));
        }
    }

    Stream<INestedLoopVar> generateGlobalStream(String varName, INestedLoopVar p) {
        // 如果是全局变量
        Object value = globalVars.get(varName);
        if (StringHelper.isEmptyObject(value))
            return Stream.of(new LoopVar(varName, null, p));

        Stream<Object> stream = CollectionHelper.toStream(value, true, false);
        return stream.map(v -> new LoopVar(varName, v, p));
    }

    Stream<INestedLoopVar> generateVarStream(NestedLoopVarModel varModel, INestedLoopVar p) {
        Stream<Object> stream = varModel.generateStream(p.getVarValue());
        return stream.map(v -> new LoopVar(varModel.getVarName(), v, p));
    }
}