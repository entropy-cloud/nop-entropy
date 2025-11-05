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

import io.nop.app.SimsMajor;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  专业: sims_major
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _SimsMajor extends DynamicOrmEntity{
    
    /* 专业ID: MAJOR_ID VARCHAR */
    public static final String PROP_NAME_majorId = "majorId";
    public static final int PROP_ID_majorId = 1;
    
    /* 专业名称: MAJOR_NAME VARCHAR */
    public static final String PROP_NAME_majorName = "majorName";
    public static final int PROP_ID_majorName = 2;
    
    /* 专业简称: SHORT_NAME VARCHAR */
    public static final String PROP_NAME_shortName = "shortName";
    public static final int PROP_ID_shortName = 3;
    
    /* 开设日期: ESTAB_DATE DATETIME */
    public static final String PROP_NAME_estabDate = "estabDate";
    public static final int PROP_ID_estabDate = 4;
    
    /* 专业介绍: INTRO VARCHAR */
    public static final String PROP_NAME_intro = "intro";
    public static final int PROP_ID_intro = 5;
    
    /* 学费: TUITION_FEE DECIMAL */
    public static final String PROP_NAME_tuitionFee = "tuitionFee";
    public static final int PROP_ID_tuitionFee = 6;
    
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
    
    /* : DEL_FLAG TINYINT */
    public static final String PROP_NAME_delFlag = "delFlag";
    public static final int PROP_ID_delFlag = 20;
    

    private static int _PROP_ID_BOUND = 21;

    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_majorId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_majorId};

    private static final String[] PROP_ID_TO_NAME = new String[21];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_majorId] = PROP_NAME_majorId;
          PROP_NAME_TO_ID.put(PROP_NAME_majorId, PROP_ID_majorId);
      
          PROP_ID_TO_NAME[PROP_ID_majorName] = PROP_NAME_majorName;
          PROP_NAME_TO_ID.put(PROP_NAME_majorName, PROP_ID_majorName);
      
          PROP_ID_TO_NAME[PROP_ID_shortName] = PROP_NAME_shortName;
          PROP_NAME_TO_ID.put(PROP_NAME_shortName, PROP_ID_shortName);
      
          PROP_ID_TO_NAME[PROP_ID_estabDate] = PROP_NAME_estabDate;
          PROP_NAME_TO_ID.put(PROP_NAME_estabDate, PROP_ID_estabDate);
      
          PROP_ID_TO_NAME[PROP_ID_intro] = PROP_NAME_intro;
          PROP_NAME_TO_ID.put(PROP_NAME_intro, PROP_ID_intro);
      
          PROP_ID_TO_NAME[PROP_ID_tuitionFee] = PROP_NAME_tuitionFee;
          PROP_NAME_TO_ID.put(PROP_NAME_tuitionFee, PROP_ID_tuitionFee);
      
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
      
          PROP_ID_TO_NAME[PROP_ID_delFlag] = PROP_NAME_delFlag;
          PROP_NAME_TO_ID.put(PROP_NAME_delFlag, PROP_ID_delFlag);
      
    }

    
    /* 专业ID: MAJOR_ID */
    private java.lang.String _majorId;
    
    /* 专业名称: MAJOR_NAME */
    private java.lang.String _majorName;
    
    /* 专业简称: SHORT_NAME */
    private java.lang.String _shortName;
    
    /* 开设日期: ESTAB_DATE */
    private java.time.LocalDateTime _estabDate;
    
    /* 专业介绍: INTRO */
    private java.lang.String _intro;
    
    /* 学费: TUITION_FEE */
    private java.math.BigDecimal _tuitionFee;
    
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
    
    /* : DEL_FLAG */
    private java.lang.Byte _delFlag;
    

    public _SimsMajor(){
        // for debug
    }

    protected SimsMajor newInstance(){
       return new SimsMajor();
    }

    @Override
    public SimsMajor cloneInstance() {
        SimsMajor entity = newInstance();
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
      return "io.nop.app.SimsMajor";
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
    
        return buildSimpleId(PROP_ID_majorId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_majorId;
          
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
        
            case PROP_ID_majorId:
               return getMajorId();
        
            case PROP_ID_majorName:
               return getMajorName();
        
            case PROP_ID_shortName:
               return getShortName();
        
            case PROP_ID_estabDate:
               return getEstabDate();
        
            case PROP_ID_intro:
               return getIntro();
        
            case PROP_ID_tuitionFee:
               return getTuitionFee();
        
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
        
            case PROP_ID_delFlag:
               return getDelFlag();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_majorId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_majorId));
               }
               setMajorId(typedValue);
               break;
            }
        
            case PROP_ID_majorName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_majorName));
               }
               setMajorName(typedValue);
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
        
            case PROP_ID_estabDate:{
               java.time.LocalDateTime typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLocalDateTime(value,
                       err-> newTypeConversionError(PROP_NAME_estabDate));
               }
               setEstabDate(typedValue);
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
        
            case PROP_ID_tuitionFee:{
               java.math.BigDecimal typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBigDecimal(value,
                       err-> newTypeConversionError(PROP_NAME_tuitionFee));
               }
               setTuitionFee(typedValue);
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
        
            case PROP_ID_delFlag:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_delFlag));
               }
               setDelFlag(typedValue);
               break;
            }
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_majorId:{
               onInitProp(propId);
               this._majorId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_majorName:{
               onInitProp(propId);
               this._majorName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_shortName:{
               onInitProp(propId);
               this._shortName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_estabDate:{
               onInitProp(propId);
               this._estabDate = (java.time.LocalDateTime)value;
               
               break;
            }
        
            case PROP_ID_intro:{
               onInitProp(propId);
               this._intro = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_tuitionFee:{
               onInitProp(propId);
               this._tuitionFee = (java.math.BigDecimal)value;
               
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
        
            case PROP_ID_delFlag:{
               onInitProp(propId);
               this._delFlag = (java.lang.Byte)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 专业ID: MAJOR_ID
     */
    public java.lang.String getMajorId(){
         onPropGet(PROP_ID_majorId);
         return _majorId;
    }

    /**
     * 专业ID: MAJOR_ID
     */
    public void setMajorId(java.lang.String value){
        if(onPropSet(PROP_ID_majorId,value)){
            this._majorId = value;
            internalClearRefs(PROP_ID_majorId);
            orm_id();
        }
    }
    
    /**
     * 专业名称: MAJOR_NAME
     */
    public java.lang.String getMajorName(){
         onPropGet(PROP_ID_majorName);
         return _majorName;
    }

    /**
     * 专业名称: MAJOR_NAME
     */
    public void setMajorName(java.lang.String value){
        if(onPropSet(PROP_ID_majorName,value)){
            this._majorName = value;
            internalClearRefs(PROP_ID_majorName);
            
        }
    }
    
    /**
     * 专业简称: SHORT_NAME
     */
    public java.lang.String getShortName(){
         onPropGet(PROP_ID_shortName);
         return _shortName;
    }

    /**
     * 专业简称: SHORT_NAME
     */
    public void setShortName(java.lang.String value){
        if(onPropSet(PROP_ID_shortName,value)){
            this._shortName = value;
            internalClearRefs(PROP_ID_shortName);
            
        }
    }
    
    /**
     * 开设日期: ESTAB_DATE
     */
    public java.time.LocalDateTime getEstabDate(){
         onPropGet(PROP_ID_estabDate);
         return _estabDate;
    }

    /**
     * 开设日期: ESTAB_DATE
     */
    public void setEstabDate(java.time.LocalDateTime value){
        if(onPropSet(PROP_ID_estabDate,value)){
            this._estabDate = value;
            internalClearRefs(PROP_ID_estabDate);
            
        }
    }
    
    /**
     * 专业介绍: INTRO
     */
    public java.lang.String getIntro(){
         onPropGet(PROP_ID_intro);
         return _intro;
    }

    /**
     * 专业介绍: INTRO
     */
    public void setIntro(java.lang.String value){
        if(onPropSet(PROP_ID_intro,value)){
            this._intro = value;
            internalClearRefs(PROP_ID_intro);
            
        }
    }
    
    /**
     * 学费: TUITION_FEE
     */
    public java.math.BigDecimal getTuitionFee(){
         onPropGet(PROP_ID_tuitionFee);
         return _tuitionFee;
    }

    /**
     * 学费: TUITION_FEE
     */
    public void setTuitionFee(java.math.BigDecimal value){
        if(onPropSet(PROP_ID_tuitionFee,value)){
            this._tuitionFee = value;
            internalClearRefs(PROP_ID_tuitionFee);
            
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
     * : DEL_FLAG
     */
    public java.lang.Byte getDelFlag(){
         onPropGet(PROP_ID_delFlag);
         return _delFlag;
    }

    /**
     * : DEL_FLAG
     */
    public void setDelFlag(java.lang.Byte value){
        if(onPropSet(PROP_ID_delFlag,value)){
            this._delFlag = value;
            internalClearRefs(PROP_ID_delFlag);
            
        }
    }
    
}
// resume CPD analysis - CPD-ON
