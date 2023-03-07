package test.entity._gen;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.orm.IOrmEntitySet;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet;
import test.entity.TestCompositeTable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * : TEST_COMPOSITE_TABLE
 */
public class _TestCompositeTable extends DynamicOrmEntity {

    /* : partition_id INTEGER */
    public static final String PROP_NAME_partitionId = "partitionId";
    public static final int PROP_ID_partitionId = 1;

    /* : SID VARCHAR */
    public static final String PROP_NAME_sid = "sid";
    public static final int PROP_ID_sid = 2;

    /* : INT_value INTEGER */
    public static final String PROP_NAME_intValue = "intValue";
    public static final int PROP_ID_intValue = 3;

    private static int _PROP_ID_BOUND = 4;

    /* relation: */
    public static final String PROP_NAME_subs = "subs";

    public static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_partitionId, PROP_NAME_sid);
    public static final int[] PK_PROP_IDS = new int[]{PROP_ID_partitionId, PROP_ID_sid};

    private static final String[] PROP_ID_TO_NAME = new String[4];
    private static final Map<String, Integer> PROP_NAME_TO_ID = new HashMap<>();

    static {

        PROP_ID_TO_NAME[PROP_ID_partitionId] = PROP_NAME_partitionId;
        PROP_NAME_TO_ID.put(PROP_NAME_partitionId, PROP_ID_partitionId);

        PROP_ID_TO_NAME[PROP_ID_sid] = PROP_NAME_sid;
        PROP_NAME_TO_ID.put(PROP_NAME_sid, PROP_ID_sid);

        PROP_ID_TO_NAME[PROP_ID_intValue] = PROP_NAME_intValue;
        PROP_NAME_TO_ID.put(PROP_NAME_intValue, PROP_ID_intValue);

    }

    /* : partition_id */
    private java.lang.String _partitionId;

    /* : SID */
    private java.lang.String _sid;

    /* : INT_value */
    private java.lang.Integer _intValue;

    public _TestCompositeTable() {
    }

    protected TestCompositeTable newInstance() {
        return new TestCompositeTable();
    }

    @Override
    public TestCompositeTable cloneInstance() {
        TestCompositeTable entity = newInstance();
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
        return "test.entity.TestCompositeTable";
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

        return buildCompositeId(PK_PROP_NAMES, PK_PROP_IDS);

    }

    @Override
    public boolean orm_isPrimary(int propId) {

        return propId == PROP_ID_partitionId || propId == PROP_ID_sid;

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

            case PROP_ID_partitionId:
                return getPartitionId();

            case PROP_ID_sid:
                return getSid();

            case PROP_ID_intValue:
                return getIntValue();

            default:
                return super.orm_propValue(propId);
        }
    }

    @Override
    public void orm_propValue(int propId, Object value) {
        switch (propId) {

            case PROP_ID_partitionId: {
                java.lang.String typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toString(value, err -> newTypeConversionError(PROP_NAME_partitionId));
                }
                setPartitionId(typedValue);
                break;
            }

            case PROP_ID_sid: {
                java.lang.String typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toString(value, err -> newTypeConversionError(PROP_NAME_sid));
                }
                setSid(typedValue);
                break;
            }

            case PROP_ID_intValue: {
                java.lang.Integer typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toInteger(value, err -> newTypeConversionError(PROP_NAME_intValue));
                }
                setIntValue(typedValue);
                break;
            }

            default:
                super.orm_propValue(propId, value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch (propId) {

            case PROP_ID_partitionId: {
                onInitProp(propId);
                this._partitionId = (java.lang.String) value;
                orm_id(); // 如果是设置主键字段，则触发watcher
                break;
            }

            case PROP_ID_sid: {
                onInitProp(propId);
                this._sid = (java.lang.String) value;
                orm_id(); // 如果是设置主键字段，则触发watcher
                break;
            }

            case PROP_ID_intValue: {
                onInitProp(propId);
                this._intValue = (java.lang.Integer) value;

                break;
            }

            default:
                super.orm_internalSet(propId, value);
        }
    }

    /**
     * : partition_id
     */
    public java.lang.String getPartitionId() {
        onPropGet(PROP_ID_partitionId);
        return _partitionId;
    }

    /**
     * : partition_id
     */
    public void setPartitionId(java.lang.String value) {
        if (onPropSet(PROP_ID_partitionId, value)) {
            this._partitionId = value;
            internalClearRefs(PROP_ID_partitionId);
            orm_id();
        }
    }

    /**
     * : SID
     */
    public java.lang.String getSid() {
        onPropGet(PROP_ID_sid);
        return _sid;
    }

    /**
     * : SID
     */
    public void setSid(java.lang.String value) {
        if (onPropSet(PROP_ID_sid, value)) {
            this._sid = value;
            internalClearRefs(PROP_ID_sid);
            orm_id();
        }
    }

    /**
     * : INT_value
     */
    public java.lang.Integer getIntValue() {
        onPropGet(PROP_ID_intValue);
        return _intValue;
    }

    /**
     * : INT_value
     */
    public void setIntValue(java.lang.Integer value) {
        if (onPropSet(PROP_ID_intValue, value)) {
            this._intValue = value;
            internalClearRefs(PROP_ID_intValue);

        }
    }

    private final OrmEntitySet<test.entity.TestCompositeSub> _subs = new OrmEntitySet<>(this, PROP_NAME_subs,
            test.entity.TestCompositeSub.PROP_NAME_parent, null, test.entity.TestCompositeSub.class);

    /**
     * 。 refPropName: parent, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<test.entity.TestCompositeSub> getSubs() {
        return _subs;
    }

}
