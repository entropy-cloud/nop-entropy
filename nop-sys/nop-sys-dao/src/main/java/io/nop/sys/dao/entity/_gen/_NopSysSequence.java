package io.nop.sys.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.sys.dao.entity.NopSysSequence;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  序列号: nop_sys_sequence
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopSysSequence extends DynamicOrmEntity{
    
    /* 名称: SEQ_NAME VARCHAR */
    public static final String PROP_NAME_seqName = "seqName";
    public static final int PROP_ID_seqName = 1;
    
    /* 类型: SEQ_TYPE VARCHAR */
    public static final String PROP_NAME_seqType = "seqType";
    public static final int PROP_ID_seqType = 2;
    
    /* 是否UUID: IS_UUID TINYINT */
    public static final String PROP_NAME_isUuid = "isUuid";
    public static final int PROP_ID_isUuid = 3;
    
    /* 下一个值: NEXT_VALUE BIGINT */
    public static final String PROP_NAME_nextValue = "nextValue";
    public static final int PROP_ID_nextValue = 4;
    
    /* 步长: STEP_SIZE INTEGER */
    public static final String PROP_NAME_stepSize = "stepSize";
    public static final int PROP_ID_stepSize = 5;
    
    /* 缓存个数: CACHE_SIZE INTEGER */
    public static final String PROP_NAME_cacheSize = "cacheSize";
    public static final int PROP_ID_cacheSize = 6;
    
    /* 最大值: MAX_VALUE BIGINT */
    public static final String PROP_NAME_maxValue = "maxValue";
    public static final int PROP_ID_maxValue = 7;
    
    /* 重置方式: RESET_TYPE INTEGER */
    public static final String PROP_NAME_resetType = "resetType";
    public static final int PROP_ID_resetType = 8;
    
    /* 删除标识: DEL_FLAG TINYINT */
    public static final String PROP_NAME_delFlag = "delFlag";
    public static final int PROP_ID_delFlag = 9;
    
    /* 数据版本: VERSION INTEGER */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 10;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 11;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 12;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 13;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 14;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 15;
    

    private static int _PROP_ID_BOUND = 16;

    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_seqName);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_seqName};

    private static final String[] PROP_ID_TO_NAME = new String[16];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_seqName] = PROP_NAME_seqName;
          PROP_NAME_TO_ID.put(PROP_NAME_seqName, PROP_ID_seqName);
      
          PROP_ID_TO_NAME[PROP_ID_seqType] = PROP_NAME_seqType;
          PROP_NAME_TO_ID.put(PROP_NAME_seqType, PROP_ID_seqType);
      
          PROP_ID_TO_NAME[PROP_ID_isUuid] = PROP_NAME_isUuid;
          PROP_NAME_TO_ID.put(PROP_NAME_isUuid, PROP_ID_isUuid);
      
          PROP_ID_TO_NAME[PROP_ID_nextValue] = PROP_NAME_nextValue;
          PROP_NAME_TO_ID.put(PROP_NAME_nextValue, PROP_ID_nextValue);
      
          PROP_ID_TO_NAME[PROP_ID_stepSize] = PROP_NAME_stepSize;
          PROP_NAME_TO_ID.put(PROP_NAME_stepSize, PROP_ID_stepSize);
      
          PROP_ID_TO_NAME[PROP_ID_cacheSize] = PROP_NAME_cacheSize;
          PROP_NAME_TO_ID.put(PROP_NAME_cacheSize, PROP_ID_cacheSize);
      
          PROP_ID_TO_NAME[PROP_ID_maxValue] = PROP_NAME_maxValue;
          PROP_NAME_TO_ID.put(PROP_NAME_maxValue, PROP_ID_maxValue);
      
          PROP_ID_TO_NAME[PROP_ID_resetType] = PROP_NAME_resetType;
          PROP_NAME_TO_ID.put(PROP_NAME_resetType, PROP_ID_resetType);
      
          PROP_ID_TO_NAME[PROP_ID_delFlag] = PROP_NAME_delFlag;
          PROP_NAME_TO_ID.put(PROP_NAME_delFlag, PROP_ID_delFlag);
      
          PROP_ID_TO_NAME[PROP_ID_version] = PROP_NAME_version;
          PROP_NAME_TO_ID.put(PROP_NAME_version, PROP_ID_version);
      
          PROP_ID_TO_NAME[PROP_ID_createdBy] = PROP_NAME_createdBy;
          PROP_NAME_TO_ID.put(PROP_NAME_createdBy, PROP_ID_createdBy);
      
          PROP_ID_TO_NAME[PROP_ID_createTime] = PROP_NAME_createTime;
          PROP_NAME_TO_ID.put(PROP_NAME_createTime, PROP_ID_createTime);
      
          PROP_ID_TO_NAME[PROP_ID_updatedBy] = PROP_NAME_updatedBy;
          PROP_NAME_TO_ID.put(PROP_NAME_updatedBy, PROP_ID_updatedBy);
      
          PROP_ID_TO_NAME[PROP_ID_updateTime] = PROP_NAME_updateTime;
          PROP_NAME_TO_ID.put(PROP_NAME_updateTime, PROP_ID_updateTime);
      
          PROP_ID_TO_NAME[PROP_ID_remark] = PROP_NAME_remark;
          PROP_NAME_TO_ID.put(PROP_NAME_remark, PROP_ID_remark);
      
    }

    
    /* 名称: SEQ_NAME */
    private java.lang.String _seqName;
    
    /* 类型: SEQ_TYPE */
    private java.lang.String _seqType;
    
    /* 是否UUID: IS_UUID */
    private java.lang.Byte _isUuid;
    
    /* 下一个值: NEXT_VALUE */
    private java.lang.Long _nextValue;
    
    /* 步长: STEP_SIZE */
    private java.lang.Integer _stepSize;
    
    /* 缓存个数: CACHE_SIZE */
    private java.lang.Integer _cacheSize;
    
    /* 最大值: MAX_VALUE */
    private java.lang.Long _maxValue;
    
    /* 重置方式: RESET_TYPE */
    private java.lang.Integer _resetType;
    
    /* 删除标识: DEL_FLAG */
    private java.lang.Byte _delFlag;
    
    /* 数据版本: VERSION */
    private java.lang.Integer _version;
    
    /* 创建人: CREATED_BY */
    private java.lang.String _createdBy;
    
    /* 创建时间: CREATE_TIME */
    private java.sql.Timestamp _createTime;
    
    /* 修改人: UPDATED_BY */
    private java.lang.String _updatedBy;
    
    /* 修改时间: UPDATE_TIME */
    private java.sql.Timestamp _updateTime;
    
    /* 备注: REMARK */
    private java.lang.String _remark;
    

    public _NopSysSequence(){
        // for debug
    }

    protected NopSysSequence newInstance(){
       return new NopSysSequence();
    }

    @Override
    public NopSysSequence cloneInstance() {
        NopSysSequence entity = newInstance();
        orm_forEachInitedProp((value, propId) -> {
            entity.orm_propValue(propId,value);
        });
        return entity;
    }

    @Override
    public String orm_entityName() {
      // 如果存在实体模型对象，则以模型对象上的设置为准
      IEntityModel entityModel = orm_entityModel();
      if(entityModel != null)
          return entityModel.getName();
      return "io.nop.sys.dao.entity.NopSysSequence";
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
    
        return buildSimpleId(PROP_ID_seqName);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_seqName;
          
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
        
            case PROP_ID_seqName:
               return getSeqName();
        
            case PROP_ID_seqType:
               return getSeqType();
        
            case PROP_ID_isUuid:
               return getIsUuid();
        
            case PROP_ID_nextValue:
               return getNextValue();
        
            case PROP_ID_stepSize:
               return getStepSize();
        
            case PROP_ID_cacheSize:
               return getCacheSize();
        
            case PROP_ID_maxValue:
               return getMaxValue();
        
            case PROP_ID_resetType:
               return getResetType();
        
            case PROP_ID_delFlag:
               return getDelFlag();
        
            case PROP_ID_version:
               return getVersion();
        
            case PROP_ID_createdBy:
               return getCreatedBy();
        
            case PROP_ID_createTime:
               return getCreateTime();
        
            case PROP_ID_updatedBy:
               return getUpdatedBy();
        
            case PROP_ID_updateTime:
               return getUpdateTime();
        
            case PROP_ID_remark:
               return getRemark();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_seqName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_seqName));
               }
               setSeqName(typedValue);
               break;
            }
        
            case PROP_ID_seqType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_seqType));
               }
               setSeqType(typedValue);
               break;
            }
        
            case PROP_ID_isUuid:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_isUuid));
               }
               setIsUuid(typedValue);
               break;
            }
        
            case PROP_ID_nextValue:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_nextValue));
               }
               setNextValue(typedValue);
               break;
            }
        
            case PROP_ID_stepSize:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_stepSize));
               }
               setStepSize(typedValue);
               break;
            }
        
            case PROP_ID_cacheSize:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_cacheSize));
               }
               setCacheSize(typedValue);
               break;
            }
        
            case PROP_ID_maxValue:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_maxValue));
               }
               setMaxValue(typedValue);
               break;
            }
        
            case PROP_ID_resetType:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_resetType));
               }
               setResetType(typedValue);
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
        
            case PROP_ID_version:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_version));
               }
               setVersion(typedValue);
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
        
            case PROP_ID_createTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_createTime));
               }
               setCreateTime(typedValue);
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
        
            case PROP_ID_updateTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_updateTime));
               }
               setUpdateTime(typedValue);
               break;
            }
        
            case PROP_ID_remark:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_remark));
               }
               setRemark(typedValue);
               break;
            }
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_seqName:{
               onInitProp(propId);
               this._seqName = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_seqType:{
               onInitProp(propId);
               this._seqType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_isUuid:{
               onInitProp(propId);
               this._isUuid = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_nextValue:{
               onInitProp(propId);
               this._nextValue = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_stepSize:{
               onInitProp(propId);
               this._stepSize = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_cacheSize:{
               onInitProp(propId);
               this._cacheSize = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_maxValue:{
               onInitProp(propId);
               this._maxValue = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_resetType:{
               onInitProp(propId);
               this._resetType = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_delFlag:{
               onInitProp(propId);
               this._delFlag = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_version:{
               onInitProp(propId);
               this._version = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_createdBy:{
               onInitProp(propId);
               this._createdBy = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_createTime:{
               onInitProp(propId);
               this._createTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_updatedBy:{
               onInitProp(propId);
               this._updatedBy = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_updateTime:{
               onInitProp(propId);
               this._updateTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_remark:{
               onInitProp(propId);
               this._remark = (java.lang.String)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 名称: SEQ_NAME
     */
    public java.lang.String getSeqName(){
         onPropGet(PROP_ID_seqName);
         return _seqName;
    }

    /**
     * 名称: SEQ_NAME
     */
    public void setSeqName(java.lang.String value){
        if(onPropSet(PROP_ID_seqName,value)){
            this._seqName = value;
            internalClearRefs(PROP_ID_seqName);
            orm_id();
        }
    }
    
    /**
     * 类型: SEQ_TYPE
     */
    public java.lang.String getSeqType(){
         onPropGet(PROP_ID_seqType);
         return _seqType;
    }

    /**
     * 类型: SEQ_TYPE
     */
    public void setSeqType(java.lang.String value){
        if(onPropSet(PROP_ID_seqType,value)){
            this._seqType = value;
            internalClearRefs(PROP_ID_seqType);
            
        }
    }
    
    /**
     * 是否UUID: IS_UUID
     */
    public java.lang.Byte getIsUuid(){
         onPropGet(PROP_ID_isUuid);
         return _isUuid;
    }

    /**
     * 是否UUID: IS_UUID
     */
    public void setIsUuid(java.lang.Byte value){
        if(onPropSet(PROP_ID_isUuid,value)){
            this._isUuid = value;
            internalClearRefs(PROP_ID_isUuid);
            
        }
    }
    
    /**
     * 下一个值: NEXT_VALUE
     */
    public java.lang.Long getNextValue(){
         onPropGet(PROP_ID_nextValue);
         return _nextValue;
    }

    /**
     * 下一个值: NEXT_VALUE
     */
    public void setNextValue(java.lang.Long value){
        if(onPropSet(PROP_ID_nextValue,value)){
            this._nextValue = value;
            internalClearRefs(PROP_ID_nextValue);
            
        }
    }
    
    /**
     * 步长: STEP_SIZE
     */
    public java.lang.Integer getStepSize(){
         onPropGet(PROP_ID_stepSize);
         return _stepSize;
    }

    /**
     * 步长: STEP_SIZE
     */
    public void setStepSize(java.lang.Integer value){
        if(onPropSet(PROP_ID_stepSize,value)){
            this._stepSize = value;
            internalClearRefs(PROP_ID_stepSize);
            
        }
    }
    
    /**
     * 缓存个数: CACHE_SIZE
     */
    public java.lang.Integer getCacheSize(){
         onPropGet(PROP_ID_cacheSize);
         return _cacheSize;
    }

    /**
     * 缓存个数: CACHE_SIZE
     */
    public void setCacheSize(java.lang.Integer value){
        if(onPropSet(PROP_ID_cacheSize,value)){
            this._cacheSize = value;
            internalClearRefs(PROP_ID_cacheSize);
            
        }
    }
    
    /**
     * 最大值: MAX_VALUE
     */
    public java.lang.Long getMaxValue(){
         onPropGet(PROP_ID_maxValue);
         return _maxValue;
    }

    /**
     * 最大值: MAX_VALUE
     */
    public void setMaxValue(java.lang.Long value){
        if(onPropSet(PROP_ID_maxValue,value)){
            this._maxValue = value;
            internalClearRefs(PROP_ID_maxValue);
            
        }
    }
    
    /**
     * 重置方式: RESET_TYPE
     */
    public java.lang.Integer getResetType(){
         onPropGet(PROP_ID_resetType);
         return _resetType;
    }

    /**
     * 重置方式: RESET_TYPE
     */
    public void setResetType(java.lang.Integer value){
        if(onPropSet(PROP_ID_resetType,value)){
            this._resetType = value;
            internalClearRefs(PROP_ID_resetType);
            
        }
    }
    
    /**
     * 删除标识: DEL_FLAG
     */
    public java.lang.Byte getDelFlag(){
         onPropGet(PROP_ID_delFlag);
         return _delFlag;
    }

    /**
     * 删除标识: DEL_FLAG
     */
    public void setDelFlag(java.lang.Byte value){
        if(onPropSet(PROP_ID_delFlag,value)){
            this._delFlag = value;
            internalClearRefs(PROP_ID_delFlag);
            
        }
    }
    
    /**
     * 数据版本: VERSION
     */
    public java.lang.Integer getVersion(){
         onPropGet(PROP_ID_version);
         return _version;
    }

    /**
     * 数据版本: VERSION
     */
    public void setVersion(java.lang.Integer value){
        if(onPropSet(PROP_ID_version,value)){
            this._version = value;
            internalClearRefs(PROP_ID_version);
            
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
     * 创建时间: CREATE_TIME
     */
    public java.sql.Timestamp getCreateTime(){
         onPropGet(PROP_ID_createTime);
         return _createTime;
    }

    /**
     * 创建时间: CREATE_TIME
     */
    public void setCreateTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_createTime,value)){
            this._createTime = value;
            internalClearRefs(PROP_ID_createTime);
            
        }
    }
    
    /**
     * 修改人: UPDATED_BY
     */
    public java.lang.String getUpdatedBy(){
         onPropGet(PROP_ID_updatedBy);
         return _updatedBy;
    }

    /**
     * 修改人: UPDATED_BY
     */
    public void setUpdatedBy(java.lang.String value){
        if(onPropSet(PROP_ID_updatedBy,value)){
            this._updatedBy = value;
            internalClearRefs(PROP_ID_updatedBy);
            
        }
    }
    
    /**
     * 修改时间: UPDATE_TIME
     */
    public java.sql.Timestamp getUpdateTime(){
         onPropGet(PROP_ID_updateTime);
         return _updateTime;
    }

    /**
     * 修改时间: UPDATE_TIME
     */
    public void setUpdateTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_updateTime,value)){
            this._updateTime = value;
            internalClearRefs(PROP_ID_updateTime);
            
        }
    }
    
    /**
     * 备注: REMARK
     */
    public java.lang.String getRemark(){
         onPropGet(PROP_ID_remark);
         return _remark;
    }

    /**
     * 备注: REMARK
     */
    public void setRemark(java.lang.String value){
        if(onPropSet(PROP_ID_remark,value)){
            this._remark = value;
            internalClearRefs(PROP_ID_remark);
            
        }
    }
    
}
// resume CPD analysis - CPD-ON
