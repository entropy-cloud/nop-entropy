/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm;

import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.data.DataBean;

import java.util.Objects;

@DataBean
public class QueryPlanCacheKey {
    private final String name;
    private final String sqlText;
    private final boolean disableLogicalDelete;

    public QueryPlanCacheKey(@Name("name") String name, @Name("sqlText") String sqlText,
                             @Name("disableLogicalDelete") boolean disableLogicalDelete) {
        this.name = name;
        this.sqlText = sqlText;
        this.disableLogicalDelete = disableLogicalDelete;
    }

    public String getName() {
        return name;
    }

    public String getSqlText() {
        return sqlText;
    }

    public boolean isDisableLogicalDelete() {
        return disableLogicalDelete;
    }

    @Override
    public int hashCode() {
        int h = name == null ? 0 : name.hashCode();
        h = sqlText.hashCode() * 31 + h;
        h += disableLogicalDelete ? 1 : 0;
        return h;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;

        if (!(o instanceof QueryPlanCacheKey))
            return false;

        QueryPlanCacheKey other = (QueryPlanCacheKey) o;
        if (!Objects.equals(name, other.name)) {
            return false;
        }

        if (!sqlText.equals(other.sqlText))
            return false;

        return disableLogicalDelete == other.disableLogicalDelete;
    }
}
