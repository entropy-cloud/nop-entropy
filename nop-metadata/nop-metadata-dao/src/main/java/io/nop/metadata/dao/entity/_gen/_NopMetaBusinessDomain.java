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

import io.nop.metadata.dao.entity.NopMetaBusinessDomain;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  业务组织域: nop_meta_business_domain
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopMetaBusinessDomain extends DynamicOrmEntity{
    
    /* 业务域ID: BUSINESS_DOMAIN_ID VARCHAR */
    public static final String PROP_NAME_businessDomainId = "businessDomainId";
    public static final int PROP_ID_businessDomainId = 1;
    
    /* 父域ID: PARENT_DOMAIN_ID VARCHAR */
    public static final String PROP_NAME_parentDomainId = "parentDomainId";
    public static final int PROP_ID_parentDomainId = 2;
    
    /* 域名: NAME VARCHAR */
    public static final String PROP_NAME_name = "name";
    public static final int PROP_ID_name = 3;
    
    /* 显示名: DISPLAY_NAME VARCHAR */
    public static final String PROP_NAME_displayName = "displayName";
    public static final int PROP_ID_displayName = 4;
    
    /* 描述: DESCRIPTION VARCHAR */
    public static final String PROP_NAME_description = "description";
    public static final int PROP_ID_description = 5;
    
    /* 业务域类型: DOMAIN_TYPE VARCHAR */
    public static final String PROP_NAME_domainType = "domainType";
    public static final int PROP_ID_domainType = 6;
    
    /* 专家列表: EXPERTS VARCHAR */
    public static final String PROP_NAME_experts = "experts";
    public static final int PROP_ID_experts = 7;
    
    /* 负责人列表: OWNERS VARCHAR */
    public static final String PROP_NAME_owners = "owners";
    public static final int PROP_ID_owners = 8;
    
    /* 扩展配置: EXT_CONFIG VARCHAR */
    public static final String PROP_NAME_extConfig = "extConfig";
    public static final int PROP_ID_extConfig = 9;
    
    /* 数据版本: VERSION BIGINT */
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

    
    /* relation: 父域 */
    public static final String PROP_NAME_parentDomain = "parentDomain";
    
    /* relation: 子域集 */
    public static final String PROP_NAME_childDomains = "childDomains";
    
    /* relation:  */
    public static final String PROP_NAME_dataProducts = "dataProducts";
    
    /* component:  */
    public static final String PROP_NAME_expertsComponent = "expertsComponent";
    
    /* component:  */
    public static final String PROP_NAME_ownersComponent = "ownersComponent";
    
    /* component:  */
    public static final String PROP_NAME_extConfigComponent = "extConfigComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_businessDomainId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_businessDomainId};

    private static final String[] PROP_ID_TO_NAME = new String[16];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_businessDomainId] = PROP_NAME_businessDomainId;
          PROP_NAME_TO_ID.put(PROP_NAME_businessDomainId, PROP_ID_businessDomainId);
      
          PROP_ID_TO_NAME[PROP_ID_parentDomainId] = PROP_NAME_parentDomainId;
          PROP_NAME_TO_ID.put(PROP_NAME_parentDomainId, PROP_ID_parentDomainId);
      
          PROP_ID_TO_NAME[PROP_ID_name] = PROP_NAME_name;
          PROP_NAME_TO_ID.put(PROP_NAME_name, PROP_ID_name);
      
          PROP_ID_TO_NAME[PROP_ID_displayName] = PROP_NAME_displayName;
          PROP_NAME_TO_ID.put(PROP_NAME_displayName, PROP_ID_displayName);
      
          PROP_ID_TO_NAME[PROP_ID_description] = PROP_NAME_description;
          PROP_NAME_TO_ID.put(PROP_NAME_description, PROP_ID_description);
      
          PROP_ID_TO_NAME[PROP_ID_domainType] = PROP_NAME_domainType;
          PROP_NAME_TO_ID.put(PROP_NAME_domainType, PROP_ID_domainType);
      
          PROP_ID_TO_NAME[PROP_ID_experts] = PROP_NAME_experts;
          PROP_NAME_TO_ID.put(PROP_NAME_experts, PROP_ID_experts);
      
          PROP_ID_TO_NAME[PROP_ID_owners] = PROP_NAME_owners;
          PROP_NAME_TO_ID.put(PROP_NAME_owners, PROP_ID_owners);
      
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

    
    /* 业务域ID: BUSINESS_DOMAIN_ID */
    private java.lang.String _businessDomainId;
    
    /* 父域ID: PARENT_DOMAIN_ID */
    private java.lang.String _parentDomainId;
    
    /* 域名: NAME */
    private java.lang.String _name;
    
    /* 显示名: DISPLAY_NAME */
    private java.lang.String _displayName;
    
    /* 描述: DESCRIPTION */
    private java.lang.String _description;
    
    /* 业务域类型: DOMAIN_TYPE */
    private java.lang.String _domainType;
    
    /* 专家列表: EXPERTS */
    private java.lang.String _experts;
    
    /* 负责人列表: OWNERS */
    private java.lang.String _owners;
    
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
    

    public _NopMetaBusinessDomain(){
        // for debug
    }

    protected NopMetaBusinessDomain newInstance(){
        NopMetaBusinessDomain entity = new NopMetaBusinessDomain();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopMetaBusinessDomain cloneInstance() {
        NopMetaBusinessDomain entity = newInstance();
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
      return "io.nop.metadata.dao.entity.NopMetaBusinessDomain";
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
    
        return buildSimpleId(PROP_ID_businessDomainId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_businessDomainId;
          
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
        
            case PROP_ID_businessDomainId:
               return getBusinessDomainId();
        
            case PROP_ID_parentDomainId:
               return getParentDomainId();
        
            case PROP_ID_name:
               return getName();
        
            case PROP_ID_displayName:
               return getDisplayName();
        
            case PROP_ID_description:
               return getDescription();
        
            case PROP_ID_domainType:
               return getDomainType();
        
            case PROP_ID_experts:
               return getExperts();
        
            case PROP_ID_owners:
               return getOwners();
        
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
        
            case PROP_ID_businessDomainId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_businessDomainId));
               }
               setBusinessDomainId(typedValue);
               break;
            }
        
            case PROP_ID_parentDomainId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_parentDomainId));
               }
               setParentDomainId(typedValue);
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
        
            case PROP_ID_description:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_description));
               }
               setDescription(typedValue);
               break;
            }
        
            case PROP_ID_domainType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_domainType));
               }
               setDomainType(typedValue);
               break;
            }
        
            case PROP_ID_experts:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_experts));
               }
               setExperts(typedValue);
               break;
            }
        
            case PROP_ID_owners:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_owners));
               }
               setOwners(typedValue);
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
        
            case PROP_ID_businessDomainId:{
               onInitProp(propId);
               this._businessDomainId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_parentDomainId:{
               onInitProp(propId);
               this._parentDomainId = (java.lang.String)value;
               
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
        
            case PROP_ID_description:{
               onInitProp(propId);
               this._description = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_domainType:{
               onInitProp(propId);
               this._domainType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_experts:{
               onInitProp(propId);
               this._experts = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_owners:{
               onInitProp(propId);
               this._owners = (java.lang.String)value;
               
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
     * 业务域ID: BUSINESS_DOMAIN_ID
     */
    public final java.lang.String getBusinessDomainId(){
         onPropGet(PROP_ID_businessDomainId);
         return _businessDomainId;
    }

    /**
     * 业务域ID: BUSINESS_DOMAIN_ID
     */
    public final void setBusinessDomainId(java.lang.String value){
        if(onPropSet(PROP_ID_businessDomainId,value)){
            this._businessDomainId = value;
            internalClearRefs(PROP_ID_businessDomainId);
            orm_id();
        }
    }
    
    /**
     * 父域ID: PARENT_DOMAIN_ID
     */
    public final java.lang.String getParentDomainId(){
         onPropGet(PROP_ID_parentDomainId);
         return _parentDomainId;
    }

    /**
     * 父域ID: PARENT_DOMAIN_ID
     */
    public final void setParentDomainId(java.lang.String value){
        if(onPropSet(PROP_ID_parentDomainId,value)){
            this._parentDomainId = value;
            internalClearRefs(PROP_ID_parentDomainId);
            
        }
    }
    
    /**
     * 域名: NAME
     */
    public final java.lang.String getName(){
         onPropGet(PROP_ID_name);
         return _name;
    }

    /**
     * 域名: NAME
     */
    public final void setName(java.lang.String value){
        if(onPropSet(PROP_ID_name,value)){
            this._name = value;
            internalClearRefs(PROP_ID_name);
            
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
     * 描述: DESCRIPTION
     */
    public final java.lang.String getDescription(){
         onPropGet(PROP_ID_description);
         return _description;
    }

    /**
     * 描述: DESCRIPTION
     */
    public final void setDescription(java.lang.String value){
        if(onPropSet(PROP_ID_description,value)){
            this._description = value;
            internalClearRefs(PROP_ID_description);
            
        }
    }
    
    /**
     * 业务域类型: DOMAIN_TYPE
     */
    public final java.lang.String getDomainType(){
         onPropGet(PROP_ID_domainType);
         return _domainType;
    }

    /**
     * 业务域类型: DOMAIN_TYPE
     */
    public final void setDomainType(java.lang.String value){
        if(onPropSet(PROP_ID_domainType,value)){
            this._domainType = value;
            internalClearRefs(PROP_ID_domainType);
            
        }
    }
    
    /**
     * 专家列表: EXPERTS
     */
    public final java.lang.String getExperts(){
         onPropGet(PROP_ID_experts);
         return _experts;
    }

    /**
     * 专家列表: EXPERTS
     */
    public final void setExperts(java.lang.String value){
        if(onPropSet(PROP_ID_experts,value)){
            this._experts = value;
            internalClearRefs(PROP_ID_experts);
            
        }
    }
    
    /**
     * 负责人列表: OWNERS
     */
    public final java.lang.String getOwners(){
         onPropGet(PROP_ID_owners);
         return _owners;
    }

    /**
     * 负责人列表: OWNERS
     */
    public final void setOwners(java.lang.String value){
        if(onPropSet(PROP_ID_owners,value)){
            this._owners = value;
            internalClearRefs(PROP_ID_owners);
            
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
     * 父域
     */
    public final io.nop.metadata.dao.entity.NopMetaBusinessDomain getParentDomain(){
       return (io.nop.metadata.dao.entity.NopMetaBusinessDomain)internalGetRefEntity(PROP_NAME_parentDomain);
    }

    public final void setParentDomain(io.nop.metadata.dao.entity.NopMetaBusinessDomain refEntity){
   
           if(refEntity == null){
           
                   this.setParentDomainId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_parentDomain, refEntity,()->{
           
                           this.setParentDomainId(refEntity.getBusinessDomainId());
                       
           });
           }
       
    }
       
    private final OrmEntitySet<io.nop.metadata.dao.entity.NopMetaBusinessDomain> _childDomains = new OrmEntitySet<>(this, PROP_NAME_childDomains,
        io.nop.metadata.dao.entity.NopMetaBusinessDomain.PROP_NAME_parentDomain, null,io.nop.metadata.dao.entity.NopMetaBusinessDomain.class);

    /**
     * 子域集。 refPropName: parentDomain, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.metadata.dao.entity.NopMetaBusinessDomain> getChildDomains(){
       return _childDomains;
    }
       
    private final OrmEntitySet<io.nop.metadata.dao.entity.NopMetaDataProduct> _dataProducts = new OrmEntitySet<>(this, PROP_NAME_dataProducts,
        io.nop.metadata.dao.entity.NopMetaDataProduct.PROP_NAME_businessDomain, null,io.nop.metadata.dao.entity.NopMetaDataProduct.class);

    /**
     * 。 refPropName: businessDomain, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.metadata.dao.entity.NopMetaDataProduct> getDataProducts(){
       return _dataProducts;
    }
       
   private io.nop.orm.component.JsonOrmComponent _expertsComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_expertsComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_expertsComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_experts);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getExpertsComponent(){
      if(_expertsComponent == null){
          _expertsComponent = new io.nop.orm.component.JsonOrmComponent();
          _expertsComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_expertsComponent);
      }
      return _expertsComponent;
   }

   private io.nop.orm.component.JsonOrmComponent _ownersComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_ownersComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_ownersComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_owners);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getOwnersComponent(){
      if(_ownersComponent == null){
          _ownersComponent = new io.nop.orm.component.JsonOrmComponent();
          _ownersComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_ownersComponent);
      }
      return _ownersComponent;
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
