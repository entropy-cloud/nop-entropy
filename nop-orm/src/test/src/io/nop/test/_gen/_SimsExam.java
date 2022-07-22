package io.nop.app._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;

import io.nop.orm.support.OrmEntitySet;
import io.nop.orm.IOrmEntitySet;
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;

import io.nop.app.SimsExam;

/**
 *  考试: sims_exam
 */
public class _SimsExam extends DynamicOrmEntity{
    
    /* 考试记录ID: EXAM_ID VARCHAR */
    public static final String PROP_NAME_examId = "examId";
    public static final int PROP_ID_examId = 1;
    
    /* 学生ID: STUDENT_ID VARCHAR */
    public static final String PROP_NAME_studentId = "studentId";
    public static final int PROP_ID_studentId = 2;
    
    /* 课程ID: LESSON_ID VARCHAR */
    public static final String PROP_NAME_lessonId = "lessonId";
    public static final int PROP_ID_lessonId = 3;
    
    /* 考试日期: EXAM_DATE DATETIME */
    public static final String PROP_NAME_examDate = "examDate";
    public static final int PROP_ID_examDate = 4;
    
    /* 考试名: EXAM_NAME VARCHAR */
    public static final String PROP_NAME_examName = "examName";
    public static final int PROP_ID_examName = 5;
    
    /* 考试分数: EXAM_SCORE DECIMAL */
    public static final String PROP_NAME_examScore = "examScore";
    public static final int PROP_ID_examScore = 6;
    
    /* 租户号: TENANT_ID VARCHAR */
    public static final String PROP_NAME_tenantId = "tenantId";
    public static final int PROP_ID_tenantId = 7;
    
    /* 乐观锁: REVISION INTEGER */
    public static final String PROP_NAME_revision = "revision";
    public static final int PROP_ID_revision = 8;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 9;
    
    /* 创建时间: CREATED_TIME DATETIME */
    public static final String PROP_NAME_createdTime = "createdTime";
    public static final int PROP_ID_createdTime = 10;
    
    /* 更新人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 11;
    
    /* 更新时间: UPDATED_TIME DATETIME */
    public static final String PROP_NAME_updatedTime = "updatedTime";
    public static final int PROP_ID_updatedTime = 12;
    
    /* : EXAM_SCORE_SCALE TINYINT */
    public static final String PROP_NAME_examScoreScale = "examScoreScale";
    public static final int PROP_ID_examScoreScale = 20;
    

    private static int _PROP_ID_BOUND = 22;

    
    /* relation:  */
    public static final String PROP_NAME_ext = "ext";
    
    /* relation:  */
    public static final String PROP_NAME_examExt = "examExt";
    
    /* alias: examScoreDecimal.normalizedValue  */
    public static final String PROP_NAME_examScoreNormalized = "examScoreNormalized";
    
    /* alias: ext.fldA.string  */
    public static final String PROP_NAME_extFldA = "extFldA";
    
    /* component:  */
    public static final String PROP_NAME_examScoreDecimal = "examScoreDecimal";
    

    public static final String[] PK_PROP_NAMES = new String[]{PROP_NAME_examId};
    public static final int[] PK_PROP_IDS = new int[]{PROP_ID_examId};

