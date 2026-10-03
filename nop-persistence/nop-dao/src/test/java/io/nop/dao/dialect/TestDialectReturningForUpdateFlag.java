/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.dialect;

import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.dialect.impl.DialectImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI3 第 1/2/3/4/5 项：`supportReturningForUpdate` 配置断言（XML → 模型 → accessor 运行时连通）。
 *
 * <p>真实语义（roadmap WI3 节头 + `dialect.xdef` javadoc）：仅指 `UPDATE` 上返回
 * <em>投影列表形式</em>（无 `INTO` 宿主变量）的 returning 子句，不是"该库有没有 RETURNING"。
 * 授权：G-4 逐项批准（`ai-dev/design/eql-cross-db-compat/2026-10-03-wi0-gate-adjudications.md`），
 * 逐项裁定与回滚单位见 plan 20 Phase 1。
 *
 * <p>编译期门行为（负向用例/正向对照）由 nop-orm-eql 的
 * {@code TestEqlUpdateReturningDialectGate} 以真实方言验证；两模块测试合起来构成
 * "XML 配置值 → features → accessor → 门行为"的运行时连通证明。
 */
public class TestDialectReturningForUpdateFlag {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    static IDialect dialectOf(String name) {
        IDialect dialect = DialectManager.instance().getDialect(name);
        assertTrue(dialect instanceof DialectImpl, () -> name + " must resolve to DialectImpl");
        return dialect;
    }

    /**
     * WI3 第 1 项：mysql `true` → `false`（MySQL 8.4 文法无 RETURNING；原误导注释
     * "mysql 8.0.14 和mariadb都支持returning语句"已随同一提交删除——该错误值的来源记录）。
     */
    @Test
    public void testMysqlExplicitFalse() {
        assertFalse(dialectOf("mysql").isSupportReturningForUpdate(),
                "MySQL has no UPDATE..RETURNING (projection-list form); see WI3 item 1");
    }

    /**
     * 继承涟漪：mysql 翻转经 x:extends 传播——mariadb（无本值配置，继承 mysql）、
     * mysql5.7（本就显式 false，双层证明）加载后有效值均为 false。
     * mariadb 的显式化（第 6 项，G-1 版本门形态）由 Phase 4 落地。
     */
    @Test
    public void testInheritanceRippleFromMysql() {
        assertFalse(dialectOf("mariadb").isSupportReturningForUpdate(),
                "mariadb inherits mysql's explicit false");
        assertFalse(dialectOf("mysql5.7").isSupportReturningForUpdate(),
                "mysql5.7 declares false itself and inherits mysql's false");
    }
}
