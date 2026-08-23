package io.nop.sys.dao.coderule;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.time.ISysCalendar;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.seq.ISequenceGenerator;
import io.nop.sys.dao.entity.NopSysCodeRule;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * check 审计 review 整改（nop-sys P3-6）：编码规则缓存除 BizModel 失效钩子外必须有 TTL 兜底——
 * 钩子只覆盖本节点的管理端修改，多节点部署下其他节点依赖超时最终一致；上限/超时可配置
 * （nop.sys.code-rule.cache-max-size / cache-timeout）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestSysCodeRuleGeneratorCache extends JunitBaseTestCase {

    private static final String TTL_KEY = "nop.sys.code-rule.cache-timeout";
    private static Duration originalTtl;

    @Inject
    IDaoProvider daoProvider;

    @BeforeAll
    public static void shortenTtl() {
        Object raw = AppConfig.getConfigProvider().getConfigValue(TTL_KEY, null);
        originalTtl = raw instanceof Duration ? (Duration) raw : null;
        AppConfig.getConfigProvider().assignConfigValue(TTL_KEY, Duration.ofSeconds(1));
    }

    @AfterAll
    public static void restoreTtl() {
        AppConfig.getConfigProvider().assignConfigValue(TTL_KEY,
                originalTtl != null ? originalTtl : Duration.ofSeconds(60));
    }

    private SysCodeRuleGenerator newGenerator() {
        SysCodeRuleGenerator generator = new SysCodeRuleGenerator();
        generator.daoProvider = daoProvider;
        generator.setSysCalendar(new ISysCalendar() {
            @Override
            public boolean isWorkDay(LocalDate date) {
                return true;
            }

            @Override
            public LocalDate nextWorkDay(LocalDate date) {
                return date;
            }

            @Override
            public LocalDate getSysDate() {
                return LocalDate.now();
            }

            @Override
            public LocalDateTime getSysDateTime() {
                return LocalDateTime.now();
            }
        });
        generator.setCodeRule(new DefaultCodeRule());
        generator.setSequenceGenerator(new ISequenceGenerator() {
            @Override
            public long generateLong(String seqName, boolean useDefault) {
                return 1L;
            }
        });
        return generator;
    }

    private NopSysCodeRule saveRule(String name, String pattern, String seqName) {
        NopSysCodeRule rule = new NopSysCodeRule();
        rule.setName(name);
        rule.setDisplayName(name);
        rule.setCodePattern(pattern);
        rule.setSeqName(seqName);
        daoProvider.daoFor(NopSysCodeRule.class).saveEntityDirectly(rule);
        return rule;
    }

    /**
     * TTL 过期兜底：DB 直改规则（模拟另一节点/绕过 BizModel 钩子的修改）后，
     * TTL 内本节点仍用缓存 pattern，TTL 过期后重新加载新 pattern。
     */
    @Test
    public void testExpiredRuleReloadedAfterTtl() throws Exception {
        saveRule("ttl-rule", "A{@seq:3}", "seq-ttl");

        SysCodeRuleGenerator generator = newGenerator();
        assertEquals("A001", generator.generate("ttl-rule", null));

        // DB 直改 pattern（不走BizModel钩子）
        NopSysCodeRule rule = daoProvider.daoFor(NopSysCodeRule.class).findFirstByExample(savedExample("ttl-rule"));
        rule.setCodePattern("B{@seq:3}");
        daoProvider.daoFor(NopSysCodeRule.class).updateEntityDirectly(rule);

        // TTL 内：缓存命中，仍是旧pattern
        assertEquals("A001", generator.generate("ttl-rule", null), "within TTL the cached rule is used");

        Thread.sleep(1500);

        // TTL 过期后：重新加载，新pattern生效
        String regenerated = generator.generate("ttl-rule", null);
        assertTrue(regenerated.startsWith("B"),
                "after TTL expiry the modified rule must be reloaded, got: " + regenerated);
    }

    private NopSysCodeRule savedExample(String name) {
        NopSysCodeRule example = new NopSysCodeRule();
        example.setName(name);
        return example;
    }
}
