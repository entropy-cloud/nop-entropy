/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
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

    private final boolean allowUnderscoreName;

    public QueryPlanCacheKey(@Name("name") String name, @Name("sqlText") String sqlText,
                             @Name("disableLogicalDelete") boolean disableLogicalDelete,
                             @Name("allowUnderscoreName") boolean allowUnderscoreName) {
        this.name = name;
        this.sqlText = sqlText;
        this.disableLogicalDelete = disableLogicalDelete;
        this.allowUnderscoreName = allowUnderscoreName;
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

    public boolean isAllowUnderscoreName() {
        return allowUnderscoreName;
    }

    @Override
    public int hashCode() {
        int h = name == null ? 0 : name.hashCode();
        h = sqlText.hashCode() * 31 + h;
        h += disableLogicalDelete ? 1 : 0;
        h += allowUnderscoreName ? 3 : 0;
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

        return disableLogicalDelete == other.disableLogicalDelete && allowUnderscoreName == other.allowUnderscoreName;
    }
}
