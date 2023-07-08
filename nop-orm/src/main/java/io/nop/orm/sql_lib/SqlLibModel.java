/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.sql_lib;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.INeedInit;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.sql.SQL;
import io.nop.orm.sql_lib._gen._SqlLibModel;

import static io.nop.orm.OrmErrors.ARG_FRAGMENT_ID;
import static io.nop.orm.OrmErrors.ARG_PATH;
import static io.nop.orm.OrmErrors.ARG_SQL_NAME;
import static io.nop.orm.OrmErrors.ERR_ORM_UNKNOWN_FRAGMENT;
import static io.nop.orm.OrmErrors.ERR_SQL_LIB_UNKNOWN_SQL_ITEM;

public class SqlLibModel extends _SqlLibModel implements INeedInit {
    public SqlLibModel() {

    }

    @Override
    public void init() {
        for (SqlItemModel item : getSqls()) {
            item.setSqlLibModel(this);
        }
    }

    public SQL buildFragment(String fragmentId, IEvalContext context) {
        SqlFragmentModel frag = getFragment(fragmentId);
        if (frag == null)
            throw new NopException(ERR_ORM_UNKNOWN_FRAGMENT)
                    .source(this)
                    .param(ARG_FRAGMENT_ID, fragmentId);

        return frag.getSource().generateSql(context);
    }

    public SqlItemModel requireSql(String name) {
        SqlItemModel item = getSql(name);
        if (item == null)
            throw new NopException(ERR_SQL_LIB_UNKNOWN_SQL_ITEM).param(ARG_PATH, resourcePath()).param(ARG_SQL_NAME,
                    name);
        return item;
    }
}
