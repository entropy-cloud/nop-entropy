package test.entity._gen;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.orm.IOrmEntitySet;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet;
import test.entity.UserInfo;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * : TEST_USER_INFO
 */
public class _UserInfo extends DynamicOrmEntity {

    /* : USER_PASS VARCHAR */
    public static final String PROP_NAME_userPass = "userPass";
    public static final int PROP_ID_userPass = 5;

    /* : DEPT_ID VARCHAR */
    public static final String PROP_NAME_deptId = "deptId";
    public static final int PROP_ID_deptId = 6;

    /* : U_ID VARCHAR */
    public static final String PROP_NAME_sid = "sid";
    public static final int PROP_ID_sid = 20;

    /* : USER_NAME VARCHAR */
    public static final String PROP_NAME_userName = "userName";
    public static final int PROP_ID_userName = 21;

    private static int _PROP_ID_BOUND = 22;

    /* relation: */
    public static final String PROP_NAME_dept = "dept";

    /* relation: */
    public static final String PROP_NAME_testSubClasses = "testSubClasses";

    public static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_sid);
    public static final int[] PK_PROP_IDS = new int[]{PROP_ID_sid};

    private static final String[] PROP_ID_TO_NAME = new String[22];
    private static final Map<String, Integer> PROP_NAME_TO_ID = new HashMap<>();

    static {

        PROP_ID_TO_NAME[PROP_ID_userPass] = PROP_NAME_userPass;
        PROP_NAME_TO_ID.put(PROP_NAME_userPass, PROP_ID_userPass);

        PROP_ID_TO_NAME[PROP_ID_deptId] = PROP_NAME_deptId;
        PROP_NAME_TO_ID.put(PROP_NAME_deptId, PROP_ID_deptId);

        PROP_ID_TO_NAME[PROP_ID_sid] = PROP_NAME_sid;
        PROP_NAME_TO_ID.put(PROP_NAME_sid, PROP_ID_sid);

        PROP_ID_TO_NAME[PROP_ID_userName] = PROP_NAME_userName;
        PROP_NAME_TO_ID.put(PROP_NAME_userName, PROP_ID_userName);

    }

    /* : USER_PASS */
    private java.lang.String _userPass;

    /* : DEPT_ID */
    private java.lang.String _deptId;

    /* : U_ID */
    private java.lang.String _sid;

    /* : USER_NAME */
    private java.lang.String _userName;

    public _UserInfo() {
    }

    protected UserInfo newInstance() {
        return new UserInfo();
    }

    @Override
    public UserInfo cloneInstance() {
        UserInfo entity = newInstance();
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
        return "test.entity.UserInfo";
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

            case PROP_ID_userPass:
                return getUserPass();

            case PROP_ID_deptId:
                return getDeptId();

            case PROP_ID_sid:
                return getSid();

            case PROP_ID_userName:
                return getUserName();

            default:
                return super.orm_propValue(propId);
        }
    }

    @Override
    public void orm_propValue(int propId, Object value) {
        switch (propId) {

            case PROP_ID_userPass: {
                java.lang.String typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toString(value, err -> newTypeConversionError(PROP_NAME_userPass));
                }
                setUserPass(typedValue);
                break;
            }

            case PROP_ID_deptId: {
                java.lang.String typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toString(value, err -> newTypeConversionError(PROP_NAME_deptId));
                }
                setDeptId(typedValue);
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

            case PROP_ID_userName: {
                java.lang.String typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toString(value, err -> newTypeConversionError(PROP_NAME_userName));
                }
                setUserName(typedValue);
                break;
            }

            default:
                super.orm_propValue(propId, value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch (propId) {

            case PROP_ID_userPass: {
                onInitProp(propId);
                this._userPass = (java.lang.String) value;

                break;
            }

            case PROP_ID_deptId: {
                onInitProp(propId);
                this._deptId = (java.lang.String) value;

                break;
            }

            case PROP_ID_sid: {
                onInitProp(propId);
                this._sid = (java.lang.String) value;
                orm_id(); // 如果是设置主键字段，则触发watcher
                break;
            }

            case PROP_ID_userName: {
                onInitProp(propId);
                this._userName = (java.lang.String) value;

                break;
            }

            default:
                super.orm_internalSet(propId, value);
        }
    }

    /**
     * : USER_PASS
     */
    public java.lang.String getUserPass() {
        onPropGet(PROP_ID_userPass);
        return _userPass;
    }

    /**
     * : USER_PASS
     */
    public void setUserPass(java.lang.String value) {
        if (onPropSet(PROP_ID_userPass, value)) {
            this._userPass = value;
            internalClearRefs(PROP_ID_userPass);

        }
    }

    /**
     * : DEPT_ID
     */
    public java.lang.String getDeptId() {
        onPropGet(PROP_ID_deptId);
        return _deptId;
    }

    /**
     * : DEPT_ID
     */
    public void setDeptId(java.lang.String value) {
        if (onPropSet(PROP_ID_deptId, value)) {
            this._deptId = value;
            internalClearRefs(PROP_ID_deptId);

        }
    }

    /**
     * : U_ID
     */
    public java.lang.String getSid() {
        onPropGet(PROP_ID_sid);
        return _sid;
    }

    /**
     * : U_ID
     */
    public void setSid(java.lang.String value) {
        if (onPropSet(PROP_ID_sid, value)) {
            this._sid = value;
            internalClearRefs(PROP_ID_sid);
            orm_id();
        }
    }

    /**
     * : USER_NAME
     */
    public java.lang.String getUserName() {
        onPropGet(PROP_ID_userName);
        return _userName;
    }

    /**
     * : USER_NAME
     */
    public void setUserName(java.lang.String value) {
        if (onPropSet(PROP_ID_userName, value)) {
            this._userName = value;
            internalClearRefs(PROP_ID_userName);

        }
    }

    /**
     *
     */
    public test.entity.Department getDept() {
        return (test.entity.Department) internalGetRefEntity(PROP_NAME_dept);
    }

    public void setDept(test.entity.Department refEntity) {
        if (refEntity == null) {

            this.setDeptId(null);

        } else {
            internalSetRefEntity(PROP_NAME_dept, refEntity, () -> {

                this.setDeptId(refEntity.getSid());

            });
        }
    }

    private final OrmEntitySet<test.entity.TestSubClass> _testSubClasses = new OrmEntitySet<>(this,
            PROP_NAME_testSubClasses, test.entity.TestSubClass.PROP_NAME_user2, null, test.entity.TestSubClass.class);

    /**
     * 。 refPropName: user2, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<test.entity.TestSubClass> getTestSubClasses() {
        return _testSubClasses;
    }

}
