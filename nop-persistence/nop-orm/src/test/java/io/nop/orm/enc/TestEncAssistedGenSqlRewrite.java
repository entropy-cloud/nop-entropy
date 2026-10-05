/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.enc;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.collections.IntArray;
import io.nop.commons.type.StdSqlType;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.dialect.IDialect;
import io.nop.dataset.binder.IDataParameterBinder;
import io.nop.orm.AbstractOrmTestCase;
import io.nop.orm.OrmErrors;
import io.nop.orm.eql.binder.OrmBinderHelper;
import io.nop.orm.factory.DefaultOrmColumnBinderEnhancer;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IOrmModel;
import io.nop.orm.model.OrmColumnModel;
import io.nop.orm.model.OrmEntityModel;
import io.nop.orm.model.OrmModel;
import io.nop.orm.model.OrmModelConstants;
import io.nop.orm.sql.EntitySQL;
import io.nop.orm.sql.GenSqlHelper;
import io.nop.app.SimsExam;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2318 Phase 4：GenSqlHelper 路径（findByExample 逐列 eq / updateByExample 的 WHERE 侧 /
 * 实体固定 filter）在声明了辅助列的 enc 主列上的等值改写与显式失败。
 *
 * <p>16 处 {@code binders[...]} 引用（plan 2318 Baseline #5）的判定结论：
 * <ul>
 * <li>改写（值构建期已知的等值过滤）：{@code genCollectionFilterEx} 固定 filter、
 * {@code addFixedFilter} 固定 filter、{@code appendExampleFilter} example 逐列 eq
 * （含 updateByExample 的 WHERE 侧）——本测试类的 testFixedFilter* / testFindByExample* /
 * testUpdateByExampleWhere* 覆盖；</li>
 * <li>显式失败（enc+辅助列声明下不可服务的等值形态）：{@code genIdEq} 主键比对
 * （testEncPrimaryKeyRejected）、{@code genFindLatestSql} rev 全列比对
 * （testRevFindLatestEncColumnRejected）、ORDER BY（{@code genOrderBy}/{@code appendOrderBy}，
 * testOrderByEncColumnRejected）；</li>
 * <li>不改写（注释说明理由）：租户列/版本列/revision 常量列是框架系统列（不可能声明 enc+辅助列）、
 * INSERT/UPDATE SET 是写路径（加密由增强 binder 完成；bulk SET 辅助列边界为既定用户裁定）、
 * 集合 join 列是 FK 关联（执行期取 owner 值）。本路径不产出 LIKE 谓词，无 like 改写面。</li>
 * </ul></p>
 *
 * <p>测试模型：SimsExam.examName（enc + assisted/like，写路径派生见 Phase 2）；
 * 动态实体 test.entity.TestEncFilterEntity（固定 filter 指向 enc 主列）与
 * test.entity.TestEncLikeOnlyEntity（只声明 like 辅助列，等值不可服务）。</p>
 */
public class TestEncAssistedGenSqlRewrite extends AbstractOrmTestCase {

    private static final String EXAM = "io.nop.app.SimsExam";
    private static final String ENC_FILTER_ENTITY = "test.entity.TestEncFilterEntity";
    private static final String ENC_LIKE_ONLY_ENTITY = "test.entity.TestEncLikeOnlyEntity";

    private IDialect dialect() {
        return jdbc().getDialectForQuerySpace(null);
    }

    private IDataParameterBinder[] binders(IEntityModel entityModel) {
        return OrmBinderHelper.buildBinders(dialect(), entityModel, new DefaultOrmColumnBinderEnhancer());
    }

    private IEntityModel entityModel(String name) {
        return sessionFactory.getOrmModel().requireEntityModel(name);
    }

