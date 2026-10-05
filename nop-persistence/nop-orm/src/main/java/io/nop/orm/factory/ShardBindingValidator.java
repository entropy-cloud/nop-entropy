/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.factory;

import io.nop.api.core.exceptions.NopException;
import io.nop.dao.shard.IShardSelector;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IOrmModel;

import static io.nop.orm.OrmErrors.ARG_ENTITY_NAME;
import static io.nop.orm.OrmErrors.ERR_ORM_SHARD_STRATEGY_MISSING;

/**
 * 分片绑定启动期校验（plan 2320 Phase 2，从Phase 1移入nop-orm层——nop-dao不依赖
 * nop-orm-model）。校验点在LoadedOrmModel构造期（模型加载即启动期）。
 *
 * <p><b>配置门控语义（显式裁定）</b>：仅当全局策略配置存在（{@code hasStrategyConfig()}）
 * 时才校验：useShard实体未绑定策略（显式绑定或default-strategy回落）→ fail-fast
 * {@code ERR_ORM_SHARD_STRATEGY_MISSING}。<b>无任何分片配置时不校验</b>——未配置分片
 * 体系的部署维持既有Empty行为，保证既有useShard测试实体零变更。</p>
 *
 * <p>校验错误码归OrmErrors（nop-orm层），与配置解析错误（DaoErrors，nop-dao层）分层。</p>
 */
public class ShardBindingValidator {

    private ShardBindingValidator() {
    }

    public static void validate(IShardSelector selector, IOrmModel ormModel) {
        if (selector == null || !selector.hasStrategyConfig())
            return;

        for (IEntityModel entityModel : ormModel.getEntityModels()) {
            if (entityModel.isUseShard() && !selector.isEntityBound(entityModel.getName()))
                throw new NopException(ERR_ORM_SHARD_STRATEGY_MISSING)
                        .param(ARG_ENTITY_NAME, entityModel.getName());
        }
    }
}
