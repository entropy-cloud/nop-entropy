/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.compile;

import io.nop.core.type.IGenericType;
import io.nop.xlang.ast.Identifier;

import java.util.Map;

public class TypeInferenceState {
    /**
     * 变量名在当前环境下对应的对象类型
     */
    Map<Identifier, IGenericType> types;

    TypeInferenceState parent;

    public TypeInferenceState newChild() {
        return null;
    }
}
