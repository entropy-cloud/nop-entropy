/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.dialect;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.dao.dialect.impl.DialectImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI3 第 7 项：mysql5.7 移除继承自 window-expr-support mixin 的 10 个窗口函数
 * （MySQL 5.7 无窗口函数，窗口函数自 8.0 引入）。
 *
 * <p>裁定（`ai-dev/design/eql-cross-db-compat/2026-10-04-wi3-phase0-adjudications.md` §三）：
 * 候选 B 内联 `x:override="remove"`（仓库 house style 先例：duckdb 跨两跳 remove 25 个
 * st_*），不用新中间方言。⚠️ mariadb 也继承该 mixin，但 MariaDB 10.2+ 确有窗口函数
 * ⇒ 不得顺手移除（roadmap 明令），本类同时锁定 mysql/mariadb 仍含全量 10 个。
 *
 * <p>泄漏防护（候选 B 唯一弱点的机制化拒绝）：{@link #testMixinContributionGuard()}
 * 在运行时 live 解析 mixin 的函数声明集，断言 mysql5.7 加载后的函数表与该集合交集为空
 * ——mixin 未来新增任何函数会立即让本测试转红，强迫显式补 remove 或重新裁定。
 */
public class TestMysql57WindowFunctionsRemoved {
    static final List<String> WINDOW_FUNCTIONS = List.of(
            "rank", "dense_rank", "row_number", "lead", "lag",
            "first_value", "last_value", "nth_value", "percent_rank", "cume_dist");

    static final String MIXIN_PATH = "/nop/dao/dialect/window-expr-support.dialect.xml";

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    static DialectImpl dialectOf(String name) {
        IDialect dialect = DialectManager.instance().getDialect(name);
        assertTrue(dialect instanceof DialectImpl, () -> name + " must resolve to DialectImpl");
        return (DialectImpl) dialect;
    }

    /** live 解析 mixin 文件声明的函数名（不经 DialectModel，防守卫被同源合并逻辑绕过）。 */
    static List<String> mixinDeclaredFunctions() {
        IResource resource = VirtualFileSystem.instance().getResource(MIXIN_PATH);
        assertNotNull(resource, () -> "mixin not found: " + MIXIN_PATH);
        XNode root = XNodeParser.instance().parseFromResource(resource);
        XNode functions = root.childByTag("functions");
        assertNotNull(functions, "mixin must declare a <functions> block");
        List<String> names = new ArrayList<>();
        for (XNode child : functions.getChildren()) {
            String name = child.attrText("name");
            if (name != null)
                names.add(name);
        }
        return names;
    }

    /**
     * WI3 第 7 项主断言：mysql5.7 加载后函数表不含 10 个窗口函数
     * （红→绿留痕：改动前 mysql5.7 经继承含全量 10 个，见 WI0 矩阵快照）。
     */
    @Test
    public void testMysql57FunctionTableHasNoWindowFunctions() {
        DialectImpl mysql57 = dialectOf("mysql5.7");
        for (String fn : WINDOW_FUNCTIONS) {
            assertNull(mysql57.getFunction(fn),
                    () -> "mysql5.7 must not register window function '" + fn
                            + "' (MySQL 5.7 has no window functions; WI3 item 7)");
        }
    }

    /**
     * 防「顺手修正」回归：mysql 与 mariadb（10.2+ 确有窗口函数）必须仍含全量 10 个。
     */
    @Test
    public void testMysqlAndMariadbKeepWindowFunctions() {
        for (String name : new String[]{"mysql", "mariadb"}) {
            DialectImpl dialect = dialectOf(name);
            for (String fn : WINDOW_FUNCTIONS) {
                assertNotNull(dialect.getFunction(fn),
                        () -> name + " must keep window function '" + fn
                                + "' (do not copy the mysql5.7 removal; WI3 item 7)");
            }
        }
    }

    /**
     * 候选 B 泄漏守卫：mysql5.7 的函数表不得含 mixin 声明的任何函数——
     * 动态取 mixin 声明集而非硬编码 10 个名字，mixin 演进自动被覆盖。
     */
    @Test
    public void testMixinContributionGuard() {
        List<String> mixinFunctions = mixinDeclaredFunctions();
        assertTrue(!mixinFunctions.isEmpty(), "mixin function declarations must be non-empty");
        DialectImpl mysql57 = dialectOf("mysql5.7");
        for (String fn : mixinFunctions) {
            assertNull(mysql57.getFunction(fn),
                    () -> "window-expr-support mixin declares '" + fn
                            + "' but mysql5.7 must not inherit it — add an explicit "
                            + "x:override=\"remove\" to mysql5.7.dialect.xml or re-adjudicate WI3 item 7");
        }
    }

    /**
     * WI3 第 8 项（已完成记录）回归断言：postgresql/duckdb/postgis 的窗口函数仍注册
     * （防本 Phase 意外破坏继承链）。
     */
    @Test
    public void testPostgresFamilyWindowFunctionsStillRegistered() {
        for (String name : new String[]{"postgresql", "duckdb", "postgis"}) {
            DialectImpl dialect = dialectOf(name);
            for (String fn : WINDOW_FUNCTIONS) {
                assertNotNull(dialect.getFunction(fn),
                        () -> name + " must keep window function '" + fn + "' (WI3 item 8 regression)");
            }
        }
    }
}
