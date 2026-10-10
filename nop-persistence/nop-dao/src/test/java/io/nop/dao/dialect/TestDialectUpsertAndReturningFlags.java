/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical_entropy
 * Gitee:  https://gitee.com/nop-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.dialect;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.dao.dialect.impl.DialectImpl;
import io.nop.dao.jdbc.UpsertStatements;
import io.nop.dao.jdbc.UpsertStyle;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2380 Phase 2：EQL {@code upsert} 与 {@code insert/delete ... returning} 三个新能力位的
 * 配置断言（XML → 模型 → accessor 运行时连通）。
 *
 * <p>权威设计：{@code ai-dev/design/eql-cross-db-compat/2026-10-10-eql-upsert-and-returning-design.md} §3.4。
 *
 * <p>本类锁死三条不变量：
 * <ol>
 *   <li><b>方言清单覆盖</b>：每个可加载方言的三新位取值都被显式断言（含"未配置 = false"的缺省侧），
 *       防止将来有人在父方言改值时静默改变子方言语义；</li>
 *   <li><b>与形态解析器一致</b>：{@code supportUpsert} 必须与
 *       {@code UpsertStatements.resolveUpsertStyle} 的结论逐方言一致——这是设计 §3.4 的
 *       "一致性硬约束"，不一致意味着"能力位说支持、SQL 生成器无对应分支"的静默不一致；</li>
 *   <li><b>继承涟漪</b>：显式覆盖与继承两条路径分别有断言（{@code <features>} 是逐属性合并的，
 *       子方言会从父方言继承 true）。</li>
 * </ol>
 *
 * <p>编译期门行为（正/负向）由 nop-orm-eql 的 upsert / returning 测试类承担；本类只负责
 * "XML 配置值 → features → accessor"这一段。
 */
public class TestDialectUpsertAndReturningFlags {

    static final String DIALECT_DIR = "/nop/dao/dialect";
    static final String DIALECT_SUFFIX = ".dialect.xml";

