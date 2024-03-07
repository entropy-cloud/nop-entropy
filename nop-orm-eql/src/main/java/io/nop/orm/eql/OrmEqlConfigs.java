package io.nop.orm.eql;

import io.nop.api.core.util.SourceLocation;

public interface OrmEqlConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(OrmEqlConfigs.class);
//    IConfigReference<Boolean> CFG_ALLOW_UNDERSCORE_NAME_IN_EQL =
//            varRef(s_loc, "nop.orm.allow-underscore-name-in-eql", Boolean.class, true);
}