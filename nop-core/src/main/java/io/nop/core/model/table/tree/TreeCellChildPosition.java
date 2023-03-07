/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.model.table.tree;

public enum TreeCellChildPosition {
    /**
     * 水平展开，父节点不单独占据位置。父节点占据的空间被子节点替换
     */
    hor,

    /**
     * 垂直展开，父节点不单独占据位置。父节点占据的空间被子节点替换
     */
    ver,

    /**
     * 子单元格位于父单元格的左侧，水平排列
     */
    left_hor,

    right_hor, top_hor,

    /**
     * 子单元格位于父单元格的下方，水平排列
     */
    bottom_hor,

    left_ver, right_ver, top_ver, bottom_ver
}