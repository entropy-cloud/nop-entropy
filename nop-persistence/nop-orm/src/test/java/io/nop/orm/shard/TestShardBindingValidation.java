/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.shard;

import io.nop.api.core.config.AppConfig;
import io.nop.commons.cache.CacheConfig;
import io.nop.commons.cache.LocalCacheProvider;
import io.nop.dao.DaoConfigs;
import io.nop.dao.seq.UuidSequenceGenerator;
import io.nop.dao.shard.ConfigShardSelector;
import io.nop.dao.shard.IShardSelector;
import io.nop.orm.AbstractJdbcTestCase;
import io.nop.api.core.exceptions.NopException;
import io.nop.orm.factory.DefaultOrmColumnBinderEnhancer;
import io.nop.orm.factory.OrmSessionFactoryBean;
import io.nop.orm.mock.MockBeanProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.nop.orm.OrmErrors.ARG_ENTITY_NAME;
import static io.nop.orm.OrmErrors.ERR_ORM_SHARD_STRATEGY_MISSING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2320 Phase 2：启动期分片绑定校验两态测试。
 *
 * <p>配置门控语义（显式裁定）：全局策略配置存在时，useShard实体未绑定策略且无
 * default-strategy → 启动fail-fast（ERR_ORM_SHARD_STRATEGY_MISSING，校验点在nop-orm层
 * LoadedOrmModel构造期）；无任何分片配置时不校验（既有useShard测试实体零变更——
 * 其余全部测试即该态的回归）。</p>
 */
public class TestShardBindingValidation extends AbstractJdbcTestCase {

    static final String SHARD_ENTITY = "test.entity.TestOrmShardTable";

    private IShardSelector selector;
    private OrmSessionFactoryBean factoryBean;

    private IShardSelector newSelector(String configJson) {
        ConfigShardSelector selector = new ConfigShardSelector();
        AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_ORM_SHARD, configJson);
        try {
            selector.init();
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_ORM_SHARD, null);
        }
        return selector;
    }

    private OrmSessionFactoryBean newFactoryBean(IShardSelector shardSelector) {
        OrmSessionFactoryBean factoryBean = new OrmSessionFactoryBean();
        factoryBean.setJdbcTemplate(jdbcTemplate);
        factoryBean.setBeanProvider(new MockBeanProvider());
        factoryBean.setGlobalCache(new LocalCacheProvider("global", CacheConfig.newConfig(1000)));
        factoryBean.setSequenceGenerator(new UuidSequenceGenerator());
        factoryBean.setColumnBinderEnhancer(new DefaultOrmColumnBinderEnhancer());
        if (shardSelector != null)
            factoryBean.setShardSelector(shardSelector);
        return factoryBean;
    }

    @AfterEach
    public void tearDownFactory() {
        if (factoryBean != null) {
            factoryBean.destroy();
            factoryBean = null;
        }
        AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_ORM_SHARD, null);
    }

    @Test
    public void testUnboundShardEntityFailsFastWhenConfigExists() {
        // 配置存在但TestOrmShardTable/TestOrmTable未绑定 → 模型加载即fail-fast
        selector = newSelector("{\"strategies\":{\"other\":{\"type\":\"mod\",\"shard-count\":2,"
                + "\"entities\":[\"no.such.Entity\"]}}}");
        factoryBean = newFactoryBean(selector);

        NopException e = assertThrows(NopException.class, factoryBean::init);
        assertEquals(ERR_ORM_SHARD_STRATEGY_MISSING.getErrorCode(), e.getErrorCode());
        assertTrue(e.getParams().get(ARG_ENTITY_NAME).toString().startsWith("test.entity.TestOrm"));
    }

    @Test
    public void testDefaultStrategySatisfiesBinding() {
        // default-strategy显式回落：所有useShard实体视为已绑定
        selector = newSelector("{\"strategies\":{\"any\":{\"type\":\"mod\",\"shard-count\":2,"
                + "\"entities\":[\"no.such.Entity\"]}},\"default-strategy\":\"any\"}");
        factoryBean = newFactoryBean(selector);

        factoryBean.init();
        // 模型加载成功（未抛ERR_ORM_SHARD_STRATEGY_MISSING）
        assertTrue(factoryBean.getObject().getOrmModel().getEntityModels().size() > 0);
    }

    @Test
    public void testNoConfigMeansNoValidation() {
        // 无任何分片配置：不校验（既有Empty行为），useShard实体照常加载
        factoryBean = newFactoryBean(null);

        factoryBean.init();
        assertTrue(factoryBean.getObject().getOrmModel().requireEntityModel(SHARD_ENTITY).isUseShard());
    }
}
