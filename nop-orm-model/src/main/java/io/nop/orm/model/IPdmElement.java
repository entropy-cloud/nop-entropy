/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.model;

import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.commons.lang.ITagSetSupport;

import java.util.Set;

public interface IPdmElement extends ITagSetSupport, ISourceLocationGetter {

    String getComment();

    Set<String> getTagSet();
}