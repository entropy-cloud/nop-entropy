/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.sql;

import io.nop.api.core.util.Guard;
import io.nop.commons.text.marker.Markers;
import io.nop.commons.type.StdDataType;
import io.nop.dataset.binder.IDataParameterBinder;

public class TypedValueMarker extends Markers.ValueMarker {
    private final IDataParameterBinder binder;

    public TypedValueMarker(int pos, Object value, IDataParameterBinder binder, boolean masked) {
        super(pos, value, masked);
        this.binder = Guard.notNull(binder, "dataParameterBinder");
    }

    public StdDataType getStdDataType() {
        return binder.getStdDataType();
    }

    public IDataParameterBinder getBinder() {
        return binder;
    }

    @Override
    protected Markers.ValueMarker newValueMarker(int begin, int end, Object value) {
        return new TypedValueMarker(begin, value, binder, isMasked());
    }
}
