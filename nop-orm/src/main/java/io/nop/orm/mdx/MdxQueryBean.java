package io.nop.orm.mdx;

import io.nop.api.core.beans.query.QueryBean;
import io.nop.commons.collections.IntArray;

public class MdxQueryBean extends QueryBean {
    private IntArray dimFieldIndexes; // dimFields在mainFields中的下标

    public IntArray getDimFieldIndexes() {
        return dimFieldIndexes;
    }

    public void setDimFieldIndexes(IntArray dimFieldIndexes) {
        this.dimFieldIndexes = dimFieldIndexes;
    }
}
