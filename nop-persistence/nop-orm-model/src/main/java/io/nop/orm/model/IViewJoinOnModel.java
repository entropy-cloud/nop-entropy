package io.nop.orm.model;

import io.nop.api.core.util.ISourceLocationGetter;

public interface IViewJoinOnModel extends ISourceLocationGetter {
    String getLeftProp();

    Object getLeftPropValue();

    String getRightProp();

    Object getRightPropValue();

}
