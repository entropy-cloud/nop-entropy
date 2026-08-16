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

import io.nop.datav.dao.entity.NopDatavPanel;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  面板: nop_datav_panel
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopDatavPanel extends DynamicOrmEntity{
    
    /* 面板ID: PANEL_ID VARCHAR */
    public static final String PROP_NAME_panelId = "panelId";
    public static final int PROP_ID_panelId = 1;
    
    /* 看板ID: DASHBOARD_ID VARCHAR */
    public static final String PROP_NAME_dashboardId = "dashboardId";
    public static final int PROP_ID_dashboardId = 2;
    
    /* 面板名: PANEL_NAME VARCHAR */
    public static final String PROP_NAME_panelName = "panelName";
    public static final int PROP_ID_panelName = 3;
    
    /* 显示名: DISPLAY_NAME VARCHAR */
    public static final String PROP_NAME_displayName = "displayName";
    public static final int PROP_ID_displayName = 4;
    
    /* 面板类型: PANEL_TYPE INTEGER */
    public static final String PROP_NAME_panelType = "panelType";
    public static final int PROP_ID_panelType = 5;
    
    /* 数据集引用ID: DATASET_REF_ID VARCHAR */
    public static final String PROP_NAME_datasetRefId = "datasetRefId";
    public static final int PROP_ID_datasetRefId = 6;
    
    /* 页签ID: TAB_ID VARCHAR */
    public static final String PROP_NAME_tabId = "tabId";
    public static final int PROP_ID_tabId = 7;
    
    /* 排序: SORT_ORDER INTEGER */
    public static final String PROP_NAME_sortOrder = "sortOrder";
    public static final int PROP_ID_sortOrder = 8;
    
    /* 面板配置: PANEL_CONFIG VARCHAR */
    public static final String PROP_NAME_panelConfig = "panelConfig";
    public static final int PROP_ID_panelConfig = 9;
    
    /* 删除标记: DEL_FLAG TINYINT */
    public static final String PROP_NAME_delFlag = "delFlag";
    public static final int PROP_ID_delFlag = 10;
    
    /* 数据版本: VERSION BIGINT */
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

    
    /* relation: 看板 */
    public static final String PROP_NAME_dashboard = "dashboard";
    
    /* component:  */
    public static final String PROP_NAME_panelConfigComponent = "panelConfigComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_panelId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_panelId};

    private static final String[] PROP_ID_TO_NAME = new String[17];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_panelId] = PROP_NAME_panelId;
          PROP_NAME_TO_ID.put(PROP_NAME_panelId, PROP_ID_panelId);
      
          PROP_ID_TO_NAME[PROP_ID_dashboardId] = PROP_NAME_dashboardId;
          PROP_NAME_TO_ID.put(PROP_NAME_dashboardId, PROP_ID_dashboardId);
      
          PROP_ID_TO_NAME[PROP_ID_panelName] = PROP_NAME_panelName;
          PROP_NAME_TO_ID.put(PROP_NAME_panelName, PROP_ID_panelName);
      
          PROP_ID_TO_NAME[PROP_ID_displayName] = PROP_NAME_displayName;
          PROP_NAME_TO_ID.put(PROP_NAME_displayName, PROP_ID_displayName);
      
          PROP_ID_TO_NAME[PROP_ID_panelType] = PROP_NAME_panelType;
          PROP_NAME_TO_ID.put(PROP_NAME_panelType, PROP_ID_panelType);
      
          PROP_ID_TO_NAME[PROP_ID_datasetRefId] = PROP_NAME_datasetRefId;
          PROP_NAME_TO_ID.put(PROP_NAME_datasetRefId, PROP_ID_datasetRefId);
      
          PROP_ID_TO_NAME[PROP_ID_tabId] = PROP_NAME_tabId;
          PROP_NAME_TO_ID.put(PROP_NAME_tabId, PROP_ID_tabId);
      
          PROP_ID_TO_NAME[PROP_ID_sortOrder] = PROP_NAME_sortOrder;
          PROP_NAME_TO_ID.put(PROP_NAME_sortOrder, PROP_ID_sortOrder);
      
          PROP_ID_TO_NAME[PROP_ID_panelConfig] = PROP_NAME_panelConfig;
          PROP_NAME_TO_ID.put(PROP_NAME_panelConfig, PROP_ID_panelConfig);
      
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

    
    /* 面板ID: PANEL_ID */
    private java.lang.String _panelId;
    
    /* 看板ID: DASHBOARD_ID */
    private java.lang.String _dashboardId;
    
    /* 面板名: PANEL_NAME */
    private java.lang.String _panelName;
    
    /* 显示名: DISPLAY_NAME */
    private java.lang.String _displayName;
    
    /* 面板类型: PANEL_TYPE */
    private java.lang.Integer _panelType;
    
    /* 数据集引用ID: DATASET_REF_ID */
    private java.lang.String _datasetRefId;
    
    /* 页签ID: TAB_ID */
    private java.lang.String _tabId;
    
    /* 排序: SORT_ORDER */
    private java.lang.Integer _sortOrder;
    
    /* 面板配置: PANEL_CONFIG */
    private java.lang.String _panelConfig;
    
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
    

    public _NopDatavPanel(){
        // for debug
    }

    protected NopDatavPanel newInstance(){
        NopDatavPanel entity = new NopDatavPanel();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopDatavPanel cloneInstance() {
        NopDatavPanel entity = newInstance();
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
      return "io.nop.datav.dao.entity.NopDatavPanel";
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
    
        return buildSimpleId(PROP_ID_panelId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_panelId;
          
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
        
            case PROP_ID_panelId:
               return getPanelId();
        
            case PROP_ID_dashboardId:
               return getDashboardId();
        
            case PROP_ID_panelName:
               return getPanelName();
        
            case PROP_ID_displayName:
               return getDisplayName();
        
            case PROP_ID_panelType:
               return getPanelType();
        
            case PROP_ID_datasetRefId:
               return getDatasetRefId();
        
            case PROP_ID_tabId:
               return getTabId();
        
            case PROP_ID_sortOrder:
               return getSortOrder();
        
            case PROP_ID_panelConfig:
               return getPanelConfig();
        
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
        
            case PROP_ID_panelId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_panelId));
               }
               setPanelId(typedValue);
               break;
            }
        
            case PROP_ID_dashboardId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_dashboardId));
               }
               setDashboardId(typedValue);
               break;
            }
        
            case PROP_ID_panelName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_panelName));
               }
               setPanelName(typedValue);
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
        
            case PROP_ID_panelType:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_panelType));
               }
               setPanelType(typedValue);
               break;
            }
        
            case PROP_ID_datasetRefId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_datasetRefId));
               }
               setDatasetRefId(typedValue);
               break;
            }
        
            case PROP_ID_tabId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_tabId));
               }
               setTabId(typedValue);
               break;
            }
        
            case PROP_ID_sortOrder:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_sortOrder));
               }
               setSortOrder(typedValue);
               break;
            }
        
            case PROP_ID_panelConfig:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_panelConfig));
               }
               setPanelConfig(typedValue);
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
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_panelId:{
               onInitProp(propId);
               this._panelId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_dashboardId:{
               onInitProp(propId);
               this._dashboardId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_panelName:{
               onInitProp(propId);
               this._panelName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_displayName:{
               onInitProp(propId);
               this._displayName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_panelType:{
               onInitProp(propId);
               this._panelType = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_datasetRefId:{
               onInitProp(propId);
               this._datasetRefId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_tabId:{
               onInitProp(propId);
               this._tabId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_sortOrder:{
               onInitProp(propId);
               this._sortOrder = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_panelConfig:{
               onInitProp(propId);
               this._panelConfig = (java.lang.String)value;
               
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
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 面板ID: PANEL_ID
     */
    public final java.lang.String getPanelId(){
         onPropGet(PROP_ID_panelId);
         return _panelId;
    }

    /**
     * 面板ID: PANEL_ID
     */
    public final void setPanelId(java.lang.String value){
        if(onPropSet(PROP_ID_panelId,value)){
            this._panelId = value;
            internalClearRefs(PROP_ID_panelId);
            orm_id();
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
            
        }
    }
    
    /**
     * 面板名: PANEL_NAME
     */
    public final java.lang.String getPanelName(){
         onPropGet(PROP_ID_panelName);
         return _panelName;
    }

    /**
     * 面板名: PANEL_NAME
     */
    public final void setPanelName(java.lang.String value){
        if(onPropSet(PROP_ID_panelName,value)){
            this._panelName = value;
            internalClearRefs(PROP_ID_panelName);
            
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
     * 面板类型: PANEL_TYPE
     */
    public final java.lang.Integer getPanelType(){
         onPropGet(PROP_ID_panelType);
         return _panelType;
    }

    /**
     * 面板类型: PANEL_TYPE
     */
    public final void setPanelType(java.lang.Integer value){
        if(onPropSet(PROP_ID_panelType,value)){
            this._panelType = value;
            internalClearRefs(PROP_ID_panelType);
            
        }
    }
    
    /**
     * 数据集引用ID: DATASET_REF_ID
     */
    public final java.lang.String getDatasetRefId(){
         onPropGet(PROP_ID_datasetRefId);
         return _datasetRefId;
    }

    /**
     * 数据集引用ID: DATASET_REF_ID
     */
    public final void setDatasetRefId(java.lang.String value){
        if(onPropSet(PROP_ID_datasetRefId,value)){
            this._datasetRefId = value;
            internalClearRefs(PROP_ID_datasetRefId);
            
        }
    }
    
    /**
     * 页签ID: TAB_ID
     */
    public final java.lang.String getTabId(){
         onPropGet(PROP_ID_tabId);
         return _tabId;
    }

    /**
     * 页签ID: TAB_ID
     */
    public final void setTabId(java.lang.String value){
        if(onPropSet(PROP_ID_tabId,value)){
            this._tabId = value;
            internalClearRefs(PROP_ID_tabId);
            
        }
    }
    
    /**
     * 排序: SORT_ORDER
     */
    public final java.lang.Integer getSortOrder(){
         onPropGet(PROP_ID_sortOrder);
         return _sortOrder;
    }

    /**
     * 排序: SORT_ORDER
     */
    public final void setSortOrder(java.lang.Integer value){
        if(onPropSet(PROP_ID_sortOrder,value)){
            this._sortOrder = value;
            internalClearRefs(PROP_ID_sortOrder);
            
        }
    }
    
    /**
     * 面板配置: PANEL_CONFIG
     */
    public final java.lang.String getPanelConfig(){
         onPropGet(PROP_ID_panelConfig);
         return _panelConfig;
    }

    /**
     * 面板配置: PANEL_CONFIG
     */
    public final void setPanelConfig(java.lang.String value){
        if(onPropSet(PROP_ID_panelConfig,value)){
            this._panelConfig = value;
            internalClearRefs(PROP_ID_panelConfig);
            
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
     * 看板
     */
    public final io.nop.datav.dao.entity.NopDatavDashboard getDashboard(){
       return (io.nop.datav.dao.entity.NopDatavDashboard)internalGetRefEntity(PROP_NAME_dashboard);
    }

    public final void setDashboard(io.nop.datav.dao.entity.NopDatavDashboard refEntity){
   
           if(refEntity == null){
           
                   this.setDashboardId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_dashboard, refEntity,()->{
           
                           this.setDashboardId(refEntity.getDashboardId());
                       
           });
           }
       
    }
       
   private io.nop.orm.component.JsonOrmComponent _panelConfigComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_panelConfigComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_panelConfigComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_panelConfig);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getPanelConfigComponent(){
      if(_panelConfigComponent == null){
          _panelConfigComponent = new io.nop.orm.component.JsonOrmComponent();
          _panelConfigComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_panelConfigComponent);
      }
      return _panelConfigComponent;
   }

}
// resume CPD analysis - CPD-ON
