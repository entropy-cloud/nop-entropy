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

import io.nop.app.VClassStudent;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  班级学生: v_class_student
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _VClassStudent extends DynamicOrmEntity{
    
    /* 班级名称: CLASS_NAME VARCHAR */
    public static final String PROP_NAME_className = "className";
    public static final int PROP_ID_className = 1;
    
    /* 英文名: ENG_NAME VARCHAR */
    public static final String PROP_NAME_engName = "engName";
    public static final int PROP_ID_engName = 2;
    
    /* 学生姓名: STUDENT_NAME VARCHAR */
    public static final String PROP_NAME_studentName = "studentName";
    public static final int PROP_ID_studentName = 3;
    
    /* 学生ID: STUDENT_ID VARCHAR */
    public static final String PROP_NAME_studentId = "studentId";
    public static final int PROP_ID_studentId = 4;
    
    /* 所在班级ID: CLASS_ID VARCHAR */
    public static final String PROP_NAME_classId = "classId";
    public static final int PROP_ID_classId = 5;
    
    /* 所在学院ID: COLLEGE_ID VARCHAR */
    public static final String PROP_NAME_collegeId = "collegeId";
    public static final int PROP_ID_collegeId = 6;
    
    /* 辅导员: ADVISER VARCHAR */
    public static final String PROP_NAME_adviser = "adviser";
    public static final int PROP_ID_adviser = 7;
    
    /* 身份证号: ID_CARD_NO VARCHAR */
    public static final String PROP_NAME_idCardNo = "idCardNo";
    public static final int PROP_ID_idCardNo = 8;
    
    /* 手机号: MOBILE_PHONE VARCHAR */
    public static final String PROP_NAME_mobilePhone = "mobilePhone";
    public static final int PROP_ID_mobilePhone = 9;
    
    /* 性别: GENDER VARCHAR */
    public static final String PROP_NAME_gender = "gender";
    public static final int PROP_ID_gender = 10;
    
    /* 婚姻状况: MARITAL VARCHAR */
    public static final String PROP_NAME_marital = "marital";
    public static final int PROP_ID_marital = 11;
    
    /* 政治面貌: POLITICAL VARCHAR */
    public static final String PROP_NAME_political = "political";
    public static final int PROP_ID_political = 12;
    
    /* 出生日期: BIRTH DATETIME */
    public static final String PROP_NAME_birth = "birth";
    public static final int PROP_ID_birth = 13;
    

    private static int _PROP_ID_BOUND = 14;

    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_studentId,PROP_NAME_classId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_studentId,PROP_ID_classId};

    private static final String[] PROP_ID_TO_NAME = new String[14];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_className] = PROP_NAME_className;
          PROP_NAME_TO_ID.put(PROP_NAME_className, PROP_ID_className);
      
          PROP_ID_TO_NAME[PROP_ID_engName] = PROP_NAME_engName;
          PROP_NAME_TO_ID.put(PROP_NAME_engName, PROP_ID_engName);
      
          PROP_ID_TO_NAME[PROP_ID_studentName] = PROP_NAME_studentName;
          PROP_NAME_TO_ID.put(PROP_NAME_studentName, PROP_ID_studentName);
      
          PROP_ID_TO_NAME[PROP_ID_studentId] = PROP_NAME_studentId;
          PROP_NAME_TO_ID.put(PROP_NAME_studentId, PROP_ID_studentId);
      
          PROP_ID_TO_NAME[PROP_ID_classId] = PROP_NAME_classId;
          PROP_NAME_TO_ID.put(PROP_NAME_classId, PROP_ID_classId);
      
          PROP_ID_TO_NAME[PROP_ID_collegeId] = PROP_NAME_collegeId;
          PROP_NAME_TO_ID.put(PROP_NAME_collegeId, PROP_ID_collegeId);
      
          PROP_ID_TO_NAME[PROP_ID_adviser] = PROP_NAME_adviser;
          PROP_NAME_TO_ID.put(PROP_NAME_adviser, PROP_ID_adviser);
      
          PROP_ID_TO_NAME[PROP_ID_idCardNo] = PROP_NAME_idCardNo;
          PROP_NAME_TO_ID.put(PROP_NAME_idCardNo, PROP_ID_idCardNo);
      
          PROP_ID_TO_NAME[PROP_ID_mobilePhone] = PROP_NAME_mobilePhone;
          PROP_NAME_TO_ID.put(PROP_NAME_mobilePhone, PROP_ID_mobilePhone);
      
          PROP_ID_TO_NAME[PROP_ID_gender] = PROP_NAME_gender;
          PROP_NAME_TO_ID.put(PROP_NAME_gender, PROP_ID_gender);
      
          PROP_ID_TO_NAME[PROP_ID_marital] = PROP_NAME_marital;
          PROP_NAME_TO_ID.put(PROP_NAME_marital, PROP_ID_marital);
      
          PROP_ID_TO_NAME[PROP_ID_political] = PROP_NAME_political;
          PROP_NAME_TO_ID.put(PROP_NAME_political, PROP_ID_political);
      
          PROP_ID_TO_NAME[PROP_ID_birth] = PROP_NAME_birth;
          PROP_NAME_TO_ID.put(PROP_NAME_birth, PROP_ID_birth);
      
    }

    
    /* 班级名称: CLASS_NAME */
    private java.lang.String _className;
    
    /* 英文名: ENG_NAME */
    private java.lang.String _engName;
    
    /* 学生姓名: STUDENT_NAME */
    private java.lang.String _studentName;
    
    /* 学生ID: STUDENT_ID */
    private java.lang.String _studentId;
    
    /* 所在班级ID: CLASS_ID */
    private java.lang.String _classId;
    
    /* 所在学院ID: COLLEGE_ID */
    private java.lang.String _collegeId;
    
    /* 辅导员: ADVISER */
    private java.lang.String _adviser;
    
    /* 身份证号: ID_CARD_NO */
    private java.lang.String _idCardNo;
    
    /* 手机号: MOBILE_PHONE */
    private java.lang.String _mobilePhone;
    
    /* 性别: GENDER */
    private java.lang.String _gender;
    
    /* 婚姻状况: MARITAL */
    private java.lang.String _marital;
    
    /* 政治面貌: POLITICAL */
    private java.lang.String _political;
    
    /* 出生日期: BIRTH */
    private java.time.LocalDateTime _birth;
    

    public _VClassStudent(){
        // for debug
    }

    protected VClassStudent newInstance(){
       return new VClassStudent();
    }

    @Override
    public VClassStudent cloneInstance() {
        VClassStudent entity = newInstance();
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
      return "io.nop.app.VClassStudent";
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
    
        return buildCompositeId(PK_PROP_NAMES,PK_PROP_IDS);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_studentId || propId == PROP_ID_classId;
          
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
        
            case PROP_ID_className:
               return getClassName();
        
            case PROP_ID_engName:
               return getEngName();
        
            case PROP_ID_studentName:
               return getStudentName();
        
            case PROP_ID_studentId:
               return getStudentId();
        
            case PROP_ID_classId:
               return getClassId();
        
            case PROP_ID_collegeId:
               return getCollegeId();
        
            case PROP_ID_adviser:
               return getAdviser();
        
            case PROP_ID_idCardNo:
               return getIdCardNo();
        
            case PROP_ID_mobilePhone:
               return getMobilePhone();
        
            case PROP_ID_gender:
               return getGender();
        
            case PROP_ID_marital:
               return getMarital();
        
            case PROP_ID_political:
               return getPolitical();
        
            case PROP_ID_birth:
               return getBirth();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_className:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_className));
               }
               setClassName(typedValue);
               break;
            }
        
            case PROP_ID_engName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_engName));
               }
               setEngName(typedValue);
               break;
            }
        
            case PROP_ID_studentName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_studentName));
               }
               setStudentName(typedValue);
               break;
            }
        
            case PROP_ID_studentId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_studentId));
               }
               setStudentId(typedValue);
               break;
            }
        
            case PROP_ID_classId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_classId));
               }
               setClassId(typedValue);
               break;
            }
        
            case PROP_ID_collegeId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_collegeId));
               }
               setCollegeId(typedValue);
               break;
            }
        
            case PROP_ID_adviser:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_adviser));
               }
               setAdviser(typedValue);
               break;
            }
        
            case PROP_ID_idCardNo:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_idCardNo));
               }
               setIdCardNo(typedValue);
               break;
            }
        
            case PROP_ID_mobilePhone:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_mobilePhone));
               }
               setMobilePhone(typedValue);
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
        
            case PROP_ID_marital:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_marital));
               }
               setMarital(typedValue);
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
        
            case PROP_ID_birth:{
               java.time.LocalDateTime typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLocalDateTime(value,
                       err-> newTypeConversionError(PROP_NAME_birth));
               }
               setBirth(typedValue);
               break;
            }
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_className:{
               onInitProp(propId);
               this._className = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_engName:{
               onInitProp(propId);
               this._engName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_studentName:{
               onInitProp(propId);
               this._studentName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_studentId:{
               onInitProp(propId);
               this._studentId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_classId:{
               onInitProp(propId);
               this._classId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_collegeId:{
               onInitProp(propId);
               this._collegeId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_adviser:{
               onInitProp(propId);
               this._adviser = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_idCardNo:{
               onInitProp(propId);
               this._idCardNo = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_mobilePhone:{
               onInitProp(propId);
               this._mobilePhone = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_gender:{
               onInitProp(propId);
               this._gender = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_marital:{
               onInitProp(propId);
               this._marital = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_political:{
               onInitProp(propId);
               this._political = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_birth:{
               onInitProp(propId);
               this._birth = (java.time.LocalDateTime)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 班级名称: CLASS_NAME
     */
    public java.lang.String getClassName(){
         onPropGet(PROP_ID_className);
         return _className;
    }

    /**
     * 班级名称: CLASS_NAME
     */
    public void setClassName(java.lang.String value){
        if(onPropSet(PROP_ID_className,value)){
            this._className = value;
            internalClearRefs(PROP_ID_className);
            
        }
    }
    
    /**
     * 英文名: ENG_NAME
     */
    public java.lang.String getEngName(){
         onPropGet(PROP_ID_engName);
         return _engName;
    }

    /**
     * 英文名: ENG_NAME
     */
    public void setEngName(java.lang.String value){
        if(onPropSet(PROP_ID_engName,value)){
            this._engName = value;
            internalClearRefs(PROP_ID_engName);
            
        }
    }
    
    /**
     * 学生姓名: STUDENT_NAME
     */
    public java.lang.String getStudentName(){
         onPropGet(PROP_ID_studentName);
         return _studentName;
    }

    /**
     * 学生姓名: STUDENT_NAME
     */
    public void setStudentName(java.lang.String value){
        if(onPropSet(PROP_ID_studentName,value)){
            this._studentName = value;
            internalClearRefs(PROP_ID_studentName);
            
        }
    }
    
    /**
     * 学生ID: STUDENT_ID
     */
    public java.lang.String getStudentId(){
         onPropGet(PROP_ID_studentId);
         return _studentId;
    }

    /**
     * 学生ID: STUDENT_ID
     */
    public void setStudentId(java.lang.String value){
        if(onPropSet(PROP_ID_studentId,value)){
            this._studentId = value;
            internalClearRefs(PROP_ID_studentId);
            orm_id();
        }
    }
    
    /**
     * 所在班级ID: CLASS_ID
     */
    public java.lang.String getClassId(){
         onPropGet(PROP_ID_classId);
         return _classId;
    }

    /**
     * 所在班级ID: CLASS_ID
     */
    public void setClassId(java.lang.String value){
        if(onPropSet(PROP_ID_classId,value)){
            this._classId = value;
            internalClearRefs(PROP_ID_classId);
            orm_id();
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
     * 辅导员: ADVISER
     */
    public java.lang.String getAdviser(){
         onPropGet(PROP_ID_adviser);
         return _adviser;
    }

    /**
     * 辅导员: ADVISER
     */
    public void setAdviser(java.lang.String value){
        if(onPropSet(PROP_ID_adviser,value)){
            this._adviser = value;
            internalClearRefs(PROP_ID_adviser);
            
        }
    }
    
    /**
     * 身份证号: ID_CARD_NO
     */
    public java.lang.String getIdCardNo(){
         onPropGet(PROP_ID_idCardNo);
         return _idCardNo;
    }

    /**
     * 身份证号: ID_CARD_NO
     */
    public void setIdCardNo(java.lang.String value){
        if(onPropSet(PROP_ID_idCardNo,value)){
            this._idCardNo = value;
            internalClearRefs(PROP_ID_idCardNo);
            
        }
    }
    
    /**
     * 手机号: MOBILE_PHONE
     */
    public java.lang.String getMobilePhone(){
         onPropGet(PROP_ID_mobilePhone);
         return _mobilePhone;
    }

    /**
     * 手机号: MOBILE_PHONE
     */
    public void setMobilePhone(java.lang.String value){
        if(onPropSet(PROP_ID_mobilePhone,value)){
            this._mobilePhone = value;
            internalClearRefs(PROP_ID_mobilePhone);
            
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
    
}
// resume CPD analysis - CPD-ON
