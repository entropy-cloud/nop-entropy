/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.session;

import io.nop.app.SimsCollege;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.AbstractOrmTestCase;
import io.nop.orm.IOrmSession;
import io.nop.orm.model.IEntityModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * plan 2517 R2-14 红⑫（C3-005/C2-006，C10-009 钉错相位注记履行）回归钉。
 * <p>新钉注入相位 = batchActionQueue.flush 失败（dirty=false 之后）——与既有装载相位钉
 * {@code TestFlushFailure#testFlushFailureClearsFlushVisiting}（失败注入在装载队列、dirty=false 之前）
 * 并存，语义对照留痕；旧钉本体零回改。</p>
 * <p>缺陷态：dirty=false 先于 batchActionQueue.flush()，批 DML 失败后重试 flush 被 {@code !dirty}
 * 短路 no-op，修改永不落库（半个成功残留）→ 红；修复后 dirty=false 移至 flush 成功之后，
 * 重试 flush 真正落库 → 绿。</p>
 */
public class TestFlushFailureCra2517 extends AbstractOrmTestCase {

    @Test
    public void testCra2517_C3_005_flushRetryAfterBatchDmlFailurePersists() {
        IEntityModel collegeModel = sessionFactory.getOrmModel().requireEntityModel(SimsCollege.class.getName());
        String tableName = collegeModel.getTableName();

        orm().runInSession((IOrmSession session) -> {
            IEntityDao<SimsCollege> dao = daoProvider().daoFor(SimsCollege.class);
            SimsCollege college = dao.loadEntityById("1");
            college.setCollegeName("cra2517-retry");

            // 失败注入相位：batchActionQueue.flush 执行 DML 时（dirty 标记生命周期内、装载相位之后）。
            // 用 RENAME（保留数据）而非 DROP：批 DML 因表对象缺失失败，重试时底行仍在、条件 UPDATE 可命中
            jdbcTemplate.executeUpdate(SQL.begin()
                    .sql("alter table " + tableName + " rename to SIMS_COLLEGE_BK2517").end());

            Throwable error = null;
            try {
                session.flush();
            } catch (Throwable e) {
                error = e;
            }
            assertNotNull(error, "batch DML 相位失败必须抛出");

            // 恢复表后同一 session 重试 flush（缺陷态被 !dirty 短路 no-op → 修改丢失）
            jdbcTemplate.executeUpdate(SQL.begin()
                    .sql("alter table SIMS_COLLEGE_BK2517 rename to " + tableName).end());
            session.flush();
            return null;
        });

        orm().runInSession((IOrmSession session) -> {
            SimsCollege college = daoProvider().daoFor(SimsCollege.class).loadEntityById("1");
            assertEquals("cra2517-retry", college.getCollegeName(),
                    "缺陷态：重试 flush 被 !dirty 短路（半个成功残留）→ 红；修复后重试生效 → 绿");
            return null;
        });
    }
}
