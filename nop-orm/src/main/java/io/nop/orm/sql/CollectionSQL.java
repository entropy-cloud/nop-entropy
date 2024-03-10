/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.sql;

import io.nop.commons.collections.ImmutableIntArray;
import io.nop.commons.collections.IntArray;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.shard.ShardSelection;
import io.nop.orm.IOrmEntity;

public class CollectionSQL {
    public final SQL sql;
    /**
     * 从子表选择的属性字段列表
     */
    public final ImmutableIntArray propIds;

    public final ImmutableIntArray paramPropIds;

    public CollectionSQL(SQL sql, IntArray propIds, IntArray paramPropIds) {
        this.sql = sql;
        this.propIds = propIds.toImmutable();
        this.paramPropIds = paramPropIds.toImmutable();
    }

    public SQL.SqlBuilder useParamsFromOwner(IDialect dialect, ShardSelection shard, IOrmEntity entity) {
        return GenSqlHelper.transform(dialect, shard, sql, entity, paramPropIds);
    }
}
