/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.dao;

import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.app.SimsCollege;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.DaoConfigs;
import io.nop.dao.DaoErrors;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.AbstractOrmTestCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.3-C5-001/C5-005（plan 2553 裁定 L）回归钉。C10-014/N4 真 H2 义务承载：本测试运行在
 * AbstractOrmTestCase 的真实 H2 内存库上（无 mock），钉两条产品契约——
 * <ul>
 * <li>C5-001：{@code ISqlExecutor.findAll} 遵守 {@code nop.dao.find-all-max-rows}（>0 时
 * 抽取器级上界，超限抛 ERR_DAO_FIND_ALL_ROW_LIMIT_EXCEEDED；≤0 不设上界）；</li>
 * <li>C5-005：{@code OrmEntityDao.findAllByQuery} 在 limit>0 时不再静默丢弃 limit，
 * 转发 findPage 语义（真实行数下推分页）。</li>
 * </ul>
 * 变异红：C5-001 擦除 maxRows 检查 → 超限不抛红；C5-005 恢复丢弃 limit → 返回全部行红。
 */
public class TestFindAllRowLimit extends AbstractOrmTestCase {

    private static final String FIND_THREE_SQL =
            "select o from io.nop.app.SimsCollege o where o.collegeId in ('2','3','4')";

    @AfterEach
    void resetFindAllMaxRows() {
        // 缺省值 0 = 不设上界档（现行语义），恢复配置面避免污染同 JVM 后续测试
        AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_DAO_FIND_ALL_MAX_ROWS, 0);
    }

    private void seedThreeColleges() {
        insertColleges(2, 4);
    }

    /**
     * C5-001 腿①：maxRows=2 时 3 行查询必须超限抛错（errorCode 与 rowCount/maxRows 参数留痕）。
     * 变异红：擦除抽取器级检查后 findAll 返回全部 3 行不抛 → 红。
     */
    @Test
    public void pin_findAllOverLimitThrows() {
        seedThreeColleges();
        AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_DAO_FIND_ALL_MAX_ROWS, 2);
        try {
            NopException err = assertThrows(NopException.class,
                    () -> orm().findAll(SQL.begin().sql(FIND_THREE_SQL).end()));
            assertEquals(DaoErrors.ERR_DAO_FIND_ALL_ROW_LIMIT_EXCEEDED.getErrorCode(), err.getErrorCode(),
                    "超限必须抛 ERR_DAO_FIND_ALL_ROW_LIMIT_EXCEEDED");
            assertNotNull(err.getParams(), "错误必须带参数留痕");
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_DAO_FIND_ALL_MAX_ROWS, 0);
        }

        // 上界恢复 ≤0 后同一查询返回全部行（不设上界档语义不回归）
        List<?> all = orm().findAll(SQL.begin().sql(FIND_THREE_SQL).end());
        assertEquals(3, all.size(), "maxRows<=0 时同一查询必须返回全部行");
    }

    /**
     * C5-001 腿②：maxRows=2 时 2 行查询恰在界内，正常返回（上界是硬顶不是偏置）。
     */
    @Test
    public void pin_findAllAtLimitReturnsAllRows() {
        seedThreeColleges();
        AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_DAO_FIND_ALL_MAX_ROWS, 2);
        try {
            List<?> rows = orm().findAll(SQL.begin()
                    .sql("select o from io.nop.app.SimsCollege o where o.collegeId in ('2','3')").end());
            assertEquals(2, rows.size(), "恰在 maxRows 界内的查询必须正常返回全部行");
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_DAO_FIND_ALL_MAX_ROWS, 0);
        }
    }

    /**
     * C5-005 腿：findAllByQuery 的 limit>0 必须真实生效（转发 findPage 语义）。
     * 变异红：恢复静默丢弃 limit 的旧行为 → 返回 3 行 → 红。
     */
    @Test
    public void pin_findAllByQueryRespectsLimit() {
        seedThreeColleges();
        IEntityDao<SimsCollege> dao = daoProvider().daoFor(SimsCollege.class);

        QueryBean query = new QueryBean();
        query.setSourceName(SimsCollege.class.getName());
        query.setLimit(1);
        List<SimsCollege> limited = dao.findAllByQuery(query);
        assertEquals(1, limited.size(), "limit=1 时即使存在 3 行也必须只返回 1 行");

        QueryBean query2 = new QueryBean();
        query2.setSourceName(SimsCollege.class.getName());
        query2.setLimit(2);
        List<SimsCollege> limited2 = dao.findAllByQuery(query2);
        assertEquals(2, limited2.size(), "limit=2 时必须只返回 2 行");
        assertTrue(limited2.size() < 3, "limit 必须真实约束返回行数（不得静默丢弃）");
    }
}
