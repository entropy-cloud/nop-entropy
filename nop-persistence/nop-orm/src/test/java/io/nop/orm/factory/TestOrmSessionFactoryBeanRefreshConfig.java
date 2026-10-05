/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.factory;

import io.nop.api.core.config.AppConfig;
import io.nop.commons.cache.CacheConfig;
import io.nop.commons.cache.LocalCacheProvider;
import io.nop.dao.seq.UuidSequenceGenerator;
import io.nop.orm.AbstractJdbcTestCase;
import io.nop.orm.OrmConfigs;
import io.nop.orm.mock.MockBeanProvider;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证refreshConfig对global cache配置的热刷新真实生效。
 * <p>
 * 历史缺陷：refreshConfig以getGlobalCache()==null为守卫，而IoC装配下@Bean字段经
 * @Inject setter注入恒非null，导致该分支在所有IoC部署中永不执行，
 * nop.orm.entity-global-cache.size/timeout的配置热刷新静默失效。
 * 本测试通过setGlobalCache模拟IoC注入路径（与AbstractOrmTestCase的装配方式一致）。
 */
public class TestOrmSessionFactoryBeanRefreshConfig extends AbstractJdbcTestCase {

    private OrmSessionFactoryBean newFactoryBean(LocalCacheProvider provider) {
        OrmSessionFactoryBean factoryBean = new OrmSessionFactoryBean();
        factoryBean.setJdbcTemplate(jdbcTemplate);
        factoryBean.setBeanProvider(new MockBeanProvider());
        factoryBean.setGlobalCache(provider);
        factoryBean.setSequenceGenerator(new UuidSequenceGenerator());
        factoryBean.setColumnBinderEnhancer(new DefaultOrmColumnBinderEnhancer());
        factoryBean.init();
        return factoryBean;
    }

    @Test
    public void testRefreshConfigAppliesGlobalCacheSizeAndTimeout() {
        CacheConfig cfg = CacheConfig.newConfig(1000);
        cfg.setExpireAfterWrite(Duration.ofSeconds(60));
        LocalCacheProvider provider = new LocalCacheProvider("test-global-cache", cfg);

        OrmSessionFactoryBean factoryBean = newFactoryBean(provider);
        try {
            assertEquals(1000, provider.getConfig().getMaximumSize());

            // 模拟IoC装配路径下的配置热刷新
            AppConfig.getConfigProvider().updateConfigValue(OrmConfigs.CFG_ENTITY_GLOBAL_CACHE_SIZE, 777);
            AppConfig.getConfigProvider().updateConfigValue(OrmConfigs.CFG_ENTITY_GLOBAL_CACHE_TIMEOUT,
                    Duration.ofSeconds(120));
            try {
                factoryBean.refreshConfig();
            } finally {
                // 还原全局配置，避免影响其他测试
                AppConfig.getConfigProvider().updateConfigValue(OrmConfigs.CFG_ENTITY_GLOBAL_CACHE_SIZE,
                        OrmConfigs.CFG_ENTITY_GLOBAL_CACHE_SIZE.getDefaultValue());
                AppConfig.getConfigProvider().updateConfigValue(OrmConfigs.CFG_ENTITY_GLOBAL_CACHE_TIMEOUT,
                        OrmConfigs.CFG_ENTITY_GLOBAL_CACHE_TIMEOUT.getDefaultValue());
            }

            // 修复前：守卫恒假，配置不变（仍为1000/60s），此处断言失败
            assertEquals(777, provider.getConfig().getMaximumSize());
            assertEquals(Duration.ofSeconds(120), provider.getConfig().getExpireAfterWrite());
        } finally {
            factoryBean.destroy();
        }
    }

    @Test
    public void testRefreshConfigKeepsPlanCacheRefreshWorking() {
        CacheConfig cfg = CacheConfig.newConfig(1000);
        LocalCacheProvider provider = new LocalCacheProvider("test-global-cache-2", cfg);

        OrmSessionFactoryBean factoryBean = newFactoryBean(provider);
        try {
            AppConfig.getConfigProvider().updateConfigValue(OrmConfigs.CFG_QUERY_PLAN_CACHE_SIZE, 555);
            try {
                factoryBean.refreshConfig();
            } finally {
                AppConfig.getConfigProvider().updateConfigValue(OrmConfigs.CFG_QUERY_PLAN_CACHE_SIZE,
                        OrmConfigs.CFG_QUERY_PLAN_CACHE_SIZE.getDefaultValue());
            }

            assertEquals(555, factoryBean.getObject().getQueryPlanCache().getConfig().getMaximumSize());
        } finally {
            factoryBean.destroy();
        }
    }
}
