package io.nop.dyn.dao.model;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdSqlType;
import io.nop.dao.api.DaoProvider;
import io.nop.dao.api.IDaoProvider;
import io.nop.dyn.dao.NopDynDaoConstants;
import io.nop.dyn.dao.entity.NopDynDomain;
import io.nop.dyn.dao.entity.NopDynEntityMeta;
import io.nop.dyn.dao.entity.NopDynPropMeta;
import io.nop.orm.model.OrmColumnModel;
import io.nop.orm.model.OrmEntityFilterModel;
import io.nop.orm.model.OrmEntityModel;
import io.nop.orm.model.OrmModelConstants;
import io.nop.orm.model.OrmToManyReferenceModel;
import io.nop.orm.support.DynamicOrmEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.nop.dyn.dao.NopDynDaoConstants.ENTITY_STORE_TYPE_REAL;
import static io.nop.dyn.dao.NopDynDaoConstants.ENTITY_STORE_TYPE_VIRTUAL;
import static io.nop.dyn.dao.NopDynDaoErrors.ERR_DYN_UNKNOWN_STD_SQL_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DynEntityMetaToOrmModel 转换语义（真实表/虚拟表两种存储形态）：动态实体元数据 ->
 * ORM 实体模型的实体名/表名/类名映射、标准列（sid+审计五列）追加、propId 顺序化、
 * 列 code 驼峰转下划线、precision 安全缺省（VARCHAR=100/DECIMAL=38）、虚拟表的
 * extField 别名路径与 nopObjType 过滤。
 */
public class TestDynEntityMetaToOrmModelTransform {

