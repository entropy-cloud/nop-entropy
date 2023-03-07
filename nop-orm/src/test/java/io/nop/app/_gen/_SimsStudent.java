package io.nop.app._gen;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.app.SimsStudent;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 学生: sims_student
 */
public class _SimsStudent extends DynamicOrmEntity {

    /* 所在学院ID: COLLEGE_ID VARCHAR */
    public static final String PROP_NAME_collegeId = "collegeId";
    public static final int PROP_ID_collegeId = 1;

    /* 所在班级ID: CLASS_ID VARCHAR */
    public static final String PROP_NAME_classId = "classId";
    public static final int PROP_ID_classId = 2;

    /* 学生ID: STUDENT_ID VARCHAR */
    public static final String PROP_NAME_studentId = "studentId";
    public static final int PROP_ID_studentId = 3;

    /* 学生姓名: STUDENT_NAME VARCHAR */
    public static final String PROP_NAME_studentName = "studentName";
    public static final int PROP_ID_studentName = 4;

    /* 英文名: ENG_NAME VARCHAR */
    public static final String PROP_NAME_engName = "engName";
    public static final int PROP_ID_engName = 5;

    /* 身份证号: ID_CARD_NO VARCHAR */
    public static final String PROP_NAME_idCardNo = "idCardNo";
    public static final int PROP_ID_idCardNo = 6;

    /* 手机号: MOBILE_PHONE VARCHAR */
    public static final String PROP_NAME_mobilePhone = "mobilePhone";
    public static final int PROP_ID_mobilePhone = 7;

    /* 性别: GENDER VARCHAR */
    public static final String PROP_NAME_gender = "gender";
    public static final int PROP_ID_gender = 8;

    /* 月薪: MONTHLY_SALARY DECIMAL */
    public static final String PROP_NAME_monthlySalary = "monthlySalary";
    public static final int PROP_ID_monthlySalary = 9;

    /* 出生日期: BIRTH DATETIME */
    public static final String PROP_NAME_birth = "birth";
    public static final int PROP_ID_birth = 10;

    /* 头像: AVATAR INTEGER */
    public static final String PROP_NAME_avatar = "avatar";
    public static final int PROP_ID_avatar = 11;

    /* 身高: HEIGHT INTEGER */
    public static final String PROP_NAME_height = "height";
    public static final int PROP_ID_height = 12;

    /* 体重: WEIGHT INTEGER */
    public static final String PROP_NAME_weight = "weight";
    public static final int PROP_ID_weight = 13;

    /* 名族: NATION VARCHAR */
    public static final String PROP_NAME_nation = "nation";
    public static final int PROP_ID_nation = 14;

    /* 政治面貌: POLITICAL VARCHAR */
    public static final String PROP_NAME_political = "political";
    public static final int PROP_ID_political = 15;

    /* 婚姻状况: MARITAL VARCHAR */
    public static final String PROP_NAME_marital = "marital";
    public static final int PROP_ID_marital = 16;

    /* 籍贯（省）: DOMICILE_PLACE_PROVINCE VARCHAR */
    public static final String PROP_NAME_domicilePlaceProvince = "domicilePlaceProvince";
    public static final int PROP_ID_domicilePlaceProvince = 17;

    /* 籍贯（市）: DOMICILE_PLACE_CITY VARCHAR */
    public static final String PROP_NAME_domicilePlaceCity = "domicilePlaceCity";
    public static final int PROP_ID_domicilePlaceCity = 18;

    /* 户籍地址: DOMICILE_PLACE_ADDRESS VARCHAR */
    public static final String PROP_NAME_domicilePlaceAddress = "domicilePlaceAddress";
    public static final int PROP_ID_domicilePlaceAddress = 19;

    /* 爱好: HOBBY VARCHAR */
    public static final String PROP_NAME_hobby = "hobby";
    public static final int PROP_ID_hobby = 20;

    /* 简要介绍: INTRO VARCHAR */
    public static final String PROP_NAME_intro = "intro";
    public static final int PROP_ID_intro = 21;

    /* 居住地址: PRESENT_ADDRESS VARCHAR */
    public static final String PROP_NAME_presentAddress = "presentAddress";
    public static final int PROP_ID_presentAddress = 22;

    /* 电子邮件: EMAIL VARCHAR */
    public static final String PROP_NAME_email = "email";
    public static final int PROP_ID_email = 23;

