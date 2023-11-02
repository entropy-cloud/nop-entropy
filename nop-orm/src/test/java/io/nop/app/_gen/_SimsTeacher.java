package io.nop.app._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.app.SimsTeacher;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  教师: sims_teacher
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public class _SimsTeacher extends DynamicOrmEntity{
    
    /* 所在学院ID: COLLEGE_ID VARCHAR */
    public static final String PROP_NAME_collegeId = "collegeId";
    public static final int PROP_ID_collegeId = 1;
    
    /* 教师ID: TEACHER_ID VARCHAR */
    public static final String PROP_NAME_teacherId = "teacherId";
    public static final int PROP_ID_teacherId = 2;
    
    /* 姓名: TEACHER_NAME VARCHAR */
    public static final String PROP_NAME_teacherName = "teacherName";
    public static final int PROP_ID_teacherName = 3;
    
    /* 性别: GENDER VARCHAR */
    public static final String PROP_NAME_gender = "gender";
    public static final int PROP_ID_gender = 4;
    
    /* 出生日期: BIRTH DATETIME */
    public static final String PROP_NAME_birth = "birth";
    public static final int PROP_ID_birth = 5;
    
    /* 毕业院校: GRADUATE_INSTITUTION VARCHAR */
    public static final String PROP_NAME_graduateInstitution = "graduateInstitution";
    public static final int PROP_ID_graduateInstitution = 6;
    
    /* 从业年限: PRACTICE_YEARS INTEGER */
    public static final String PROP_NAME_practiceYears = "practiceYears";
    public static final int PROP_ID_practiceYears = 7;
    
    /* 政治面貌: POLITICAL VARCHAR */
    public static final String PROP_NAME_political = "political";
    public static final int PROP_ID_political = 8;
    
    /* 婚姻状况: MARITAL VARCHAR */
    public static final String PROP_NAME_marital = "marital";
    public static final int PROP_ID_marital = 9;
    
    /* 头像: AVATAR VARCHAR */
    public static final String PROP_NAME_avatar = "avatar";
    public static final int PROP_ID_avatar = 10;
    
    /* 介绍: INTRO VARCHAR */
    public static final String PROP_NAME_intro = "intro";
    public static final int PROP_ID_intro = 11;
    
    /* 租户号: TENANT_ID VARCHAR */
    public static final String PROP_NAME_tenantId = "tenantId";
    public static final int PROP_ID_tenantId = 12;
    
    /* 乐观锁: REVISION INTEGER */
    public static final String PROP_NAME_revision = "revision";
    public static final int PROP_ID_revision = 13;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 14;
    
    /* 创建时间: CREATED_TIME DATETIME */
    public static final String PROP_NAME_createdTime = "createdTime";
    public static final int PROP_ID_createdTime = 15;
    
    /* 更新人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 16;
    
    /* 更新时间: UPDATED_TIME DATETIME */
    public static final String PROP_NAME_updatedTime = "updatedTime";
    public static final int PROP_ID_updatedTime = 17;
    

    private static int _PROP_ID_BOUND = 18;

    

    public static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_teacherId);
    public static final int[] PK_PROP_IDS = new int[]{PROP_ID_teacherId};

    private static final String[] PROP_ID_TO_NAME = new String[18];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_collegeId] = PROP_NAME_collegeId;
          PROP_NAME_TO_ID.put(PROP_NAME_collegeId, PROP_ID_collegeId);
      
          PROP_ID_TO_NAME[PROP_ID_teacherId] = PROP_NAME_teacherId;
          PROP_NAME_TO_ID.put(PROP_NAME_teacherId, PROP_ID_teacherId);
      
          PROP_ID_TO_NAME[PROP_ID_teacherName] = PROP_NAME_teacherName;
          PROP_NAME_TO_ID.put(PROP_NAME_teacherName, PROP_ID_teacherName);
      
          PROP_ID_TO_NAME[PROP_ID_gender] = PROP_NAME_gender;
          PROP_NAME_TO_ID.put(PROP_NAME_gender, PROP_ID_gender);
      
          PROP_ID_TO_NAME[PROP_ID_birth] = PROP_NAME_birth;
          PROP_NAME_TO_ID.put(PROP_NAME_birth, PROP_ID_birth);
      
          PROP_ID_TO_NAME[PROP_ID_graduateInstitution] = PROP_NAME_graduateInstitution;
          PROP_NAME_TO_ID.put(PROP_NAME_graduateInstitution, PROP_ID_graduateInstitution);
      
          PROP_ID_TO_NAME[PROP_ID_practiceYears] = PROP_NAME_practiceYears;
          PROP_NAME_TO_ID.put(PROP_NAME_practiceYears, PROP_ID_practiceYears);
      
          PROP_ID_TO_NAME[PROP_ID_political] = PROP_NAME_political;
          PROP_NAME_TO_ID.put(PROP_NAME_political, PROP_ID_political);
      
          PROP_ID_TO_NAME[PROP_ID_marital] = PROP_NAME_marital;
          PROP_NAME_TO_ID.put(PROP_NAME_marital, PROP_ID_marital);
      
          PROP_ID_TO_NAME[PROP_ID_avatar] = PROP_NAME_avatar;
          PROP_NAME_TO_ID.put(PROP_NAME_avatar, PROP_ID_avatar);
      
          PROP_ID_TO_NAME[PROP_ID_intro] = PROP_NAME_intro;
          PROP_NAME_TO_ID.put(PROP_NAME_intro, PROP_ID_intro);
      
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
    
    /* 教师ID: TEACHER_ID */
    private java.lang.String _teacherId;
    
    /* 姓名: TEACHER_NAME */
    private java.lang.String _teacherName;
    
    /* 性别: GENDER */
    private java.lang.String _gender;
    
    /* 出生日期: BIRTH */
    private java.time.LocalDateTime _birth;
    
    /* 毕业院校: GRADUATE_INSTITUTION */
    private java.lang.String _graduateInstitution;
    
    /* 从业年限: PRACTICE_YEARS */
    private java.lang.Integer _practiceYears;
    
    /* 政治面貌: POLITICAL */
    private java.lang.String _political;
    
    /* 婚姻状况: MARITAL */
    private java.lang.String _marital;
    
    /* 头像: AVATAR */
    private java.lang.String _avatar;
    
    /* 介绍: INTRO */
    private java.lang.String _intro;
    
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
    

    public _SimsTeacher(){
    }

    protected SimsTeacher newInstance(){
       return new SimsTeacher();
    }

    @Override
    public SimsTeacher cloneInstance() {
        SimsTeacher entity = newInstance();
        orm_forEachInitedProp((value, propId) -> {
            entity.onInitProp(propId);
        });
        return entity;
    }

    @Override
    public String orm_entityName() {
      // 如果存在实体模型对象，则以模型对象上的设置为准
      IEntityModel entityModel = orm_entityModel();
      if(entityModel != null)
          return entityModel.getName();
      return "io.nop.app.SimsTeacher";
    }

    @Override
    public int orm_propIdBound(){
      IEntityModel entityModel = orm_entityModel();
      if(entityModel != null)
          return entityModel.getPropIdBound();
      return _PROP_ID_BOUND;
    }

    @Override
    public Object orm_id() {
    
        return buildSimpleId(PROP_ID_teacherId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_teacherId;
          
    }

    @Override
    public String orm_propName(int propId) {
        if(propId >= PROP_ID_TO_NAME.length)
            return super.orm_propName(propId);
        String propName = PROP_ID_TO_NAME[propId];
        if(propName == null)
           return super.orm_propName(propId);
        return propName;
    }

    @Override
    public int orm_propId(String propName) {
        Integer propId = PROP_NAME_TO_ID.get(propName);
        if(propId == null)
            return super.orm_propId(propName);
        return propId;
    }

    @Override
    public Object orm_propValue(int propId) {
        switch(propId){
        
            case PROP_ID_collegeId:
               return getCollegeId();
        
            case PROP_ID_teacherId:
               return getTeacherId();
        
            case PROP_ID_teacherName:
               return getTeacherName();
        
            case PROP_ID_gender:
               return getGender();
        
            case PROP_ID_birth:
               return getBirth();
        
            case PROP_ID_graduateInstitution:
               return getGraduateInstitution();
        
            case PROP_ID_practiceYears:
               return getPracticeYears();
        
            case PROP_ID_political:
               return getPolitical();
        
            case PROP_ID_marital:
               return getMarital();
        
            case PROP_ID_avatar:
               return getAvatar();
        
            case PROP_ID_intro:
               return getIntro();
        
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
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_collegeId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_collegeId));
               }
               setCollegeId(typedValue);
               break;
            }
        
            case PROP_ID_teacherId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_teacherId));
               }
               setTeacherId(typedValue);
               break;
            }
        
            case PROP_ID_teacherName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_teacherName));
               }
               setTeacherName(typedValue);
               break;
            }
        
            case PROP_ID_gender:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_gender));
               }
               setGender(typedValue);
               break;
            }
        
            case PROP_ID_birth:{
               java.time.LocalDateTime typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLocalDateTime(value,
                       err-> newTypeConversionError(PROP_NAME_birth));
               }
               setBirth(typedValue);
               break;
            }
        
            case PROP_ID_graduateInstitution:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_graduateInstitution));
               }
               setGraduateInstitution(typedValue);
               break;
            }
        
            case PROP_ID_practiceYears:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_practiceYears));
               }
               setPracticeYears(typedValue);
               break;
            }
        
            case PROP_ID_political:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_political));
               }
               setPolitical(typedValue);
               break;
            }
        
            case PROP_ID_marital:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_marital));
               }
               setMarital(typedValue);
               break;
            }
        
            case PROP_ID_avatar:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_avatar));
               }
               setAvatar(typedValue);
               break;
            }
        
            case PROP_ID_intro:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_intro));
               }
               setIntro(typedValue);
               break;
            }
        
            case PROP_ID_tenantId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_tenantId));
               }
               setTenantId(typedValue);
               break;
            }
        
            case PROP_ID_revision:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_revision));
               }
               setRevision(typedValue);
               break;
            }
        
            case PROP_ID_createdBy:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_createdBy));
               }
               setCreatedBy(typedValue);
               break;
            }
        
            case PROP_ID_createdTime:{
               java.time.LocalDateTime typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLocalDateTime(value,
                       err-> newTypeConversionError(PROP_NAME_createdTime));
               }
               setCreatedTime(typedValue);
               break;
            }
        
            case PROP_ID_updatedBy:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_updatedBy));
               }
               setUpdatedBy(typedValue);
               break;
            }
        
            case PROP_ID_updatedTime:{
               java.time.LocalDateTime typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLocalDateTime(value,
                       err-> newTypeConversionError(PROP_NAME_updatedTime));
               }
               setUpdatedTime(typedValue);
               break;
            }
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_collegeId:{
               onInitProp(propId);
               this._collegeId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_teacherId:{
               onInitProp(propId);
               this._teacherId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_teacherName:{
               onInitProp(propId);
               this._teacherName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_gender:{
               onInitProp(propId);
               this._gender = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_birth:{
               onInitProp(propId);
               this._birth = (java.time.LocalDateTime)value;
               
               break;
            }
        
            case PROP_ID_graduateInstitution:{
               onInitProp(propId);
               this._graduateInstitution = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_practiceYears:{
               onInitProp(propId);
               this._practiceYears = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_political:{
               onInitProp(propId);
               this._political = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_marital:{
               onInitProp(propId);
               this._marital = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_avatar:{
               onInitProp(propId);
               this._avatar = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_intro:{
               onInitProp(propId);
               this._intro = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_tenantId:{
               onInitProp(propId);
               this._tenantId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_revision:{
               onInitProp(propId);
               this._revision = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_createdBy:{
               onInitProp(propId);
               this._createdBy = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_createdTime:{
               onInitProp(propId);
               this._createdTime = (java.time.LocalDateTime)value;
               
               break;
            }
        
            case PROP_ID_updatedBy:{
               onInitProp(propId);
               this._updatedBy = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_updatedTime:{
               onInitProp(propId);
               this._updatedTime = (java.time.LocalDateTime)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 所在学院ID: COLLEGE_ID
     */
    public java.lang.String getCollegeId(){
         onPropGet(PROP_ID_collegeId);
         return _collegeId;
    }

    /**
     * 所在学院ID: COLLEGE_ID
     */
    public void setCollegeId(java.lang.String value){
        if(onPropSet(PROP_ID_collegeId,value)){
            this._collegeId = value;
            internalClearRefs(PROP_ID_collegeId);
            
        }
    }
    
    /**
     * 教师ID: TEACHER_ID
     */
    public java.lang.String getTeacherId(){
         onPropGet(PROP_ID_teacherId);
         return _teacherId;
    }

    /**
     * 教师ID: TEACHER_ID
     */
    public void setTeacherId(java.lang.String value){
        if(onPropSet(PROP_ID_teacherId,value)){
            this._teacherId = value;
            internalClearRefs(PROP_ID_teacherId);
            orm_id();
        }
    }
    
    /**
     * 姓名: TEACHER_NAME
     */
    public java.lang.String getTeacherName(){
         onPropGet(PROP_ID_teacherName);
         return _teacherName;
    }

    /**
     * 姓名: TEACHER_NAME
     */
    public void setTeacherName(java.lang.String value){
        if(onPropSet(PROP_ID_teacherName,value)){
            this._teacherName = value;
            internalClearRefs(PROP_ID_teacherName);
            
        }
    }
    
    /**
     * 性别: GENDER
     */
    public java.lang.String getGender(){
         onPropGet(PROP_ID_gender);
         return _gender;
    }

    /**
     * 性别: GENDER
     */
    public void setGender(java.lang.String value){
        if(onPropSet(PROP_ID_gender,value)){
            this._gender = value;
            internalClearRefs(PROP_ID_gender);
            
        }
    }
    
    /**
     * 出生日期: BIRTH
     */
    public java.time.LocalDateTime getBirth(){
         onPropGet(PROP_ID_birth);
         return _birth;
    }

    /**
     * 出生日期: BIRTH
     */
    public void setBirth(java.time.LocalDateTime value){
        if(onPropSet(PROP_ID_birth,value)){
            this._birth = value;
            internalClearRefs(PROP_ID_birth);
            
        }
    }
    
    /**
     * 毕业院校: GRADUATE_INSTITUTION
     */
    public java.lang.String getGraduateInstitution(){
         onPropGet(PROP_ID_graduateInstitution);
         return _graduateInstitution;
    }

    /**
     * 毕业院校: GRADUATE_INSTITUTION
     */
    public void setGraduateInstitution(java.lang.String value){
        if(onPropSet(PROP_ID_graduateInstitution,value)){
            this._graduateInstitution = value;
            internalClearRefs(PROP_ID_graduateInstitution);
            
        }
    }
    
    /**
     * 从业年限: PRACTICE_YEARS
     */
    public java.lang.Integer getPracticeYears(){
         onPropGet(PROP_ID_practiceYears);
         return _practiceYears;
    }

    /**
     * 从业年限: PRACTICE_YEARS
     */
    public void setPracticeYears(java.lang.Integer value){
        if(onPropSet(PROP_ID_practiceYears,value)){
            this._practiceYears = value;
            internalClearRefs(PROP_ID_practiceYears);
            
        }
    }
    
    /**
     * 政治面貌: POLITICAL
     */
    public java.lang.String getPolitical(){
         onPropGet(PROP_ID_political);
         return _political;
    }

    /**
     * 政治面貌: POLITICAL
     */
    public void setPolitical(java.lang.String value){
        if(onPropSet(PROP_ID_political,value)){
            this._political = value;
            internalClearRefs(PROP_ID_political);
            
        }
    }
    
    /**
     * 婚姻状况: MARITAL
     */
    public java.lang.String getMarital(){
         onPropGet(PROP_ID_marital);
         return _marital;
    }

    /**
     * 婚姻状况: MARITAL
     */
    public void setMarital(java.lang.String value){
        if(onPropSet(PROP_ID_marital,value)){
            this._marital = value;
            internalClearRefs(PROP_ID_marital);
            
        }
    }
    
    /**
     * 头像: AVATAR
     */
    public java.lang.String getAvatar(){
         onPropGet(PROP_ID_avatar);
         return _avatar;
    }

    /**
     * 头像: AVATAR
     */
    public void setAvatar(java.lang.String value){
        if(onPropSet(PROP_ID_avatar,value)){
            this._avatar = value;
            internalClearRefs(PROP_ID_avatar);
            
        }
    }
    
    /**
     * 介绍: INTRO
     */
    public java.lang.String getIntro(){
         onPropGet(PROP_ID_intro);
         return _intro;
    }

    /**
     * 介绍: INTRO
     */
    public void setIntro(java.lang.String value){
        if(onPropSet(PROP_ID_intro,value)){
            this._intro = value;
            internalClearRefs(PROP_ID_intro);
            
        }
    }
    
    /**
     * 租户号: TENANT_ID
     */
    public java.lang.String getTenantId(){
         onPropGet(PROP_ID_tenantId);
         return _tenantId;
    }

    /**
     * 租户号: TENANT_ID
     */
    public void setTenantId(java.lang.String value){
        if(onPropSet(PROP_ID_tenantId,value)){
            this._tenantId = value;
            internalClearRefs(PROP_ID_tenantId);
            
        }
    }
    
    /**
     * 乐观锁: REVISION
     */
    public java.lang.Integer getRevision(){
         onPropGet(PROP_ID_revision);
         return _revision;
    }

    /**
     * 乐观锁: REVISION
     */
    public void setRevision(java.lang.Integer value){
        if(onPropSet(PROP_ID_revision,value)){
            this._revision = value;
            internalClearRefs(PROP_ID_revision);
            
        }
    }
    
    /**
     * 创建人: CREATED_BY
     */
    public java.lang.String getCreatedBy(){
         onPropGet(PROP_ID_createdBy);
         return _createdBy;
    }

    /**
     * 创建人: CREATED_BY
     */
    public void setCreatedBy(java.lang.String value){
        if(onPropSet(PROP_ID_createdBy,value)){
            this._createdBy = value;
            internalClearRefs(PROP_ID_createdBy);
            
        }
    }
    
    /**
     * 创建时间: CREATED_TIME
     */
    public java.time.LocalDateTime getCreatedTime(){
         onPropGet(PROP_ID_createdTime);
         return _createdTime;
    }

    /**
     * 创建时间: CREATED_TIME
     */
    public void setCreatedTime(java.time.LocalDateTime value){
        if(onPropSet(PROP_ID_createdTime,value)){
            this._createdTime = value;
            internalClearRefs(PROP_ID_createdTime);
            
        }
    }
    
    /**
     * 更新人: UPDATED_BY
     */
    public java.lang.String getUpdatedBy(){
         onPropGet(PROP_ID_updatedBy);
         return _updatedBy;
    }

    /**
     * 更新人: UPDATED_BY
     */
    public void setUpdatedBy(java.lang.String value){
        if(onPropSet(PROP_ID_updatedBy,value)){
            this._updatedBy = value;
            internalClearRefs(PROP_ID_updatedBy);
            
        }
    }
    
    /**
     * 更新时间: UPDATED_TIME
     */
    public java.time.LocalDateTime getUpdatedTime(){
         onPropGet(PROP_ID_updatedTime);
         return _updatedTime;
    }

    /**
     * 更新时间: UPDATED_TIME
     */
    public void setUpdatedTime(java.time.LocalDateTime value){
        if(onPropSet(PROP_ID_updatedTime,value)){
            this._updatedTime = value;
            internalClearRefs(PROP_ID_updatedTime);
            
        }
    }
    
}
// resume CPD analysis - CPD-ON
