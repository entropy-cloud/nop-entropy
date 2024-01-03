package io.nop.dyn.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.dyn.dao.entity.NopDynFunctionMeta;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  实体函数定义: nop_dyn_function_meta
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopDynFunctionMeta extends DynamicOrmEntity{
    
    /* 函数定义ID: FUNC_META_ID VARCHAR */
    public static final String PROP_NAME_funcMetaId = "funcMetaId";
    public static final int PROP_ID_funcMetaId = 1;
    
    /* 实体定义ID: ENTITY_META_ID VARCHAR */
    public static final String PROP_NAME_entityMetaId = "entityMetaId";
    public static final int PROP_ID_entityMetaId = 2;
    
    /* 函数名: NAME VARCHAR */
    public static final String PROP_NAME_name = "name";
    public static final int PROP_ID_name = 3;
    
    /* 显示名: DISPLAY_NAME VARCHAR */
    public static final String PROP_NAME_displayName = "displayName";
    public static final int PROP_ID_displayName = 4;
    
    /* 函数类型: FUNCTION_TYPE VARCHAR */
    public static final String PROP_NAME_functionType = "functionType";
    public static final int PROP_ID_functionType = 5;
    
    /* 参数定义: ARGS_META VARCHAR */
    public static final String PROP_NAME_argsMeta = "argsMeta";
    public static final int PROP_ID_argsMeta = 6;
    
    /* 返回类型: RETURN_TYPE VARCHAR */
    public static final String PROP_NAME_returnType = "returnType";
    public static final int PROP_ID_returnType = 7;
    
    /* 标签: TAG_SET VARCHAR */
    public static final String PROP_NAME_tagSet = "tagSet";
    public static final int PROP_ID_tagSet = 8;
    
    /* 源码: SOURCE VARCHAR */
    public static final String PROP_NAME_source = "source";
    public static final int PROP_ID_source = 9;
    
    /* 状态: STATUS INTEGER */
    public static final String PROP_NAME_status = "status";
    public static final int PROP_ID_status = 10;
    
    /* 数据版本: VERSION INTEGER */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 11;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 12;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 13;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 14;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 15;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 16;
    

    private static int _PROP_ID_BOUND = 17;

    
    /* relation: 所属模块 */
    public static final String PROP_NAME_entityMeta = "entityMeta";
    
    /* component:  */
    public static final String PROP_NAME_argsMetaComponent = "argsMetaComponent";
    
    /* component:  */
    public static final String PROP_NAME_sourceComponent = "sourceComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_funcMetaId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_funcMetaId};

    private static final String[] PROP_ID_TO_NAME = new String[17];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_funcMetaId] = PROP_NAME_funcMetaId;
          PROP_NAME_TO_ID.put(PROP_NAME_funcMetaId, PROP_ID_funcMetaId);
      
          PROP_ID_TO_NAME[PROP_ID_entityMetaId] = PROP_NAME_entityMetaId;
          PROP_NAME_TO_ID.put(PROP_NAME_entityMetaId, PROP_ID_entityMetaId);
      
          PROP_ID_TO_NAME[PROP_ID_name] = PROP_NAME_name;
          PROP_NAME_TO_ID.put(PROP_NAME_name, PROP_ID_name);
      
          PROP_ID_TO_NAME[PROP_ID_displayName] = PROP_NAME_displayName;
          PROP_NAME_TO_ID.put(PROP_NAME_displayName, PROP_ID_displayName);
      
          PROP_ID_TO_NAME[PROP_ID_functionType] = PROP_NAME_functionType;
          PROP_NAME_TO_ID.put(PROP_NAME_functionType, PROP_ID_functionType);
      
          PROP_ID_TO_NAME[PROP_ID_argsMeta] = PROP_NAME_argsMeta;
          PROP_NAME_TO_ID.put(PROP_NAME_argsMeta, PROP_ID_argsMeta);
      
          PROP_ID_TO_NAME[PROP_ID_returnType] = PROP_NAME_returnType;
          PROP_NAME_TO_ID.put(PROP_NAME_returnType, PROP_ID_returnType);
      
          PROP_ID_TO_NAME[PROP_ID_tagSet] = PROP_NAME_tagSet;
          PROP_NAME_TO_ID.put(PROP_NAME_tagSet, PROP_ID_tagSet);
      
          PROP_ID_TO_NAME[PROP_ID_source] = PROP_NAME_source;
          PROP_NAME_TO_ID.put(PROP_NAME_source, PROP_ID_source);
      
          PROP_ID_TO_NAME[PROP_ID_status] = PROP_NAME_status;
          PROP_NAME_TO_ID.put(PROP_NAME_status, PROP_ID_status);
      
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

    
    /* 函数定义ID: FUNC_META_ID */
    private java.lang.String _funcMetaId;
    
    /* 实体定义ID: ENTITY_META_ID */
    private java.lang.String _entityMetaId;
    
    /* 函数名: NAME */
    private java.lang.String _name;
    
    /* 显示名: DISPLAY_NAME */
    private java.lang.String _displayName;
    
    /* 函数类型: FUNCTION_TYPE */
    private java.lang.String _functionType;
    
    /* 参数定义: ARGS_META */
    private java.lang.String _argsMeta;
    
    /* 返回类型: RETURN_TYPE */
    private java.lang.String _returnType;
    
    /* 标签: TAG_SET */
    private java.lang.String _tagSet;
    
    /* 源码: SOURCE */
    private java.lang.String _source;
    
    /* 状态: STATUS */
    private java.lang.Integer _status;
    
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
    

    public _NopDynFunctionMeta(){
        // for debug
    }

    protected NopDynFunctionMeta newInstance(){
       return new NopDynFunctionMeta();
    }

    @Override
    public NopDynFunctionMeta cloneInstance() {
        NopDynFunctionMeta entity = newInstance();
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
      return "io.nop.dyn.dao.entity.NopDynFunctionMeta";
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
    
        return buildSimpleId(PROP_ID_funcMetaId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_funcMetaId;
          
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
        
            case PROP_ID_funcMetaId:
               return getFuncMetaId();
        
            case PROP_ID_entityMetaId:
               return getEntityMetaId();
        
            case PROP_ID_name:
               return getName();
        
            case PROP_ID_displayName:
               return getDisplayName();
        
            case PROP_ID_functionType:
               return getFunctionType();
        
            case PROP_ID_argsMeta:
               return getArgsMeta();
        
            case PROP_ID_returnType:
               return getReturnType();
        
            case PROP_ID_tagSet:
               return getTagSet();
        
            case PROP_ID_source:
               return getSource();
        
            case PROP_ID_status:
               return getStatus();
        
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
        
            case PROP_ID_funcMetaId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_funcMetaId));
               }
               setFuncMetaId(typedValue);
               break;
            }
        
            case PROP_ID_entityMetaId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_entityMetaId));
               }
               setEntityMetaId(typedValue);
               break;
            }
        
            case PROP_ID_name:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_name));
               }
               setName(typedValue);
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
        
            case PROP_ID_functionType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_functionType));
               }
               setFunctionType(typedValue);
               break;
            }
        
            case PROP_ID_argsMeta:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_argsMeta));
               }
               setArgsMeta(typedValue);
               break;
            }
        
            case PROP_ID_returnType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_returnType));
               }
               setReturnType(typedValue);
               break;
            }
        
            case PROP_ID_tagSet:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_tagSet));
               }
               setTagSet(typedValue);
               break;
            }
        
            case PROP_ID_source:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_source));
               }
               setSource(typedValue);
               break;
            }
        
            case PROP_ID_status:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_status));
               }
               setStatus(typedValue);
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
        
            case PROP_ID_funcMetaId:{
               onInitProp(propId);
               this._funcMetaId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_entityMetaId:{
               onInitProp(propId);
               this._entityMetaId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_name:{
               onInitProp(propId);
               this._name = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_displayName:{
               onInitProp(propId);
               this._displayName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_functionType:{
               onInitProp(propId);
               this._functionType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_argsMeta:{
               onInitProp(propId);
               this._argsMeta = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_returnType:{
               onInitProp(propId);
               this._returnType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_tagSet:{
               onInitProp(propId);
               this._tagSet = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_source:{
               onInitProp(propId);
               this._source = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_status:{
               onInitProp(propId);
               this._status = (java.lang.Integer)value;
               
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
     * 函数定义ID: FUNC_META_ID
     */
    public java.lang.String getFuncMetaId(){
         onPropGet(PROP_ID_funcMetaId);
         return _funcMetaId;
    }

    /**
     * 函数定义ID: FUNC_META_ID
     */
    public void setFuncMetaId(java.lang.String value){
        if(onPropSet(PROP_ID_funcMetaId,value)){
            this._funcMetaId = value;
            internalClearRefs(PROP_ID_funcMetaId);
            orm_id();
        }
    }
    
    /**
     * 实体定义ID: ENTITY_META_ID
     */
    public java.lang.String getEntityMetaId(){
         onPropGet(PROP_ID_entityMetaId);
         return _entityMetaId;
    }

    /**
     * 实体定义ID: ENTITY_META_ID
     */
    public void setEntityMetaId(java.lang.String value){
        if(onPropSet(PROP_ID_entityMetaId,value)){
            this._entityMetaId = value;
            internalClearRefs(PROP_ID_entityMetaId);
            
        }
    }
    
    /**
     * 函数名: NAME
     */
    public java.lang.String getName(){
         onPropGet(PROP_ID_name);
         return _name;
    }

    /**
     * 函数名: NAME
     */
    public void setName(java.lang.String value){
        if(onPropSet(PROP_ID_name,value)){
            this._name = value;
            internalClearRefs(PROP_ID_name);
            
        }
    }
    
    /**
     * 显示名: DISPLAY_NAME
     */
    public java.lang.String getDisplayName(){
         onPropGet(PROP_ID_displayName);
         return _displayName;
    }

    /**
     * 显示名: DISPLAY_NAME
     */
    public void setDisplayName(java.lang.String value){
        if(onPropSet(PROP_ID_displayName,value)){
            this._displayName = value;
            internalClearRefs(PROP_ID_displayName);
            
        }
    }
    
    /**
     * 函数类型: FUNCTION_TYPE
     */
    public java.lang.String getFunctionType(){
         onPropGet(PROP_ID_functionType);
         return _functionType;
    }

    /**
     * 函数类型: FUNCTION_TYPE
     */
    public void setFunctionType(java.lang.String value){
        if(onPropSet(PROP_ID_functionType,value)){
            this._functionType = value;
            internalClearRefs(PROP_ID_functionType);
            
        }
    }
    
    /**
     * 参数定义: ARGS_META
     */
    public java.lang.String getArgsMeta(){
         onPropGet(PROP_ID_argsMeta);
         return _argsMeta;
    }

    /**
     * 参数定义: ARGS_META
     */
    public void setArgsMeta(java.lang.String value){
        if(onPropSet(PROP_ID_argsMeta,value)){
            this._argsMeta = value;
            internalClearRefs(PROP_ID_argsMeta);
            
        }
    }
    
    /**
     * 返回类型: RETURN_TYPE
     */
    public java.lang.String getReturnType(){
         onPropGet(PROP_ID_returnType);
         return _returnType;
    }

    /**
     * 返回类型: RETURN_TYPE
     */
    public void setReturnType(java.lang.String value){
        if(onPropSet(PROP_ID_returnType,value)){
            this._returnType = value;
            internalClearRefs(PROP_ID_returnType);
            
        }
    }
    
    /**
     * 标签: TAG_SET
     */
    public java.lang.String getTagSet(){
         onPropGet(PROP_ID_tagSet);
         return _tagSet;
    }

    /**
     * 标签: TAG_SET
     */
    public void setTagSet(java.lang.String value){
        if(onPropSet(PROP_ID_tagSet,value)){
            this._tagSet = value;
            internalClearRefs(PROP_ID_tagSet);
            
        }
    }
    
    /**
     * 源码: SOURCE
     */
    public java.lang.String getSource(){
         onPropGet(PROP_ID_source);
         return _source;
    }

    /**
     * 源码: SOURCE
     */
    public void setSource(java.lang.String value){
        if(onPropSet(PROP_ID_source,value)){
            this._source = value;
            internalClearRefs(PROP_ID_source);
            
        }
    }
    
    /**
     * 状态: STATUS
     */
    public java.lang.Integer getStatus(){
         onPropGet(PROP_ID_status);
         return _status;
    }

    /**
     * 状态: STATUS
     */
    public void setStatus(java.lang.Integer value){
        if(onPropSet(PROP_ID_status,value)){
            this._status = value;
            internalClearRefs(PROP_ID_status);
            
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
    
    /**
     * 所属模块
     */
    public io.nop.dyn.dao.entity.NopDynEntityMeta getEntityMeta(){
       return (io.nop.dyn.dao.entity.NopDynEntityMeta)internalGetRefEntity(PROP_NAME_entityMeta);
    }

    public void setEntityMeta(io.nop.dyn.dao.entity.NopDynEntityMeta refEntity){
       if(refEntity == null){
         
         this.setEntityMetaId(null);
         
       }else{
          internalSetRefEntity(PROP_NAME_entityMeta, refEntity,()->{
             
                    this.setEntityMetaId(refEntity.getEntityMetaId());
                 
          });
       }
    }
       
   private io.nop.orm.component.JsonOrmComponent _argsMetaComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_argsMetaComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_argsMetaComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_argsMeta);
      
   }

   public io.nop.orm.component.JsonOrmComponent getArgsMetaComponent(){
      if(_argsMetaComponent == null){
          _argsMetaComponent = new io.nop.orm.component.JsonOrmComponent();
          _argsMetaComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_argsMetaComponent);
      }
      return _argsMetaComponent;
   }

   private io.nop.orm.component.JsonOrmComponent _sourceComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_sourceComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_sourceComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_source);
      
   }

   public io.nop.orm.component.JsonOrmComponent getSourceComponent(){
      if(_sourceComponent == null){
          _sourceComponent = new io.nop.orm.component.JsonOrmComponent();
          _sourceComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_sourceComponent);
      }
      return _sourceComponent;
   }

}
// resume CPD analysis - CPD-ON
