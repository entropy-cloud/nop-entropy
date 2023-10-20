/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.web.page.vue;

import io.nop.xlang.ast.Expression;

import java.util.List;

public class VueNode {
    private String type;
    private String ref;
    private Expression key;

    private Expression ifExpr;

    private Expression itemsExpr;
    private String indexVarName;
    private String itemVarName;
    private List<VueNode> children;
}