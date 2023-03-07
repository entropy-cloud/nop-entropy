package test.entity._gen;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.orm.IOrmEntitySet;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet;
import test.entity.TestOrmMethodParam;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * : TEST_ORM_METHOD_PARAM
 */
public class _TestOrmMethodParam extends DynamicOrmEntity {

    /* : SID INTEGER */
    public static final String PROP_NAME_sid = "sid";
    public static final int PROP_ID_sid = 1;

    /* : NAME VARCHAR */
    public static final String PROP_NAME_name = "name";
    public static final int PROP_ID_name = 2;

    /* : METHOD_ID INTEGER */
    public static final String PROP_NAME_methodId = "methodId";
    public static final int PROP_ID_methodId = 3;

    /* : DATA_TYPE INTEGER */
    public static final String PROP_NAME_dataType = "dataType";
    public static final int PROP_ID_dataType = 4;

    private static int _PROP_ID_BOUND = 5;

    /* relation: */
    public static final String PROP_NAME_method = "method";

    /* relation: */
    public static final String PROP_NAME_datas = "datas";

    public static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_sid);
    public static final int[] PK_PROP_IDS = new int[]{PROP_ID_sid};

    private static final String[] PROP_ID_TO_NAME = new String[5];
    private static final Map<String, Integer> PROP_NAME_TO_ID = new HashMap<>();

    static {

        PROP_ID_TO_NAME[PROP_ID_sid] = PROP_NAME_sid;
        PROP_NAME_TO_ID.put(PROP_NAME_sid, PROP_ID_sid);

        PROP_ID_TO_NAME[PROP_ID_name] = PROP_NAME_name;
        PROP_NAME_TO_ID.put(PROP_NAME_name, PROP_ID_name);

        PROP_ID_TO_NAME[PROP_ID_methodId] = PROP_NAME_methodId;
        PROP_NAME_TO_ID.put(PROP_NAME_methodId, PROP_ID_methodId);

        PROP_ID_TO_NAME[PROP_ID_dataType] = PROP_NAME_dataType;
        PROP_NAME_TO_ID.put(PROP_NAME_dataType, PROP_ID_dataType);

    }

    /* : SID */
    private java.lang.Integer _sid;

    /* : NAME */
    private java.lang.String _name;

    /* : METHOD_ID */
    private java.lang.Integer _methodId;

    /* : DATA_TYPE */
    private java.lang.Integer _dataType;

    public _TestOrmMethodParam() {
    }

    protected TestOrmMethodParam newInstance() {
        return new TestOrmMethodParam();
    }

    @Override
    public TestOrmMethodParam cloneInstance() {
        TestOrmMethodParam entity = newInstance();
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
        return "test.entity.TestOrmMethodParam";
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

            case PROP_ID_name:
                return getName();

            case PROP_ID_methodId:
                return getMethodId();

            case PROP_ID_dataType:
                return getDataType();

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

            case PROP_ID_name: {
                java.lang.String typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toString(value, err -> newTypeConversionError(PROP_NAME_name));
                }
                setName(typedValue);
                break;
            }

            case PROP_ID_methodId: {
                java.lang.Integer typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toInteger(value, err -> newTypeConversionError(PROP_NAME_methodId));
                }
                setMethodId(typedValue);
                break;
            }

            case PROP_ID_dataType: {
                java.lang.Integer typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toInteger(value, err -> newTypeConversionError(PROP_NAME_dataType));
                }
                setDataType(typedValue);
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

            case PROP_ID_name: {
                onInitProp(propId);
                this._name = (java.lang.String) value;

                break;
            }

            case PROP_ID_methodId: {
                onInitProp(propId);
                this._methodId = (java.lang.Integer) value;

                break;
            }

            case PROP_ID_dataType: {
                onInitProp(propId);
                this._dataType = (java.lang.Integer) value;

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
     * : NAME
     */
    public java.lang.String getName() {
        onPropGet(PROP_ID_name);
        return _name;
    }

    /**
     * : NAME
     */
    public void setName(java.lang.String value) {
        if (onPropSet(PROP_ID_name, value)) {
            this._name = value;
            internalClearRefs(PROP_ID_name);

        }
    }

    /**
     * : METHOD_ID
     */
    public java.lang.Integer getMethodId() {
        onPropGet(PROP_ID_methodId);
        return _methodId;
    }

    /**
     * : METHOD_ID
     */
    public void setMethodId(java.lang.Integer value) {
        if (onPropSet(PROP_ID_methodId, value)) {
            this._methodId = value;
            internalClearRefs(PROP_ID_methodId);

        }
    }

    /**
     * : DATA_TYPE
     */
    public java.lang.Integer getDataType() {
        onPropGet(PROP_ID_dataType);
        return _dataType;
    }

    /**
     * : DATA_TYPE
     */
    public void setDataType(java.lang.Integer value) {
        if (onPropSet(PROP_ID_dataType, value)) {
            this._dataType = value;
            internalClearRefs(PROP_ID_dataType);

        }
    }

    /**
     *
     */
    public test.entity.TestOrmMethod getMethod() {
        return (test.entity.TestOrmMethod) internalGetRefEntity(PROP_NAME_method);
    }

    public void setMethod(test.entity.TestOrmMethod refEntity) {
        if (refEntity == null) {

            this.setMethodId(null);

        } else {
            internalSetRefEntity(PROP_NAME_method, refEntity, () -> {

                this.setMethodId(refEntity.getSid());

            });
        }
    }

    private final OrmEntitySet<test.entity.TestOrmData> _datas = new OrmEntitySet<>(this, PROP_NAME_datas,
            test.entity.TestOrmData.PROP_NAME_methodParam, null, test.entity.TestOrmData.class);

    /**
     * 。 refPropName: methodParam, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<test.entity.TestOrmData> getDatas() {
        return _datas;
    }

}
