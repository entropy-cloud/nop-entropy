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

import io.nop.app.SimsCollege;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  学院: sims_college
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public class _SimsCollege extends DynamicOrmEntity{
    
    /* 学院名称: COLLEGE_NAME VARCHAR */
    public static final String PROP_NAME_collegeName = "collegeName";
    public static final int PROP_ID_collegeName = 1;
    
    /* 学院ID: COLLEGE_ID VARCHAR */
    public static final String PROP_NAME_collegeId = "collegeId";
    public static final int PROP_ID_collegeId = 2;
    
    /* 学院简称: SHORT_NAME VARCHAR */
    public static final String PROP_NAME_shortName = "shortName";
    public static final int PROP_ID_shortName = 3;
    
    /* 学院介绍: INTRO VARCHAR */
    public static final String PROP_NAME_intro = "intro";
    public static final int PROP_ID_intro = 4;
    
    /* 专业个数: PROFESSION_NUMBER INTEGER */
    public static final String PROP_NAME_professionNumber = "professionNumber";
    public static final int PROP_ID_professionNumber = 5;
    
    /* 学生人数: STUDENT_NUMBER INTEGER */
    public static final String PROP_NAME_studentNumber = "studentNumber";
    public static final int PROP_ID_studentNumber = 6;
    
    /* 院长: PRESIDENT VARCHAR */
    public static final String PROP_NAME_president = "president";
    public static final int PROP_ID_president = 7;
    
    /* 租户号: TENANT_ID VARCHAR */
    public static final String PROP_NAME_tenantId = "tenantId";
    public static final int PROP_ID_tenantId = 8;
    
    /* 乐观锁: REVISION INTEGER */
    public static final String PROP_NAME_revision = "revision";
    public static final int PROP_ID_revision = 9;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 10;
    
    /* 创建时间: CREATED_TIME DATETIME */
    public static final String PROP_NAME_createdTime = "createdTime";
    public static final int PROP_ID_createdTime = 11;
    
    /* 更新人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 12;
    
    /* 更新时间: UPDATED_TIME DATETIME */
    public static final String PROP_NAME_updatedTime = "updatedTime";
    public static final int PROP_ID_updatedTime = 13;
    

    private static int _PROP_ID_BOUND = 14;

    
    /* relation:  */
    public static final String PROP_NAME_simsClasses = "simsClasses";
    

    public static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_collegeId);
    public static final int[] PK_PROP_IDS = new int[]{PROP_ID_collegeId};

    private static final String[] PROP_ID_TO_NAME = new String[14];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_collegeName] = PROP_NAME_collegeName;
          PROP_NAME_TO_ID.put(PROP_NAME_collegeName, PROP_ID_collegeName);
      
          PROP_ID_TO_NAME[PROP_ID_collegeId] = PROP_NAME_collegeId;
          PROP_NAME_TO_ID.put(PROP_NAME_collegeId, PROP_ID_collegeId);
      
          PROP_ID_TO_NAME[PROP_ID_shortName] = PROP_NAME_shortName;
          PROP_NAME_TO_ID.put(PROP_NAME_shortName, PROP_ID_shortName);
      
          PROP_ID_TO_NAME[PROP_ID_intro] = PROP_NAME_intro;
          PROP_NAME_TO_ID.put(PROP_NAME_intro, PROP_ID_intro);
      
          PROP_ID_TO_NAME[PROP_ID_professionNumber] = PROP_NAME_professionNumber;
          PROP_NAME_TO_ID.put(PROP_NAME_professionNumber, PROP_ID_professionNumber);
      
          PROP_ID_TO_NAME[PROP_ID_studentNumber] = PROP_NAME_studentNumber;
          PROP_NAME_TO_ID.put(PROP_NAME_studentNumber, PROP_ID_studentNumber);
      
          PROP_ID_TO_NAME[PROP_ID_president] = PROP_NAME_president;
          PROP_NAME_TO_ID.put(PROP_NAME_president, PROP_ID_president);
      
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

    
    /* 学院名称: COLLEGE_NAME */
    private java.lang.String _collegeName;
    
    /* 学院ID: COLLEGE_ID */
    private java.lang.String _collegeId;
    
    /* 学院简称: SHORT_NAME */
    private java.lang.String _shortName;
    
    /* 学院介绍: INTRO */
    private java.lang.String _intro;
    
    /* 专业个数: PROFESSION_NUMBER */
    private java.lang.Integer _professionNumber;
    
    /* 学生人数: STUDENT_NUMBER */
    private java.lang.Integer _studentNumber;
    
    /* 院长: PRESIDENT */
    private java.lang.String _president;
    
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
    

    public _SimsCollege(){
    }

    protected SimsCollege newInstance(){
       return new SimsCollege();
    }

    @Override
    public SimsCollege cloneInstance() {
        SimsCollege entity = newInstance();
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
      return "io.nop.app.SimsCollege";
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
    
        return buildSimpleId(PROP_ID_collegeId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_collegeId;
          
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
        
            case PROP_ID_collegeName:
               return getCollegeName();
        
            case PROP_ID_collegeId:
               return getCollegeId();
        
            case PROP_ID_shortName:
               return getShortName();
        
            case PROP_ID_intro:
               return getIntro();
        
            case PROP_ID_professionNumber:
               return getProfessionNumber();
        
            case PROP_ID_studentNumber:
               return getStudentNumber();
        
            case PROP_ID_president:
               return getPresident();
        
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
        
            case PROP_ID_collegeName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_collegeName));
               }
               setCollegeName(typedValue);
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
        
            case PROP_ID_shortName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_shortName));
               }
               setShortName(typedValue);
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
        
            case PROP_ID_professionNumber:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_professionNumber));
               }
               setProfessionNumber(typedValue);
               break;
            }
        
            case PROP_ID_studentNumber:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_studentNumber));
               }
               setStudentNumber(typedValue);
               break;
            }
        
            case PROP_ID_president:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_president));
               }
               setPresident(typedValue);
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
        
            case PROP_ID_collegeName:{
               onInitProp(propId);
               this._collegeName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_collegeId:{
               onInitProp(propId);
               this._collegeId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_shortName:{
               onInitProp(propId);
               this._shortName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_intro:{
               onInitProp(propId);
               this._intro = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_professionNumber:{
               onInitProp(propId);
               this._professionNumber = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_studentNumber:{
               onInitProp(propId);
               this._studentNumber = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_president:{
               onInitProp(propId);
               this._president = (java.lang.String)value;
               
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
     * 学院名称: COLLEGE_NAME
     */
    public java.lang.String getCollegeName(){
         onPropGet(PROP_ID_collegeName);
         return _collegeName;
    }

    /**
     * 学院名称: COLLEGE_NAME
     */
    public void setCollegeName(java.lang.String value){
        if(onPropSet(PROP_ID_collegeName,value)){
            this._collegeName = value;
            internalClearRefs(PROP_ID_collegeName);
            
        }
    }
    
    /**
     * 学院ID: COLLEGE_ID
     */
    public java.lang.String getCollegeId(){
         onPropGet(PROP_ID_collegeId);
         return _collegeId;
    }

    /**
     * 学院ID: COLLEGE_ID
     */
    public void setCollegeId(java.lang.String value){
        if(onPropSet(PROP_ID_collegeId,value)){
            this._collegeId = value;
            internalClearRefs(PROP_ID_collegeId);
            orm_id();
        }
    }
    
    /**
     * 学院简称: SHORT_NAME
     */
    public java.lang.String getShortName(){
         onPropGet(PROP_ID_shortName);
         return _shortName;
    }

    /**
     * 学院简称: SHORT_NAME
     */
    public void setShortName(java.lang.String value){
        if(onPropSet(PROP_ID_shortName,value)){
            this._shortName = value;
            internalClearRefs(PROP_ID_shortName);
            
        }
    }
    
    /**
     * 学院介绍: INTRO
     */
    public java.lang.String getIntro(){
         onPropGet(PROP_ID_intro);
         return _intro;
    }

    /**
     * 学院介绍: INTRO
     */
    public void setIntro(java.lang.String value){
        if(onPropSet(PROP_ID_intro,value)){
            this._intro = value;
            internalClearRefs(PROP_ID_intro);
            
        }
    }
    
    /**
     * 专业个数: PROFESSION_NUMBER
     */
    public java.lang.Integer getProfessionNumber(){
         onPropGet(PROP_ID_professionNumber);
         return _professionNumber;
    }

    /**
     * 专业个数: PROFESSION_NUMBER
     */
    public void setProfessionNumber(java.lang.Integer value){
        if(onPropSet(PROP_ID_professionNumber,value)){
            this._professionNumber = value;
            internalClearRefs(PROP_ID_professionNumber);
            
        }
    }
    
    /**
     * 学生人数: STUDENT_NUMBER
     */
    public java.lang.Integer getStudentNumber(){
         onPropGet(PROP_ID_studentNumber);
         return _studentNumber;
    }

    /**
     * 学生人数: STUDENT_NUMBER
     */
    public void setStudentNumber(java.lang.Integer value){
        if(onPropSet(PROP_ID_studentNumber,value)){
            this._studentNumber = value;
            internalClearRefs(PROP_ID_studentNumber);
            
        }
    }
    
    /**
     * 院长: PRESIDENT
     */
    public java.lang.String getPresident(){
         onPropGet(PROP_ID_president);
         return _president;
    }

    /**
     * 院长: PRESIDENT
     */
    public void setPresident(java.lang.String value){
        if(onPropSet(PROP_ID_president,value)){
            this._president = value;
            internalClearRefs(PROP_ID_president);
            
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
    
    private final OrmEntitySet<io.nop.app.SimsClass> _simsClasses = new OrmEntitySet<>(this, PROP_NAME_simsClasses,
        io.nop.app.SimsClass.PROP_NAME_simsCollege, null,io.nop.app.SimsClass.class);

    /**
     * 。 refPropName: simsCollege, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<io.nop.app.SimsClass> getSimsClasses(){
       return _simsClasses;
    }
       
}
// resume CPD analysis - CPD-ON