    /* 入学日期: ENTRY_DATE DATETIME */
    public static final String PROP_NAME_entryDate = "entryDate";
    public static final int PROP_ID_entryDate = 24;

    /* 状态: STATUS VARCHAR */
    public static final String PROP_NAME_status = "status";
    public static final int PROP_ID_status = 25;

    /* 租户号: TENANT_ID VARCHAR */
    public static final String PROP_NAME_tenantId = "tenantId";
    public static final int PROP_ID_tenantId = 26;

    /* 乐观锁: REVISION INTEGER */
    public static final String PROP_NAME_revision = "revision";
    public static final int PROP_ID_revision = 27;

    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 28;

    /* 创建时间: CREATED_TIME DATETIME */
    public static final String PROP_NAME_createdTime = "createdTime";
    public static final int PROP_ID_createdTime = 29;

    /* 更新人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 30;

    /* 更新时间: UPDATED_TIME DATETIME */
    public static final String PROP_NAME_updatedTime = "updatedTime";
    public static final int PROP_ID_updatedTime = 31;

    private static int _PROP_ID_BOUND = 32;

    public static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_studentId);
    public static final int[] PK_PROP_IDS = new int[]{PROP_ID_studentId};

    private static final String[] PROP_ID_TO_NAME = new String[32];
    private static final Map<String, Integer> PROP_NAME_TO_ID = new HashMap<>();

    static {

        PROP_ID_TO_NAME[PROP_ID_collegeId] = PROP_NAME_collegeId;
        PROP_NAME_TO_ID.put(PROP_NAME_collegeId, PROP_ID_collegeId);

        PROP_ID_TO_NAME[PROP_ID_classId] = PROP_NAME_classId;
        PROP_NAME_TO_ID.put(PROP_NAME_classId, PROP_ID_classId);

        PROP_ID_TO_NAME[PROP_ID_studentId] = PROP_NAME_studentId;
        PROP_NAME_TO_ID.put(PROP_NAME_studentId, PROP_ID_studentId);

        PROP_ID_TO_NAME[PROP_ID_studentName] = PROP_NAME_studentName;
        PROP_NAME_TO_ID.put(PROP_NAME_studentName, PROP_ID_studentName);

        PROP_ID_TO_NAME[PROP_ID_engName] = PROP_NAME_engName;
        PROP_NAME_TO_ID.put(PROP_NAME_engName, PROP_ID_engName);

        PROP_ID_TO_NAME[PROP_ID_idCardNo] = PROP_NAME_idCardNo;
        PROP_NAME_TO_ID.put(PROP_NAME_idCardNo, PROP_ID_idCardNo);

        PROP_ID_TO_NAME[PROP_ID_mobilePhone] = PROP_NAME_mobilePhone;
        PROP_NAME_TO_ID.put(PROP_NAME_mobilePhone, PROP_ID_mobilePhone);

        PROP_ID_TO_NAME[PROP_ID_gender] = PROP_NAME_gender;
        PROP_NAME_TO_ID.put(PROP_NAME_gender, PROP_ID_gender);

        PROP_ID_TO_NAME[PROP_ID_monthlySalary] = PROP_NAME_monthlySalary;
        PROP_NAME_TO_ID.put(PROP_NAME_monthlySalary, PROP_ID_monthlySalary);

        PROP_ID_TO_NAME[PROP_ID_birth] = PROP_NAME_birth;
        PROP_NAME_TO_ID.put(PROP_NAME_birth, PROP_ID_birth);

        PROP_ID_TO_NAME[PROP_ID_avatar] = PROP_NAME_avatar;
        PROP_NAME_TO_ID.put(PROP_NAME_avatar, PROP_ID_avatar);

        PROP_ID_TO_NAME[PROP_ID_height] = PROP_NAME_height;
        PROP_NAME_TO_ID.put(PROP_NAME_height, PROP_ID_height);

        PROP_ID_TO_NAME[PROP_ID_weight] = PROP_NAME_weight;
        PROP_NAME_TO_ID.put(PROP_NAME_weight, PROP_ID_weight);

        PROP_ID_TO_NAME[PROP_ID_nation] = PROP_NAME_nation;
        PROP_NAME_TO_ID.put(PROP_NAME_nation, PROP_ID_nation);

        PROP_ID_TO_NAME[PROP_ID_political] = PROP_NAME_political;
        PROP_NAME_TO_ID.put(PROP_NAME_political, PROP_ID_political);

        PROP_ID_TO_NAME[PROP_ID_marital] = PROP_NAME_marital;
        PROP_NAME_TO_ID.put(PROP_NAME_marital, PROP_ID_marital);

        PROP_ID_TO_NAME[PROP_ID_domicilePlaceProvince] = PROP_NAME_domicilePlaceProvince;
        PROP_NAME_TO_ID.put(PROP_NAME_domicilePlaceProvince, PROP_ID_domicilePlaceProvince);

        PROP_ID_TO_NAME[PROP_ID_domicilePlaceCity] = PROP_NAME_domicilePlaceCity;
        PROP_NAME_TO_ID.put(PROP_NAME_domicilePlaceCity, PROP_ID_domicilePlaceCity);

        PROP_ID_TO_NAME[PROP_ID_domicilePlaceAddress] = PROP_NAME_domicilePlaceAddress;
        PROP_NAME_TO_ID.put(PROP_NAME_domicilePlaceAddress, PROP_ID_domicilePlaceAddress);

        PROP_ID_TO_NAME[PROP_ID_hobby] = PROP_NAME_hobby;
        PROP_NAME_TO_ID.put(PROP_NAME_hobby, PROP_ID_hobby);

        PROP_ID_TO_NAME[PROP_ID_intro] = PROP_NAME_intro;
        PROP_NAME_TO_ID.put(PROP_NAME_intro, PROP_ID_intro);

        PROP_ID_TO_NAME[PROP_ID_presentAddress] = PROP_NAME_presentAddress;
        PROP_NAME_TO_ID.put(PROP_NAME_presentAddress, PROP_ID_presentAddress);

        PROP_ID_TO_NAME[PROP_ID_email] = PROP_NAME_email;
        PROP_NAME_TO_ID.put(PROP_NAME_email, PROP_ID_email);

        PROP_ID_TO_NAME[PROP_ID_entryDate] = PROP_NAME_entryDate;
        PROP_NAME_TO_ID.put(PROP_NAME_entryDate, PROP_ID_entryDate);

        PROP_ID_TO_NAME[PROP_ID_status] = PROP_NAME_status;
        PROP_NAME_TO_ID.put(PROP_NAME_status, PROP_ID_status);

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

    }

    /* 所在学院ID: COLLEGE_ID */
    private java.lang.String _collegeId;

    /* 所在班级ID: CLASS_ID */
    private java.lang.String _classId;

    /* 学生ID: STUDENT_ID */
    private java.lang.String _studentId;

    /* 学生姓名: STUDENT_NAME */
    private java.lang.String _studentName;

    /* 英文名: ENG_NAME */
    private java.lang.String _engName;

    /* 身份证号: ID_CARD_NO */
    private java.lang.String _idCardNo;

    /* 手机号: MOBILE_PHONE */
    private java.lang.String _mobilePhone;

    /* 性别: GENDER */
    private java.lang.String _gender;

    /* 月薪: MONTHLY_SALARY */
    private java.math.BigDecimal _monthlySalary;

    /* 出生日期: BIRTH */
    private java.time.LocalDateTime _birth;

    /* 头像: AVATAR */
    private java.lang.Integer _avatar;

    /* 身高: HEIGHT */
    private java.lang.Integer _height;

    /* 体重: WEIGHT */
    private java.lang.Integer _weight;

    /* 名族: NATION */
    private java.lang.String _nation;

    /* 政治面貌: POLITICAL */
    private java.lang.String _political;

    /* 婚姻状况: MARITAL */
    private java.lang.String _marital;

    /* 籍贯（省）: DOMICILE_PLACE_PROVINCE */
    private java.lang.String _domicilePlaceProvince;

    /* 籍贯（市）: DOMICILE_PLACE_CITY */
    private java.lang.String _domicilePlaceCity;

    /* 户籍地址: DOMICILE_PLACE_ADDRESS */
    private java.lang.String _domicilePlaceAddress;

    /* 爱好: HOBBY */
    private java.lang.String _hobby;

    /* 简要介绍: INTRO */
    private java.lang.String _intro;

    /* 居住地址: PRESENT_ADDRESS */
    private java.lang.String _presentAddress;

    /* 电子邮件: EMAIL */
    private java.lang.String _email;

    /* 入学日期: ENTRY_DATE */
    private java.time.LocalDateTime _entryDate;

    /* 状态: STATUS */
    private java.lang.String _status;

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

    public _SimsStudent() {
    }

    protected SimsStudent newInstance() {
        return new SimsStudent();
    }

    @Override
    public SimsStudent cloneInstance() {
        SimsStudent entity = newInstance();
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
        return "io.nop.app.SimsStudent";
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

        return buildSimpleId(PROP_ID_studentId);

    }

    @Override
    public boolean orm_isPrimary(int propId) {

        return propId == PROP_ID_studentId;

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

            case PROP_ID_collegeId:
                return getCollegeId();

            case PROP_ID_classId:
                return getClassId();

            case PROP_ID_studentId:
                return getStudentId();

            case PROP_ID_studentName:
                return getStudentName();

            case PROP_ID_engName:
                return getEngName();

            case PROP_ID_idCardNo:
                return getIdCardNo();

            case PROP_ID_mobilePhone:
                return getMobilePhone();

            case PROP_ID_gender:
                return getGender();

            case PROP_ID_monthlySalary:
                return getMonthlySalary();

            case PROP_ID_birth:
                return getBirth();

            case PROP_ID_avatar:
                return getAvatar();

            case PROP_ID_height:
                return getHeight();

            case PROP_ID_weight:
                return getWeight();

            case PROP_ID_nation:
                return getNation();

            case PROP_ID_political:
                return getPolitical();

            case PROP_ID_marital:
                return getMarital();

            case PROP_ID_domicilePlaceProvince:
                return getDomicilePlaceProvince();

            case PROP_ID_domicilePlaceCity:
                return getDomicilePlaceCity();

            case PROP_ID_domicilePlaceAddress:
                return getDomicilePlaceAddress();

            case PROP_ID_hobby:
                return getHobby();

            case PROP_ID_intro:
                return getIntro();

            case PROP_ID_presentAddress:
                return getPresentAddress();

            case PROP_ID_email:
                return getEmail();

            case PROP_ID_entryDate:
                return getEntryDate();

            case PROP_ID_status:
                return getStatus();

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

            default:
                return super.orm_propValue(propId);
        }
    }

    @Override
    public void orm_propValue(int propId, Object value) {
        switch (propId) {

            case PROP_ID_collegeId: {
                java.lang.String typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toString(value, err -> newTypeConversionError(PROP_NAME_collegeId));
                }
                setCollegeId(typedValue);
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

            case PROP_ID_studentId: {
                java.lang.String typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toString(value, err -> newTypeConversionError(PROP_NAME_studentId));
                }
                setStudentId(typedValue);
                break;
            }

            case PROP_ID_studentName: {
                java.lang.String typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toString(value, err -> newTypeConversionError(PROP_NAME_studentName));
                }
                setStudentName(typedValue);
                break;
            }

            case PROP_ID_engName: {
                java.lang.String typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toString(value, err -> newTypeConversionError(PROP_NAME_engName));
                }
                setEngName(typedValue);
                break;
            }

            case PROP_ID_idCardNo: {
                java.lang.String typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toString(value, err -> newTypeConversionError(PROP_NAME_idCardNo));
                }
                setIdCardNo(typedValue);
                break;
            }

            case PROP_ID_mobilePhone: {
                java.lang.String typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toString(value, err -> newTypeConversionError(PROP_NAME_mobilePhone));
                }
                setMobilePhone(typedValue);
                break;
            }

            case PROP_ID_gender: {
                java.lang.String typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toString(value, err -> newTypeConversionError(PROP_NAME_gender));
                }
                setGender(typedValue);
                break;
            }

            case PROP_ID_monthlySalary: {
                java.math.BigDecimal typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toBigDecimal(value, err -> newTypeConversionError(PROP_NAME_monthlySalary));
                }
                setMonthlySalary(typedValue);
                break;
            }

            case PROP_ID_birth: {
                java.time.LocalDateTime typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toLocalDateTime(value, err -> newTypeConversionError(PROP_NAME_birth));
                }
                setBirth(typedValue);
                break;
            }

            case PROP_ID_avatar: {
                java.lang.Integer typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toInteger(value, err -> newTypeConversionError(PROP_NAME_avatar));
                }
                setAvatar(typedValue);
                break;
            }

            case PROP_ID_height: {
                java.lang.Integer typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toInteger(value, err -> newTypeConversionError(PROP_NAME_height));
                }
                setHeight(typedValue);
                break;
            }

            case PROP_ID_weight: {
                java.lang.Integer typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toInteger(value, err -> newTypeConversionError(PROP_NAME_weight));
                }
                setWeight(typedValue);
                break;
            }

            case PROP_ID_nation: {
                java.lang.String typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toString(value, err -> newTypeConversionError(PROP_NAME_nation));
                }
                setNation(typedValue);
                break;
            }

            case PROP_ID_political: {
                java.lang.String typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toString(value, err -> newTypeConversionError(PROP_NAME_political));
                }
                setPolitical(typedValue);
                break;
            }

            case PROP_ID_marital: {
                java.lang.String typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toString(value, err -> newTypeConversionError(PROP_NAME_marital));
                }
                setMarital(typedValue);
                break;
            }

            case PROP_ID_domicilePlaceProvince: {
                java.lang.String typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toString(value,
                            err -> newTypeConversionError(PROP_NAME_domicilePlaceProvince));
                }
                setDomicilePlaceProvince(typedValue);
                break;
            }

            case PROP_ID_domicilePlaceCity: {
                java.lang.String typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toString(value, err -> newTypeConversionError(PROP_NAME_domicilePlaceCity));
                }
                setDomicilePlaceCity(typedValue);
                break;
            }

            case PROP_ID_domicilePlaceAddress: {
                java.lang.String typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toString(value,
                            err -> newTypeConversionError(PROP_NAME_domicilePlaceAddress));
                }
                setDomicilePlaceAddress(typedValue);
                break;
            }

            case PROP_ID_hobby: {
                java.lang.String typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toString(value, err -> newTypeConversionError(PROP_NAME_hobby));
                }
                setHobby(typedValue);
                break;
            }

            case PROP_ID_intro: {
                java.lang.String typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toString(value, err -> newTypeConversionError(PROP_NAME_intro));
                }
                setIntro(typedValue);
                break;
            }

            case PROP_ID_presentAddress: {
                java.lang.String typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toString(value, err -> newTypeConversionError(PROP_NAME_presentAddress));
                }
                setPresentAddress(typedValue);
                break;
            }

            case PROP_ID_email: {
                java.lang.String typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toString(value, err -> newTypeConversionError(PROP_NAME_email));
                }
                setEmail(typedValue);
                break;
            }

            case PROP_ID_entryDate: {
                java.time.LocalDateTime typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toLocalDateTime(value, err -> newTypeConversionError(PROP_NAME_entryDate));
                }
                setEntryDate(typedValue);
                break;
            }

            case PROP_ID_status: {
                java.lang.String typedValue = null;
                if (value != null) {
                    typedValue = ConvertHelper.toString(value, err -> newTypeConversionError(PROP_NAME_status));
                }
                setStatus(typedValue);
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

            default:
                super.orm_propValue(propId, value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch (propId) {

            case PROP_ID_collegeId: {
                onInitProp(propId);
                this._collegeId = (java.lang.String) value;

                break;
            }

            case PROP_ID_classId: {
                onInitProp(propId);
                this._classId = (java.lang.String) value;

                break;
            }

            case PROP_ID_studentId: {
                onInitProp(propId);
                this._studentId = (java.lang.String) value;
                orm_id(); // 如果是设置主键字段，则触发watcher
                break;
            }

            case PROP_ID_studentName: {
                onInitProp(propId);
                this._studentName = (java.lang.String) value;

                break;
            }

            case PROP_ID_engName: {
                onInitProp(propId);
                this._engName = (java.lang.String) value;

                break;
            }

            case PROP_ID_idCardNo: {
                onInitProp(propId);
                this._idCardNo = (java.lang.String) value;

                break;
            }

            case PROP_ID_mobilePhone: {
                onInitProp(propId);
                this._mobilePhone = (java.lang.String) value;

                break;
            }

            case PROP_ID_gender: {
                onInitProp(propId);
                this._gender = (java.lang.String) value;

                break;
            }

            case PROP_ID_monthlySalary: {
                onInitProp(propId);
                this._monthlySalary = (java.math.BigDecimal) value;

                break;
            }

            case PROP_ID_birth: {
                onInitProp(propId);
                this._birth = (java.time.LocalDateTime) value;

                break;
            }

            case PROP_ID_avatar: {
                onInitProp(propId);
                this._avatar = (java.lang.Integer) value;

                break;
            }

            case PROP_ID_height: {
                onInitProp(propId);
                this._height = (java.lang.Integer) value;

                break;
            }

            case PROP_ID_weight: {
                onInitProp(propId);
                this._weight = (java.lang.Integer) value;

                break;
            }

            case PROP_ID_nation: {
                onInitProp(propId);
                this._nation = (java.lang.String) value;

                break;
            }

            case PROP_ID_political: {
                onInitProp(propId);
                this._political = (java.lang.String) value;

                break;
            }

            case PROP_ID_marital: {
                onInitProp(propId);
                this._marital = (java.lang.String) value;

                break;
            }

            case PROP_ID_domicilePlaceProvince: {
                onInitProp(propId);
                this._domicilePlaceProvince = (java.lang.String) value;

                break;
            }

            case PROP_ID_domicilePlaceCity: {
                onInitProp(propId);
                this._domicilePlaceCity = (java.lang.String) value;

                break;
            }

            case PROP_ID_domicilePlaceAddress: {
                onInitProp(propId);
                this._domicilePlaceAddress = (java.lang.String) value;

                break;
            }

            case PROP_ID_hobby: {
                onInitProp(propId);
                this._hobby = (java.lang.String) value;

                break;
            }

            case PROP_ID_intro: {
                onInitProp(propId);
                this._intro = (java.lang.String) value;

                break;
            }

            case PROP_ID_presentAddress: {
                onInitProp(propId);
                this._presentAddress = (java.lang.String) value;

                break;
            }

            case PROP_ID_email: {
                onInitProp(propId);
                this._email = (java.lang.String) value;

                break;
            }

            case PROP_ID_entryDate: {
                onInitProp(propId);
                this._entryDate = (java.time.LocalDateTime) value;

                break;
            }

            case PROP_ID_status: {
                onInitProp(propId);
                this._status = (java.lang.String) value;

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

            default:
                super.orm_internalSet(propId, value);
        }
    }

    /**
     * 所在学院ID: COLLEGE_ID
     */
    public java.lang.String getCollegeId() {
        onPropGet(PROP_ID_collegeId);
        return _collegeId;
    }

    /**
     * 所在学院ID: COLLEGE_ID
     */
    public void setCollegeId(java.lang.String value) {
        if (onPropSet(PROP_ID_collegeId, value)) {
            this._collegeId = value;
            internalClearRefs(PROP_ID_collegeId);

        }
    }

    /**
     * 所在班级ID: CLASS_ID
     */
    public java.lang.String getClassId() {
        onPropGet(PROP_ID_classId);
        return _classId;
    }

    /**
     * 所在班级ID: CLASS_ID
     */
    public void setClassId(java.lang.String value) {
        if (onPropSet(PROP_ID_classId, value)) {
            this._classId = value;
            internalClearRefs(PROP_ID_classId);

        }
    }

    /**
     * 学生ID: STUDENT_ID
     */
    public java.lang.String getStudentId() {
        onPropGet(PROP_ID_studentId);
        return _studentId;
    }

    /**
     * 学生ID: STUDENT_ID
     */
    public void setStudentId(java.lang.String value) {
        if (onPropSet(PROP_ID_studentId, value)) {
            this._studentId = value;
            internalClearRefs(PROP_ID_studentId);
            orm_id();
        }
    }

    /**
     * 学生姓名: STUDENT_NAME
     */
    public java.lang.String getStudentName() {
        onPropGet(PROP_ID_studentName);
        return _studentName;
    }

    /**
     * 学生姓名: STUDENT_NAME
     */
    public void setStudentName(java.lang.String value) {
        if (onPropSet(PROP_ID_studentName, value)) {
            this._studentName = value;
            internalClearRefs(PROP_ID_studentName);

        }
    }

    /**
     * 英文名: ENG_NAME
     */
    public java.lang.String getEngName() {
        onPropGet(PROP_ID_engName);
        return _engName;
    }

    /**
     * 英文名: ENG_NAME
     */
    public void setEngName(java.lang.String value) {
        if (onPropSet(PROP_ID_engName, value)) {
            this._engName = value;
            internalClearRefs(PROP_ID_engName);

        }
    }

    /**
     * 身份证号: ID_CARD_NO
     */
    public java.lang.String getIdCardNo() {
        onPropGet(PROP_ID_idCardNo);
        return _idCardNo;
    }

    /**
     * 身份证号: ID_CARD_NO
     */
    public void setIdCardNo(java.lang.String value) {
        if (onPropSet(PROP_ID_idCardNo, value)) {
            this._idCardNo = value;
            internalClearRefs(PROP_ID_idCardNo);

        }
    }

    /**
     * 手机号: MOBILE_PHONE
     */
    public java.lang.String getMobilePhone() {
        onPropGet(PROP_ID_mobilePhone);
        return _mobilePhone;
    }

    /**
     * 手机号: MOBILE_PHONE
     */
    public void setMobilePhone(java.lang.String value) {
        if (onPropSet(PROP_ID_mobilePhone, value)) {
            this._mobilePhone = value;
            internalClearRefs(PROP_ID_mobilePhone);

        }
    }

    /**
     * 性别: GENDER
     */
    public java.lang.String getGender() {
        onPropGet(PROP_ID_gender);
        return _gender;
    }

    /**
     * 性别: GENDER
     */
    public void setGender(java.lang.String value) {
        if (onPropSet(PROP_ID_gender, value)) {
            this._gender = value;
            internalClearRefs(PROP_ID_gender);

        }
    }

    /**
     * 月薪: MONTHLY_SALARY
     */
    public java.math.BigDecimal getMonthlySalary() {
        onPropGet(PROP_ID_monthlySalary);
        return _monthlySalary;
    }

    /**
     * 月薪: MONTHLY_SALARY
     */
    public void setMonthlySalary(java.math.BigDecimal value) {
        if (onPropSet(PROP_ID_monthlySalary, value)) {
            this._monthlySalary = value;
            internalClearRefs(PROP_ID_monthlySalary);

        }
    }

    /**
     * 出生日期: BIRTH
     */
    public java.time.LocalDateTime getBirth() {
        onPropGet(PROP_ID_birth);
        return _birth;
    }

    /**
     * 出生日期: BIRTH
     */
    public void setBirth(java.time.LocalDateTime value) {
        if (onPropSet(PROP_ID_birth, value)) {
            this._birth = value;
            internalClearRefs(PROP_ID_birth);

        }
    }

    /**
     * 头像: AVATAR
     */
    public java.lang.Integer getAvatar() {
        onPropGet(PROP_ID_avatar);
        return _avatar;
    }

    /**
     * 头像: AVATAR
     */
    public void setAvatar(java.lang.Integer value) {
        if (onPropSet(PROP_ID_avatar, value)) {
            this._avatar = value;
            internalClearRefs(PROP_ID_avatar);

        }
    }

    /**
     * 身高: HEIGHT
     */
    public java.lang.Integer getHeight() {
        onPropGet(PROP_ID_height);
        return _height;
    }

    /**
     * 身高: HEIGHT
     */
    public void setHeight(java.lang.Integer value) {
        if (onPropSet(PROP_ID_height, value)) {
            this._height = value;
            internalClearRefs(PROP_ID_height);

        }
    }

    /**
     * 体重: WEIGHT
     */
    public java.lang.Integer getWeight() {
        onPropGet(PROP_ID_weight);
        return _weight;
    }

    /**
     * 体重: WEIGHT
     */
    public void setWeight(java.lang.Integer value) {
        if (onPropSet(PROP_ID_weight, value)) {
            this._weight = value;
            internalClearRefs(PROP_ID_weight);

        }
    }

    /**
     * 名族: NATION
     */
    public java.lang.String getNation() {
        onPropGet(PROP_ID_nation);
        return _nation;
    }

    /**
     * 名族: NATION
     */
    public void setNation(java.lang.String value) {
        if (onPropSet(PROP_ID_nation, value)) {
            this._nation = value;
            internalClearRefs(PROP_ID_nation);

        }
    }

    /**
     * 政治面貌: POLITICAL
     */
    public java.lang.String getPolitical() {
        onPropGet(PROP_ID_political);
        return _political;
    }

    /**
     * 政治面貌: POLITICAL
     */
    public void setPolitical(java.lang.String value) {
        if (onPropSet(PROP_ID_political, value)) {
            this._political = value;
            internalClearRefs(PROP_ID_political);

        }
    }

    /**
     * 婚姻状况: MARITAL
     */
    public java.lang.String getMarital() {
        onPropGet(PROP_ID_marital);
        return _marital;
    }

    /**
     * 婚姻状况: MARITAL
     */
    public void setMarital(java.lang.String value) {
        if (onPropSet(PROP_ID_marital, value)) {
            this._marital = value;
            internalClearRefs(PROP_ID_marital);

        }
    }

    /**
     * 籍贯（省）: DOMICILE_PLACE_PROVINCE
     */
    public java.lang.String getDomicilePlaceProvince() {
        onPropGet(PROP_ID_domicilePlaceProvince);
        return _domicilePlaceProvince;
    }

    /**
     * 籍贯（省）: DOMICILE_PLACE_PROVINCE
     */
    public void setDomicilePlaceProvince(java.lang.String value) {
        if (onPropSet(PROP_ID_domicilePlaceProvince, value)) {
            this._domicilePlaceProvince = value;
            internalClearRefs(PROP_ID_domicilePlaceProvince);

        }
    }

    /**
     * 籍贯（市）: DOMICILE_PLACE_CITY
     */
    public java.lang.String getDomicilePlaceCity() {
        onPropGet(PROP_ID_domicilePlaceCity);
        return _domicilePlaceCity;
    }

    /**
     * 籍贯（市）: DOMICILE_PLACE_CITY
     */
    public void setDomicilePlaceCity(java.lang.String value) {
        if (onPropSet(PROP_ID_domicilePlaceCity, value)) {
            this._domicilePlaceCity = value;
            internalClearRefs(PROP_ID_domicilePlaceCity);

        }
    }

    /**
     * 户籍地址: DOMICILE_PLACE_ADDRESS
     */
    public java.lang.String getDomicilePlaceAddress() {
        onPropGet(PROP_ID_domicilePlaceAddress);
        return _domicilePlaceAddress;
    }

    /**
     * 户籍地址: DOMICILE_PLACE_ADDRESS
     */
    public void setDomicilePlaceAddress(java.lang.String value) {
        if (onPropSet(PROP_ID_domicilePlaceAddress, value)) {
            this._domicilePlaceAddress = value;
            internalClearRefs(PROP_ID_domicilePlaceAddress);

        }
    }

    /**
     * 爱好: HOBBY
     */
    public java.lang.String getHobby() {
        onPropGet(PROP_ID_hobby);
        return _hobby;
    }

    /**
     * 爱好: HOBBY
     */
    public void setHobby(java.lang.String value) {
        if (onPropSet(PROP_ID_hobby, value)) {
            this._hobby = value;
            internalClearRefs(PROP_ID_hobby);

        }
    }

    /**
     * 简要介绍: INTRO
     */
    public java.lang.String getIntro() {
        onPropGet(PROP_ID_intro);
        return _intro;
    }

    /**
     * 简要介绍: INTRO
     */
    public void setIntro(java.lang.String value) {
        if (onPropSet(PROP_ID_intro, value)) {
            this._intro = value;
            internalClearRefs(PROP_ID_intro);

        }
    }

    /**
     * 居住地址: PRESENT_ADDRESS
     */
    public java.lang.String getPresentAddress() {
        onPropGet(PROP_ID_presentAddress);
        return _presentAddress;
    }

    /**
     * 居住地址: PRESENT_ADDRESS
     */
    public void setPresentAddress(java.lang.String value) {
        if (onPropSet(PROP_ID_presentAddress, value)) {
            this._presentAddress = value;
            internalClearRefs(PROP_ID_presentAddress);

        }
    }

    /**
     * 电子邮件: EMAIL
     */
    public java.lang.String getEmail() {
        onPropGet(PROP_ID_email);
        return _email;
    }

    /**
     * 电子邮件: EMAIL
     */
    public void setEmail(java.lang.String value) {
        if (onPropSet(PROP_ID_email, value)) {
            this._email = value;
            internalClearRefs(PROP_ID_email);

        }
    }

    /**
     * 入学日期: ENTRY_DATE
     */
    public java.time.LocalDateTime getEntryDate() {
        onPropGet(PROP_ID_entryDate);
        return _entryDate;
    }

    /**
     * 入学日期: ENTRY_DATE
     */
    public void setEntryDate(java.time.LocalDateTime value) {
        if (onPropSet(PROP_ID_entryDate, value)) {
            this._entryDate = value;
            internalClearRefs(PROP_ID_entryDate);

        }
    }

    /**
     * 状态: STATUS
     */
    public java.lang.String getStatus() {
        onPropGet(PROP_ID_status);
        return _status;
    }

    /**
     * 状态: STATUS
     */
    public void setStatus(java.lang.String value) {
        if (onPropSet(PROP_ID_status, value)) {
            this._status = value;
            internalClearRefs(PROP_ID_status);

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

}
