package io.nop.app._gen;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.app.SimsClass;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 班级: sims_class
 */
public class _SimsClass extends DynamicOrmEntity {

    /* 所属专业ID: MAJOR_ID VARCHAR */
    public static final String PROP_NAME_majorId = "majorId";
    public static final int PROP_ID_majorId = 2;

    /* 班级ID: CLASS_ID VARCHAR */
    public static final String PROP_NAME_classId = "classId";
    public static final int PROP_ID_classId = 3;

    /* 班级名称: CLASS_NAME VARCHAR */
    public static final String PROP_NAME_className = "className";
    public static final int PROP_ID_className = 4;

    /* 班级人数: STUDENT_NUMBER INTEGER */
    public static final String PROP_NAME_studentNumber = "studentNumber";
    public static final int PROP_ID_studentNumber = 5;

    /* 辅导员: ADVISER VARCHAR */
    public static final String PROP_NAME_adviser = "adviser";
    public static final int PROP_ID_adviser = 6;

    /* 成立时间: ESTAB_DATE DATETIME */
    public static final String PROP_NAME_estabDate = "estabDate";
    public static final int PROP_ID_estabDate = 7;

    /* 学习年数: YEAR_NUMBER INTEGER */
    public static final String PROP_NAME_yearNumber = "yearNumber";
    public static final int PROP_ID_yearNumber = 8;

    /* 租户号: TENANT_ID VARCHAR */
    public static final String PROP_NAME_tenantId = "tenantId";
    public static final int PROP_ID_tenantId = 9;

    /* 乐观锁: REVISION INTEGER */
    public static final String PROP_NAME_revision = "revision";
    public static final int PROP_ID_revision = 10;

    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 11;

    /* 创建时间: CREATED_TIME DATETIME */
    public static final String PROP_NAME_createdTime = "createdTime";
    public static final int PROP_ID_createdTime = 12;

    /* 更新人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 13;

    /* 更新时间: UPDATED_TIME DATETIME */
    public static final String PROP_NAME_updatedTime = "updatedTime";
    public static final int PROP_ID_updatedTime = 14;

    /* 所在学院: COLLEGE_ID VARCHAR */
    public static final String PROP_NAME_collegeId = "collegeId";
    public static final int PROP_ID_collegeId = 100;

    /* : JSON_EXT VARCHAR */
    public static final String PROP_NAME_jsonExt = "jsonExt";
    public static final int PROP_ID_jsonExt = 101;

    private static int _PROP_ID_BOUND = 102;

    /* relation: */
    public static final String PROP_NAME_simsCollege = "simsCollege";

    /* alias: simsCollege.collegeName */
    public static final String PROP_NAME_collegeName = "collegeName";

    /* component: */
    public static final String PROP_NAME_jsonExtComponent = "jsonExtComponent";

