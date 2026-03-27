/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.db.migration.executor;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.core.resource.component.AbstractComponentModel;
import io.nop.dao.dialect.IDialect;
import io.nop.db.migration.core.MigrationContext;
import io.nop.db.migration.model.CreateViewChange;

public class CreateViewExecutor implements IChangeExecutor {

    public static final String CHANGE_TYPE = "createView";

    @Override
    public void execute(AbstractComponentModel change, MigrationContext context, IDialect dialect) {
        CreateViewChange createView = (CreateViewChange) change;
        String viewName = createView.getName();
        if (StringHelper.isBlank(viewName)) {
            return;
        }
        String sql = buildCreateViewSql(createView, dialect);
        if (StringHelper.isNotBlank(sql)) {
            context.getJdbcTemplate().executeUpdate(
                SQL.begin()
                    .querySpace(context.getQuerySpace())
                    .name("create-view-" + viewName)
                    .append(sql)
                    .end()
            );
        }
    }

    @Override
    public String generateRollbackSql(AbstractComponentModel change, IDialect dialect) {
        CreateViewChange createView = (CreateViewChange) change;
        return "DROP VIEW IF EXISTS " + dialect.escapeSQLName(createView.getName());
    }

    @Override
    public boolean supports(String changeType) {
        return CHANGE_TYPE.equals(changeType);
    }

    protected String buildCreateViewSql(CreateViewChange change, IDialect dialect) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE VIEW ");
        sb.append(dialect.escapeSQLName(change.getName()));
        sb.append(" AS ");
        sb.append(change.getSelectSql());
        return sb.toString();
    }
}
