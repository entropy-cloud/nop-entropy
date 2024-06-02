package io.nop.orm.model;

import io.nop.api.core.annotations.core.Label;

public enum OrmRelationType {
    @Label("one-to-one")
    o2o,

    @Label("many-to-many")
    m2m,

    @Label("one-to-many")
    o2m,

    @Label("many-to-one")
    m2o
}