    public static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_classId);
    public static final int[] PK_PROP_IDS = new int[]{PROP_ID_classId};

    private static final String[] PROP_ID_TO_NAME = new String[102];
    private static final Map<String, Integer> PROP_NAME_TO_ID = new HashMap<>();

    static {

        PROP_ID_TO_NAME[PROP_ID_majorId] = PROP_NAME_majorId;
        PROP_NAME_TO_ID.put(PROP_NAME_majorId, PROP_ID_majorId);

        PROP_ID_TO_NAME[PROP_ID_classId] = PROP_NAME_classId;
        PROP_NAME_TO_ID.put(PROP_NAME_classId, PROP_ID_classId);

        PROP_ID_TO_NAME[PROP_ID_className] = PROP_NAME_className;
        PROP_NAME_TO_ID.put(PROP_NAME_className, PROP_ID_className);

        PROP_ID_TO_NAME[PROP_ID_studentNumber] = PROP_NAME_studentNumber;
        PROP_NAME_TO_ID.put(PROP_NAME_studentNumber, PROP_ID_studentNumber);

        PROP_ID_TO_NAME[PROP_ID_adviser] = PROP_NAME_adviser;
        PROP_NAME_TO_ID.put(PROP_NAME_adviser, PROP_ID_adviser);

        PROP_ID_TO_NAME[PROP_ID_estabDate] = PROP_NAME_estabDate;
        PROP_NAME_TO_ID.put(PROP_NAME_estabDate, PROP_ID_estabDate);

        PROP_ID_TO_NAME[PROP_ID_yearNumber] = PROP_NAME_yearNumber;
        PROP_NAME_TO_ID.put(PROP_NAME_yearNumber, PROP_ID_yearNumber);

        PROP_ID_TO_NAME[PROP_ID_tenantId] = PROP_NAME_tenantId;
        PROP_NAME_TO_ID.put(PROP_NAME_tenantId, PROP_ID_tenantId);

        PROP_ID_TO_NAME[PROP_ID_revision] = PROP_NAME_revision;
        PROP_NAME_TO_ID.put(PROP_NAME_revision, PROP_ID_revision);

        PROP_ID_TO_NAME[PROP_ID_createdBy] = PROP_NAME_createdBy;
        PROP_NAME_TO_ID.put(PROP_NAME_createdBy, PROP_ID_createdBy);

        PROP_ID_TO_NAME[PROP_ID_createdTime] = PROP_NAME_createdTime;
        PROP_NAME_TO_ID.put(PROP_NAME_createdTime, PROP_ID_createdTime);

        PROP_ID_TO_NAME[PROP_ID_updatedBy] = PROP_NAME_updatedBy;
        PROP_NAME_TO_ID.put(PROP_NAME_updatedBy, PROP_ID_updatedBy);

        PROP_ID_TO_NAME[PROP_ID_updatedTime] = PROP_NAME_updatedTime;
        PROP_NAME_TO_ID.put(PROP_NAME_updatedTime, PROP_ID_updatedTime);

        PROP_ID_TO_NAME[PROP_ID_collegeId] = PROP_NAME_collegeId;
        PROP_NAME_TO_ID.put(PROP_NAME_collegeId, PROP_ID_collegeId);

        PROP_ID_TO_NAME[PROP_ID_jsonExt] = PROP_NAME_jsonExt;
        PROP_NAME_TO_ID.put(PROP_NAME_jsonExt, PROP_ID_jsonExt);

    }

    /* 所属专业ID: MAJOR_ID */
    private java.lang.String _majorId;

    /* 班级ID: CLASS_ID */
    private java.lang.String _classId;

    /* 班级名称: CLASS_NAME */
    private java.lang.String _className;

    /* 班级人数: STUDENT_NUMBER */
    private java.lang.Integer _studentNumber;

    /* 辅导员: ADVISER */
    private java.lang.String _adviser;

    /* 成立时间: ESTAB_DATE */
    private java.time.LocalDateTime _estabDate;

    /* 学习年数: YEAR_NUMBER */
    private java.lang.Integer _yearNumber;

    /* 租户号: TENANT_ID */
    private java.lang.String _tenantId;

    /* 乐观锁: REVISION */
    private java.lang.Integer _revision;

    /* 创建人: CREATED_BY */
    private java.lang.String _createdBy;

    /* 创建时间: CREATED_TIME */
    private java.time.LocalDateTime _createdTime;

    /* 更新人: UPDATED_BY */
    private java.lang.String _updatedBy;

    /* 更新时间: UPDATED_TIME */
    private java.time.LocalDateTime _updatedTime;

    /* 所在学院: COLLEGE_ID */
    private java.lang.String _collegeId;

    /* : JSON_EXT */
    private java.lang.String _jsonExt;

    public _SimsClass() {
    }

    protected SimsClass newInstance() {
        return new SimsClass();
    }

    @Override
    public SimsClass cloneInstance() {
        SimsClass entity = newInstance();
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
        return "io.nop.app.SimsClass";
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

        return buildSimpleId(PROP_ID_classId);

    }

    @Override
    public boolean orm_isPrimary(int propId) {

        return propId == PROP_ID_classId;

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

            case PROP_ID_majorId:
                return getMajorId();

            case PROP_ID_classId:
                return getClassId();

            case PROP_ID_className:
                return getClassName();

            case PROP_ID_studentNumber:
                return getStudentNumber();

            case PROP_ID_adviser:
                return getAdviser();

            case PROP_ID_estabDate:
                return getEstabDate();

            case PROP_ID_yearNumber:
                return getYearNumber();

            case PROP_ID_tenantId:
                return getTenantId();

            case PROP_ID_revision:
                return getRevision();

            case PROP_ID_createdBy:
                return getCreatedBy();

            case PROP_ID_createdTime:
                return getCreatedTime();

            case PROP_ID_updatedBy:
                return getUpdatedBy();

            case PROP_ID_updatedTime:
                return getUpdatedTime();

            case PROP_ID_collegeId:
                return getCollegeId();

            case PROP_ID_jsonExt:
                return getJsonExt();

            default:
                return super.orm_propValue(propId);
        }
    }

    @Override
    public void orm_propValue(int propId, Object value) {
        switch (propId) {

            case PROP_ID_majorId: {
                java.lang.String typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toString(value, err -> newTypeConversionError(PROP_NAME_majorId));
                }
                setMajorId(typedValue);
                break;
            }

            case PROP_ID_classId: {
                java.lang.String typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toString(value, err -> newTypeConversionError(PROP_NAME_classId));
                }
                setClassId(typedValue);
                break;
            }

            case PROP_ID_className: {
                java.lang.String typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toString(value, err -> newTypeConversionError(PROP_NAME_className));
                }
                setClassName(typedValue);
                break;
            }

            case PROP_ID_studentNumber: {
                java.lang.Integer typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toInteger(value, err -> newTypeConversionError(PROP_NAME_studentNumber));
                }
                setStudentNumber(typedValue);
                break;
            }

            case PROP_ID_adviser: {
                java.lang.String typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toString(value, err -> newTypeConversionError(PROP_NAME_adviser));
                }
                setAdviser(typedValue);
                break;
            }

            case PROP_ID_estabDate: {
                java.time.LocalDateTime typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toLocalDateTime(value, err -> newTypeConversionError(PROP_NAME_estabDate));
                }
                setEstabDate(typedValue);
                break;
            }

            case PROP_ID_yearNumber: {
                java.lang.Integer typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toInteger(value, err -> newTypeConversionError(PROP_NAME_yearNumber));
                }
                setYearNumber(typedValue);
                break;
            }

            case PROP_ID_tenantId: {
                java.lang.String typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toString(value, err -> newTypeConversionError(PROP_NAME_tenantId));
                }
                setTenantId(typedValue);
                break;
            }

            case PROP_ID_revision: {
                java.lang.Integer typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toInteger(value, err -> newTypeConversionError(PROP_NAME_revision));
                }
                setRevision(typedValue);
                break;
            }

            case PROP_ID_createdBy: {
                java.lang.String typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toString(value, err -> newTypeConversionError(PROP_NAME_createdBy));
                }
                setCreatedBy(typedValue);
                break;
            }

            case PROP_ID_createdTime: {
                java.time.LocalDateTime typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toLocalDateTime(value, err -> newTypeConversionError(PROP_NAME_createdTime));
                }
                setCreatedTime(typedValue);
                break;
            }

            case PROP_ID_updatedBy: {
                java.lang.String typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toString(value, err -> newTypeConversionError(PROP_NAME_updatedBy));
                }
                setUpdatedBy(typedValue);
                break;
            }

            case PROP_ID_updatedTime: {
                java.time.LocalDateTime typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toLocalDateTime(value, err -> newTypeConversionError(PROP_NAME_updatedTime));
                }
                setUpdatedTime(typedValue);
                break;
            }

            case PROP_ID_collegeId: {
                java.lang.String typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toString(value, err -> newTypeConversionError(PROP_NAME_collegeId));
                }
                setCollegeId(typedValue);
                break;
            }

            case PROP_ID_jsonExt: {
                java.lang.String typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toString(value, err -> newTypeConversionError(PROP_NAME_jsonExt));
                }
                setJsonExt(typedValue);
                break;
            }

            default:
                super.orm_propValue(propId, value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch (propId) {

            case PROP_ID_majorId: {
                onInitProp(propId);
                this._majorId = (java.lang.String) value;

                break;
            }

            case PROP_ID_classId: {
                onInitProp(propId);
                this._classId = (java.lang.String) value;
                orm_id(); // 如果是设置主键字段，则触发watcher
                break;
            }

            case PROP_ID_className: {
                onInitProp(propId);
                this._className = (java.lang.String) value;

                break;
            }

            case PROP_ID_studentNumber: {
                onInitProp(propId);
                this._studentNumber = (java.lang.Integer) value;

                break;
            }

            case PROP_ID_adviser: {
                onInitProp(propId);
                this._adviser = (java.lang.String) value;

                break;
            }

            case PROP_ID_estabDate: {
                onInitProp(propId);
                this._estabDate = (java.time.LocalDateTime) value;

                break;
            }

            case PROP_ID_yearNumber: {
                onInitProp(propId);
                this._yearNumber = (java.lang.Integer) value;

                break;
            }

            case PROP_ID_tenantId: {
                onInitProp(propId);
                this._tenantId = (java.lang.String) value;

                break;
            }

            case PROP_ID_revision: {
                onInitProp(propId);
                this._revision = (java.lang.Integer) value;

                break;
            }

            case PROP_ID_createdBy: {
                onInitProp(propId);
                this._createdBy = (java.lang.String) value;

                break;
            }

            case PROP_ID_createdTime: {
                onInitProp(propId);
                this._createdTime = (java.time.LocalDateTime) value;

                break;
            }

            case PROP_ID_updatedBy: {
                onInitProp(propId);
                this._updatedBy = (java.lang.String) value;

                break;
            }

            case PROP_ID_updatedTime: {
                onInitProp(propId);
                this._updatedTime = (java.time.LocalDateTime) value;

                break;
            }

            case PROP_ID_collegeId: {
                onInitProp(propId);
                this._collegeId = (java.lang.String) value;

                break;
            }

            case PROP_ID_jsonExt: {
                onInitProp(propId);
                this._jsonExt = (java.lang.String) value;

                break;
            }

            default:
                super.orm_internalSet(propId, value);
        }
    }

    /**
     * 所属专业ID: MAJOR_ID
     */
    public java.lang.String getMajorId() {
        onPropGet(PROP_ID_majorId);
        return _majorId;
    }

    /**
     * 所属专业ID: MAJOR_ID
     */
    public void setMajorId(java.lang.String value) {
        if (onPropSet(PROP_ID_majorId, value)) {
            this._majorId = value;
            internalClearRefs(PROP_ID_majorId);

        }
    }

    /**
     * 班级ID: CLASS_ID
     */
    public java.lang.String getClassId() {
        onPropGet(PROP_ID_classId);
        return _classId;
    }

    /**
     * 班级ID: CLASS_ID
     */
    public void setClassId(java.lang.String value) {
        if (onPropSet(PROP_ID_classId, value)) {
            this._classId = value;
            internalClearRefs(PROP_ID_classId);
            orm_id();
        }
    }

    /**
     * 班级名称: CLASS_NAME
     */
    public java.lang.String getClassName() {
        onPropGet(PROP_ID_className);
        return _className;
    }

    /**
     * 班级名称: CLASS_NAME
     */
    public void setClassName(java.lang.String value) {
        if (onPropSet(PROP_ID_className, value)) {
            this._className = value;
            internalClearRefs(PROP_ID_className);

        }
    }

    /**
     * 班级人数: STUDENT_NUMBER
     */
    public java.lang.Integer getStudentNumber() {
        onPropGet(PROP_ID_studentNumber);
        return _studentNumber;
    }

    /**
     * 班级人数: STUDENT_NUMBER
     */
    public void setStudentNumber(java.lang.Integer value) {
        if (onPropSet(PROP_ID_studentNumber, value)) {
            this._studentNumber = value;
            internalClearRefs(PROP_ID_studentNumber);

        }
    }

    /**
     * 辅导员: ADVISER
     */
    public java.lang.String getAdviser() {
        onPropGet(PROP_ID_adviser);
        return _adviser;
    }

    /**
     * 辅导员: ADVISER
     */
    public void setAdviser(java.lang.String value) {
        if (onPropSet(PROP_ID_adviser, value)) {
            this._adviser = value;
            internalClearRefs(PROP_ID_adviser);

        }
    }

    /**
     * 成立时间: ESTAB_DATE
     */
    public java.time.LocalDateTime getEstabDate() {
        onPropGet(PROP_ID_estabDate);
        return _estabDate;
    }

    /**
     * 成立时间: ESTAB_DATE
     */
    public void setEstabDate(java.time.LocalDateTime value) {
        if (onPropSet(PROP_ID_estabDate, value)) {
            this._estabDate = value;
            internalClearRefs(PROP_ID_estabDate);

        }
    }

    /**
     * 学习年数: YEAR_NUMBER
     */
    public java.lang.Integer getYearNumber() {
        onPropGet(PROP_ID_yearNumber);
        return _yearNumber;
    }

    /**
     * 学习年数: YEAR_NUMBER
     */
    public void setYearNumber(java.lang.Integer value) {
        if (onPropSet(PROP_ID_yearNumber, value)) {
            this._yearNumber = value;
            internalClearRefs(PROP_ID_yearNumber);

        }
    }

    /**
     * 租户号: TENANT_ID
     */
    public java.lang.String getTenantId() {
        onPropGet(PROP_ID_tenantId);
        return _tenantId;
    }

    /**
     * 租户号: TENANT_ID
     */
    public void setTenantId(java.lang.String value) {
        if (onPropSet(PROP_ID_tenantId, value)) {
            this._tenantId = value;
            internalClearRefs(PROP_ID_tenantId);

        }
    }

    /**
     * 乐观锁: REVISION
     */
    public java.lang.Integer getRevision() {
        onPropGet(PROP_ID_revision);
        return _revision;
    }

    /**
     * 乐观锁: REVISION
     */
    public void setRevision(java.lang.Integer value) {
        if (onPropSet(PROP_ID_revision, value)) {
            this._revision = value;
            internalClearRefs(PROP_ID_revision);

        }
    }

    /**
     * 创建人: CREATED_BY
     */
    public java.lang.String getCreatedBy() {
        onPropGet(PROP_ID_createdBy);
        return _createdBy;
    }

    /**
     * 创建人: CREATED_BY
     */
    public void setCreatedBy(java.lang.String value) {
        if (onPropSet(PROP_ID_createdBy, value)) {
            this._createdBy = value;
            internalClearRefs(PROP_ID_createdBy);

        }
    }

    /**
     * 创建时间: CREATED_TIME
     */
    public java.time.LocalDateTime getCreatedTime() {
        onPropGet(PROP_ID_createdTime);
        return _createdTime;
    }

    /**
     * 创建时间: CREATED_TIME
     */
    public void setCreatedTime(java.time.LocalDateTime value) {
        if (onPropSet(PROP_ID_createdTime, value)) {
            this._createdTime = value;
            internalClearRefs(PROP_ID_createdTime);

        }
    }

    /**
     * 更新人: UPDATED_BY
     */
    public java.lang.String getUpdatedBy() {
        onPropGet(PROP_ID_updatedBy);
        return _updatedBy;
    }

    /**
     * 更新人: UPDATED_BY
     */
    public void setUpdatedBy(java.lang.String value) {
        if (onPropSet(PROP_ID_updatedBy, value)) {
            this._updatedBy = value;
            internalClearRefs(PROP_ID_updatedBy);

        }
    }

    /**
     * 更新时间: UPDATED_TIME
     */
    public java.time.LocalDateTime getUpdatedTime() {
        onPropGet(PROP_ID_updatedTime);
        return _updatedTime;
    }

    /**
     * 更新时间: UPDATED_TIME
     */
    public void setUpdatedTime(java.time.LocalDateTime value) {
        if (onPropSet(PROP_ID_updatedTime, value)) {
            this._updatedTime = value;
            internalClearRefs(PROP_ID_updatedTime);

        }
    }

    /**
     * 所在学院: COLLEGE_ID
     */
    public java.lang.String getCollegeId() {
        onPropGet(PROP_ID_collegeId);
        return _collegeId;
    }

    /**
     * 所在学院: COLLEGE_ID
     */
    public void setCollegeId(java.lang.String value) {
        if (onPropSet(PROP_ID_collegeId, value)) {
            this._collegeId = value;
            internalClearRefs(PROP_ID_collegeId);

        }
    }

    /**
     * : JSON_EXT
     */
    public java.lang.String getJsonExt() {
        onPropGet(PROP_ID_jsonExt);
        return _jsonExt;
    }

    /**
     * : JSON_EXT
     */
    public void setJsonExt(java.lang.String value) {
        if (onPropSet(PROP_ID_jsonExt, value)) {
            this._jsonExt = value;
            internalClearRefs(PROP_ID_jsonExt);

        }
    }

    /**
     *
     */
    public io.nop.app.SimsCollege getSimsCollege() {
        return (io.nop.app.SimsCollege) internalGetRefEntity(PROP_NAME_simsCollege);
    }

    public void setSimsCollege(io.nop.app.SimsCollege refEntity) {
        if (refEntity == null) {

            this.setCollegeId(null);

        } else {
            internalSetRefEntity(PROP_NAME_simsCollege, refEntity, () -> {

                this.setCollegeId(refEntity.getCollegeId());

            });
        }
    }

    public java.lang.String getCollegeName() {
        return (java.lang.String) internalGetAliasValue("simsCollege.collegeName");
    }

    public void setCollegeName(java.lang.String value) {
        internalSetAliasValue("simsCollege.collegeName", value);
    }

    private io.nop.orm.support.JsonOrmComponent _jsonExtComponent;

    private static Map<String, Integer> COMPONENT_PROP_ID_MAP_jsonExtComponent = new HashMap<>();

    static {

        COMPONENT_PROP_ID_MAP_jsonExtComponent.put(io.nop.orm.support.JsonOrmComponent.PROP_NAME__jsonText,
                PROP_ID_jsonExt);

    }

    public io.nop.orm.support.JsonOrmComponent getJsonExtComponent() {
        if (_jsonExtComponent == null) {
            _jsonExtComponent = new io.nop.orm.support.JsonOrmComponent();
            _jsonExtComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_jsonExtComponent);
        }
        return _jsonExtComponent;
    }

}
