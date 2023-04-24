package io.nop.core.resource.component;

import io.nop.api.core.util.IComponentModel;

public interface IComponentTransformer<S extends IComponentModel, R extends IComponentModel> {
    R transform(S model);
}
