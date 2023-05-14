/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.eql.ast;

import io.nop.orm.eql.ast._gen._SqlParameterMarker;

public class SqlParameterMarker extends _SqlParameterMarker {
    private int paramIndex;

    public int getParamIndex() {
        return paramIndex;
    }

    public void setParamIndex(int paramIndex) {
        this.paramIndex = paramIndex;
    }

    public SqlParameterMarker newInstance() {
        SqlParameterMarker ret = new SqlParameterMarker();
        ret.setParamIndex(paramIndex);
        return ret;
    }
}
