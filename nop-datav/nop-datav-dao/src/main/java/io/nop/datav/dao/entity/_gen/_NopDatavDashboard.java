package io.nop.datav.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.datav.dao.entity.NopDatavDashboard;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  看板: nop_datav_dashboard
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopDatavDashboard extends DynamicOrmEntity{
    
    /* 看板ID: DASHBOARD_ID VARCHAR */
    public static final String PROP_NAME_dashboardId = "dashboardId";
    public static final int PROP_ID_dashboardId = 1;
    
    /* 看板名: DASHBOARD_NAME VARCHAR */
    public static final String PROP_NAME_dashboardName = "dashboardName";
    public static final int PROP_ID_dashboardName = 2;
    
    /* 显示名: DISPLAY_NAME VARCHAR */
    public static final String PROP_NAME_displayName = "displayName";
    public static final int PROP_ID_displayName = 3;
    
    /* 描述: DESCRIPTION VARCHAR */
    public static final String PROP_NAME_description = "description";
    public static final int PROP_ID_description = 4;
    
    /* 看板类型: DASHBOARD_TYPE INTEGER */
    public static final String PROP_NAME_dashboardType = "dashboardType";
    public static final int PROP_ID_dashboardType = 5;
    
    /* 发布状态: PUBLISH_STATUS INTEGER */
    public static final String PROP_NAME_publishStatus = "publishStatus";
    public static final int PROP_ID_publishStatus = 6;
    
    /* 已发布版本: PUBLISHED_VERSION BIGINT */
    public static final String PROP_NAME_publishedVersion = "publishedVersion";
    public static final int PROP_ID_publishedVersion = 7;
    
    /* 发布人: PUBLISHED_BY VARCHAR */
    public static final String PROP_NAME_publishedBy = "publishedBy";
    public static final int PROP_ID_publishedBy = 8;
    
    /* 发布时间: PUBLISHED_TIME TIMESTAMP */
    public static final String PROP_NAME_publishedTime = "publishedTime";
    public static final int PROP_ID_publishedTime = 9;
    
    /* 布局配置: LAYOUT_CONFIG VARCHAR */
    public static final String PROP_NAME_layoutConfig = "layoutConfig";
    public static final int PROP_ID_layoutConfig = 10;
    
    /* 删除标记: DEL_FLAG TINYINT */
    public static final String PROP_NAME_delFlag = "delFlag";
    public static final int PROP_ID_delFlag = 11;
    
    /* 数据版本: VERSION BIGINT */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 12;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 13;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 14;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 15;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 16;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 17;
    
    /* 参数定义: PARAM_CONFIG CLOB */
    public static final String PROP_NAME_paramConfig = "paramConfig";
    public static final int PROP_ID_paramConfig = 18;
    

    private static int _PROP_ID_BOUND = 19;

    
    /* component:  */
    public static final String PROP_NAME_layoutConfigComponent = "layoutConfigComponent";
    
    /* component:  */
    public static final String PROP_NAME_paramConfigComponent = "paramConfigComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_dashboardId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_dashboardId};

    private static final String[] PROP_ID_TO_NAME = new String[19];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_dashboardId] = PROP_NAME_dashboardId;
          PROP_NAME_TO_ID.put(PROP_NAME_dashboardId, PROP_ID_dashboardId);
      
          PROP_ID_TO_NAME[PROP_ID_dashboardName] = PROP_NAME_dashboardName;
          PROP_NAME_TO_ID.put(PROP_NAME_dashboardName, PROP_ID_dashboardName);
      
          PROP_ID_TO_NAME[PROP_ID_displayName] = PROP_NAME_displayName;
          PROP_NAME_TO_ID.put(PROP_NAME_displayName, PROP_ID_displayName);
      
          PROP_ID_TO_NAME[PROP_ID_description] = PROP_NAME_description;
          PROP_NAME_TO_ID.put(PROP_NAME_description, PROP_ID_description);
      
          PROP_ID_TO_NAME[PROP_ID_dashboardType] = PROP_NAME_dashboardType;
          PROP_NAME_TO_ID.put(PROP_NAME_dashboardType, PROP_ID_dashboardType);
      
          PROP_ID_TO_NAME[PROP_ID_publishStatus] = PROP_NAME_publishStatus;
          PROP_NAME_TO_ID.put(PROP_NAME_publishStatus, PROP_ID_publishStatus);
      
          PROP_ID_TO_NAME[PROP_ID_publishedVersion] = PROP_NAME_publishedVersion;
          PROP_NAME_TO_ID.put(PROP_NAME_publishedVersion, PROP_ID_publishedVersion);
      
          PROP_ID_TO_NAME[PROP_ID_publishedBy] = PROP_NAME_publishedBy;
          PROP_NAME_TO_ID.put(PROP_NAME_publishedBy, PROP_ID_publishedBy);
      
          PROP_ID_TO_NAME[PROP_ID_publishedTime] = PROP_NAME_publishedTime;
          PROP_NAME_TO_ID.put(PROP_NAME_publishedTime, PROP_ID_publishedTime);
      
          PROP_ID_TO_NAME[PROP_ID_layoutConfig] = PROP_NAME_layoutConfig;
          PROP_NAME_TO_ID.put(PROP_NAME_layoutConfig, PROP_ID_layoutConfig);
      
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
      
          PROP_ID_TO_NAME[PROP_ID_paramConfig] = PROP_NAME_paramConfig;
          PROP_NAME_TO_ID.put(PROP_NAME_paramConfig, PROP_ID_paramConfig);
      
    }

    
    /* 看板ID: DASHBOARD_ID */
    private java.lang.String _dashboardId;
    
    /* 看板名: DASHBOARD_NAME */
    private java.lang.String _dashboardName;
    
    /* 显示名: DISPLAY_NAME */
    private java.lang.String _displayName;
    
    /* 描述: DESCRIPTION */
    private java.lang.String _description;
    
    /* 看板类型: DASHBOARD_TYPE */
    private java.lang.Integer _dashboardType;
    
    /* 发布状态: PUBLISH_STATUS */
    private java.lang.Integer _publishStatus;
    
    /* 已发布版本: PUBLISHED_VERSION */
    private java.lang.Long _publishedVersion;
    
    /* 发布人: PUBLISHED_BY */
    private java.lang.String _publishedBy;
    
    /* 发布时间: PUBLISHED_TIME */
    private java.sql.Timestamp _publishedTime;
    
    /* 布局配置: LAYOUT_CONFIG */
    private java.lang.String _layoutConfig;
    
    /* 删除标记: DEL_FLAG */
    private java.lang.Byte _delFlag;
    
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
    
    /* 参数定义: PARAM_CONFIG */
    private java.lang.String _paramConfig;
    

    public _NopDatavDashboard(){
        // for debug
    }

    protected NopDatavDashboard newInstance(){
        NopDatavDashboard entity = new NopDatavDashboard();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopDatavDashboard cloneInstance() {
        NopDatavDashboard entity = newInstance();
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
      return "io.nop.datav.dao.entity.NopDatavDashboard";
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
    
        return buildSimpleId(PROP_ID_dashboardId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_dashboardId;
          
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
        
            case PROP_ID_dashboardId:
               return getDashboardId();
        
            case PROP_ID_dashboardName:
               return getDashboardName();
        
            case PROP_ID_displayName:
               return getDisplayName();
        
            case PROP_ID_description:
               return getDescription();
        
            case PROP_ID_dashboardType:
               return getDashboardType();
        
            case PROP_ID_publishStatus:
               return getPublishStatus();
        
            case PROP_ID_publishedVersion:
               return getPublishedVersion();
        
            case PROP_ID_publishedBy:
               return getPublishedBy();
        
            case PROP_ID_publishedTime:
               return getPublishedTime();
        
            case PROP_ID_layoutConfig:
               return getLayoutConfig();
        
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
        
            case PROP_ID_paramConfig:
               return getParamConfig();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_dashboardId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_dashboardId));
               }
               setDashboardId(typedValue);
               break;
            }
        
            case PROP_ID_dashboardName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_dashboardName));
               }
               setDashboardName(typedValue);
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
        
            case PROP_ID_dashboardType:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_dashboardType));
               }
               setDashboardType(typedValue);
               break;
            }
        
            case PROP_ID_publishStatus:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_publishStatus));
               }
               setPublishStatus(typedValue);
               break;
            }
        
            case PROP_ID_publishedVersion:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_publishedVersion));
               }
               setPublishedVersion(typedValue);
               break;
            }
        
            case PROP_ID_publishedBy:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_publishedBy));
               }
               setPublishedBy(typedValue);
               break;
            }
        
            case PROP_ID_publishedTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_publishedTime));
               }
               setPublishedTime(typedValue);
               break;
            }
        
            case PROP_ID_layoutConfig:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_layoutConfig));
               }
               setLayoutConfig(typedValue);
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
        
            case PROP_ID_paramConfig:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_paramConfig));
               }
               setParamConfig(typedValue);
               break;
            }
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_dashboardId:{
               onInitProp(propId);
               this._dashboardId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_dashboardName:{
               onInitProp(propId);
               this._dashboardName = (java.lang.String)value;
               
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
        
            case PROP_ID_dashboardType:{
               onInitProp(propId);
               this._dashboardType = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_publishStatus:{
               onInitProp(propId);
               this._publishStatus = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_publishedVersion:{
               onInitProp(propId);
               this._publishedVersion = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_publishedBy:{
               onInitProp(propId);
               this._publishedBy = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_publishedTime:{
               onInitProp(propId);
               this._publishedTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_layoutConfig:{
               onInitProp(propId);
               this._layoutConfig = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_delFlag:{
               onInitProp(propId);
               this._delFlag = (java.lang.Byte)value;
               
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
        
            case PROP_ID_paramConfig:{
               onInitProp(propId);
               this._paramConfig = (java.lang.String)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 看板ID: DASHBOARD_ID
     */
    public final java.lang.String getDashboardId(){
         onPropGet(PROP_ID_dashboardId);
         return _dashboardId;
    }

    /**
     * 看板ID: DASHBOARD_ID
     */
    public final void setDashboardId(java.lang.String value){
        if(onPropSet(PROP_ID_dashboardId,value)){
            this._dashboardId = value;
            internalClearRefs(PROP_ID_dashboardId);
            orm_id();
        }
    }
    
    /**
     * 看板名: DASHBOARD_NAME
     */
    public final java.lang.String getDashboardName(){
         onPropGet(PROP_ID_dashboardName);
         return _dashboardName;
    }

    /**
     * 看板名: DASHBOARD_NAME
     */
    public final void setDashboardName(java.lang.String value){
        if(onPropSet(PROP_ID_dashboardName,value)){
            this._dashboardName = value;
            internalClearRefs(PROP_ID_dashboardName);
            
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
     * 看板类型: DASHBOARD_TYPE
     */
    public final java.lang.Integer getDashboardType(){
         onPropGet(PROP_ID_dashboardType);
         return _dashboardType;
    }

    /**
     * 看板类型: DASHBOARD_TYPE
     */
    public final void setDashboardType(java.lang.Integer value){
        if(onPropSet(PROP_ID_dashboardType,value)){
            this._dashboardType = value;
            internalClearRefs(PROP_ID_dashboardType);
            
        }
    }
    
    /**
     * 发布状态: PUBLISH_STATUS
     */
    public final java.lang.Integer getPublishStatus(){
         onPropGet(PROP_ID_publishStatus);
         return _publishStatus;
    }

    /**
     * 发布状态: PUBLISH_STATUS
     */
    public final void setPublishStatus(java.lang.Integer value){
        if(onPropSet(PROP_ID_publishStatus,value)){
            this._publishStatus = value;
            internalClearRefs(PROP_ID_publishStatus);
            
        }
    }
    
    /**
     * 已发布版本: PUBLISHED_VERSION
     */
    public final java.lang.Long getPublishedVersion(){
         onPropGet(PROP_ID_publishedVersion);
         return _publishedVersion;
    }

    /**
     * 已发布版本: PUBLISHED_VERSION
     */
    public final void setPublishedVersion(java.lang.Long value){
        if(onPropSet(PROP_ID_publishedVersion,value)){
            this._publishedVersion = value;
            internalClearRefs(PROP_ID_publishedVersion);
            
        }
    }
    
    /**
     * 发布人: PUBLISHED_BY
     */
    public final java.lang.String getPublishedBy(){
         onPropGet(PROP_ID_publishedBy);
         return _publishedBy;
    }

    /**
     * 发布人: PUBLISHED_BY
     */
    public final void setPublishedBy(java.lang.String value){
        if(onPropSet(PROP_ID_publishedBy,value)){
            this._publishedBy = value;
            internalClearRefs(PROP_ID_publishedBy);
            
        }
    }
    
    /**
     * 发布时间: PUBLISHED_TIME
     */
    public final java.sql.Timestamp getPublishedTime(){
         onPropGet(PROP_ID_publishedTime);
         return _publishedTime;
    }

    /**
     * 发布时间: PUBLISHED_TIME
     */
    public final void setPublishedTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_publishedTime,value)){
            this._publishedTime = value;
            internalClearRefs(PROP_ID_publishedTime);
            
        }
    }
    
    /**
     * 布局配置: LAYOUT_CONFIG
     */
    public final java.lang.String getLayoutConfig(){
         onPropGet(PROP_ID_layoutConfig);
         return _layoutConfig;
    }

    /**
     * 布局配置: LAYOUT_CONFIG
     */
    public final void setLayoutConfig(java.lang.String value){
        if(onPropSet(PROP_ID_layoutConfig,value)){
            this._layoutConfig = value;
            internalClearRefs(PROP_ID_layoutConfig);
            
        }
    }
    
    /**
     * 删除标记: DEL_FLAG
     */
    public final java.lang.Byte getDelFlag(){
         onPropGet(PROP_ID_delFlag);
         return _delFlag;
    }

    /**
     * 删除标记: DEL_FLAG
     */
    public final void setDelFlag(java.lang.Byte value){
        if(onPropSet(PROP_ID_delFlag,value)){
            this._delFlag = value;
            internalClearRefs(PROP_ID_delFlag);
            
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
     * 参数定义: PARAM_CONFIG
     */
    public final java.lang.String getParamConfig(){
         onPropGet(PROP_ID_paramConfig);
         return _paramConfig;
    }

    /**
     * 参数定义: PARAM_CONFIG
     */
    public final void setParamConfig(java.lang.String value){
        if(onPropSet(PROP_ID_paramConfig,value)){
            this._paramConfig = value;
            internalClearRefs(PROP_ID_paramConfig);
            
        }
    }
    
   private io.nop.orm.component.JsonOrmComponent _layoutConfigComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_layoutConfigComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_layoutConfigComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_layoutConfig);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getLayoutConfigComponent(){
      if(_layoutConfigComponent == null){
          _layoutConfigComponent = new io.nop.orm.component.JsonOrmComponent();
          _layoutConfigComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_layoutConfigComponent);
      }
      return _layoutConfigComponent;
   }

   private io.nop.orm.component.JsonOrmComponent _paramConfigComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_paramConfigComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_paramConfigComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_paramConfig);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getParamConfigComponent(){
      if(_paramConfigComponent == null){
          _paramConfigComponent = new io.nop.orm.component.JsonOrmComponent();
          _paramConfigComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_paramConfigComponent);
      }
      return _paramConfigComponent;
   }

}
// resume CPD analysis - CPD-ON