    private static final String[] PROP_ID_TO_NAME = new String[22];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_examId] = PROP_NAME_examId;
          PROP_NAME_TO_ID.put(PROP_NAME_examId, PROP_ID_examId);
      
          PROP_ID_TO_NAME[PROP_ID_studentId] = PROP_NAME_studentId;
          PROP_NAME_TO_ID.put(PROP_NAME_studentId, PROP_ID_studentId);
      
          PROP_ID_TO_NAME[PROP_ID_lessonId] = PROP_NAME_lessonId;
          PROP_NAME_TO_ID.put(PROP_NAME_lessonId, PROP_ID_lessonId);
      
          PROP_ID_TO_NAME[PROP_ID_examDate] = PROP_NAME_examDate;
          PROP_NAME_TO_ID.put(PROP_NAME_examDate, PROP_ID_examDate);
      
          PROP_ID_TO_NAME[PROP_ID_examName] = PROP_NAME_examName;
          PROP_NAME_TO_ID.put(PROP_NAME_examName, PROP_ID_examName);
      
          PROP_ID_TO_NAME[PROP_ID_examScore] = PROP_NAME_examScore;
          PROP_NAME_TO_ID.put(PROP_NAME_examScore, PROP_ID_examScore);
      
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
      
          PROP_ID_TO_NAME[PROP_ID_examScoreScale] = PROP_NAME_examScoreScale;
          PROP_NAME_TO_ID.put(PROP_NAME_examScoreScale, PROP_ID_examScoreScale);
      
    }

    
    /* 考试记录ID: EXAM_ID */
    private java.lang.String _examId;
    
    /* 学生ID: STUDENT_ID */
    private java.lang.String _studentId;
    
    /* 课程ID: LESSON_ID */
    private java.lang.String _lessonId;
    
    /* 考试日期: EXAM_DATE */
    private java.time.LocalDateTime _examDate;
    
    /* 考试名: EXAM_NAME */
    private java.lang.String _examName;
    
    /* 考试分数: EXAM_SCORE */
    private java.math.BigDecimal _examScore;
    
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
    
    /* : EXAM_SCORE_SCALE */
    private java.lang.Byte _examScoreScale;
    

    public _SimsExam(){
    }

    protected SimsExam newInstance(){
       return new SimsExam();
    }

    @Override
    public SimsExam cloneInstance() {
        SimsExam entity = newInstance();
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
      return "io.nop.app.SimsExam";
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
    
        return buildSimpleId(PROP_ID_examId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_examId;
          
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
        
            case PROP_ID_examId:
               return getExamId();
        
            case PROP_ID_studentId:
               return getStudentId();
        
            case PROP_ID_lessonId:
               return getLessonId();
        
            case PROP_ID_examDate:
               return getExamDate();
        
            case PROP_ID_examName:
               return getExamName();
        
            case PROP_ID_examScore:
               return getExamScore();
        
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
        
            case PROP_ID_examScoreScale:
               return getExamScoreScale();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_examId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_examId));
               }
               setExamId(typedValue);
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
        
            case PROP_ID_lessonId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_lessonId));
               }
               setLessonId(typedValue);
               break;
            }
        
            case PROP_ID_examDate:{
               java.time.LocalDateTime typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLocalDateTime(value,
                       err-> newTypeConversionError(PROP_NAME_examDate));
               }
               setExamDate(typedValue);
               break;
            }
        
            case PROP_ID_examName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_examName));
               }
               setExamName(typedValue);
               break;
            }
        
            case PROP_ID_examScore:{
               java.math.BigDecimal typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBigDecimal(value,
                       err-> newTypeConversionError(PROP_NAME_examScore));
               }
               setExamScore(typedValue);
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
        
            case PROP_ID_examScoreScale:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_examScoreScale));
               }
               setExamScoreScale(typedValue);
               break;
            }
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_examId:{
               onInitProp(propId);
               this._examId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_studentId:{
               onInitProp(propId);
               this._studentId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_lessonId:{
               onInitProp(propId);
               this._lessonId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_examDate:{
               onInitProp(propId);
               this._examDate = (java.time.LocalDateTime)value;
               
               break;
            }
        
            case PROP_ID_examName:{
               onInitProp(propId);
               this._examName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_examScore:{
               onInitProp(propId);
               this._examScore = (java.math.BigDecimal)value;
               
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
        
            case PROP_ID_examScoreScale:{
               onInitProp(propId);
               this._examScoreScale = (java.lang.Byte)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 考试记录ID: EXAM_ID
     */
    public java.lang.String getExamId(){
         onPropGet(PROP_ID_examId);
         return _examId;
    }

    /**
     * 考试记录ID: EXAM_ID
     */
    public void setExamId(java.lang.String value){
        if(onPropSet(PROP_ID_examId,value)){
            this._examId = value;
            internalClearRefs(PROP_ID_examId);
            orm_id();
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
            
        }
    }
    
    /**
     * 考试日期: EXAM_DATE
     */
    public java.time.LocalDateTime getExamDate(){
         onPropGet(PROP_ID_examDate);
         return _examDate;
    }

    /**
     * 考试日期: EXAM_DATE
     */
    public void setExamDate(java.time.LocalDateTime value){
        if(onPropSet(PROP_ID_examDate,value)){
            this._examDate = value;
            internalClearRefs(PROP_ID_examDate);
            
        }
    }
    
    /**
     * 考试名: EXAM_NAME
     */
    public java.lang.String getExamName(){
         onPropGet(PROP_ID_examName);
         return _examName;
    }

    /**
     * 考试名: EXAM_NAME
     */
    public void setExamName(java.lang.String value){
        if(onPropSet(PROP_ID_examName,value)){
            this._examName = value;
            internalClearRefs(PROP_ID_examName);
            
        }
    }
    
    /**
     * 考试分数: EXAM_SCORE
     */
    public java.math.BigDecimal getExamScore(){
         onPropGet(PROP_ID_examScore);
         return _examScore;
    }

    /**
     * 考试分数: EXAM_SCORE
     */
    public void setExamScore(java.math.BigDecimal value){
        if(onPropSet(PROP_ID_examScore,value)){
            this._examScore = value;
            internalClearRefs(PROP_ID_examScore);
            
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
    
    /**
     * : EXAM_SCORE_SCALE
     */
    public java.lang.Byte getExamScoreScale(){
         onPropGet(PROP_ID_examScoreScale);
         return _examScoreScale;
    }

    /**
     * : EXAM_SCORE_SCALE
     */
    public void setExamScoreScale(java.lang.Byte value){
        if(onPropSet(PROP_ID_examScoreScale,value)){
            this._examScoreScale = value;
            internalClearRefs(PROP_ID_examScoreScale);
            
        }
    }
    
    private final OrmEntitySet<io.nop.app.SimsExtField> _ext = new OrmEntitySet<>(this, PROP_NAME_ext,
        null, io.nop.app.SimsExtField.PROP_NAME_fieldName,io.nop.app.SimsExtField.class);

    /**
     * 。 refPropName: , keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<io.nop.app.SimsExtField> getExt(){
       return _ext;
    }
       
    private final OrmEntitySet<io.nop.app.SimsExamExtField> _examExt = new OrmEntitySet<>(this, PROP_NAME_examExt,
        null, io.nop.app.SimsExamExtField.PROP_NAME_fieldName,io.nop.app.SimsExamExtField.class);

    /**
     * 。 refPropName: , keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<io.nop.app.SimsExamExtField> getExamExt(){
       return _examExt;
    }
       
   public java.math.BigDecimal getExamScoreNormalized(){
      return (java.math.BigDecimal)internalGetAliasValue("examScoreDecimal.normalizedValue");
   }

   public void setExamScoreNormalized(java.math.BigDecimal value){
      internalSetAliasValue("examScoreDecimal.normalizedValue",value);
   }

   public java.lang.String getExtFldA(){
      return (java.lang.String)internalGetAliasValue("ext.fldA.string");
   }

   public void setExtFldA(java.lang.String value){
      internalSetAliasValue("ext.fldA.string",value);
   }

   private io.nop.orm.support.FloatingScaleDecimal _examScoreDecimal;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_examScoreDecimal = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_examScoreDecimal.put(io.nop.orm.support.FloatingScaleDecimal.PROP_NAME_value,PROP_ID_examScore);
      
         COMPONENT_PROP_ID_MAP_examScoreDecimal.put(io.nop.orm.support.FloatingScaleDecimal.PROP_NAME_scale,PROP_ID_examScoreScale);
      
   }

   public io.nop.orm.support.FloatingScaleDecimal getExamScoreDecimal(){
      if(_examScoreDecimal == null){
          _examScoreDecimal = new io.nop.orm.support.FloatingScaleDecimal();
          _examScoreDecimal.bindToEntity(this, COMPONENT_PROP_ID_MAP_examScoreDecimal);
      }
      return _examScoreDecimal;
   }

}