    @BeforeEach
    public void setUp() {
        DaoProvider.registerInstance((IDaoProvider) Proxy.newProxyInstance(
                IDaoProvider.class.getClassLoader(), new Class[]{IDaoProvider.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("daoFor"))
                        return dynEntityDaoStub();
                    return TestDynEntityMetaToOrmModelColumn.defaultValue(method.getReturnType());
                }));
    }

    @AfterEach
    public void tearDown() {
        DaoProvider.registerInstance(null);
    }

    /** daoFor 返回的假 dao：getEntityModel 给出动态实体/关联标准列的桩模型 */
    @SuppressWarnings("unchecked")
    static Object dynEntityDaoStub() {
        return Proxy.newProxyInstance(IDaoProvider.class.getClassLoader(),
                new Class[]{io.nop.orm.dao.IOrmEntityDao.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getEntityModel"))
                        return dynEntityModelStub();
                    return TestDynEntityMetaToOrmModelColumn.defaultValue(method.getReturnType());
                });
    }

    /** 用真实 OrmColumnModel（转换时会 cloneInstance）构造 NopDynEntity 标准列桩 */
    static io.nop.orm.model.IEntityModel dynEntityModelStub() {
        Map<String, OrmColumnModel> cols = new LinkedHashMap<>();
        cols.put("sid", col("sid", "SID", StdSqlType.VARCHAR, 32, 0));
        cols.put("version", col("version", "VERSION", StdSqlType.BIGINT, null, 0));
        cols.put("createdBy", col("createdBy", "CREATED_BY", StdSqlType.VARCHAR, 32, 0));
        cols.put("createTime", col("createTime", "CREATE_TIME", StdSqlType.TIMESTAMP, null, 0));
        cols.put("updatedBy", col("updatedBy", "UPDATED_BY", StdSqlType.VARCHAR, 32, 0));
        cols.put("updateTime", col("updateTime", "UPDATE_TIME", StdSqlType.TIMESTAMP, null, 0));
        cols.put("nopObjType", col("nopObjType", "NOP_OBJ_TYPE", StdSqlType.VARCHAR, 32, 0));
        cols.put("str1", col("str1", "STR1", StdSqlType.VARCHAR, 100, 0));
        cols.put("int1", col("int1", "INT1", StdSqlType.INTEGER, null, 0));

        return (io.nop.orm.model.IEntityModel) Proxy.newProxyInstance(
                io.nop.orm.model.IEntityModel.class.getClassLoader(),
                new Class[]{io.nop.orm.model.IEntityModel.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getName":
                            return "io.nop.dyn.dao.entity.NopDynEntity";
                        case "getTableName":
                            return "nop_dyn_entity";
                        case "getClassName":
                            return "io.nop.dyn.dao.entity.NopDynEntity";
                        case "isUseTenant":
                            return false;
                        case "getTenantColumn":
                            return null;
                        case "getColumn":
                            return cols.get(args[0]);
                        case "getRelation":
                            // 虚拟表路径 addExtFields 需要 extFields 的 to-many 克隆
                            if ("extFields".equals(args[0]))
                                return new OrmToManyReferenceModel();
                            return null;
                        default:
                            return TestDynEntityMetaToOrmModelColumn.defaultValue(method.getReturnType());
                    }
                });
    }

    static OrmColumnModel col(String name, String code, StdSqlType sqlType, Integer precision, int scale) {
        OrmColumnModel c = new OrmColumnModel();
        c.setName(name);
        c.setCode(code);
        c.setStdSqlType(sqlType);
        if (precision != null)
            c.setPrecision(precision);
        c.setScale(scale);
        return c;
    }

    static NopDynPropMeta newProp(NopDynEntityMeta entityMeta, String propName, String sqlType,
                                  Integer precision, int propId) {
        NopDynPropMeta propMeta = new NopDynPropMeta();
        propMeta.setPropName(propName);
        propMeta.setDisplayName(propName);
        propMeta.setStdSqlType(sqlType);
        propMeta.setPrecision(precision);
        propMeta.setPropId(propId);
        propMeta.setEntityMeta(entityMeta);
        return propMeta;
    }

    /**
     * 脱离 session 的 NopDynPropMeta 在 toColumnModel 末尾会懒加载 getDomain()（未设置时
     * 抛 session-not-attached），因此 detached 场景必须显式 setDomain 挂接数据域。
     */
    static NopDynDomain newDomain(String name, String sqlType, Integer precision, Integer scale) {
        NopDynDomain domain = new NopDynDomain();
        domain.setDomainName(name);
        domain.setDisplayName(name);
        domain.setStdSqlType(sqlType);
        domain.setPrecision(precision);
        domain.setScale(scale);
        return domain;
    }

    @Test
    public void testRealTableTransformAddsStdColumnsAndNormalizesPropIds() {
        DynEntityMetaToOrmModel transformer = new DynEntityMetaToOrmModel(false);
        NopDynEntityMeta meta = new NopDynEntityMeta();
        meta.setEntityName("app.dyn.entity.Wi7Customer");
        meta.setTableName("wi7_customer");
        meta.setDisplayName("客户");
        meta.setStoreType(ENTITY_STORE_TYPE_REAL);
        NopDynPropMeta prop = newProp(meta, "custName", StdSqlType.VARCHAR.getName(), 64, 2);
        prop.setDomain(newDomain("custName", StdSqlType.VARCHAR.getName(), 64, null));
        meta.getPropMetas().add(prop);

        OrmEntityModel em = transformer.transformEntityModel(meta);

        assertEquals("app.dyn.entity.Wi7Customer", em.getName(),
                "带包名前缀的实体名必须原样作为 ORM 实体全名");
        assertEquals("Wi7Customer", meta.getBizObjName());
        assertEquals("wi7_customer", em.getTableName());
        assertEquals(DynamicOrmEntity.class.getName(), em.getClassName(),
                "真实表动态实体必须映射到 DynamicOrmEntity");
        assertEquals("客户", em.getDisplayName());
        assertTrue(em.isRegisterShortName());

        assertEquals("version", em.getVersionProp(), "标准审计列必须挂接 version 乐观锁属性");
        assertEquals("createTime", em.getCreateTimeProp());
        assertEquals("updateTime", em.getUpdateTimeProp());
        assertEquals("createdBy", em.getCreaterProp());
        assertEquals("updatedBy", em.getUpdaterProp());

        // sid + custName + version + createTime + updateTime + createdBy + updatedBy
        assertEquals(7, em.getColumns().size());
        assertNotNull(em.getColumn("sid"), "动态实体必须强制使用 sid 主键");

        // normalizePropIds: 全部列 propId 必须被顺序化为 1..n
        for (int i = 0; i < em.getColumns().size(); i++) {
            assertEquals(i + 1, em.getColumns().get(i).getPropId(),
                    "列 propId 必须按声明顺序顺序化，index=" + i);
        }

        OrmColumnModel custName = em.getColumn("custName");
        assertEquals("CUST_NAME", custName.getCode(),
                "属性名必须驼峰转大写下划线生成列 code");
        assertFalse(custName.isPrimary(), "业务列不得声明为主键（主键固定为 sid）");
        assertEquals("X", em.getColumn("sid").prop_get("ui:show"),
                "sid 主键列必须带 ui:show=X 标记");
    }

    @Test
    public void testColumnPrecisionFallsBackToSafeDefaults() {
        DynEntityMetaToOrmModel transformer = new DynEntityMetaToOrmModel(false);

        NopDynEntityMeta meta = new NopDynEntityMeta();
        meta.setEntityName("app.dyn.entity.Wi7Precision");
        meta.setTableName("wi7_precision");
        meta.setStoreType(ENTITY_STORE_TYPE_REAL);

        // defaultPrecision 是脱离 ORM 的静态缺省策略，可直接断言
        assertEquals(38, DynEntityMetaToOrmModel.defaultPrecision(StdSqlType.DECIMAL),
                "DECIMAL 缺省 precision 必须是 38");
        assertEquals(100, DynEntityMetaToOrmModel.defaultPrecision(StdSqlType.VARCHAR),
                "VARCHAR 缺省 precision 必须是 100，避免 VARCHAR(1) 截断");

        // 域未填 precision 时列继承安全缺省：VARCHAR -> 100
        OrmColumnModel varcharCol = transformer.toColumnModel(withDomain(
                newProp(meta, "shortName", StdSqlType.VARCHAR.getName(), null, 2),
                newDomain("shortName", StdSqlType.VARCHAR.getName(), null, null)));
        assertEquals(StdSqlType.VARCHAR, varcharCol.getStdSqlType());
        assertEquals(100, varcharCol.getPrecision(), "域缺省 precision 必须回落到 100");

        // DECIMAL 域未填 precision/scale -> 38 / 0
        OrmColumnModel decimalCol = transformer.toColumnModel(withDomain(
                newProp(meta, "amount", StdSqlType.DECIMAL.getName(), null, 3),
                newDomain("amount", StdSqlType.DECIMAL.getName(), null, null)));
        assertEquals(38, decimalCol.getPrecision(), "DECIMAL 域缺省 precision 必须是 38");
        assertEquals(0, decimalCol.getScale(), "DECIMAL 域缺省 scale 必须是 0");

        // 域显式 precision 必须透传
        OrmColumnModel explicit = transformer.toColumnModel(withDomain(
                newProp(meta, "nick", StdSqlType.VARCHAR.getName(), null, 4),
                newDomain("nick", StdSqlType.VARCHAR.getName(), 32, null)));
        assertEquals(32, explicit.getPrecision(), "域显式 precision 必须原样保留");
    }

    static NopDynPropMeta withDomain(NopDynPropMeta propMeta, NopDynDomain domain) {
        propMeta.setDomain(domain);
        return propMeta;
    }

    @Test
    public void testUnknownStdSqlTypeRejected() {
        DynEntityMetaToOrmModel transformer = new DynEntityMetaToOrmModel(false);
        NopDynEntityMeta meta = new NopDynEntityMeta();
        meta.setEntityName("app.dyn.entity.Wi7BadType");
        meta.setTableName("wi7_bad_type");
        meta.setStoreType(ENTITY_STORE_TYPE_REAL);

        NopDynPropMeta prop = newProp(meta, "weird", "NO_SUCH_SQL_TYPE", null, 2);
        prop.setDomain(newDomain("weird", StdSqlType.VARCHAR.getName(), null, null));
        NopException ex = assertThrows(NopException.class, () -> transformer.toColumnModel(prop));
        assertEquals(ERR_DYN_UNKNOWN_STD_SQL_TYPE.getErrorCode(), ex.getErrorCode());
        assertEquals("NO_SUCH_SQL_TYPE", ex.getParam("stdSqlType"),
                "错误必须携带非法 SQL 类型参数便于排障");
    }

    @Test
    public void testVirtualEntityTransformMapsAliasAndObjTypeFilter() {
        DynEntityMetaToOrmModel transformer = new DynEntityMetaToOrmModel(false);
        NopDynEntityMeta meta = new NopDynEntityMeta();
        meta.setEntityName("app.dyn.entity.Wi7Virtual");
        meta.setTableName("ignored_virtual");
        meta.setStoreType(ENTITY_STORE_TYPE_VIRTUAL);

        // 有 dynPropMapping：映射到 NopDynEntity 的 str1 宽列，名称不同时暴露别名且原列标记 sys
        NopDynPropMeta mapped = newProp(meta, "nickName", StdSqlType.VARCHAR.getName(), 100, 2);
        mapped.setDynPropMapping("str1");
        meta.getPropMetas().add(mapped);

        // 无 dynPropMapping：走 extFields 别名，propPath = extFields.<name>.<javaType>
        NopDynPropMeta extProp = newProp(meta, "score", StdSqlType.INTEGER.getName(), null, 3);
        meta.getPropMetas().add(extProp);

        OrmEntityModel em = transformer.transformEntityModel(meta);

        assertTrue(em.isTableView(), "虚拟表实体必须是 table-view 形态");
        assertEquals("nop_dyn_entity", em.getTableName(),
                "虚拟表必须共享 NopDynEntity 物理表");
        assertEquals("io.nop.dyn.dao.entity.NopDynEntity", em.getClassName());
        assertEquals(1, em.getFilters().size());
        OrmEntityFilterModel filter = em.getFilters().get(0);
        assertEquals("nopObjType", filter.getName());
        assertEquals("Wi7Virtual", filter.getValue(), "虚拟表必须按 nopObjType=bizObjName 过滤实现逻辑隔离");

        assertEquals("str1", em.getColumn("str1").getName(),
                "映射属性必须克隆 NopDynEntity 的物理宽列 str1");
        assertTrue(em.hasAlias("nickName"), "映射名与属性名不同时必须暴露别名");
        assertEquals("str1", em.getAlias("nickName").getPropPath(),
                "映射别名的 propPath 必须指向物理宽列");
        assertTrue(em.hasAlias("score"), "无映射属性必须暴露 extFields 别名");
        assertEquals("extFields.score.int", em.getAlias("score").getPropPath(),
                "extField 别名的 propPath 必须指向 extFields 的类型化字段");
        assertTrue(em.getAlias("score").getTagSet().contains(OrmModelConstants.TAG_EAGER),
                "extField 别名必须 eager 加载");

        OrmColumnModel objType = em.getColumn("nopObjType");
        assertTrue(objType.containsTag(OrmModelConstants.TAG_NOT_PUB),
                "nopObjType 列必须标记 not-pub，不对外发布");
    }

    @Test
    public void testDomainPrecisionDefaults() {
        DynEntityMetaToOrmModel transformer = new DynEntityMetaToOrmModel(false);

        NopDynDomain decimalDomain = newDomain("amount", StdSqlType.DECIMAL.getName(), null, null);
        io.nop.orm.model.OrmDomainModel ormDomain = transformer.toOrmDomain(decimalDomain);
        assertEquals("amount", ormDomain.getName());
        assertEquals(38, ormDomain.getPrecision(), "DECIMAL 域缺省 precision=38");
        assertEquals(0, ormDomain.getScale(), "DECIMAL 域缺省 scale=0");

        NopDynDomain varcharDomain = newDomain("name", StdSqlType.VARCHAR.getName(), null, null);
        io.nop.orm.model.OrmDomainModel varcharOrm = transformer.toOrmDomain(varcharDomain);
        assertEquals(100, varcharOrm.getPrecision(), "VARCHAR 域缺省 precision=100");

        // 域显式 precision 必须透传（VARCHAR 不允许 scale，scale 保持 null）
        NopDynDomain explicit = newDomain("nick", StdSqlType.VARCHAR.getName(), 32, null);
        io.nop.orm.model.OrmDomainModel explicitOrm = transformer.toOrmDomain(explicit);
        assertEquals(32, explicitOrm.getPrecision());

        NopDynDomain decimalExplicit = newDomain("amount2", StdSqlType.DECIMAL.getName(), 20, 4);
        io.nop.orm.model.OrmDomainModel decimalExplicitOrm = transformer.toOrmDomain(decimalExplicit);
        assertEquals(20, decimalExplicitOrm.getPrecision(), "DECIMAL 域显式 precision 必须透传");
        assertEquals(4, decimalExplicitOrm.getScale(), "DECIMAL 域显式 scale 必须透传");
    }
}
