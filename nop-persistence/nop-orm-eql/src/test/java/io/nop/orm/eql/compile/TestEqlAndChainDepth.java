/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.compile;

import io.nop.commons.type.StdSqlType;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.impl.DialectImpl;
import io.nop.dao.dialect.model.DialectFeatures;
import io.nop.dao.dialect.model.DialectModel;
import io.nop.orm.eql.ICompiledSql;
import io.nop.orm.eql.IEqlAstTransformer;
import io.nop.orm.eql.compile.TestEqlAndChainDepth.TestCompileContext;
import io.nop.orm.eql.meta.EntityTableMeta;
import io.nop.orm.eql.meta.ISqlTableMeta;
import io.nop.orm.eql.sql.IAliasGenerator;
import io.nop.orm.eql.sql.SeqAliasGenerator;
import io.nop.orm.model.OrmColumnModel;
import io.nop.orm.model.OrmEntityModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 度量长 AND 条件链的编译深度承受能力（backlog 遗留项 1 的测量依据，见
 * ai-dev/backlog/orm-defect-audit-followups.md）。
 * <p>
 * 背景：BaseRule.g4 的 sqlExpr 左递归使解析期产出左深树，AbstractVisitor 按树深递归，
 * 理论上 N 项合取链的 visitor 递归深度为 N。本测试：
 * <ul>
 * <li>确定性下限断言：1000 项合取链在 1MB 栈线程内必须编译成功（现实业务谓词数远低于该值）</li>
 * <li>观测项：倍增探测首次 StackOverflowError 的 N 量级，打印记录（不做断言，避免跨环境抖动）</li>
 * </ul>
 */
public class TestEqlAndChainDepth {

    static IDialect dialect;
    static TestCompileContext context;

    @BeforeAll
    public static void init() {
        DialectModel model = new DialectModel();
        model.setFeatures(new DialectFeatures());
        model.setReservedKeywords(Collections.emptySet());
        model.setSqls(new io.nop.dao.dialect.model.DialectSqls());
        model.setSqlDataTypes(Collections.emptyList());

        io.nop.dao.dialect.model.SqlNativeFunctionModel currentTimestamp = new io.nop.dao.dialect.model.SqlNativeFunctionModel();
        currentTimestamp.setName("current_timestamp");
        currentTimestamp.setHasParenthesis(false);
        currentTimestamp.setReturnType(StdSqlType.TIMESTAMP);
        model.setFunctions(Collections.singletonList(currentTimestamp));

        dialect = new DialectImpl(model);

        context = new TestCompileContext();
        context.entities.put("AppUser", entity("AppUser", "APP_USER", "spaceA",
                col("id", "SID", 1, true), col("name", "NAME", 2, false)));
    }

    static String andChain(int n) {
        StringBuilder sb = new StringBuilder("select o.id from AppUser o where ");
        for (int i = 0; i < n; i++) {
            if (i > 0)
                sb.append(" and ");
            sb.append("o.name = 'v").append(i).append("'");
        }
        return sb.toString();
    }

    /** 在固定 1MB 栈的线程内编译，返回是否发生 StackOverflowError */
    static boolean compileOverflows(final int n) throws InterruptedException {
        AtomicBoolean overflow = new AtomicBoolean(false);
        Thread t = new Thread(null, () -> {
            try {
                new EqlCompiler().compile("test", andChain(n), context);
            } catch (StackOverflowError e) {
                overflow.set(true);
            }
        }, "eql-depth-probe", 1L << 20);
        t.start();
        t.join();
        return overflow.get();
    }

    @Test
    public void testAndChain1000CompilesOn1MbStack() throws InterruptedException {
        assertTrue(!compileOverflows(1000), "1000 项 AND 链在 1MB 栈下必须编译成功");
        ICompiledSql compiled = new EqlCompiler().compile("test", andChain(1000), context);
        assertNotNull(compiled);
        String sql = compiled.getSql().getText();
        assertTrue(sql.contains("APP_USER"), sql.substring(0, Math.min(80, sql.length())));
    }

    @Test
    public void testMeasureFirstOverflowThreshold() throws InterruptedException {
        int lastOk = 0;
        int firstOverflow = -1;
        for (int n = 2000; n <= 1_048_576; n *= 2) {
            if (compileOverflows(n)) {
                firstOverflow = n;
                break;
            }
            lastOk = n;
        }
        System.out.println("TestEqlAndChainDepth.measure: lastOk=" + lastOk
                + (firstOverflow > 0 ? ", firstOverflowAt=" + firstOverflow : ", noOverflowUpTo=" + lastOk));
        // 只断言一个宽松的观测面：2000 项以内不应溢出（低于此值才可能触及现实业务）
        assertTrue(!compileOverflows(2000), "2000 项 AND 链在 1MB 栈下应可编译");
    }

    // ---- 以下为与 TestEqlCompileSql 相同的最小自包含 harness ----

    static OrmColumnModel col(String name, String code, int propId, boolean primary) {
        OrmColumnModel c = new OrmColumnModel();
        c.setName(name);
        c.setCode(code);
        c.setPropId(propId);
        c.setStdSqlType(StdSqlType.VARCHAR);
        c.setPrimary(primary);
        return c;
    }

    static OrmEntityModel entity(String name, String tableName, String querySpace, OrmColumnModel... cols) {
        OrmEntityModel m = new OrmEntityModel();
        m.setName(name);
        m.setTableName(tableName);
        m.setQuerySpace(querySpace);
        m.setColumns(new ArrayList<>(Arrays.asList(cols)));
        m.init();
        return m;
    }

    static class TestCompileContext implements ISqlCompileContext {
        final Map<String, OrmEntityModel> entities = new HashMap<>();
        IDialect effectiveDialect = dialect;

        @Override
        public boolean isDisableLogicalDelete() {
            return false;
        }

        @Override
        public boolean isAllowUnderscoreName() {
            return false;
        }

        @Override
        public boolean isEnableFilter() {
            return false;
        }

        @Override
        public IEqlAstTransformer getAstTransformer() {
            return null;
        }

        @Override
        public IDialect getDialectForQuerySpace(String querySpace) {
            return effectiveDialect;
        }

        @Override
        public ISqlTableMeta resolveEntityTableMeta(String entityName) {
            OrmEntityModel m = entities.get(entityName);
            if (m == null)
                return null;
            return new EntityTableMeta(m, null, getDialectForQuerySpace(m.getQuerySpace()));
        }

        @Override
        public IAliasGenerator getAliasGenerator() {
            return new SeqAliasGenerator();
        }
    }
}
