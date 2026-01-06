package io.nop.orm.model;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import static io.nop.api.core.config.AppConfig.varRef;

public interface OrmModelConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(OrmModelConfigs.class);

    @Description("是否检查实体模型的循环依赖")
    IConfigReference<Boolean> CFG_ORM_CHECK_ENTITY_LOOP_DEPENDENCY = varRef(s_loc, "nop.orm.check-entity-loop-dependency", Boolean.class, true);
}
