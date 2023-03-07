package org.beetl.sql.jmh.xorm.vo._gen;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.orm.IOrmEntitySet;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet;
import org.beetl.sql.jmh.xorm.vo.SysCustomer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * : sys_customer
 */
public class _SysCustomer extends DynamicOrmEntity {

    /* : ID INTEGER */
    public static final String PROP_NAME_id = "id";
    public static final int PROP_ID_id = 1;

    /* : CODE VARCHAR */
    public static final String PROP_NAME_code = "code";
    public static final int PROP_ID_code = 2;

    /* : NAME VARCHAR */
    public static final String PROP_NAME_name = "name";
    public static final int PROP_ID_name = 3;

    private static int _PROP_ID_BOUND = 4;

    /* relation: */
    public static final String PROP_NAME_orders = "orders";

    public static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_id);
    public static final int[] PK_PROP_IDS = new int[]{PROP_ID_id};

    private static final String[] PROP_ID_TO_NAME = new String[4];
    private static final Map<String, Integer> PROP_NAME_TO_ID = new HashMap<>();

    static {

        PROP_ID_TO_NAME[PROP_ID_id] = PROP_NAME_id;
        PROP_NAME_TO_ID.put(PROP_NAME_id, PROP_ID_id);

        PROP_ID_TO_NAME[PROP_ID_code] = PROP_NAME_code;
        PROP_NAME_TO_ID.put(PROP_NAME_code, PROP_ID_code);

        PROP_ID_TO_NAME[PROP_ID_name] = PROP_NAME_name;
        PROP_NAME_TO_ID.put(PROP_NAME_name, PROP_ID_name);

    }

    /* : ID */
    private java.lang.Integer _id;

    /* : CODE */
    private java.lang.String _code;

    /* : NAME */
    private java.lang.String _name;

    public _SysCustomer() {
    }

    protected SysCustomer newInstance() {
        return new SysCustomer();
    }

    @Override
    public SysCustomer cloneInstance() {
        SysCustomer entity = newInstance();
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
        return "org.beetl.sql.jmh.xorm.vo.SysCustomer";
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

        return buildSimpleId(PROP_ID_id);

    }

    @Override
    public boolean orm_isPrimary(int propId) {

        return propId == PROP_ID_id;

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

            case PROP_ID_id:
                return getId();

            case PROP_ID_code:
                return getCode();

            case PROP_ID_name:
                return getName();

            default:
                return super.orm_propValue(propId);
        }
    }

    @Override
    public void orm_propValue(int propId, Object value) {
        switch (propId) {

            case PROP_ID_id: {
                java.lang.Integer typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toInteger(value, err -> newTypeConversionError(PROP_NAME_id));
                }
                setId(typedValue);
                break;
            }

            case PROP_ID_code: {
                java.lang.String typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toString(value, err -> newTypeConversionError(PROP_NAME_code));
                }
                setCode(typedValue);
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

            default:
                super.orm_propValue(propId, value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch (propId) {

            case PROP_ID_id: {
                onInitProp(propId);
                this._id = (java.lang.Integer) value;
                orm_id(); // 如果是设置主键字段，则触发watcher
                break;
            }

            case PROP_ID_code: {
                onInitProp(propId);
                this._code = (java.lang.String) value;

                break;
            }

            case PROP_ID_name: {
                onInitProp(propId);
                this._name = (java.lang.String) value;

                break;
            }

            default:
                super.orm_internalSet(propId, value);
        }
    }

    /**
     * : ID
     */
    public java.lang.Integer getId() {
        onPropGet(PROP_ID_id);
        return _id;
    }

    /**
     * : ID
     */
    public void setId(java.lang.Integer value) {
        if (onPropSet(PROP_ID_id, value)) {
            this._id = value;
            internalClearRefs(PROP_ID_id);
            orm_id();
        }
    }

    /**
     * : CODE
     */
    public java.lang.String getCode() {
        onPropGet(PROP_ID_code);
        return _code;
    }

    /**
     * : CODE
     */
    public void setCode(java.lang.String value) {
        if (onPropSet(PROP_ID_code, value)) {
            this._code = value;
            internalClearRefs(PROP_ID_code);

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

    private final OrmEntitySet<org.beetl.sql.jmh.xorm.vo.SysOrder> _orders = new OrmEntitySet<>(this, PROP_NAME_orders,
            org.beetl.sql.jmh.xorm.vo.SysOrder.PROP_NAME_customer, null, org.beetl.sql.jmh.xorm.vo.SysOrder.class);

    /**
     * 。 refPropName: customer, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<org.beetl.sql.jmh.xorm.vo.SysOrder> getOrders() {
        return _orders;
    }

}
