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

import io.nop.metadata.dao.entity.NopMetaDataProduct;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  数据产品: nop_meta_data_product
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopMetaDataProduct extends DynamicOrmEntity{
    
    /* 数据产品ID: DATA_PRODUCT_ID VARCHAR */
    public static final String PROP_NAME_dataProductId = "dataProductId";
    public static final int PROP_ID_dataProductId = 1;
    
    /* 所属业务域ID: BUSINESS_DOMAIN_ID VARCHAR */
    public static final String PROP_NAME_businessDomainId = "businessDomainId";
    public static final int PROP_ID_businessDomainId = 2;
    
    /* 产品名: NAME VARCHAR */
    public static final String PROP_NAME_name = "name";
    public static final int PROP_ID_name = 3;
    
    /* 显示名: DISPLAY_NAME VARCHAR */
    public static final String PROP_NAME_displayName = "displayName";
    public static final int PROP_ID_displayName = 4;
    
    /* 描述: DESCRIPTION VARCHAR */
    public static final String PROP_NAME_description = "description";
    public static final int PROP_ID_description = 5;
    
    /* 生命周期阶段: LIFECYCLE_STAGE VARCHAR */
    public static final String PROP_NAME_lifecycleStage = "lifecycleStage";
    public static final int PROP_ID_lifecycleStage = 6;
    
    /* 数据产品类型: DATA_PRODUCT_TYPE VARCHAR */
    public static final String PROP_NAME_dataProductType = "dataProductType";
    public static final int PROP_ID_dataProductType = 7;
    
    /* 可见性: VISIBILITY VARCHAR */
    public static final String PROP_NAME_visibility = "visibility";
    public static final int PROP_ID_visibility = 8;
    
    /* 投资组合优先级: PORTFOLIO_PRIORITY VARCHAR */
    public static final String PROP_NAME_portfolioPriority = "portfolioPriority";
    public static final int PROP_ID_portfolioPriority = 9;
    
    /* SLA定义: SLA VARCHAR */
    public static final String PROP_NAME_sla = "sla";
    public static final int PROP_ID_sla = 10;
    
    /* 依赖产品列表: CONSUMES_FROM VARCHAR */
    public static final String PROP_NAME_consumesFrom = "consumesFrom";
    public static final int PROP_ID_consumesFrom = 11;
    
    /* 被依赖产品列表: PROVIDES_TO VARCHAR */
    public static final String PROP_NAME_providesTo = "providesTo";
    public static final int PROP_ID_providesTo = 12;
    
    /* 专家列表: EXPERTS VARCHAR */
    public static final String PROP_NAME_experts = "experts";
    public static final int PROP_ID_experts = 13;
    
    /* 关联资产列表: ASSETS VARCHAR */
    public static final String PROP_NAME_assets = "assets";
    public static final int PROP_ID_assets = 14;
    
    /* 数据端口列表: PORTS VARCHAR */
    public static final String PROP_NAME_ports = "ports";
    public static final int PROP_ID_ports = 15;
    
    /* 扩展配置: EXT_CONFIG VARCHAR */
    public static final String PROP_NAME_extConfig = "extConfig";
    public static final int PROP_ID_extConfig = 16;
    
    /* 数据版本: VERSION BIGINT */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 17;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 18;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 19;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 20;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 21;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 22;
    

    private static int _PROP_ID_BOUND = 23;

    
    /* relation: 所属业务域 */
    public static final String PROP_NAME_businessDomain = "businessDomain";
    
    /* component:  */
    public static final String PROP_NAME_slaComponent = "slaComponent";
    
    /* component:  */
    public static final String PROP_NAME_consumesFromComponent = "consumesFromComponent";
    
    /* component:  */
    public static final String PROP_NAME_providesToComponent = "providesToComponent";
    
    /* component:  */
    public static final String PROP_NAME_expertsComponent = "expertsComponent";
    
    /* component:  */
    public static final String PROP_NAME_assetsComponent = "assetsComponent";
    
    /* component:  */
    public static final String PROP_NAME_portsComponent = "portsComponent";
    
    /* component:  */
    public static final String PROP_NAME_extConfigComponent = "extConfigComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_dataProductId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_dataProductId};

    private static final String[] PROP_ID_TO_NAME = new String[23];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_dataProductId] = PROP_NAME_dataProductId;
          PROP_NAME_TO_ID.put(PROP_NAME_dataProductId, PROP_ID_dataProductId);
      
          PROP_ID_TO_NAME[PROP_ID_businessDomainId] = PROP_NAME_businessDomainId;
          PROP_NAME_TO_ID.put(PROP_NAME_businessDomainId, PROP_ID_businessDomainId);
      
          PROP_ID_TO_NAME[PROP_ID_name] = PROP_NAME_name;
          PROP_NAME_TO_ID.put(PROP_NAME_name, PROP_ID_name);
      
          PROP_ID_TO_NAME[PROP_ID_displayName] = PROP_NAME_displayName;
          PROP_NAME_TO_ID.put(PROP_NAME_displayName, PROP_ID_displayName);
      
          PROP_ID_TO_NAME[PROP_ID_description] = PROP_NAME_description;
          PROP_NAME_TO_ID.put(PROP_NAME_description, PROP_ID_description);
      
          PROP_ID_TO_NAME[PROP_ID_lifecycleStage] = PROP_NAME_lifecycleStage;
          PROP_NAME_TO_ID.put(PROP_NAME_lifecycleStage, PROP_ID_lifecycleStage);
      
          PROP_ID_TO_NAME[PROP_ID_dataProductType] = PROP_NAME_dataProductType;
          PROP_NAME_TO_ID.put(PROP_NAME_dataProductType, PROP_ID_dataProductType);
      
          PROP_ID_TO_NAME[PROP_ID_visibility] = PROP_NAME_visibility;
          PROP_NAME_TO_ID.put(PROP_NAME_visibility, PROP_ID_visibility);
      
          PROP_ID_TO_NAME[PROP_ID_portfolioPriority] = PROP_NAME_portfolioPriority;
          PROP_NAME_TO_ID.put(PROP_NAME_portfolioPriority, PROP_ID_portfolioPriority);
      
          PROP_ID_TO_NAME[PROP_ID_sla] = PROP_NAME_sla;
          PROP_NAME_TO_ID.put(PROP_NAME_sla, PROP_ID_sla);
      
          PROP_ID_TO_NAME[PROP_ID_consumesFrom] = PROP_NAME_consumesFrom;
          PROP_NAME_TO_ID.put(PROP_NAME_consumesFrom, PROP_ID_consumesFrom);
      
          PROP_ID_TO_NAME[PROP_ID_providesTo] = PROP_NAME_providesTo;
          PROP_NAME_TO_ID.put(PROP_NAME_providesTo, PROP_ID_providesTo);
      
          PROP_ID_TO_NAME[PROP_ID_experts] = PROP_NAME_experts;
          PROP_NAME_TO_ID.put(PROP_NAME_experts, PROP_ID_experts);
      
          PROP_ID_TO_NAME[PROP_ID_assets] = PROP_NAME_assets;
          PROP_NAME_TO_ID.put(PROP_NAME_assets, PROP_ID_assets);
      
          PROP_ID_TO_NAME[PROP_ID_ports] = PROP_NAME_ports;
          PROP_NAME_TO_ID.put(PROP_NAME_ports, PROP_ID_ports);
      
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

    
    /* 数据产品ID: DATA_PRODUCT_ID */
    private java.lang.String _dataProductId;
    
    /* 所属业务域ID: BUSINESS_DOMAIN_ID */
    private java.lang.String _businessDomainId;
    
    /* 产品名: NAME */
    private java.lang.String _name;
    
    /* 显示名: DISPLAY_NAME */
    private java.lang.String _displayName;
    
    /* 描述: DESCRIPTION */
    private java.lang.String _description;
    
    /* 生命周期阶段: LIFECYCLE_STAGE */
    private java.lang.String _lifecycleStage;
    
    /* 数据产品类型: DATA_PRODUCT_TYPE */
    private java.lang.String _dataProductType;
    
    /* 可见性: VISIBILITY */
    private java.lang.String _visibility;
    
    /* 投资组合优先级: PORTFOLIO_PRIORITY */
    private java.lang.String _portfolioPriority;
    
    /* SLA定义: SLA */
    private java.lang.String _sla;
    
    /* 依赖产品列表: CONSUMES_FROM */
    private java.lang.String _consumesFrom;
    
    /* 被依赖产品列表: PROVIDES_TO */
    private java.lang.String _providesTo;
    
    /* 专家列表: EXPERTS */
    private java.lang.String _experts;
    
    /* 关联资产列表: ASSETS */
    private java.lang.String _assets;
    
    /* 数据端口列表: PORTS */
    private java.lang.String _ports;
    
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
    

    public _NopMetaDataProduct(){
        // for debug
    }

    protected NopMetaDataProduct newInstance(){
        NopMetaDataProduct entity = new NopMetaDataProduct();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopMetaDataProduct cloneInstance() {
        NopMetaDataProduct entity = newInstance();
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
      return "io.nop.metadata.dao.entity.NopMetaDataProduct";
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
    
        return buildSimpleId(PROP_ID_dataProductId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_dataProductId;
          
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
        
            case PROP_ID_dataProductId:
               return getDataProductId();
        
            case PROP_ID_businessDomainId:
               return getBusinessDomainId();
        
            case PROP_ID_name:
               return getName();
        
            case PROP_ID_displayName:
               return getDisplayName();
        
            case PROP_ID_description:
               return getDescription();
        
            case PROP_ID_lifecycleStage:
               return getLifecycleStage();
        
            case PROP_ID_dataProductType:
               return getDataProductType();
        
            case PROP_ID_visibility:
               return getVisibility();
        
            case PROP_ID_portfolioPriority:
               return getPortfolioPriority();
        
            case PROP_ID_sla:
               return getSla();
        
            case PROP_ID_consumesFrom:
               return getConsumesFrom();
        
            case PROP_ID_providesTo:
               return getProvidesTo();
        
            case PROP_ID_experts:
               return getExperts();
        
            case PROP_ID_assets:
               return getAssets();
        
            case PROP_ID_ports:
               return getPorts();
        
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
        
            case PROP_ID_dataProductId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_dataProductId));
               }
               setDataProductId(typedValue);
               break;
            }
        
            case PROP_ID_businessDomainId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_businessDomainId));
               }
               setBusinessDomainId(typedValue);
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
        
            case PROP_ID_lifecycleStage:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_lifecycleStage));
               }
               setLifecycleStage(typedValue);
               break;
            }
        
            case PROP_ID_dataProductType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_dataProductType));
               }
               setDataProductType(typedValue);
               break;
            }
        
            case PROP_ID_visibility:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_visibility));
               }
               setVisibility(typedValue);
               break;
            }
        
            case PROP_ID_portfolioPriority:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_portfolioPriority));
               }
               setPortfolioPriority(typedValue);
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
        
            case PROP_ID_consumesFrom:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_consumesFrom));
               }
               setConsumesFrom(typedValue);
               break;
            }
        
            case PROP_ID_providesTo:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_providesTo));
               }
               setProvidesTo(typedValue);
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
        
            case PROP_ID_assets:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_assets));
               }
               setAssets(typedValue);
               break;
            }
        
            case PROP_ID_ports:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_ports));
               }
               setPorts(typedValue);
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
        
            case PROP_ID_dataProductId:{
               onInitProp(propId);
               this._dataProductId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_businessDomainId:{
               onInitProp(propId);
               this._businessDomainId = (java.lang.String)value;
               
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
        
            case PROP_ID_lifecycleStage:{
               onInitProp(propId);
               this._lifecycleStage = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_dataProductType:{
               onInitProp(propId);
               this._dataProductType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_visibility:{
               onInitProp(propId);
               this._visibility = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_portfolioPriority:{
               onInitProp(propId);
               this._portfolioPriority = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_sla:{
               onInitProp(propId);
               this._sla = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_consumesFrom:{
               onInitProp(propId);
               this._consumesFrom = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_providesTo:{
               onInitProp(propId);
               this._providesTo = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_experts:{
               onInitProp(propId);
               this._experts = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_assets:{
               onInitProp(propId);
               this._assets = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_ports:{
               onInitProp(propId);
               this._ports = (java.lang.String)value;
               
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
     * 数据产品ID: DATA_PRODUCT_ID
     */
    public final java.lang.String getDataProductId(){
         onPropGet(PROP_ID_dataProductId);
         return _dataProductId;
    }

    /**
     * 数据产品ID: DATA_PRODUCT_ID
     */
    public final void setDataProductId(java.lang.String value){
        if(onPropSet(PROP_ID_dataProductId,value)){
            this._dataProductId = value;
            internalClearRefs(PROP_ID_dataProductId);
            orm_id();
        }
    }
    
    /**
     * 所属业务域ID: BUSINESS_DOMAIN_ID
     */
    public final java.lang.String getBusinessDomainId(){
         onPropGet(PROP_ID_businessDomainId);
         return _businessDomainId;
    }

    /**
     * 所属业务域ID: BUSINESS_DOMAIN_ID
     */
    public final void setBusinessDomainId(java.lang.String value){
        if(onPropSet(PROP_ID_businessDomainId,value)){
            this._businessDomainId = value;
            internalClearRefs(PROP_ID_businessDomainId);
            
        }
    }
    
    /**
     * 产品名: NAME
     */
    public final java.lang.String getName(){
         onPropGet(PROP_ID_name);
         return _name;
    }

    /**
     * 产品名: NAME
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
     * 生命周期阶段: LIFECYCLE_STAGE
     */
    public final java.lang.String getLifecycleStage(){
         onPropGet(PROP_ID_lifecycleStage);
         return _lifecycleStage;
    }

    /**
     * 生命周期阶段: LIFECYCLE_STAGE
     */
    public final void setLifecycleStage(java.lang.String value){
        if(onPropSet(PROP_ID_lifecycleStage,value)){
            this._lifecycleStage = value;
            internalClearRefs(PROP_ID_lifecycleStage);
            
        }
    }
    
    /**
     * 数据产品类型: DATA_PRODUCT_TYPE
     */
    public final java.lang.String getDataProductType(){
         onPropGet(PROP_ID_dataProductType);
         return _dataProductType;
    }

    /**
     * 数据产品类型: DATA_PRODUCT_TYPE
     */
    public final void setDataProductType(java.lang.String value){
        if(onPropSet(PROP_ID_dataProductType,value)){
            this._dataProductType = value;
            internalClearRefs(PROP_ID_dataProductType);
            
        }
    }
    
    /**
     * 可见性: VISIBILITY
     */
    public final java.lang.String getVisibility(){
         onPropGet(PROP_ID_visibility);
         return _visibility;
    }

    /**
     * 可见性: VISIBILITY
     */
    public final void setVisibility(java.lang.String value){
        if(onPropSet(PROP_ID_visibility,value)){
            this._visibility = value;
            internalClearRefs(PROP_ID_visibility);
            
        }
    }
    
    /**
     * 投资组合优先级: PORTFOLIO_PRIORITY
     */
    public final java.lang.String getPortfolioPriority(){
         onPropGet(PROP_ID_portfolioPriority);
         return _portfolioPriority;
    }

    /**
     * 投资组合优先级: PORTFOLIO_PRIORITY
     */
    public final void setPortfolioPriority(java.lang.String value){
        if(onPropSet(PROP_ID_portfolioPriority,value)){
            this._portfolioPriority = value;
            internalClearRefs(PROP_ID_portfolioPriority);
            
        }
    }
    
    /**
     * SLA定义: SLA
     */
    public final java.lang.String getSla(){
         onPropGet(PROP_ID_sla);
         return _sla;
    }

    /**
     * SLA定义: SLA
     */
    public final void setSla(java.lang.String value){
        if(onPropSet(PROP_ID_sla,value)){
            this._sla = value;
            internalClearRefs(PROP_ID_sla);
            
        }
    }
    
    /**
     * 依赖产品列表: CONSUMES_FROM
     */
    public final java.lang.String getConsumesFrom(){
         onPropGet(PROP_ID_consumesFrom);
         return _consumesFrom;
    }

    /**
     * 依赖产品列表: CONSUMES_FROM
     */
    public final void setConsumesFrom(java.lang.String value){
        if(onPropSet(PROP_ID_consumesFrom,value)){
            this._consumesFrom = value;
            internalClearRefs(PROP_ID_consumesFrom);
            
        }
    }
    
    /**
     * 被依赖产品列表: PROVIDES_TO
     */
    public final java.lang.String getProvidesTo(){
         onPropGet(PROP_ID_providesTo);
         return _providesTo;
    }

    /**
     * 被依赖产品列表: PROVIDES_TO
     */
    public final void setProvidesTo(java.lang.String value){
        if(onPropSet(PROP_ID_providesTo,value)){
            this._providesTo = value;
            internalClearRefs(PROP_ID_providesTo);
            
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
     * 关联资产列表: ASSETS
     */
    public final java.lang.String getAssets(){
         onPropGet(PROP_ID_assets);
         return _assets;
    }

    /**
     * 关联资产列表: ASSETS
     */
    public final void setAssets(java.lang.String value){
        if(onPropSet(PROP_ID_assets,value)){
            this._assets = value;
            internalClearRefs(PROP_ID_assets);
            
        }
    }
    
    /**
     * 数据端口列表: PORTS
     */
    public final java.lang.String getPorts(){
         onPropGet(PROP_ID_ports);
         return _ports;
    }

    /**
     * 数据端口列表: PORTS
     */
    public final void setPorts(java.lang.String value){
        if(onPropSet(PROP_ID_ports,value)){
            this._ports = value;
            internalClearRefs(PROP_ID_ports);
            
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
     * 所属业务域
     */
    public final io.nop.metadata.dao.entity.NopMetaBusinessDomain getBusinessDomain(){
       return (io.nop.metadata.dao.entity.NopMetaBusinessDomain)internalGetRefEntity(PROP_NAME_businessDomain);
    }

    public final void setBusinessDomain(io.nop.metadata.dao.entity.NopMetaBusinessDomain refEntity){
   
           if(refEntity == null){
           
                   this.setBusinessDomainId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_businessDomain, refEntity,()->{
           
                           this.setBusinessDomainId(refEntity.getBusinessDomainId());
                       
           });
           }
       
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

   private io.nop.orm.component.JsonOrmComponent _consumesFromComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_consumesFromComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_consumesFromComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_consumesFrom);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getConsumesFromComponent(){
      if(_consumesFromComponent == null){
          _consumesFromComponent = new io.nop.orm.component.JsonOrmComponent();
          _consumesFromComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_consumesFromComponent);
      }
      return _consumesFromComponent;
   }

   private io.nop.orm.component.JsonOrmComponent _providesToComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_providesToComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_providesToComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_providesTo);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getProvidesToComponent(){
      if(_providesToComponent == null){
          _providesToComponent = new io.nop.orm.component.JsonOrmComponent();
          _providesToComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_providesToComponent);
      }
      return _providesToComponent;
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

   private io.nop.orm.component.JsonOrmComponent _assetsComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_assetsComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_assetsComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_assets);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getAssetsComponent(){
      if(_assetsComponent == null){
          _assetsComponent = new io.nop.orm.component.JsonOrmComponent();
          _assetsComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_assetsComponent);
      }
      return _assetsComponent;
   }

   private io.nop.orm.component.JsonOrmComponent _portsComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_portsComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_portsComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_ports);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getPortsComponent(){
      if(_portsComponent == null){
          _portsComponent = new io.nop.orm.component.JsonOrmComponent();
          _portsComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_portsComponent);
      }
      return _portsComponent;
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
