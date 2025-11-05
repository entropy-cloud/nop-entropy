/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.ast;

import io.nop.orm.eql.ast._gen._SqlParameterMarker;
import io.nop.orm.eql.param.ISqlParamBuilder;

public class SqlParameterMarker extends _SqlParameterMarker {
    private int paramIndex;
    private boolean masked;

    private ISqlParamBuilder sqlParamBuilder;

    public boolean isMasked() {
        return masked;
    }

    public void setMasked(boolean masked) {
        this.masked = masked;
    }

    public int getParamIndex() {
        return paramIndex;
    }

    public void setParamIndex(int paramIndex) {
        this.paramIndex = paramIndex;
    }

    public ISqlParamBuilder getSqlParamBuilder() {
        return sqlParamBuilder;
    }

    public void setSqlParamBuilder(ISqlParamBuilder sqlParamBuilder) {
        this.sqlParamBuilder = sqlParamBuilder;
    }

    public SqlParameterMarker newInstance() {
        SqlParameterMarker ret = new SqlParameterMarker();
        ret.setParamIndex(paramIndex);
        ret.setSqlParamBuilder(sqlParamBuilder);
        return ret;
    }
}
