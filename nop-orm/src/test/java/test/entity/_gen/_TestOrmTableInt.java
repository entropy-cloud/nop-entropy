package test.entity._gen;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import test.entity.TestOrmTableInt;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * : TEST_ORM_TABLE
 */
public class _TestOrmTableInt extends DynamicOrmEntity {

    /* : SID INTEGER */
    public static final String PROP_NAME_sid = "sid";
    public static final int PROP_ID_sid = 1;

    /* : STR_VALUE VARCHAR */
    public static final String PROP_NAME_strValue = "strValue";
    public static final int PROP_ID_strValue = 2;

    private static int _PROP_ID_BOUND = 3;

    public static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_sid);
    public static final int[] PK_PROP_IDS = new int[]{PROP_ID_sid};

    private static final String[] PROP_ID_TO_NAME = new String[3];
    private static final Map<String, Integer> PROP_NAME_TO_ID = new HashMap<>();

    static {

        PROP_ID_TO_NAME[PROP_ID_sid] = PROP_NAME_sid;
        PROP_NAME_TO_ID.put(PROP_NAME_sid, PROP_ID_sid);

        PROP_ID_TO_NAME[PROP_ID_strValue] = PROP_NAME_strValue;
        PROP_NAME_TO_ID.put(PROP_NAME_strValue, PROP_ID_strValue);

    }

    /* : SID */
    private java.lang.Integer _sid;

    /* : STR_VALUE */
    private java.lang.String _strValue;

    public _TestOrmTableInt() {
    }

    protected TestOrmTableInt newInstance() {
        return new TestOrmTableInt();
    }

    @Override
    public TestOrmTableInt cloneInstance() {
        TestOrmTableInt entity = newInstance();
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
        return "test.entity.TestOrmTableInt";
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

            default:
                return super.orm_propValue(propId);
        }
    }

    @Override
    public void orm_propValue(int propId, Object value) {
        switch (propId) {

            case PROP_ID_sid: {
                java.lang.Integer typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toInteger(value, err -> newTypeConversionError(PROP_NAME_sid));
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

            default:
                super.orm_propValue(propId, value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch (propId) {

            case PROP_ID_sid: {
                onInitProp(propId);
                this._sid = (java.lang.Integer) value;
                orm_id(); // 如果是设置主键字段，则触发watcher
                break;
            }

            case PROP_ID_strValue: {
                onInitProp(propId);
                this._strValue = (java.lang.String) value;

                break;
            }

            default:
                super.orm_internalSet(propId, value);
        }
    }

    /**
     * : SID
     */
    public java.lang.Integer getSid() {
        onPropGet(PROP_ID_sid);
        return _sid;
    }

    /**
     * : SID
     */
    public void setSid(java.lang.Integer value) {
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

}
