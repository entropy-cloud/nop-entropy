/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.eql.sql;

/**
 * @author canonical_entropy@163.com
 */
public class SeqAliasGenerator implements IAliasGenerator {
    private int tableSeq;
    private int columnSeq;
    @Override
    public String genTableAlias() {
        tableSeq++;
        return "t" + tableSeq;
    }

    @Override
    public String genColumnAlias() {
        columnSeq++;
        return "c" + columnSeq;
    }
}