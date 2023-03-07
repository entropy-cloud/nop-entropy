/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.ast;

import io.nop.api.core.util.IDeepCloneable;
import io.nop.api.core.util.IFreezable;
import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.core.lang.json.IJsonSerializable;

import java.io.Serializable;

public interface IASTNode extends ISourceLocationGetter, Serializable, IDeepCloneable, IJsonSerializable, IFreezable {
    String getASTType();
}
