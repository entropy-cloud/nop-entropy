/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.sql;

import io.nop.api.core.util.Guard;
import io.nop.commons.text.marker.Markers;
import io.nop.commons.type.StdDataType;
import io.nop.dataset.binder.IDataParameterBinder;

public class TypedParamMarker extends Markers.ParamMarker {
    private final IDataParameterBinder binder;

    public TypedParamMarker(int pos, IDataParameterBinder binder, boolean masked) {
        super(pos, masked);
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
        value = binder.getStdDataType().convert(value);
        return new TypedValueMarker(begin, value, binder, isMasked());
    }
}
