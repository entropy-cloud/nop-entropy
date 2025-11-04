/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xmeta.layout;

import io.nop.core.model.table.ICell;

public interface ILayoutCellModel extends ICell {
    String getLabel();

    String getType();

    void display(StringBuilder sb, int indent);
}
