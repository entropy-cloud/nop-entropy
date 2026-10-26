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

import io.nop.metadata.dao.entity.NopMetaEntityMethod;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  实体方法: nop_meta_entity_method
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopMetaEntityMethod extends DynamicOrmEntity{
    
    /* 方法ID: METHOD_ID VARCHAR */
    public static final String PROP_NAME_methodId = "methodId";
    public static final int PROP_ID_methodId = 1;
    
    /* 实体ID: META_ENTITY_ID VARCHAR */
    public static final String PROP_NAME_metaEntityId = "metaEntityId";
    public static final int PROP_ID_metaEntityId = 2;
    
    /* 是否Delta: IS_DELTA TINYINT */
    public static final String PROP_NAME_isDelta = "isDelta";
    public static final int PROP_ID_isDelta = 3;
    
    /* 方法名: METHOD_NAME VARCHAR */
    public static final String PROP_NAME_methodName = "methodName";
    public static final int PROP_ID_methodName = 4;
    
    /* 方法类别: METHOD_KIND VARCHAR */
    public static final String PROP_NAME_methodKind = "methodKind";
    public static final int PROP_ID_methodKind = 5;
    
    /* 来源类别: SOURCE_KIND VARCHAR */
    public static final String PROP_NAME_sourceKind = "sourceKind";
    public static final int PROP_ID_sourceKind = 6;
    
    /* 实现类别: IMPL_KIND VARCHAR */
    public static final String PROP_NAME_implKind = "implKind";
    public static final int PROP_ID_implKind = 7;
    
    /* 实现体: IMPL_SOURCE VARCHAR */
    public static final String PROP_NAME_implSource = "implSource";
    public static final int PROP_ID_implSource = 8;
    
    /* 返回类型: RETURN_TYPE VARCHAR */
    public static final String PROP_NAME_returnType = "returnType";
    public static final int PROP_ID_returnType = 9;
    
    /* 行级角色: ROLES VARCHAR */
    public static final String PROP_NAME_roles = "roles";
    public static final int PROP_ID_roles = 10;
    
    /* 禁用: DISABLED TINYINT */
    public static final String PROP_NAME_disabled = "disabled";
    public static final int PROP_ID_disabled = 11;
    
    /* 扩展配置: EXT_CONFIG VARCHAR */
    public static final String PROP_NAME_extConfig = "extConfig";
    public static final int PROP_ID_extConfig = 12;
    
    /* 数据版本: VERSION BIGINT */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 13;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 14;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 15;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 16;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 17;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 18;
    

    private static int _PROP_ID_BOUND = 19;

    
    /* relation: 元数据实体 */
    public static final String PROP_NAME_metaEntity = "metaEntity";
    
    /* relation: 方法参数集 */
    public static final String PROP_NAME_args = "args";
    
    /* component:  */
    public static final String PROP_NAME_extConfigComponent = "extConfigComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_methodId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_methodId};

    private static final String[] PROP_ID_TO_NAME = new String[19];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_methodId] = PROP_NAME_methodId;
          PROP_NAME_TO_ID.put(PROP_NAME_methodId, PROP_ID_methodId);
      
          PROP_ID_TO_NAME[PROP_ID_metaEntityId] = PROP_NAME_metaEntityId;
          PROP_NAME_TO_ID.put(PROP_NAME_metaEntityId, PROP_ID_metaEntityId);
      
          PROP_ID_TO_NAME[PROP_ID_isDelta] = PROP_NAME_isDelta;
          PROP_NAME_TO_ID.put(PROP_NAME_isDelta, PROP_ID_isDelta);
      
          PROP_ID_TO_NAME[PROP_ID_methodName] = PROP_NAME_methodName;
          PROP_NAME_TO_ID.put(PROP_NAME_methodName, PROP_ID_methodName);
      
          PROP_ID_TO_NAME[PROP_ID_methodKind] = PROP_NAME_methodKind;
          PROP_NAME_TO_ID.put(PROP_NAME_methodKind, PROP_ID_methodKind);
      
          PROP_ID_TO_NAME[PROP_ID_sourceKind] = PROP_NAME_sourceKind;
          PROP_NAME_TO_ID.put(PROP_NAME_sourceKind, PROP_ID_sourceKind);
      
          PROP_ID_TO_NAME[PROP_ID_implKind] = PROP_NAME_implKind;
          PROP_NAME_TO_ID.put(PROP_NAME_implKind, PROP_ID_implKind);
      
          PROP_ID_TO_NAME[PROP_ID_implSource] = PROP_NAME_implSource;
          PROP_NAME_TO_ID.put(PROP_NAME_implSource, PROP_ID_implSource);
      
          PROP_ID_TO_NAME[PROP_ID_returnType] = PROP_NAME_returnType;
          PROP_NAME_TO_ID.put(PROP_NAME_returnType, PROP_ID_returnType);
      
          PROP_ID_TO_NAME[PROP_ID_roles] = PROP_NAME_roles;
          PROP_NAME_TO_ID.put(PROP_NAME_roles, PROP_ID_roles);
      
          PROP_ID_TO_NAME[PROP_ID_disabled] = PROP_NAME_disabled;
          PROP_NAME_TO_ID.put(PROP_NAME_disabled, PROP_ID_disabled);
      
          PROP_ID_TO_NAME[PROP_ID_extConfig] = PROP_NAME_extConfig;
          PROP_NAME_TO_ID.put(PROP_NAME_extConfig, PROP_ID_extConfig);
      
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

    
    /* 方法ID: METHOD_ID */
    private java.lang.String _methodId;
    
    /* 实体ID: META_ENTITY_ID */
    private java.lang.String _metaEntityId;
    
    /* 是否Delta: IS_DELTA */
    private java.lang.Byte _isDelta;
    
    /* 方法名: METHOD_NAME */
    private java.lang.String _methodName;
    
    /* 方法类别: METHOD_KIND */
    private java.lang.String _methodKind;
    
    /* 来源类别: SOURCE_KIND */
    private java.lang.String _sourceKind;
    
    /* 实现类别: IMPL_KIND */
    private java.lang.String _implKind;
    
    /* 实现体: IMPL_SOURCE */
    private java.lang.String _implSource;
    
    /* 返回类型: RETURN_TYPE */
    private java.lang.String _returnType;
    
    /* 行级角色: ROLES */
    private java.lang.String _roles;
    
    /* 禁用: DISABLED */
    private java.lang.Byte _disabled;
    
    /* 扩展配置: EXT_CONFIG */
    private java.lang.String _extConfig;
    
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
    
    /* 备注: REMARK */
    private java.lang.String _remark;
    

    public _NopMetaEntityMethod(){
        // for debug
    }

    protected NopMetaEntityMethod newInstance(){
        NopMetaEntityMethod entity = new NopMetaEntityMethod();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopMetaEntityMethod cloneInstance() {
        NopMetaEntityMethod entity = newInstance();
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
      return "io.nop.metadata.dao.entity.NopMetaEntityMethod";
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
    
        return buildSimpleId(PROP_ID_methodId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_methodId;
          
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
        
            case PROP_ID_methodId:
               return getMethodId();
        
            case PROP_ID_metaEntityId:
               return getMetaEntityId();
        
            case PROP_ID_isDelta:
               return getIsDelta();
        
            case PROP_ID_methodName:
               return getMethodName();
        
            case PROP_ID_methodKind:
               return getMethodKind();
        
            case PROP_ID_sourceKind:
               return getSourceKind();
        
            case PROP_ID_implKind:
               return getImplKind();
        
            case PROP_ID_implSource:
               return getImplSource();
        
            case PROP_ID_returnType:
               return getReturnType();
        
            case PROP_ID_roles:
               return getRoles();
        
            case PROP_ID_disabled:
               return getDisabled();
        
            case PROP_ID_extConfig:
               return getExtConfig();
        
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
        
            case PROP_ID_methodId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_methodId));
               }
               setMethodId(typedValue);
               break;
            }
        
            case PROP_ID_metaEntityId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_metaEntityId));
               }
               setMetaEntityId(typedValue);
               break;
            }
        
            case PROP_ID_isDelta:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_isDelta));
               }
               setIsDelta(typedValue);
               break;
            }
        
            case PROP_ID_methodName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_methodName));
               }
               setMethodName(typedValue);
               break;
            }
        
            case PROP_ID_methodKind:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_methodKind));
               }
               setMethodKind(typedValue);
               break;
            }
        
            case PROP_ID_sourceKind:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_sourceKind));
               }
               setSourceKind(typedValue);
               break;
            }
        
            case PROP_ID_implKind:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_implKind));
               }
               setImplKind(typedValue);
               break;
            }
        
            case PROP_ID_implSource:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_implSource));
               }
               setImplSource(typedValue);
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
        
            case PROP_ID_roles:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_roles));
               }
               setRoles(typedValue);
               break;
            }
        
            case PROP_ID_disabled:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_disabled));
               }
               setDisabled(typedValue);
               break;
            }
        
            case PROP_ID_extConfig:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_extConfig));
               }
               setExtConfig(typedValue);
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
        
            case PROP_ID_methodId:{
               onInitProp(propId);
               this._methodId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_metaEntityId:{
               onInitProp(propId);
               this._metaEntityId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_isDelta:{
               onInitProp(propId);
               this._isDelta = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_methodName:{
               onInitProp(propId);
               this._methodName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_methodKind:{
               onInitProp(propId);
               this._methodKind = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_sourceKind:{
               onInitProp(propId);
               this._sourceKind = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_implKind:{
               onInitProp(propId);
               this._implKind = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_implSource:{
               onInitProp(propId);
               this._implSource = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_returnType:{
               onInitProp(propId);
               this._returnType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_roles:{
               onInitProp(propId);
               this._roles = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_disabled:{
               onInitProp(propId);
               this._disabled = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_extConfig:{
               onInitProp(propId);
               this._extConfig = (java.lang.String)value;
               
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
            orm_id();
        }
    }
    
    /**
     * 实体ID: META_ENTITY_ID
     */
    public final java.lang.String getMetaEntityId(){
         onPropGet(PROP_ID_metaEntityId);
         return _metaEntityId;
    }

    /**
     * 实体ID: META_ENTITY_ID
     */
    public final void setMetaEntityId(java.lang.String value){
        if(onPropSet(PROP_ID_metaEntityId,value)){
            this._metaEntityId = value;
            internalClearRefs(PROP_ID_metaEntityId);
            
        }
    }
    
    /**
     * 是否Delta: IS_DELTA
     */
    public final java.lang.Byte getIsDelta(){
         onPropGet(PROP_ID_isDelta);
         return _isDelta;
    }

    /**
     * 是否Delta: IS_DELTA
     */
    public final void setIsDelta(java.lang.Byte value){
        if(onPropSet(PROP_ID_isDelta,value)){
            this._isDelta = value;
            internalClearRefs(PROP_ID_isDelta);
            
        }
    }
    
    /**
     * 方法名: METHOD_NAME
     */
    public final java.lang.String getMethodName(){
         onPropGet(PROP_ID_methodName);
         return _methodName;
    }

    /**
     * 方法名: METHOD_NAME
     */
    public final void setMethodName(java.lang.String value){
        if(onPropSet(PROP_ID_methodName,value)){
            this._methodName = value;
            internalClearRefs(PROP_ID_methodName);
            
        }
    }
    
    /**
     * 方法类别: METHOD_KIND
     */
    public final java.lang.String getMethodKind(){
         onPropGet(PROP_ID_methodKind);
         return _methodKind;
    }

    /**
     * 方法类别: METHOD_KIND
     */
    public final void setMethodKind(java.lang.String value){
        if(onPropSet(PROP_ID_methodKind,value)){
            this._methodKind = value;
            internalClearRefs(PROP_ID_methodKind);
            
        }
    }
    
    /**
     * 来源类别: SOURCE_KIND
     */
    public final java.lang.String getSourceKind(){
         onPropGet(PROP_ID_sourceKind);
         return _sourceKind;
    }

    /**
     * 来源类别: SOURCE_KIND
     */
    public final void setSourceKind(java.lang.String value){
        if(onPropSet(PROP_ID_sourceKind,value)){
            this._sourceKind = value;
            internalClearRefs(PROP_ID_sourceKind);
            
        }
    }
    
    /**
     * 实现类别: IMPL_KIND
     */
    public final java.lang.String getImplKind(){
         onPropGet(PROP_ID_implKind);
         return _implKind;
    }

    /**
     * 实现类别: IMPL_KIND
     */
    public final void setImplKind(java.lang.String value){
        if(onPropSet(PROP_ID_implKind,value)){
            this._implKind = value;
            internalClearRefs(PROP_ID_implKind);
            
        }
    }
    
    /**
     * 实现体: IMPL_SOURCE
     */
    public final java.lang.String getImplSource(){
         onPropGet(PROP_ID_implSource);
         return _implSource;
    }

    /**
     * 实现体: IMPL_SOURCE
     */
    public final void setImplSource(java.lang.String value){
        if(onPropSet(PROP_ID_implSource,value)){
            this._implSource = value;
            internalClearRefs(PROP_ID_implSource);
            
        }
    }
    
    /**
     * 返回类型: RETURN_TYPE
     */
    public final java.lang.String getReturnType(){
         onPropGet(PROP_ID_returnType);
         return _returnType;
    }

    /**
     * 返回类型: RETURN_TYPE
     */
    public final void setReturnType(java.lang.String value){
        if(onPropSet(PROP_ID_returnType,value)){
            this._returnType = value;
            internalClearRefs(PROP_ID_returnType);
            
        }
    }
    
    /**
     * 行级角色: ROLES
     */
    public final java.lang.String getRoles(){
         onPropGet(PROP_ID_roles);
         return _roles;
    }

    /**
     * 行级角色: ROLES
     */
    public final void setRoles(java.lang.String value){
        if(onPropSet(PROP_ID_roles,value)){
            this._roles = value;
            internalClearRefs(PROP_ID_roles);
            
        }
    }
    
    /**
     * 禁用: DISABLED
     */
    public final java.lang.Byte getDisabled(){
         onPropGet(PROP_ID_disabled);
         return _disabled;
    }

    /**
     * 禁用: DISABLED
     */
    public final void setDisabled(java.lang.Byte value){
        if(onPropSet(PROP_ID_disabled,value)){
            this._disabled = value;
            internalClearRefs(PROP_ID_disabled);
            
        }
    }
    
    /**
     * 扩展配置: EXT_CONFIG
     */
    public final java.lang.String getExtConfig(){
         onPropGet(PROP_ID_extConfig);
         return _extConfig;
    }

    /**
     * 扩展配置: EXT_CONFIG
     */
    public final void setExtConfig(java.lang.String value){
        if(onPropSet(PROP_ID_extConfig,value)){
            this._extConfig = value;
            internalClearRefs(PROP_ID_extConfig);
            
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
     * 备注: REMARK
     */
    public final java.lang.String getRemark(){
         onPropGet(PROP_ID_remark);
         return _remark;
    }

    /**
     * 备注: REMARK
     */
    public final void setRemark(java.lang.String value){
        if(onPropSet(PROP_ID_remark,value)){
            this._remark = value;
            internalClearRefs(PROP_ID_remark);
            
        }
    }
    
    /**
     * 元数据实体
     */
    public final io.nop.metadata.dao.entity.NopMetaEntity getMetaEntity(){
       return (io.nop.metadata.dao.entity.NopMetaEntity)internalGetRefEntity(PROP_NAME_metaEntity);
    }

    public final void setMetaEntity(io.nop.metadata.dao.entity.NopMetaEntity refEntity){
   
           if(refEntity == null){
           
                   this.setMetaEntityId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_metaEntity, refEntity,()->{
           
                           this.setMetaEntityId(refEntity.getMetaEntityId());
                       
           });
           }
       
    }
       
    private final OrmEntitySet<io.nop.metadata.dao.entity.NopMetaMethodArg> _args = new OrmEntitySet<>(this, PROP_NAME_args,
        io.nop.metadata.dao.entity.NopMetaMethodArg.PROP_NAME_method, null,io.nop.metadata.dao.entity.NopMetaMethodArg.class);

    /**
     * 方法参数集。 refPropName: method, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.metadata.dao.entity.NopMetaMethodArg> getArgs(){
       return _args;
    }
       
   private io.nop.orm.component.JsonOrmComponent _extConfigComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_extConfigComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_extConfigComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_extConfig);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getExtConfigComponent(){
      if(_extConfigComponent == null){
          _extConfigComponent = new io.nop.orm.component.JsonOrmComponent();
          _extConfigComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_extConfigComponent);
      }
      return _extConfigComponent;
   }

}
// resume CPD analysis - CPD-ON