    /** WHERE 子句片段（select 列清单含 enc 主列，改写断言只看 where 之后） */
    private String whereClauseOf(String sqlText) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\bwhere\\b",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(sqlText);
        assertTrue(m.find(), "sql must contain where clause: " + sqlText);
        return sqlText.substring(m.end());
    }

    private void assertNotSearchable(NopException e, String propName) {
        assertEquals(OrmErrors.ERR_ORM_ENC_COLUMN_NOT_SEARCHABLE.getErrorCode(), e.getErrorCode());
        assertEquals(propName, e.getParam("propName"));
    }

    private void saveExam(String examId, String examName) {
        IEntityDao<SimsExam> dao = daoProvider().daoFor(SimsExam.class);
        SimsExam entity = dao.newEntity();
        entity.setExamId(examId);
        entity.setExamName(examName);
        dao.saveEntity(entity);
    }

    // ---- findByExample 逐列 eq 改写 ----

    @Test
    public void testFindByExampleEqRewritesToAssistedColumn() {
        IEntityModel model = entityModel(EXAM);
        SimsExam example = new SimsExam();
        example.setExamName("FindMe");

        SQL sql = GenSqlHelper.genFindByExample(dialect(), model, binders(model), example, null).end();
        String where = whereClauseOf(sql.getText());
        assertTrue(where.contains("EXAM_NAME_ASSISTED"), "example eq must rewrite to assisted column: " + where);
        assertFalse(where.matches("(?s).*\\bEXAM_NAME\\b(?!_).*"),
                "master column must not appear in rewritten predicate: " + where);
        // 参数在 SQL 构建期直接摘要（不经 marker builder）
        assertEquals(EncAssistedHelper.instance().assistedDigest("FindMe"), sql.getParams().get(0));
        assertFalse(sql.getParams().contains("FindMe"), "plaintext param must not remain: " + sql.getParams());
    }

    @Test
    public void testFindByExampleEqHitsAndDecrypts() {
        saveExam("401", "FindMe");
        saveExam("402", "OtherExam");

        SimsExam example = new SimsExam();
        example.setExamName("FindMe");
        List<SimsExam> rows = daoProvider().daoFor(SimsExam.class).findAllByExample(example);
        assertEquals(1, rows.size(), "example eq via assisted column must hit the saved entity");
        assertEquals("401", rows.get(0).getExamId());
        assertEquals("FindMe", rows.get(0).getExamName(), "entity load must decrypt master column");

        // 非命中值不返回
        SimsExam miss = new SimsExam();
        miss.setExamName("NotFound");
        assertTrue(daoProvider().daoFor(SimsExam.class).findAllByExample(miss).isEmpty());
    }

    @Test
    public void testCountByExampleRewritesAndCounts() {
        saveExam("421", "CountMe");
        saveExam("422", "CountMe");

        SimsExam example = new SimsExam();
        example.setExamName("CountMe");
        // countByExample 复用 appendExampleFilter 的同一改写
        assertEquals(2, daoProvider().daoFor(SimsExam.class).countByExample(example));
    }

    @Test
    public void testUpdateByExampleWhereRewritten() {
        saveExam("431", "OldName");

        SimsExam example = new SimsExam();
        example.setExamName("OldName");
        SimsExam updated = new SimsExam();
        updated.setExamScoreScale((byte) 7);

        // 白盒：WHERE 侧改写到 assisted 列；SET 侧是非 enc 列（bulk SET 边界不涉及）。
        // 参数次序：SET 值(7) → WHERE 摘要 → 租户
        SQL sql = GenSqlHelper.genUpdateByExample(dialect(), entityModel(EXAM), binders(entityModel(EXAM)),
                example, updated).end();
        String where = whereClauseOf(sql.getText());
        assertTrue(where.contains("EXAM_NAME_ASSISTED"), "updateByExample where must rewrite: " + where);
        assertEquals(EncAssistedHelper.instance().assistedDigest("OldName"), sql.getParams().get(1));

        // 端到端：经 session 真实执行，WHERE 按 assisted 命中
        Long count = orm().runInSession(session -> session.updateByExample(example, updated));
        assertEquals(1L, count);
        Map<String, Object> row = jdbc().findFirst(new SQL("select * from sims_exam where exam_id='431'"));
        assertEquals((byte) 7, ((Number) row.get("EXAM_SCORE_SCALE")).byteValue());
        // SET 未触碰主列：辅助列保持原派生值
        assertEquals(EncAssistedHelper.instance().assistedDigest("OldName"), row.get("EXAM_NAME_ASSISTED"));
    }

    // ---- 实体固定 filter 改写（动态实体 test.entity.TestEncFilterEntity，filter 指向 enc 主列） ----

    private int propId(IEntityModel model, String propName) {
        return model.requireColumn(propName).getPropId();
    }

    private void saveEncFilterEntity(long sid, String examName) {
        IEntityModel model = entityModel(ENC_FILTER_ENTITY);
        io.nop.orm.IOrmEntity entity = orm().newEntity(ENC_FILTER_ENTITY);
        entity.orm_propValue(propId(model, "sid"), sid);
        entity.orm_propValue(propId(model, "examName"), examName);
        orm().save(entity);
    }

    @Test
    public void testFixedFilterRewritesToAssistedColumn() {
        IEntityModel model = entityModel(ENC_FILTER_ENTITY);
        SQL.SqlBuilder sb = SQL.begin();
        GenSqlHelper.genCollectionFilterEx(sb, dialect(), model, null, binders(model));
        SQL sql = sb.end();
        assertTrue(sql.getText().contains("NAME_ASSISTED"), "fixed filter must rewrite to assisted column: " + sql);
        // 固定 filter 值在构建期摘要后作为参数绑定
        assertEquals(EncAssistedHelper.instance().assistedDigest("OpenExam"), sql.getParams().get(0));
    }

    @Test
    public void testFixedFilterLoadHitsOnlyMatchingRows() {
        saveEncFilterEntity(101L, "OpenExam");
        // 固定 filter 是分区语义：save 时 persister.bindFilter 会把 filter 列强制改写为 filter 值，
        // 因此"不满足 filter 的行"只能经裸 SQL 预置（加密主列 + 错误的辅助列摘要）
        insertRawExamName(102L, "ClosedExam");

        IEntityModel model = entityModel(ENC_FILTER_ENTITY);
        EntitySQL loadSql = GenSqlHelper.genLoadSql(dialect(), model, binders(model), model.getEagerLoadProps());
        String where = whereClauseOf(loadSql.sql.getText());
        assertTrue(where.contains("NAME_ASSISTED"), "load sql fixed filter must rewrite: " + where);

        // 经 session 按主键装载（genLoadSql → addFixedFilter 的真实消费路径）：
        // 固定 filter（examName='OpenExam' 摘要）只放行匹配行
        io.nop.orm.IOrmEntity hit = orm().get(ENC_FILTER_ENTITY, 101L);
        assertNotNull(hit, "row matching fixed filter must be loadable");
        assertEquals("OpenExam", hit.orm_propValue(propId(model, "examName")));

        io.nop.orm.IOrmEntity miss = orm().get(ENC_FILTER_ENTITY, 102L);
        assertNull(miss, "row not matching fixed filter must not be loadable");
    }

    private void insertRawExamName(long sid, String examName) {
        IColumnModel master = entityModel(ENC_FILTER_ENTITY).requireColumn("examName");
        String cipherText = new io.nop.commons.crypto.impl.AESTextCipher().encrypt(examName);
        jdbc().executeUpdate(SQL.begin()
                .sql("insert into test_enc_filter_entity (SID, EXAM_NAME, NAME_ASSISTED) values(")
                .param(sid)
                .sql(",")
                .param("@enc:" + cipherText)
                .sql(",")
                .param(EncAssistedHelper.instance().assistedDigest(examName))
                .sql(")")
                .end());
    }

    // ---- 不支持形态显式失败（同一错误码矩阵） ----

    @Test
    public void testExampleEqOnLikeOnlyEncColumnFails() {
        IEntityModel model = entityModel(ENC_LIKE_ONLY_ENTITY);
        io.nop.orm.IOrmEntity example = orm().newEntity(ENC_LIKE_ONLY_ENTITY);
        example.orm_propValue(propId(model, "name"), "x");

        // SQL 构建期显式失败（driver.findAllByExample 的同一构建点）
        NopException e = assertThrows(NopException.class,
                () -> GenSqlHelper.genFindByExample(dialect(), model, binders(model), example, null));
        assertNotSearchable(e, "name");
        assertEquals("eq-needs-enc-assisted-helper", e.getParam("reason"));

        // 端到端：session.findAllByExample 走同一构建路径，同样显式失败
        AtomicReference<NopException> ref = new AtomicReference<>();
        orm().runInSession(session -> {
            try {
                session.findAllByExample(example, null);
            } catch (NopException ex) {
                ref.set(ex);
            }
            return null;
        });
        assertNotNull(ref.get(), "session example query must fail explicitly");
        assertNotSearchable(ref.get(), "name");
    }

    @Test
    public void testOrderByEncColumnRejected() {
        IEntityModel model = entityModel(EXAM);

        NopException e = assertThrows(NopException.class, () -> GenSqlHelper.appendOrderBy(SQL.begin(),
                dialect(), "o", model, Arrays.asList(io.nop.api.core.beans.query.OrderFieldBean.forField("examName"))));
        assertNotSearchable(e, "examName");
        assertEquals("order-by-or-group-by-not-searchable", e.getParam("reason"));

        // 非 enc 列排序不受影响
        GenSqlHelper.appendOrderBy(SQL.begin(), dialect(), "o", model,
                Arrays.asList(io.nop.api.core.beans.query.OrderFieldBean.forField("examId")));

        // 端到端：example 查询携带 enc 列排序时显式失败
        AtomicReference<NopException> ref = new AtomicReference<>();
        orm().runInSession(session -> {
            try {
                session.findAllByExample(new SimsExam(), Arrays.asList(io.nop.api.core.beans.query.OrderFieldBean.forField("examName")));
            } catch (NopException ex) {
                ref.set(ex);
            }
            return null;
        });
        assertNotNull(ref.get());
        assertNotSearchable(ref.get(), "examName");
    }

    /**
     * enc 主键列（声明辅助列）：按主键装载/删除/更新的 WHERE 是随机 IV 密文比对（恒不命中），
     * 显式失败而非静默丢行（程序化模型验证，避免污染共享测试模型）。
     */
    @Test
    public void testEncPrimaryKeyRejected() {
        IEntityModel model = buildModelWithEncPrimaryKey();
        NopException e = assertThrows(NopException.class,
                () -> GenSqlHelper.genIdEq(new io.nop.commons.collections.MutableIntArray(2), SQL.begin(),
                        dialect(), null, model, binders(model)));
        assertNotSearchable(e, "sid");
        assertEquals("primary-key-not-searchable", e.getParam("reason"));
    }

    /**
     * rev 实体的 findLatest 全列状态比对：声明了辅助列的 enc 主列显式失败
     * （随机 IV 下密文比对恒不命中，辅助列无法表达全行比对）。
     */
    @Test
    public void testRevFindLatestEncColumnRejected() {
        IEntityModel model = buildModelWithRevisionEncColumn();
        NopException e = assertThrows(NopException.class,
                () -> GenSqlHelper.genFindLatestSql(dialect(), model, binders(model), model.getAllPropIds()));
        assertNotSearchable(e, "examName");
        assertEquals("rev-find-latest-all-columns-not-searchable", e.getParam("reason"));
    }

    // ---- 非 enc 列零影响 ----

    @Test
    public void testNonEncColumnExampleUnaffected() {
        saveExam("441", "PlainQuery");
        IEntityModel model = entityModel(EXAM);

        SimsExam example = new SimsExam();
        example.setExamId("441");
        SQL sql = GenSqlHelper.genFindByExample(dialect(), model, binders(model), example, null).end();
        String where = whereClauseOf(sql.getText());
        assertTrue(where.contains("EXAM_ID"), "non-enc column stays untouched: " + where);
        assertFalse(where.contains("ASSISTED"), "non-enc column must not be rewritten: " + where);
        assertEquals("441", sql.getParams().get(0), "non-enc param keeps raw value");

        List<SimsExam> rows = daoProvider().daoFor(SimsExam.class).findAllByExample(example);
        assertEquals(1, rows.size());
    }

    // ---- 程序化模型构造（显式失败场景，不污染共享测试模型） ----

    private OrmColumnModel column(String name, int propId, boolean primary, String... tags) {
        OrmColumnModel col = new OrmColumnModel();
        col.setName(name);
        col.setCode(name.toUpperCase());
        col.setPropId(propId);
        col.setStdSqlType(StdSqlType.VARCHAR);
        col.setPrimary(primary);
        if (tags.length > 0)
            col.setTagSet(new HashSet<>(Arrays.asList(tags)));
        return col;
    }

    private OrmModel initModel(OrmEntityModel... entities) {
        OrmModel model = new OrmModel();
        model.setEntities(new java.util.ArrayList<>(Arrays.asList(entities)));
        model.init();
        return model;
    }

    private IEntityModel buildModelWithEncPrimaryKey() {
        OrmColumnModel pk = column("sid", 1, true, OrmModelConstants.TAG_ENC);
        pk.setExtProp(OrmModelConstants.EXT_ENC_ASSISTED, "sidAssisted");
        OrmColumnModel assisted = column("sidAssisted", 2, false, OrmModelConstants.TAG_ENC_ASSISTED);
        OrmColumnModel name = column("name", 3, false);

        OrmEntityModel entity = new OrmEntityModel();
        entity.setName("test.enc.EncPkEntity");
        entity.setTableName("TEST_ENC_PK_ENTITY");
        entity.setColumns(new java.util.ArrayList<>(Arrays.asList(pk, assisted, name)));
        initModel(entity);
        return entity;
    }

    private IEntityModel buildModelWithRevisionEncColumn() {
        OrmColumnModel pk = column("sid", 1, true);
        OrmColumnModel master = column("examName", 2, false, OrmModelConstants.TAG_ENC);
        master.setExtProp(OrmModelConstants.EXT_ENC_ASSISTED, "examNameAssisted");
        OrmColumnModel assisted = column("examNameAssisted", 3, false, OrmModelConstants.TAG_ENC_ASSISTED);
        // useRevision 实体必须在模型中显式声明 nopRevExtChange 列（否则模型加载期报 unknown-column）
        OrmColumnModel extChange = column("nopRevExtChange", 4, false);
        extChange.setStdSqlType(StdSqlType.BIGINT);

        OrmEntityModel entity = new OrmEntityModel();
        entity.setName("test.enc.EncRevEntity");
        entity.setTableName("TEST_ENC_REV_ENTITY");
        entity.setUseRevision(true);
        entity.setColumns(new java.util.ArrayList<>(Arrays.asList(pk, master, assisted, extChange)));
        initModel(entity);
        assertTrue(entity.getNopRevEndVarPropId() > 0, "useRevision entity must have revEnd prop");
        return entity;
    }
}
