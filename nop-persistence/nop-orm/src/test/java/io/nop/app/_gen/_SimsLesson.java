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

import io.nop.app.SimsLesson;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  课程: sims_lesson
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _SimsLesson extends DynamicOrmEntity{
    
    /* 课程ID: LESSON_ID VARCHAR */
    public static final String PROP_NAME_lessonId = "lessonId";
    public static final int PROP_ID_lessonId = 1;
    
    /* 课程名: LESSON_NAME VARCHAR */
    public static final String PROP_NAME_lessonName = "lessonName";
    public static final int PROP_ID_lessonName = 2;
    
    /* 课程说明: INTRO VARCHAR */
    public static final String PROP_NAME_intro = "intro";
    public static final int PROP_ID_intro = 3;
    
    /* 学时: HOURS INTEGER */
    public static final String PROP_NAME_hours = "hours";
    public static final int PROP_ID_hours = 4;
    
    /* 学分: SCORE INTEGER */
    public static final String PROP_NAME_score = "score";
    public static final int PROP_ID_score = 5;
    
    /* 租户号: TENANT_ID VARCHAR */
    public static final String PROP_NAME_tenantId = "tenantId";
    public static final int PROP_ID_tenantId = 6;
    
    /* 乐观锁: REVISION INTEGER */
    public static final String PROP_NAME_revision = "revision";
    public static final int PROP_ID_revision = 7;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 8;
    
    /* 创建时间: CREATED_TIME DATETIME */
    public static final String PROP_NAME_createdTime = "createdTime";
    public static final int PROP_ID_createdTime = 9;
    
    /* 更新人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 10;
    
    /* 更新时间: UPDATED_TIME DATETIME */
    public static final String PROP_NAME_updatedTime = "updatedTime";
    public static final int PROP_ID_updatedTime = 11;
    

    private static int _PROP_ID_BOUND = 12;

    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_lessonId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_lessonId};

    private static final String[] PROP_ID_TO_NAME = new String[12];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_lessonId] = PROP_NAME_lessonId;
          PROP_NAME_TO_ID.put(PROP_NAME_lessonId, PROP_ID_lessonId);
      
          PROP_ID_TO_NAME[PROP_ID_lessonName] = PROP_NAME_lessonName;
          PROP_NAME_TO_ID.put(PROP_NAME_lessonName, PROP_ID_lessonName);
      
          PROP_ID_TO_NAME[PROP_ID_intro] = PROP_NAME_intro;
          PROP_NAME_TO_ID.put(PROP_NAME_intro, PROP_ID_intro);
      
          PROP_ID_TO_NAME[PROP_ID_hours] = PROP_NAME_hours;
          PROP_NAME_TO_ID.put(PROP_NAME_hours, PROP_ID_hours);
      
          PROP_ID_TO_NAME[PROP_ID_score] = PROP_NAME_score;
          PROP_NAME_TO_ID.put(PROP_NAME_score, PROP_ID_score);
      
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

    
    /* 课程ID: LESSON_ID */
    private java.lang.String _lessonId;
    
    /* 课程名: LESSON_NAME */
    private java.lang.String _lessonName;
    
    /* 课程说明: INTRO */
    private java.lang.String _intro;
    
    /* 学时: HOURS */
    private java.lang.Integer _hours;
    
    /* 学分: SCORE */
    private java.lang.Integer _score;
    
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
    

    public _SimsLesson(){
        // for debug
    }

    protected SimsLesson newInstance(){
       return new SimsLesson();
    }

    @Override
    public SimsLesson cloneInstance() {
        SimsLesson entity = newInstance();
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
      return "io.nop.app.SimsLesson";
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
    
        return buildSimpleId(PROP_ID_lessonId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_lessonId;
          
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
        
            case PROP_ID_lessonId:
               return getLessonId();
        
            case PROP_ID_lessonName:
               return getLessonName();
        
            case PROP_ID_intro:
               return getIntro();
        
            case PROP_ID_hours:
               return getHours();
        
            case PROP_ID_score:
               return getScore();
        
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
        
            case PROP_ID_lessonId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_lessonId));
               }
               setLessonId(typedValue);
               break;
            }
        
            case PROP_ID_lessonName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_lessonName));
               }
               setLessonName(typedValue);
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
        
            case PROP_ID_hours:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_hours));
               }
               setHours(typedValue);
               break;
            }
        
            case PROP_ID_score:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_score));
               }
               setScore(typedValue);
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
        
            case PROP_ID_lessonId:{
               onInitProp(propId);
               this._lessonId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_lessonName:{
               onInitProp(propId);
               this._lessonName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_intro:{
               onInitProp(propId);
               this._intro = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_hours:{
               onInitProp(propId);
               this._hours = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_score:{
               onInitProp(propId);
               this._score = (java.lang.Integer)value;
               
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
     * 课程ID: LESSON_ID
     */
    public java.lang.String getLessonId(){
         onPropGet(PROP_ID_lessonId);
         return _lessonId;
    }

    /**
     * 课程ID: LESSON_ID
     */
    public void setLessonId(java.lang.String value){
        if(onPropSet(PROP_ID_lessonId,value)){
            this._lessonId = value;
            internalClearRefs(PROP_ID_lessonId);
            orm_id();
        }
    }
    
    /**
     * 课程名: LESSON_NAME
     */
    public java.lang.String getLessonName(){
         onPropGet(PROP_ID_lessonName);
         return _lessonName;
    }

    /**
     * 课程名: LESSON_NAME
     */
    public void setLessonName(java.lang.String value){
        if(onPropSet(PROP_ID_lessonName,value)){
            this._lessonName = value;
            internalClearRefs(PROP_ID_lessonName);
            
        }
    }
    
    /**
     * 课程说明: INTRO
     */
    public java.lang.String getIntro(){
         onPropGet(PROP_ID_intro);
         return _intro;
    }

    /**
     * 课程说明: INTRO
     */
    public void setIntro(java.lang.String value){
        if(onPropSet(PROP_ID_intro,value)){
            this._intro = value;
            internalClearRefs(PROP_ID_intro);
            
        }
    }
    
    /**
     * 学时: HOURS
     */
    public java.lang.Integer getHours(){
         onPropGet(PROP_ID_hours);
         return _hours;
    }

    /**
     * 学时: HOURS
     */
    public void setHours(java.lang.Integer value){
        if(onPropSet(PROP_ID_hours,value)){
            this._hours = value;
            internalClearRefs(PROP_ID_hours);
            
        }
    }
    
    /**
     * 学分: SCORE
     */
    public java.lang.Integer getScore(){
         onPropGet(PROP_ID_score);
         return _score;
    }

    /**
     * 学分: SCORE
     */
    public void setScore(java.lang.Integer value){
        if(onPropSet(PROP_ID_score,value)){
            this._score = value;
            internalClearRefs(PROP_ID_score);
            
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