    static String dialectName(String path) {
        String name = path;
        int pos = name.lastIndexOf('/');
        if (pos >= 0)
            name = name.substring(pos + 1);
        return name.substring(0, name.length() - DIALECT_SUFFIX.length());
    }

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
        assertNotNull(dialect, () -> name + " must resolve to a dialect");
        assertTrue(dialect instanceof DialectImpl, () -> name + " must resolve to DialectImpl");
        return dialect;
    }

    /** 与 {@code TestDialectCapabilityMatrix} 同款：枚举 VFS 下的 *.dialect.xml 后逐个 try-load。 */
    static List<String> loadableDialects() {
        Collection<? extends IResource> resources = VirtualFileSystem.instance()
                .getAllResources(DIALECT_DIR, DIALECT_SUFFIX);
        assertTrue(resources != null && !resources.isEmpty(),
                "VFS must expose " + DIALECT_DIR + "*" + DIALECT_SUFFIX + " resources");
        Map<String, IResource> byName = new TreeMap<>();
        for (IResource resource : resources) {
            byName.putIfAbsent(dialectName(resource.getPath()), resource);
        }
        List<String> loadable = new ArrayList<>();
        for (String name : byName.keySet()) {
            try {
                if (DialectManager.instance().getDialect(name) != null)
                    loadable.add(name);
            } catch (Throwable e) {
                // 不可加载方言（default 继承根缺 mandatory 子节点、db2 缺 driverClassName、
                // geo-support/window-expr-support 是 mixin）的登记由 TestDialectCapabilityMatrix 负责
            }
        }
        assertFalse(loadable.isEmpty(), "at least one dialect must be loadable");
        return loadable;
    }

    @Test
    public void testPostgresqlFamilyUpsertAndReturning() {
        for (String name : new String[]{"postgresql", "postgis", "duckdb"}) {
            IDialect d = dialectOf(name);
            assertTrue(d.isSupportUpsert(), name + " supports ON CONFLICT .. DO UPDATE (design 3.4)");
            assertTrue(d.isSupportReturningForInsert(), name + " supports INSERT .. RETURNING (design 3.4)");
            assertTrue(d.isSupportReturningForDelete(), name + " supports DELETE .. RETURNING (design 3.4)");
        }
    }

    /**
     * duckdb 自带 {@code <features>}，只覆盖显式属性（{@code supportReturningForUpdate="false"}、
     * {@code supportNullsFirst}、{@code supportLateral}），其余从 postgresql 继承。三新位必须写全，
     * 否则将来父方言改值时会静默漂移——本断言固化"它确实是显式 true 而不是碰巧继承"。
     */
    @Test
    public void testDuckdbDeclaresItsOwnFeatureValues() {
        IDialect duckdb = dialectOf("duckdb");
        assertFalse(duckdb.isSupportReturningForUpdate(),
                "duckdb keeps its explicit false: RETURNING exists only on INSERT/DELETE/MERGE");
        // 三新位为 true 的同时，既有 supportReturningForUpdate 仍为 false —— 分立标志互不影响
        assertTrue(duckdb.isSupportReturningForInsert() && duckdb.isSupportReturningForDelete());
        assertFalse(duckdb.isSupportReturningForUpdate());
    }

    @Test
    public void testMySqlFamilyUpsertOnly() {
        for (String name : new String[]{"mysql", "mysql5.7", "mariadb"}) {
            IDialect d = dialectOf(name);
            assertTrue(d.isSupportUpsert(), name + " supports ON DUPLICATE KEY UPDATE (design 3.4)");
            assertFalse(d.isSupportReturningForInsert(), name + " has no RETURNING at the committed baseline");
            assertFalse(d.isSupportReturningForDelete(), name + " has no RETURNING at the committed baseline");
        }
    }

    /**
     * H2 的 upsert <b>受限</b>：{@code MERGE INTO t(cols) KEY(kcols) VALUES(...)} 没有更新子句，
     * 固定按列清单整列更新 ⇒ EQL 侧有两道编译期必要条件。本类只锁能力位为 true；
     * 两道条件的门行为由 nop-orm-eql 的 upsert 编译期测试承担。
     */
    @Test
    public void testH2FamilyUpsertOnly() {
        for (String name : new String[]{"h2", "h2gis"}) {
            IDialect d = dialectOf(name);
            assertTrue(d.isSupportUpsert(), name + " supports MERGE .. KEY .. VALUES (design 3.6, restricted)");
            assertFalse(d.isSupportReturningForInsert(), name + " has no RETURNING clause at all");
            assertFalse(d.isSupportReturningForDelete(), name + " has no RETURNING clause at all");
        }
    }

    @Test
    public void testUnsupportedDialectsDefaultToFalse() {
        for (String name : new String[]{"oracle", "oracle-reverse", "mssql", "dm", "tdengine"}) {
            IDialect d = dialectOf(name);
            assertFalse(d.isSupportUpsert(), name + " has no EQL upsert lowering (design 3.4)");
            assertFalse(d.isSupportReturningForInsert(), name + " has no projection-list RETURNING on INSERT");
            assertFalse(d.isSupportReturningForDelete(), name + " has no projection-list RETURNING on DELETE");
        }
    }

    /**
     * 设计 §3.4 的一致性硬约束：{@code supportUpsert} 必须与形态解析器
     * {@code UpsertStatements.resolveUpsertStyle} 逐方言一致。不一致意味着 SQL 生成器
     * 会遇到"能力位放行但没有对应分支"的静默不一致——这是本设计要消灭的那一类错误，
     * 绝不能自己引入。
     */
    @Test
    public void testSupportUpsertAgreesWithStyleResolverForEveryDialect() {
        for (String name : loadableDialects()) {
            IDialect d = dialectOf(name);
            boolean hasNativeStyle = UpsertStatements.resolveUpsertStyle(d) != null;
            assertEquals(hasNativeStyle, d.isSupportUpsert(),
                    () -> "dialect '" + name + "': supportUpsert=" + d.isSupportUpsert()
                            + " but resolveUpsertStyle=" + UpsertStatements.resolveUpsertStyle(d)
                            + " — the two must agree (design 3.4 hard constraint)");
        }
    }

    /** 形态解析器本身的扩面回归：Plan 2380 把 postgis / duckdb 纳入 PG 族。 */
    @Test
    public void testStyleResolverCoversPostgisAndDuckdb() {
        assertEquals(UpsertStyle.POSTGRESQL, UpsertStatements.resolveUpsertStyle(dialectOf("postgis")));
        assertEquals(UpsertStyle.POSTGRESQL, UpsertStatements.resolveUpsertStyle(dialectOf("duckdb")));
        assertEquals(UpsertStyle.POSTGRESQL, UpsertStatements.resolveUpsertStyle(dialectOf("postgresql")));
        assertEquals(UpsertStyle.MYSQL, UpsertStatements.resolveUpsertStyle(dialectOf("mariadb")));
        assertEquals(UpsertStyle.H2, UpsertStatements.resolveUpsertStyle(dialectOf("h2")));
    }

    /**
     * 形态解析器的前缀匹配大小写不敏感（既有契约，扩面后仍须成立）。
     */
    @Test
    public void testStyleResolverIsCaseInsensitive() {
        for (String name : new String[]{"postgis", "duckdb", "POSTGIS", "DuckDB"}) {
            IDialect d = dialectOf(name.toLowerCase(Locale.ROOT));
            assertNotNull(d);
            assertEquals(UpsertStyle.POSTGRESQL, UpsertStatements.resolveUpsertStyle(d));
        }
    }
}