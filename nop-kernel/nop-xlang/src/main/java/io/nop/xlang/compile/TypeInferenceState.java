/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.compile;

import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.xlang.ast.Identifier;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class TypeInferenceState {
    private final TypeInferenceState parent;
    private final Map<String, IGenericType> variableTypes;
    private final Map<String, IGenericType> functionReturnTypes;
    private IGenericType currentReturnType;
    private final Map<String, IGenericType> narrowedTypes;
    private final int depth;

    public TypeInferenceState() {
        this(null);
    }

    public TypeInferenceState(TypeInferenceState parent) {
        this.parent = parent;
        this.variableTypes = new HashMap<>();
        this.functionReturnTypes = new HashMap<>();
        this.narrowedTypes = new HashMap<>();
        this.depth = parent == null ? 0 : parent.depth + 1;
    }

    public TypeInferenceState newChild() {
        return new TypeInferenceState(this);
    }

    public TypeInferenceState getParent() {
        return parent;
    }

    public int getDepth() {
        return depth;
    }

    public void setVariableType(String name, IGenericType type) {
        variableTypes.put(name, type);
    }

    public void setVariableType(Identifier id, IGenericType type) {
        variableTypes.put(id.getName(), type);
        id.setReturnTypeInfo(type);
    }

    public IGenericType getVariableType(String name) {
        IGenericType type = narrowedTypes.get(name);
        if (type != null) {
            return type;
        }
        type = variableTypes.get(name);
        if (type == null && parent != null) {
            return parent.getVariableType(name);
        }
        return type;
    }

    public IGenericType getVariableType(Identifier id) {
        IGenericType type = getVariableType(id.getName());
        if (type != null) {
            id.setReturnTypeInfo(type);
        }
        return type;
    }

    public boolean hasVariable(String name) {
        if (variableTypes.containsKey(name)) {
            return true;
        }
        if (narrowedTypes.containsKey(name)) {
            return true;
        }
        return parent != null && parent.hasVariable(name);
    }

    public void setNarrowedType(String name, IGenericType type) {
        narrowedTypes.put(name, type);
    }

    public void setNarrowedTypes(Map<String, IGenericType> types) {
        if (types != null) {
            narrowedTypes.putAll(types);
        }
    }

    public void clearNarrowedTypes() {
        narrowedTypes.clear();
    }

    public Map<String, IGenericType> getNarrowedTypes() {
        return Collections.unmodifiableMap(narrowedTypes);
    }

    public void setFunctionReturnType(String name, IGenericType type) {
        functionReturnTypes.put(name, type);
    }

    public IGenericType getFunctionReturnType(String name) {
        IGenericType type = functionReturnTypes.get(name);
        if (type == null && parent != null) {
            return parent.getFunctionReturnType(name);
        }
        return type;
    }

    public void setCurrentReturnType(IGenericType type) {
        this.currentReturnType = type;
    }

    public IGenericType getCurrentReturnType() {
        if (currentReturnType != null) {
            return currentReturnType;
        }
        if (parent != null) {
            return parent.getCurrentReturnType();
        }
        return null;
    }

    public Map<String, IGenericType> getAllVariableTypes() {
        Map<String, IGenericType> all = new HashMap<>();
        collectVariableTypes(all);
        return all;
    }

    private void collectVariableTypes(Map<String, IGenericType> all) {
        if (parent != null) {
            parent.collectVariableTypes(all);
        }
        all.putAll(variableTypes);
        all.putAll(narrowedTypes);
    }

    public boolean isEmpty() {
        return variableTypes.isEmpty() && narrowedTypes.isEmpty() && functionReturnTypes.isEmpty();
    }
}
