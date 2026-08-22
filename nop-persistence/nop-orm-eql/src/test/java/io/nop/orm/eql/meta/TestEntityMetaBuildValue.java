package io.nop.orm.eql.meta;

import io.nop.commons.collections.IntArray;
import io.nop.commons.type.StdDataType;
import io.nop.commons.type.StdSqlType;
import io.nop.dao.api.IDaoEntity;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.impl.DialectImpl;
import io.nop.dao.dialect.model.DialectFeatures;
import io.nop.dao.dialect.model.DialectModel;
import io.nop.dataset.binder.DataParameterBinders;
import io.nop.dataset.binder.IDataParameterBinder;
import io.nop.orm.eql.IEqlQueryContext;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.OrmColumnModel;
import io.nop.orm.model.OrmComponentModel;
import io.nop.orm.model.OrmComponentPropModel;
import io.nop.orm.model.OrmEntityModel;
import io.nop.orm.model.OrmJoinOnModel;
import io.nop.orm.model.OrmToOneReferenceModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestEntityMetaBuildValue {
    static IDialect dialect;

    @BeforeAll
    public static void init() {
        // 模块测试环境未注册dialect.xml组件加载器，直接构造最小测试方言
        DialectModel model = new DialectModel();
        model.setFeatures(new DialectFeatures());
        model.setReservedKeywords(java.util.Collections.emptySet());
        model.setSqls(new io.nop.dao.dialect.model.DialectSqls());
        model.setSqlDataTypes(java.util.Collections.emptyList());

        io.nop.dao.dialect.model.SqlNativeFunctionModel currentTimestamp = new io.nop.dao.dialect.model.SqlNativeFunctionModel();
        currentTimestamp.setName("current_timestamp");
        currentTimestamp.setHasParenthesis(false);
        currentTimestamp.setReturnType(StdSqlType.TIMESTAMP);
        model.setFunctions(java.util.Collections.singletonList(currentTimestamp));
        dialect = new DialectImpl(model);
    }

    static OrmColumnModel col(String name, String code, int propId, boolean primary) {
        OrmColumnModel c = new OrmColumnModel();
        c.setName(name);
        c.setCode(code);
        c.setPropId(propId);
        c.setStdSqlType(StdSqlType.VARCHAR);
        c.setPrimary(primary);
        return c;
    }

    static OrmEntityModel entity(String name, String tableName, OrmColumnModel... cols) {
        OrmEntityModel m = new OrmEntityModel();
        m.setName(name);
        m.setTableName(tableName);
        m.setColumns(new ArrayList<>(Arrays.asList(cols)));
        m.init();
        return m;
    }

    /**
     * 复合外键join条件为 leftProp=a / leftValue='X' / leftProp=b 时，
     * pk槽位必须按join位置对齐，列值按列条件顺序取自row
     */
    @Test
    public void testRefPropBuildValueWithConstantJoinCondition() {
        OrmEntityModel refEntity = entity("RefEntity", "REF_ENTITY", col("id", "SID", 1, true));

        OrmToOneReferenceModel rel = new OrmToOneReferenceModel();
        rel.setName("refProp");
        rel.setRefEntityName("RefEntity");
        rel.setRefEntityModel(refEntity);

        OrmJoinOnModel joinA = new OrmJoinOnModel();
        joinA.setLeftPropModel(col("a", "A_COL", 1, false));
        joinA.setRightPropModel(refEntity.getColumns().get(0));

        OrmJoinOnModel joinConst = new OrmJoinOnModel();
        joinConst.setLeftValue("X");

        OrmJoinOnModel joinB = new OrmJoinOnModel();
        joinB.setLeftPropModel(col("b", "B_COL", 2, false));
        joinB.setRightPropModel(refEntity.getColumns().get(0));

        rel.setJoin(Arrays.asList(joinA, joinConst, joinB));

        IDataParameterBinder binder = DataParameterBinders.ANY;
        EntityRefPropExprMeta meta = new EntityRefPropExprMeta(
                Arrays.asList("A_COL", "B_COL"), Arrays.asList(binder, binder), rel);

        final Object[] captured = new Object[1];
        IEqlQueryContext session = new IEqlQueryContext() {
            @Override
            public Object internalReadId(Object[] values, int fromIndex, IEntityModel entityModel) {
                return null;
            }

            @Override
            public Object castId(IEntityModel entityModel, Object id) {
                captured[0] = id;
                return id;
            }

            @Override
            public IDaoEntity internalMakeEntity(String entityName, Object id, Object[] propValues, IntArray propIds) {
                return null;
            }

            @Override
            public IDaoEntity internalLoad(String entityName, Object id) {
                return null;
            }
        };

        Object[] row = {"aValue", "bValue"};
        meta.buildValue(row, 0, session);

        assertArrayEquals(new Object[]{"aValue", "X", "bValue"}, (Object[]) captured[0]);
    }

    /**
     * kv表没有dateTimeValue列时应回退使用timestampValue列（TIMESTAMP类型），不能错误映射到decimal列
     */
    @Test
    public void testKvTableDatetimeFallsBackToTimestampColumn() {
        OrmEntityModel model = new OrmEntityModel();
        model.setName("KvEntity");
        model.setTableName("KV_ENTITY");
        model.setNoPrimaryKey(true);
        model.setColumns(new ArrayList<>(Arrays.asList(
                col("stringValue", "STRING_VALUE", 1, false),
                col("timestampValue", "TIMESTAMP_VALUE", 2, false))));
        model.setKvTable(true);
        model.init();

        EntityTableMeta meta = new EntityTableMeta(model, null, dialect);
        ISqlExprMeta dateTime = meta.getValueExprMeta(StdDataType.DATETIME.getName());
        assertNotNull(dateTime);
        assertEquals(Arrays.asList(dialect.normalizeColumnName("TIMESTAMP_VALUE")), dateTime.getColumnNames());
    }

    /**
     * 有主键的实体应注册component元数据（无主键实体不支持按component查询）
     */
    @Test
    public void testComponentRegisteredForEntityWithPrimaryKey() {
        OrmEntityModel model = new OrmEntityModel();
        model.setName("CompEntity");
        model.setTableName("COMP_ENTITY");
        model.setColumns(new ArrayList<>(Arrays.asList(
                col("id", "SID", 1, true), col("extA", "EXT_A", 2, false))));

        OrmComponentModel component = new OrmComponentModel();
        component.setName("ext");
        OrmComponentPropModel prop = new OrmComponentPropModel();
        prop.setName("extA");
        prop.setColumn("EXT_A");
        component.setProps(new ArrayList<>(Arrays.asList(prop)));
        model.setComponents(new ArrayList<>(Arrays.asList(component)));
        model.init();

        EntityTableMeta meta = new EntityTableMeta(model, null, dialect);
        assertNotNull(meta.getFieldExprMeta("ext", false));
    }
}
