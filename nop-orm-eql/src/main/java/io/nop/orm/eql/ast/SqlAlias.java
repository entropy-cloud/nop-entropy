/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.ast;

import io.nop.core.lang.ast.ASTNode;
import io.nop.orm.eql.ast._gen._SqlAlias;

public class SqlAlias extends _SqlAlias {
    /**
     * 由编译期自动生成的alias，而不是由代码直接指定的alias。编译期会检查自动生成的alias只会被自动生成的代码所引用。
     */
    private boolean generated;

    private boolean used;

    public boolean isUsed() {
        return used;
    }

    public void setUsed(boolean used) {
        this.used = used;
    }

    @Override
    protected void copyExtFieldsTo(ASTNode node) {
        SqlAlias alias = (SqlAlias) node;
        alias.generated = generated;
        alias.used = used;
    }

    public boolean isGenerated() {
        return generated;
    }

    public void setGenerated(boolean generated) {
        this.generated = generated;
    }
}
