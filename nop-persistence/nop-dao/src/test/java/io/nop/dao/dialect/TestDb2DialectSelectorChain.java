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
import io.nop.dao.dialect.model.DialectErrorCodeModel;
import io.nop.dao.dialect.model.DialectModel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI3 第 12 项：db2 selector 断链（先复现再修，按 WI0 快照三元组）。
 *
 * <p>断链事实（`ai-dev/audits/eql-wi0/dialect-capability-matrix.json` 的
 * `dialects.db2`：`loadable:false` + `nop.err.xlang.xdsl.missing-mandatory-child` +
 * `missingMandatoryChild: driverClassName`；selectors.DB2 → dialectName=db2 已注册）：
 * DB2 自动选择经 selector 命中 `db2` 后实例化失败。修法（裁定文档 §四）= 补 mandatory
 * 子节点 `<driverClassName>`/`<jdbcUrlPattern>`（JCC 坐标已经 IBM 官方文档核对），
 * 不下掉 selector。修复前本测试的端到端用例红（red-first 留痕于提交说明）。
 */
public class TestDb2DialectSelectorChain {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    /**
     * 复现前置：DB2 自动选择已注册（selector 存在，这正是断链的"半边"）。
     */
    @Test
    public void testDb2SelectorRegisteredForAutoSelection() {
        Map<String, List<DialectSelector>> selectors = DialectManager.instance().getDialectSelectors();
        List<DialectSelector> list = selectors.get("DB2");
        assertNotNull(list, "db2.selector.yaml must be registered (WI0 snapshot fact)");
        assertTrue(!list.isEmpty(), "DB2 selector list must be non-empty");
        assertEquals("db2", list.get(0).getDialectName(),
                "DB2 selector must map to dialect db2");
    }

    /**
     * 端到端断链复现/修复验证：selector 命中的 dialectName 必须能实例化
     * （修复前 db2.dialect.xml 缺 driverClassName 抛
     * `nop.err.xlang.xdsl.missing-mandatory-child`，本用例红）。
     */
    @Test
    public void testDb2DialectEndToEndLoadable() {
        IDialect dialect = DialectManager.instance().getDialect("db2");
        assertNotNull(dialect, "db2 dialect must load after mandatory-children fix (WI3 item 12)");
        assertTrue(dialect instanceof DialectImpl, "db2 must resolve to DialectImpl");
    }

    /**
     * mandatory 三件套齐全：dbProductNames（原有）+ driverClassName/jdbcUrlPattern（本 WI 补）。
     * JCC 坐标 = com.ibm.db2.jcc.DB2Driver / jdbc:db2://{host}:{port}/{db}（默认端口 50000）。
     */
    @Test
    public void testDb2MandatoryChildrenComplete() {
        DialectModel model = ((DialectImpl) DialectManager.instance().getDialect("db2"))
                .getDialectModel();
        assertTrue(model.getDbProductNames().contains("DB2"), "dbProductNames=DB2 must stay");
        assertEquals("com.ibm.db2.jcc.DB2Driver", model.getDriverClassName(),
                "DB2 JCC driver class (IBM Data Server Driver for JDBC/SQLJ)");
        assertNotNull(model.getJdbcUrlPattern(), "jdbcUrlPattern is mandatory");
        assertTrue(model.getJdbcUrlPattern().startsWith("jdbc:db2://"),
                () -> "DB2 type-4 url pattern, got: " + model.getJdbcUrlPattern());
    }

    /**
     * 既有 db2 errorCodes 表不因修复改变（`-803` 等由 DialectSQLExceptionTranslator 消费）。
     */
    @Test
    public void testDb2ErrorCodesUntouched() {
        DialectModel model = ((DialectImpl) DialectManager.instance().getDialect("db2"))
                .getDialectModel();
        List<DialectErrorCodeModel> errorCodes = model.getErrorCodes();
        assertNotNull(errorCodes);
        boolean hasDuplicateKey = errorCodes.stream()
                .anyMatch(e -> "nop.err.dao.sql.duplicate-key".equals(e.getName())
                        && e.getValues() != null && e.getValues().contains("-803"));
        assertTrue(hasDuplicateKey, "db2 duplicate-key -803 mapping must stay");
    }
}
