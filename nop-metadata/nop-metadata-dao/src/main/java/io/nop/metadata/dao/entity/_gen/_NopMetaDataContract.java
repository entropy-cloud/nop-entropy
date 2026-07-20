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

import io.nop.metadata.dao.entity.NopMetaDataContract;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  数据契约: nop_meta_data_contract
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopMetaDataContract extends DynamicOrmEntity{
    
    /* 契约ID: CONTRACT_ID VARCHAR */
    public static final String PROP_NAME_contractId = "contractId";
    public static final int PROP_ID_contractId = 1;
    
    /* 契约名: CONTRACT_NAME VARCHAR */
    public static final String PROP_NAME_contractName = "contractName";
    public static final int PROP_ID_contractName = 2;
    
    /* 显示名: DISPLAY_NAME VARCHAR */
    public static final String PROP_NAME_displayName = "displayName";
    public static final int PROP_ID_displayName = 3;
    
    /* 关联数据表ID: ENTITY_TABLE_ID VARCHAR */
    public static final String PROP_NAME_entityTableId = "entityTableId";
    public static final int PROP_ID_entityTableId = 4;
    
    /* 契约状态: STATUS VARCHAR */
    public static final String PROP_NAME_status = "status";
    public static final int PROP_ID_status = 5;
    
    /* 契约所有者: OWNER_USER_ID VARCHAR */
    public static final String PROP_NAME_ownerUserId = "ownerUserId";
    public static final int PROP_ID_ownerUserId = 6;
    
    /* JSON Schema 定义: SCHEMA VARCHAR */
    public static final String PROP_NAME_schema = "schema";
    public static final int PROP_ID_schema = 7;
    
    /* SLA 定义: SLA VARCHAR */
    public static final String PROP_NAME_sla = "sla";
    public static final int PROP_ID_sla = 8;
    
    /* 质量期望: QUALITY_EXPECTATIONS VARCHAR */
    public static final String PROP_NAME_qualityExpectations = "qualityExpectations";
    public static final int PROP_ID_qualityExpectations = 9;
    
    /* 安全策略: SECURITY VARCHAR */
    public static final String PROP_NAME_security = "security";
    public static final int PROP_ID_security = 10;
    
    /* 最新执行结果: LATEST_RESULT VARCHAR */
    public static final String PROP_NAME_latestResult = "latestResult";
    public static final int PROP_ID_latestResult = 11;
    
    /* 标签集合: TAG_SET VARCHAR */
    public static final String PROP_NAME_tagSet = "tagSet";
    public static final int PROP_ID_tagSet = 12;
    
    /* 扩展配置: EXT_CONFIG VARCHAR */
    public static final String PROP_NAME_extConfig = "extConfig";
    public static final int PROP_ID_extConfig = 13;
    
    /* 数据版本: VERSION BIGINT */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 14;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 15;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 16;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 17;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 18;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 19;
    

    private static int _PROP_ID_BOUND = 20;

    
    /* relation: 逻辑表 */
    public static final String PROP_NAME_metaTable = "metaTable";
    
    /* component:  */
    public static final String PROP_NAME_schemaComponent = "schemaComponent";
    
    /* component:  */
    public static final String PROP_NAME_slaComponent = "slaComponent";
    
    /* component:  */
    public static final String PROP_NAME_qualityExpectationsComponent = "qualityExpectationsComponent";
    
    /* component:  */
    public static final String PROP_NAME_securityComponent = "securityComponent";
    
    /* component:  */
    public static final String PROP_NAME_latestResultComponent = "latestResultComponent";
    
    /* component:  */
    public static final String PROP_NAME_extConfigComponent = "extConfigComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_contractId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_contractId};

    private static final String[] PROP_ID_TO_NAME = new String[20];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_contractId] = PROP_NAME_contractId;
          PROP_NAME_TO_ID.put(PROP_NAME_contractId, PROP_ID_contractId);
      
          PROP_ID_TO_NAME[PROP_ID_contractName] = PROP_NAME_contractName;
          PROP_NAME_TO_ID.put(PROP_NAME_contractName, PROP_ID_contractName);
      
          PROP_ID_TO_NAME[PROP_ID_displayName] = PROP_NAME_displayName;
          PROP_NAME_TO_ID.put(PROP_NAME_displayName, PROP_ID_displayName);
      
          PROP_ID_TO_NAME[PROP_ID_entityTableId] = PROP_NAME_entityTableId;
          PROP_NAME_TO_ID.put(PROP_NAME_entityTableId, PROP_ID_entityTableId);
      
          PROP_ID_TO_NAME[PROP_ID_status] = PROP_NAME_status;
          PROP_NAME_TO_ID.put(PROP_NAME_status, PROP_ID_status);
      
          PROP_ID_TO_NAME[PROP_ID_ownerUserId] = PROP_NAME_ownerUserId;
          PROP_NAME_TO_ID.put(PROP_NAME_ownerUserId, PROP_ID_ownerUserId);
      
          PROP_ID_TO_NAME[PROP_ID_schema] = PROP_NAME_schema;
          PROP_NAME_TO_ID.put(PROP_NAME_schema, PROP_ID_schema);
      
          PROP_ID_TO_NAME[PROP_ID_sla] = PROP_NAME_sla;
          PROP_NAME_TO_ID.put(PROP_NAME_sla, PROP_ID_sla);
      
          PROP_ID_TO_NAME[PROP_ID_qualityExpectations] = PROP_NAME_qualityExpectations;
          PROP_NAME_TO_ID.put(PROP_NAME_qualityExpectations, PROP_ID_qualityExpectations);
      
          PROP_ID_TO_NAME[PROP_ID_security] = PROP_NAME_security;
          PROP_NAME_TO_ID.put(PROP_NAME_security, PROP_ID_security);
      
          PROP_ID_TO_NAME[PROP_ID_latestResult] = PROP_NAME_latestResult;
          PROP_NAME_TO_ID.put(PROP_NAME_latestResult, PROP_ID_latestResult);
      
          PROP_ID_TO_NAME[PROP_ID_tagSet] = PROP_NAME_tagSet;
          PROP_NAME_TO_ID.put(PROP_NAME_tagSet, PROP_ID_tagSet);
      
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

    
    /* 契约ID: CONTRACT_ID */
    private java.lang.String _contractId;
    
    /* 契约名: CONTRACT_NAME */
    private java.lang.String _contractName;
    
    /* 显示名: DISPLAY_NAME */
    private java.lang.String _displayName;
    
    /* 关联数据表ID: ENTITY_TABLE_ID */
    private java.lang.String _entityTableId;
    
    /* 契约状态: STATUS */
    private java.lang.String _status;
    
    /* 契约所有者: OWNER_USER_ID */
    private java.lang.String _ownerUserId;
    
    /* JSON Schema 定义: SCHEMA */
    private java.lang.String _schema;
    
    /* SLA 定义: SLA */
    private java.lang.String _sla;
    
    /* 质量期望: QUALITY_EXPECTATIONS */
    private java.lang.String _qualityExpectations;
    
    /* 安全策略: SECURITY */
    private java.lang.String _security;
    
    /* 最新执行结果: LATEST_RESULT */
    private java.lang.String _latestResult;
    
    /* 标签集合: TAG_SET */
    private java.lang.String _tagSet;
    
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
    

    public _NopMetaDataContract(){
        // for debug
    }

    protected NopMetaDataContract newInstance(){
        NopMetaDataContract entity = new NopMetaDataContract();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopMetaDataContract cloneInstance() {
        NopMetaDataContract entity = newInstance();
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
      return "io.nop.metadata.dao.entity.NopMetaDataContract";
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
    
        return buildSimpleId(PROP_ID_contractId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_contractId;
          
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
        
            case PROP_ID_contractId:
               return getContractId();
        
            case PROP_ID_contractName:
               return getContractName();
        
            case PROP_ID_displayName:
               return getDisplayName();
        
            case PROP_ID_entityTableId:
               return getEntityTableId();
        
            case PROP_ID_status:
               return getStatus();
        
            case PROP_ID_ownerUserId:
               return getOwnerUserId();
        
            case PROP_ID_schema:
               return getSchema();
        
            case PROP_ID_sla:
               return getSla();
        
            case PROP_ID_qualityExpectations:
               return getQualityExpectations();
        
            case PROP_ID_security:
               return getSecurity();
        
            case PROP_ID_latestResult:
               return getLatestResult();
        
            case PROP_ID_tagSet:
               return getTagSet();
        
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
        
            case PROP_ID_contractId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_contractId));
               }
               setContractId(typedValue);
               break;
            }
        
            case PROP_ID_contractName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_contractName));
               }
               setContractName(typedValue);
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
        
            case PROP_ID_entityTableId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_entityTableId));
               }
               setEntityTableId(typedValue);
               break;
            }
        
            case PROP_ID_status:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_status));
               }
               setStatus(typedValue);
               break;
            }
        
            case PROP_ID_ownerUserId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_ownerUserId));
               }
               setOwnerUserId(typedValue);
               break;
            }
        
            case PROP_ID_schema:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_schema));
               }
               setSchema(typedValue);
               break;
            }
        
            case PROP_ID_sla:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_sla));
               }
               setSla(typedValue);
               break;
            }
        
            case PROP_ID_qualityExpectations:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_qualityExpectations));
               }
               setQualityExpectations(typedValue);
               break;
            }
        
            case PROP_ID_security:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_security));
               }
               setSecurity(typedValue);
               break;
            }
        
            case PROP_ID_latestResult:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_latestResult));
               }
               setLatestResult(typedValue);
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
        
            case PROP_ID_contractId:{
               onInitProp(propId);
               this._contractId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_contractName:{
               onInitProp(propId);
               this._contractName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_displayName:{
               onInitProp(propId);
               this._displayName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_entityTableId:{
               onInitProp(propId);
               this._entityTableId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_status:{
               onInitProp(propId);
               this._status = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_ownerUserId:{
               onInitProp(propId);
               this._ownerUserId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_schema:{
               onInitProp(propId);
               this._schema = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_sla:{
               onInitProp(propId);
               this._sla = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_qualityExpectations:{
               onInitProp(propId);
               this._qualityExpectations = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_security:{
               onInitProp(propId);
               this._security = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_latestResult:{
               onInitProp(propId);
               this._latestResult = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_tagSet:{
               onInitProp(propId);
               this._tagSet = (java.lang.String)value;
               
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
     * 契约ID: CONTRACT_ID
     */
    public final java.lang.String getContractId(){
         onPropGet(PROP_ID_contractId);
         return _contractId;
    }

    /**
     * 契约ID: CONTRACT_ID
     */
    public final void setContractId(java.lang.String value){
        if(onPropSet(PROP_ID_contractId,value)){
            this._contractId = value;
            internalClearRefs(PROP_ID_contractId);
            orm_id();
        }
    }
    
    /**
     * 契约名: CONTRACT_NAME
     */
    public final java.lang.String getContractName(){
         onPropGet(PROP_ID_contractName);
         return _contractName;
    }

    /**
     * 契约名: CONTRACT_NAME
     */
    public final void setContractName(java.lang.String value){
        if(onPropSet(PROP_ID_contractName,value)){
            this._contractName = value;
            internalClearRefs(PROP_ID_contractName);
            
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
     * 关联数据表ID: ENTITY_TABLE_ID
     */
    public final java.lang.String getEntityTableId(){
         onPropGet(PROP_ID_entityTableId);
         return _entityTableId;
    }

    /**
     * 关联数据表ID: ENTITY_TABLE_ID
     */
    public final void setEntityTableId(java.lang.String value){
        if(onPropSet(PROP_ID_entityTableId,value)){
            this._entityTableId = value;
            internalClearRefs(PROP_ID_entityTableId);
            
        }
    }
    
    /**
     * 契约状态: STATUS
     */
    public final java.lang.String getStatus(){
         onPropGet(PROP_ID_status);
         return _status;
    }

    /**
     * 契约状态: STATUS
     */
    public final void setStatus(java.lang.String value){
        if(onPropSet(PROP_ID_status,value)){
            this._status = value;
            internalClearRefs(PROP_ID_status);
            
        }
    }
    
    /**
     * 契约所有者: OWNER_USER_ID
     */
    public final java.lang.String getOwnerUserId(){
         onPropGet(PROP_ID_ownerUserId);
         return _ownerUserId;
    }

    /**
     * 契约所有者: OWNER_USER_ID
     */
    public final void setOwnerUserId(java.lang.String value){
        if(onPropSet(PROP_ID_ownerUserId,value)){
            this._ownerUserId = value;
            internalClearRefs(PROP_ID_ownerUserId);
            
        }
    }
    
    /**
     * JSON Schema 定义: SCHEMA
     */
    public final java.lang.String getSchema(){
         onPropGet(PROP_ID_schema);
         return _schema;
    }

    /**
     * JSON Schema 定义: SCHEMA
     */
    public final void setSchema(java.lang.String value){
        if(onPropSet(PROP_ID_schema,value)){
            this._schema = value;
            internalClearRefs(PROP_ID_schema);
            
        }
    }
    
    /**
     * SLA 定义: SLA
     */
    public final java.lang.String getSla(){
         onPropGet(PROP_ID_sla);
         return _sla;
    }

    /**
     * SLA 定义: SLA
     */
    public final void setSla(java.lang.String value){
        if(onPropSet(PROP_ID_sla,value)){
            this._sla = value;
            internalClearRefs(PROP_ID_sla);
            
        }
    }
    
    /**
     * 质量期望: QUALITY_EXPECTATIONS
     */
    public final java.lang.String getQualityExpectations(){
         onPropGet(PROP_ID_qualityExpectations);
         return _qualityExpectations;
    }

    /**
     * 质量期望: QUALITY_EXPECTATIONS
     */
    public final void setQualityExpectations(java.lang.String value){
        if(onPropSet(PROP_ID_qualityExpectations,value)){
            this._qualityExpectations = value;
            internalClearRefs(PROP_ID_qualityExpectations);
            
        }
    }
    
    /**
     * 安全策略: SECURITY
     */
    public final java.lang.String getSecurity(){
         onPropGet(PROP_ID_security);
         return _security;
    }

    /**
     * 安全策略: SECURITY
     */
    public final void setSecurity(java.lang.String value){
        if(onPropSet(PROP_ID_security,value)){
            this._security = value;
            internalClearRefs(PROP_ID_security);
            
        }
    }
    
    /**
     * 最新执行结果: LATEST_RESULT
     */
    public final java.lang.String getLatestResult(){
         onPropGet(PROP_ID_latestResult);
         return _latestResult;
    }

    /**
     * 最新执行结果: LATEST_RESULT
     */
    public final void setLatestResult(java.lang.String value){
        if(onPropSet(PROP_ID_latestResult,value)){
            this._latestResult = value;
            internalClearRefs(PROP_ID_latestResult);
            
        }
    }
    
    /**
     * 标签集合: TAG_SET
     */
    public final java.lang.String getTagSet(){
         onPropGet(PROP_ID_tagSet);
         return _tagSet;
    }

    /**
     * 标签集合: TAG_SET
     */
    public final void setTagSet(java.lang.String value){
        if(onPropSet(PROP_ID_tagSet,value)){
            this._tagSet = value;
            internalClearRefs(PROP_ID_tagSet);
            
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
     * 逻辑表
     */
    public final io.nop.metadata.dao.entity.NopMetaTable getMetaTable(){
       return (io.nop.metadata.dao.entity.NopMetaTable)internalGetRefEntity(PROP_NAME_metaTable);
    }

    public final void setMetaTable(io.nop.metadata.dao.entity.NopMetaTable refEntity){
   
           if(refEntity == null){
           
                   this.setEntityTableId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_metaTable, refEntity,()->{
           
                           this.setEntityTableId(refEntity.getMetaTableId());
                       
           });
           }
       
    }
       
   private io.nop.orm.component.JsonOrmComponent _schemaComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_schemaComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_schemaComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_schema);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getSchemaComponent(){
      if(_schemaComponent == null){
          _schemaComponent = new io.nop.orm.component.JsonOrmComponent();
          _schemaComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_schemaComponent);
      }
      return _schemaComponent;
   }

   private io.nop.orm.component.JsonOrmComponent _slaComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_slaComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_slaComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_sla);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getSlaComponent(){
      if(_slaComponent == null){
          _slaComponent = new io.nop.orm.component.JsonOrmComponent();
          _slaComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_slaComponent);
      }
      return _slaComponent;
   }

   private io.nop.orm.component.JsonOrmComponent _qualityExpectationsComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_qualityExpectationsComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_qualityExpectationsComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_qualityExpectations);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getQualityExpectationsComponent(){
      if(_qualityExpectationsComponent == null){
          _qualityExpectationsComponent = new io.nop.orm.component.JsonOrmComponent();
          _qualityExpectationsComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_qualityExpectationsComponent);
      }
      return _qualityExpectationsComponent;
   }

   private io.nop.orm.component.JsonOrmComponent _securityComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_securityComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_securityComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_security);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getSecurityComponent(){
      if(_securityComponent == null){
          _securityComponent = new io.nop.orm.component.JsonOrmComponent();
          _securityComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_securityComponent);
      }
      return _securityComponent;
   }

   private io.nop.orm.component.JsonOrmComponent _latestResultComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_latestResultComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_latestResultComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_latestResult);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getLatestResultComponent(){
      if(_latestResultComponent == null){
          _latestResultComponent = new io.nop.orm.component.JsonOrmComponent();
          _latestResultComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_latestResultComponent);
      }
      return _latestResultComponent;
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
