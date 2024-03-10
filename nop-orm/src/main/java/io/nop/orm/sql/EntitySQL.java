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

public class EntitySQL {
    public final SQL sql;
    public final ImmutableIntArray propIds;
    /**
     * 从实体上读取属性作为sql的参数
     */
    public final ImmutableIntArray paramPropIds;

    public EntitySQL(SQL sql, IntArray propIds, IntArray paramPropIds) {
        this.sql = sql;
        // 因为可能需要复用，所以需要转换为不可变对象。否则可能拼接SQL的时候会追加额外属性
        this.propIds = propIds.toImmutable();
        this.paramPropIds = paramPropIds.toImmutable();
    }

    public SQL.SqlBuilder useParamsFromEntity(IDialect dialect, ShardSelection shard, IOrmEntity entity) {
        return GenSqlHelper.transform(dialect, shard, sql, entity, paramPropIds);
    }
}
