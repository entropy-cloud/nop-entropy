package io.nop.spring.delta.beans;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.spring.delta.mybatis.NopMybatisSessionFactoryCustomizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.SimpleBeanDefinitionRegistry;
import org.springframework.core.type.AnnotationMetadata;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 4.4-C4-004（plan 2562 裁定 G）载体：NopBeansAutoConfiguration.NopBeansRegistrar 与
 * NopMybatisSessionFactoryCustomizer（F3 孪生）在 CoreInitialization 未初始化时必须
 * typed 拒绝（NopException / nop.err.spring-delta.core-not-initialized），
 * 不再静默空 discovery。
 *
 * <p><b>RP-3 反向钉 + 隔离设计</b>：本模块此前没有 src/test 树，surefire fork 内
 * CoreInitialization 静态状态天然保持未初始化——本测试类从不调用
 * CoreInitialization.initialize/reinitialize，也从不预初始化平台（与同 fork 其他测试
 * 无共享可变平台状态的竞争面；模块内亦无其他测试类改写该静态状态）。断言前置
 * {@code !CoreInitialization.isInitialized()} 即是对该隔离前提的守护：若未来有测试
 * 顺序污染状态，此处先失败暴露，而不是让守卫断言假绿。独立 fork 在本模块 surefire
 * 配置下不可用（无 fork 隔离配置项），故采用上述"未初始化前提自校验"形态。</p>
 */
public class TestNopSpringDeltaInitGuard {

    @BeforeEach
    public void verifyUninitializedPremise() {
        // RP-3 隔离前提自校验（见类注释）：同 fork 内不允许任何测试预初始化平台
        assertFalse(CoreInitialization.isInitialized(),
                "isolation premise broken: CoreInitialization must stay uninitialized in this fork");
    }

    @Test
    public void testRegistrarRejectsWithoutInitialization() {
        NopBeansAutoConfiguration.NopBeansRegistrar registrar = new NopBeansAutoConfiguration.NopBeansRegistrar();
        BeanDefinitionRegistry registry = new SimpleBeanDefinitionRegistry();

        NopException e = assertThrows(NopException.class,
                () -> registrar.registerBeanDefinitions(AnnotationMetadata.introspect(Object.class), registry),
                "registrar must typed-reject when CoreInitialization is not initialized");

        assertTrue(e.getErrorCode().contains("spring-delta.core-not-initialized"),
                "rejection must carry the typed spring-delta error code, got: " + e.getErrorCode());
    }

    @Test
    public void testMybatisCustomizerRejectsWithoutInitialization() {
        NopMybatisSessionFactoryCustomizer customizer = new NopMybatisSessionFactoryCustomizer();
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();

        NopException e = assertThrows(NopException.class,
                () -> customizer.customize(factoryBean),
                "mybatis customizer must typed-reject when CoreInitialization is not initialized");

        assertTrue(e.getErrorCode().contains("spring-delta.core-not-initialized"),
                "rejection must carry the typed spring-delta error code, got: " + e.getErrorCode());
    }
}
