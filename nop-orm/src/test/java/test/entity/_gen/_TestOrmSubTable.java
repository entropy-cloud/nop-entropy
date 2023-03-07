package test.entity._gen;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import test.entity.TestOrmSubTable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * : TEST_ORM_SUB_TABLE
 */
public class _TestOrmSubTable extends DynamicOrmEntity {

    /* : SUB_ID VARCHAR */
    public static final String PROP_NAME_sid = "sid";
    public static final int PROP_ID_sid = 1;

    /* : STR_VALUE VARCHAR */
    public static final String PROP_NAME_strValue = "strValue";
    public static final int PROP_ID_strValue = 2;

    /* : MAIN_ID VARCHAR */
    public static final String PROP_NAME_mainId = "mainId";
    public static final int PROP_ID_mainId = 3;

    private static int _PROP_ID_BOUND = 4;

    /* relation: */
    public static final String PROP_NAME_testOrmTable = "testOrmTable";

    public static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_sid);
    public static final int[] PK_PROP_IDS = new int[]{PROP_ID_sid};

    private static final String[] PROP_ID_TO_NAME = new String[4];
    private static final Map<String, Integer> PROP_NAME_TO_ID = new HashMap<>();

    static {

        PROP_ID_TO_NAME[PROP_ID_sid] = PROP_NAME_sid;
        PROP_NAME_TO_ID.put(PROP_NAME_sid, PROP_ID_sid);

        PROP_ID_TO_NAME[PROP_ID_strValue] = PROP_NAME_strValue;
        PROP_NAME_TO_ID.put(PROP_NAME_strValue, PROP_ID_strValue);

        PROP_ID_TO_NAME[PROP_ID_mainId] = PROP_NAME_mainId;
        PROP_NAME_TO_ID.put(PROP_NAME_mainId, PROP_ID_mainId);

    }

    /* : SUB_ID */
    private java.lang.String _sid;

    /* : STR_VALUE */
    private java.lang.String _strValue;

    /* : MAIN_ID */
    private java.lang.String _mainId;

    public _TestOrmSubTable() {
    }

    protected TestOrmSubTable newInstance() {
        return new TestOrmSubTable();
    }

    @Override
    public TestOrmSubTable cloneInstance() {
        TestOrmSubTable entity = newInstance();
        orm_forEachInitedProp((value, propId) -> {
            entity.onInitProp(propId);
        });
        return entity;
    }

    @Override
    public String orm_entityName() {
        // 如果存在实体模型对象，则以模型对象上的设置为准
        IEntityModel entityModel = orm_entityModel();
        if (entityModel != null)
            return entityModel.getName();
        return "test.entity.TestOrmSubTable";
    }

    @Override
    public int orm_propIdBound() {
        IEntityModel entityModel = orm_entityModel();
        if (entityModel != null)
            return entityModel.getPropIdBound();
        return _PROP_ID_BOUND;
    }

    @Override
    public Object orm_id() {

        return buildSimpleId(PROP_ID_sid);

    }

    @Override
    public boolean orm_isPrimary(int propId) {

        return propId == PROP_ID_sid;

    }

    @Override
    public String orm_propName(int propId) {
        if (propId >= PROP_ID_TO_NAME.length)
            return super.orm_propName(propId);
        String propName = PROP_ID_TO_NAME[propId];
        if (propName == null)
            return super.orm_propName(propId);
        return propName;
    }

    @Override
    public int orm_propId(String propName) {
        Integer propId = PROP_NAME_TO_ID.get(propName);
        if (propId == null)
            return super.orm_propId(propName);
        return propId;
    }

    @Override
    public Object orm_propValue(int propId) {
        switch (propId) {

            case PROP_ID_sid:
                return getSid();

            case PROP_ID_strValue:
                return getStrValue();

            case PROP_ID_mainId:
                return getMainId();

            default:
                return super.orm_propValue(propId);
        }
    }

    @Override
    public void orm_propValue(int propId, Object value) {
        switch (propId) {

            case PROP_ID_sid: {
                java.lang.String typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toString(value, err -> newTypeConversionError(PROP_NAME_sid));
                }
                setSid(typedValue);
                break;
            }

            case PROP_ID_strValue: {
                java.lang.String typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toString(value, err -> newTypeConversionError(PROP_NAME_strValue));
                }
                setStrValue(typedValue);
                break;
            }

            case PROP_ID_mainId: {
                java.lang.String typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toString(value, err -> newTypeConversionError(PROP_NAME_mainId));
                }
                setMainId(typedValue);
                break;
            }

            default:
                super.orm_propValue(propId, value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch (propId) {

            case PROP_ID_sid: {
                onInitProp(propId);
                this._sid = (java.lang.String) value;
                orm_id(); // 如果是设置主键字段，则触发watcher
                break;
            }

            case PROP_ID_strValue: {
                onInitProp(propId);
                this._strValue = (java.lang.String) value;

                break;
            }

            case PROP_ID_mainId: {
                onInitProp(propId);
                this._mainId = (java.lang.String) value;

                break;
            }

            default:
                super.orm_internalSet(propId, value);
        }
    }

    /**
     * : SUB_ID
     */
    public java.lang.String getSid() {
        onPropGet(PROP_ID_sid);
        return _sid;
    }

    /**
     * : SUB_ID
     */
    public void setSid(java.lang.String value) {
        if (onPropSet(PROP_ID_sid, value)) {
            this._sid = value;
            internalClearRefs(PROP_ID_sid);
            orm_id();
        }
    }

    /**
     * : STR_VALUE
     */
    public java.lang.String getStrValue() {
        onPropGet(PROP_ID_strValue);
        return _strValue;
    }

    /**
     * : STR_VALUE
     */
    public void setStrValue(java.lang.String value) {
        if (onPropSet(PROP_ID_strValue, value)) {
            this._strValue = value;
            internalClearRefs(PROP_ID_strValue);

        }
    }

    /**
     * : MAIN_ID
     */
    public java.lang.String getMainId() {
        onPropGet(PROP_ID_mainId);
        return _mainId;
    }

    /**
     * : MAIN_ID
     */
    public void setMainId(java.lang.String value) {
        if (onPropSet(PROP_ID_mainId, value)) {
            this._mainId = value;
            internalClearRefs(PROP_ID_mainId);

        }
    }

    /**
     *
     */
    public test.entity.TestOrmTable getTestOrmTable() {
        return (test.entity.TestOrmTable) internalGetRefEntity(PROP_NAME_testOrmTable);
    }

    public void setTestOrmTable(test.entity.TestOrmTable refEntity) {
        if (refEntity == null) {

            this.setMainId(null);

        } else {
            internalSetRefEntity(PROP_NAME_testOrmTable, refEntity, () -> {

                this.orm_propValue(PROP_ID_mainId, refEntity.getSid());

            });
        }
    }

}
