package io.nop.metadata.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.metadata.dao.entity.NopMetaMethodArg;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  方法参数: nop_meta_method_arg
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopMetaMethodArg extends DynamicOrmEntity{
    
    /* 参数ID: ARG_ID VARCHAR */
    public static final String PROP_NAME_argId = "argId";
    public static final int PROP_ID_argId = 1;
    
    /* 方法ID: METHOD_ID VARCHAR */
    public static final String PROP_NAME_methodId = "methodId";
    public static final int PROP_ID_methodId = 2;
    
    /* 参数名: ARG_NAME VARCHAR */
    public static final String PROP_NAME_argName = "argName";
    public static final int PROP_ID_argName = 3;
    
    /* 参数类型: ARG_TYPE VARCHAR */
    public static final String PROP_NAME_argType = "argType";
    public static final int PROP_ID_argType = 4;
    
    /* 必填: MANDATORY TINYINT */
    public static final String PROP_NAME_mandatory = "mandatory";
    public static final int PROP_ID_mandatory = 5;
    
    /* 默认值: DEFAULT_VALUE VARCHAR */
    public static final String PROP_NAME_defaultValue = "defaultValue";
    public static final int PROP_ID_defaultValue = 6;
    
    /* 显示名: DISPLAY_NAME VARCHAR */
    public static final String PROP_NAME_displayName = "displayName";
    public static final int PROP_ID_displayName = 7;
    
    /* 数据版本: VERSION BIGINT */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 8;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 9;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 10;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 11;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 12;
    

    private static int _PROP_ID_BOUND = 13;

    
    /* relation: 实体方法 */
    public static final String PROP_NAME_method = "method";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_argId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_argId};

    private static final String[] PROP_ID_TO_NAME = new String[13];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_argId] = PROP_NAME_argId;
          PROP_NAME_TO_ID.put(PROP_NAME_argId, PROP_ID_argId);
      
          PROP_ID_TO_NAME[PROP_ID_methodId] = PROP_NAME_methodId;
          PROP_NAME_TO_ID.put(PROP_NAME_methodId, PROP_ID_methodId);
      
          PROP_ID_TO_NAME[PROP_ID_argName] = PROP_NAME_argName;
          PROP_NAME_TO_ID.put(PROP_NAME_argName, PROP_ID_argName);
      
          PROP_ID_TO_NAME[PROP_ID_argType] = PROP_NAME_argType;
          PROP_NAME_TO_ID.put(PROP_NAME_argType, PROP_ID_argType);
      
          PROP_ID_TO_NAME[PROP_ID_mandatory] = PROP_NAME_mandatory;
          PROP_NAME_TO_ID.put(PROP_NAME_mandatory, PROP_ID_mandatory);
      
          PROP_ID_TO_NAME[PROP_ID_defaultValue] = PROP_NAME_defaultValue;
          PROP_NAME_TO_ID.put(PROP_NAME_defaultValue, PROP_ID_defaultValue);
      
          PROP_ID_TO_NAME[PROP_ID_displayName] = PROP_NAME_displayName;
          PROP_NAME_TO_ID.put(PROP_NAME_displayName, PROP_ID_displayName);
      
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
      
    }

    
    /* 参数ID: ARG_ID */
    private java.lang.String _argId;
    
    /* 方法ID: METHOD_ID */
    private java.lang.String _methodId;
    
    /* 参数名: ARG_NAME */
    private java.lang.String _argName;
    
    /* 参数类型: ARG_TYPE */
    private java.lang.String _argType;
    
    /* 必填: MANDATORY */
    private java.lang.Byte _mandatory;
    
    /* 默认值: DEFAULT_VALUE */
    private java.lang.String _defaultValue;
    
    /* 显示名: DISPLAY_NAME */
    private java.lang.String _displayName;
    
    /* 数据版本: VERSION */
    private java.lang.Long _version;
    
    /* 创建人: CREATED_BY */
    private java.lang.String _createdBy;
    
    /* 创建时间: CREATE_TIME */
    private java.sql.Timestamp _createTime;
    
    /* 修改人: UPDATED_BY */
    private java.lang.String _updatedBy;
    
    /* 修改时间: UPDATE_TIME */
    private java.sql.Timestamp _updateTime;
    

    public _NopMetaMethodArg(){
        // for debug
    }

    protected NopMetaMethodArg newInstance(){
        NopMetaMethodArg entity = new NopMetaMethodArg();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopMetaMethodArg cloneInstance() {
        NopMetaMethodArg entity = newInstance();
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
      return "io.nop.metadata.dao.entity.NopMetaMethodArg";
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
    
        return buildSimpleId(PROP_ID_argId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_argId;
          
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
        
            case PROP_ID_argId:
               return getArgId();
        
            case PROP_ID_methodId:
               return getMethodId();
        
            case PROP_ID_argName:
               return getArgName();
        
            case PROP_ID_argType:
               return getArgType();
        
            case PROP_ID_mandatory:
               return getMandatory();
        
            case PROP_ID_defaultValue:
               return getDefaultValue();
        
            case PROP_ID_displayName:
               return getDisplayName();
        
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
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_argId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_argId));
               }
               setArgId(typedValue);
               break;
            }
        
            case PROP_ID_methodId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_methodId));
               }
               setMethodId(typedValue);
               break;
            }
        
            case PROP_ID_argName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_argName));
               }
               setArgName(typedValue);
               break;
            }
        
            case PROP_ID_argType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_argType));
               }
               setArgType(typedValue);
               break;
            }
        
            case PROP_ID_mandatory:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_mandatory));
               }
               setMandatory(typedValue);
               break;
            }
        
            case PROP_ID_defaultValue:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_defaultValue));
               }
               setDefaultValue(typedValue);
               break;
            }
        
            case PROP_ID_displayName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_displayName));
               }
               setDisplayName(typedValue);
               break;
            }
        
            case PROP_ID_version:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
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
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_argId:{
               onInitProp(propId);
               this._argId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_methodId:{
               onInitProp(propId);
               this._methodId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_argName:{
               onInitProp(propId);
               this._argName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_argType:{
               onInitProp(propId);
               this._argType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_mandatory:{
               onInitProp(propId);
               this._mandatory = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_defaultValue:{
               onInitProp(propId);
               this._defaultValue = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_displayName:{
               onInitProp(propId);
               this._displayName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_version:{
               onInitProp(propId);
               this._version = (java.lang.Long)value;
               
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
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 参数ID: ARG_ID
     */
    public final java.lang.String getArgId(){
         onPropGet(PROP_ID_argId);
         return _argId;
    }

    /**
     * 参数ID: ARG_ID
     */
    public final void setArgId(java.lang.String value){
        if(onPropSet(PROP_ID_argId,value)){
            this._argId = value;
            internalClearRefs(PROP_ID_argId);
            orm_id();
        }
    }
    
    /**
     * 方法ID: METHOD_ID
     */
    public final java.lang.String getMethodId(){
         onPropGet(PROP_ID_methodId);
         return _methodId;
    }

    /**
     * 方法ID: METHOD_ID
     */
    public final void setMethodId(java.lang.String value){
        if(onPropSet(PROP_ID_methodId,value)){
            this._methodId = value;
            internalClearRefs(PROP_ID_methodId);
            
        }
    }
    
    /**
     * 参数名: ARG_NAME
     */
    public final java.lang.String getArgName(){
         onPropGet(PROP_ID_argName);
         return _argName;
    }

    /**
     * 参数名: ARG_NAME
     */
    public final void setArgName(java.lang.String value){
        if(onPropSet(PROP_ID_argName,value)){
            this._argName = value;
            internalClearRefs(PROP_ID_argName);
            
        }
    }
    
    /**
     * 参数类型: ARG_TYPE
     */
    public final java.lang.String getArgType(){
         onPropGet(PROP_ID_argType);
         return _argType;
    }

    /**
     * 参数类型: ARG_TYPE
     */
    public final void setArgType(java.lang.String value){
        if(onPropSet(PROP_ID_argType,value)){
            this._argType = value;
            internalClearRefs(PROP_ID_argType);
            
        }
    }
    
    /**
     * 必填: MANDATORY
     */
    public final java.lang.Byte getMandatory(){
         onPropGet(PROP_ID_mandatory);
         return _mandatory;
    }

    /**
     * 必填: MANDATORY
     */
    public final void setMandatory(java.lang.Byte value){
        if(onPropSet(PROP_ID_mandatory,value)){
            this._mandatory = value;
            internalClearRefs(PROP_ID_mandatory);
            
        }
    }
    
    /**
     * 默认值: DEFAULT_VALUE
     */
    public final java.lang.String getDefaultValue(){
         onPropGet(PROP_ID_defaultValue);
         return _defaultValue;
    }

    /**
     * 默认值: DEFAULT_VALUE
     */
    public final void setDefaultValue(java.lang.String value){
        if(onPropSet(PROP_ID_defaultValue,value)){
            this._defaultValue = value;
            internalClearRefs(PROP_ID_defaultValue);
            
        }
    }
    
    /**
     * 显示名: DISPLAY_NAME
     */
    public final java.lang.String getDisplayName(){
         onPropGet(PROP_ID_displayName);
         return _displayName;
    }

    /**
     * 显示名: DISPLAY_NAME
     */
    public final void setDisplayName(java.lang.String value){
        if(onPropSet(PROP_ID_displayName,value)){
            this._displayName = value;
            internalClearRefs(PROP_ID_displayName);
            
        }
    }
    
    /**
     * 数据版本: VERSION
     */
    public final java.lang.Long getVersion(){
         onPropGet(PROP_ID_version);
         return _version;
    }

    /**
     * 数据版本: VERSION
     */
    public final void setVersion(java.lang.Long value){
        if(onPropSet(PROP_ID_version,value)){
            this._version = value;
            internalClearRefs(PROP_ID_version);
            
        }
    }
    
    /**
     * 创建人: CREATED_BY
     */
    public final java.lang.String getCreatedBy(){
         onPropGet(PROP_ID_createdBy);
         return _createdBy;
    }

    /**
     * 创建人: CREATED_BY
     */
    public final void setCreatedBy(java.lang.String value){
        if(onPropSet(PROP_ID_createdBy,value)){
            this._createdBy = value;
            internalClearRefs(PROP_ID_createdBy);
            
        }
    }
    
    /**
     * 创建时间: CREATE_TIME
     */
    public final java.sql.Timestamp getCreateTime(){
         onPropGet(PROP_ID_createTime);
         return _createTime;
    }

    /**
     * 创建时间: CREATE_TIME
     */
    public final void setCreateTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_createTime,value)){
            this._createTime = value;
            internalClearRefs(PROP_ID_createTime);
            
        }
    }
    
    /**
     * 修改人: UPDATED_BY
     */
    public final java.lang.String getUpdatedBy(){
         onPropGet(PROP_ID_updatedBy);
         return _updatedBy;
    }

    /**
     * 修改人: UPDATED_BY
     */
    public final void setUpdatedBy(java.lang.String value){
        if(onPropSet(PROP_ID_updatedBy,value)){
            this._updatedBy = value;
            internalClearRefs(PROP_ID_updatedBy);
            
        }
    }
    
    /**
     * 修改时间: UPDATE_TIME
     */
    public final java.sql.Timestamp getUpdateTime(){
         onPropGet(PROP_ID_updateTime);
         return _updateTime;
    }

    /**
     * 修改时间: UPDATE_TIME
     */
    public final void setUpdateTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_updateTime,value)){
            this._updateTime = value;
            internalClearRefs(PROP_ID_updateTime);
            
        }
    }
    
    /**
     * 实体方法
     */
    public final io.nop.metadata.dao.entity.NopMetaEntityMethod getMethod(){
       return (io.nop.metadata.dao.entity.NopMetaEntityMethod)internalGetRefEntity(PROP_NAME_method);
    }

    public final void setMethod(io.nop.metadata.dao.entity.NopMetaEntityMethod refEntity){
   
           if(refEntity == null){
           
                   this.setMethodId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_method, refEntity,()->{
           
                           this.setMethodId(refEntity.getMethodId());
                       
           });
           }
       
    }
       
}
// resume CPD analysis - CPD-ON
