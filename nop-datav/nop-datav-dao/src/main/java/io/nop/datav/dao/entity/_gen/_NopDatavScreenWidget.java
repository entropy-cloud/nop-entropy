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

import io.nop.datav.dao.entity.NopDatavScreenWidget;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  大屏组件: nop_datav_screen_widget
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopDatavScreenWidget extends DynamicOrmEntity{
    
    /* 组件ID: WIDGET_ID VARCHAR */
    public static final String PROP_NAME_widgetId = "widgetId";
    public static final int PROP_ID_widgetId = 1;
    
    /* 大屏ID: SCREEN_ID VARCHAR */
    public static final String PROP_NAME_screenId = "screenId";
    public static final int PROP_ID_screenId = 2;
    
    /* 组件名: WIDGET_NAME VARCHAR */
    public static final String PROP_NAME_widgetName = "widgetName";
    public static final int PROP_ID_widgetName = 3;
    
    /* 显示名: DISPLAY_NAME VARCHAR */
    public static final String PROP_NAME_displayName = "displayName";
    public static final int PROP_ID_displayName = 4;
    
    /* 组件类型: COMPONENT_TYPE VARCHAR */
    public static final String PROP_NAME_componentType = "componentType";
    public static final int PROP_ID_componentType = 5;
    
    /* 数据集引用ID: DATASET_REF_ID VARCHAR */
    public static final String PROP_NAME_datasetRefId = "datasetRefId";
    public static final int PROP_ID_datasetRefId = 6;
    
    /* X坐标: X INTEGER */
    public static final String PROP_NAME_x = "x";
    public static final int PROP_ID_x = 7;
    
    /* Y坐标: Y INTEGER */
    public static final String PROP_NAME_y = "y";
    public static final int PROP_ID_y = 8;
    
    /* 宽度: W INTEGER */
    public static final String PROP_NAME_w = "w";
    public static final int PROP_ID_w = 9;
    
    /* 高度: H INTEGER */
    public static final String PROP_NAME_h = "h";
    public static final int PROP_ID_h = 10;
    
    /* 层级: Z INTEGER */
    public static final String PROP_NAME_z = "z";
    public static final int PROP_ID_z = 11;
    
    /* 组件配置: WIDGET_CONFIG VARCHAR */
    public static final String PROP_NAME_widgetConfig = "widgetConfig";
    public static final int PROP_ID_widgetConfig = 12;
    
    /* 删除标记: DEL_FLAG TINYINT */
    public static final String PROP_NAME_delFlag = "delFlag";
    public static final int PROP_ID_delFlag = 13;
    
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

    
    /* relation: 大屏 */
    public static final String PROP_NAME_screen = "screen";
    
    /* component:  */
    public static final String PROP_NAME_widgetConfigComponent = "widgetConfigComponent";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_widgetId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_widgetId};

    private static final String[] PROP_ID_TO_NAME = new String[20];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_widgetId] = PROP_NAME_widgetId;
          PROP_NAME_TO_ID.put(PROP_NAME_widgetId, PROP_ID_widgetId);
      
          PROP_ID_TO_NAME[PROP_ID_screenId] = PROP_NAME_screenId;
          PROP_NAME_TO_ID.put(PROP_NAME_screenId, PROP_ID_screenId);
      
          PROP_ID_TO_NAME[PROP_ID_widgetName] = PROP_NAME_widgetName;
          PROP_NAME_TO_ID.put(PROP_NAME_widgetName, PROP_ID_widgetName);
      
          PROP_ID_TO_NAME[PROP_ID_displayName] = PROP_NAME_displayName;
          PROP_NAME_TO_ID.put(PROP_NAME_displayName, PROP_ID_displayName);
      
          PROP_ID_TO_NAME[PROP_ID_componentType] = PROP_NAME_componentType;
          PROP_NAME_TO_ID.put(PROP_NAME_componentType, PROP_ID_componentType);
      
          PROP_ID_TO_NAME[PROP_ID_datasetRefId] = PROP_NAME_datasetRefId;
          PROP_NAME_TO_ID.put(PROP_NAME_datasetRefId, PROP_ID_datasetRefId);
      
          PROP_ID_TO_NAME[PROP_ID_x] = PROP_NAME_x;
          PROP_NAME_TO_ID.put(PROP_NAME_x, PROP_ID_x);
      
          PROP_ID_TO_NAME[PROP_ID_y] = PROP_NAME_y;
          PROP_NAME_TO_ID.put(PROP_NAME_y, PROP_ID_y);
      
          PROP_ID_TO_NAME[PROP_ID_w] = PROP_NAME_w;
          PROP_NAME_TO_ID.put(PROP_NAME_w, PROP_ID_w);
      
          PROP_ID_TO_NAME[PROP_ID_h] = PROP_NAME_h;
          PROP_NAME_TO_ID.put(PROP_NAME_h, PROP_ID_h);
      
          PROP_ID_TO_NAME[PROP_ID_z] = PROP_NAME_z;
          PROP_NAME_TO_ID.put(PROP_NAME_z, PROP_ID_z);
      
          PROP_ID_TO_NAME[PROP_ID_widgetConfig] = PROP_NAME_widgetConfig;
          PROP_NAME_TO_ID.put(PROP_NAME_widgetConfig, PROP_ID_widgetConfig);
      
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

    
    /* 组件ID: WIDGET_ID */
    private java.lang.String _widgetId;
    
    /* 大屏ID: SCREEN_ID */
    private java.lang.String _screenId;
    
    /* 组件名: WIDGET_NAME */
    private java.lang.String _widgetName;
    
    /* 显示名: DISPLAY_NAME */
    private java.lang.String _displayName;
    
    /* 组件类型: COMPONENT_TYPE */
    private java.lang.String _componentType;
    
    /* 数据集引用ID: DATASET_REF_ID */
    private java.lang.String _datasetRefId;
    
    /* X坐标: X */
    private java.lang.Integer _x;
    
    /* Y坐标: Y */
    private java.lang.Integer _y;
    
    /* 宽度: W */
    private java.lang.Integer _w;
    
    /* 高度: H */
    private java.lang.Integer _h;
    
    /* 层级: Z */
    private java.lang.Integer _z;
    
    /* 组件配置: WIDGET_CONFIG */
    private java.lang.String _widgetConfig;
    
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
    

    public _NopDatavScreenWidget(){
        // for debug
    }

    protected NopDatavScreenWidget newInstance(){
        NopDatavScreenWidget entity = new NopDatavScreenWidget();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopDatavScreenWidget cloneInstance() {
        NopDatavScreenWidget entity = newInstance();
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
      return "io.nop.datav.dao.entity.NopDatavScreenWidget";
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
    
        return buildSimpleId(PROP_ID_widgetId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_widgetId;
          
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
        
            case PROP_ID_widgetId:
               return getWidgetId();
        
            case PROP_ID_screenId:
               return getScreenId();
        
            case PROP_ID_widgetName:
               return getWidgetName();
        
            case PROP_ID_displayName:
               return getDisplayName();
        
            case PROP_ID_componentType:
               return getComponentType();
        
            case PROP_ID_datasetRefId:
               return getDatasetRefId();
        
            case PROP_ID_x:
               return getX();
        
            case PROP_ID_y:
               return getY();
        
            case PROP_ID_w:
               return getW();
        
            case PROP_ID_h:
               return getH();
        
            case PROP_ID_z:
               return getZ();
        
            case PROP_ID_widgetConfig:
               return getWidgetConfig();
        
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
        
            case PROP_ID_widgetId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_widgetId));
               }
               setWidgetId(typedValue);
               break;
            }
        
            case PROP_ID_screenId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_screenId));
               }
               setScreenId(typedValue);
               break;
            }
        
            case PROP_ID_widgetName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_widgetName));
               }
               setWidgetName(typedValue);
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
        
            case PROP_ID_componentType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_componentType));
               }
               setComponentType(typedValue);
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
        
            case PROP_ID_x:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_x));
               }
               setX(typedValue);
               break;
            }
        
            case PROP_ID_y:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_y));
               }
               setY(typedValue);
               break;
            }
        
            case PROP_ID_w:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_w));
               }
               setW(typedValue);
               break;
            }
        
            case PROP_ID_h:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_h));
               }
               setH(typedValue);
               break;
            }
        
            case PROP_ID_z:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_z));
               }
               setZ(typedValue);
               break;
            }
        
            case PROP_ID_widgetConfig:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_widgetConfig));
               }
               setWidgetConfig(typedValue);
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
        
            case PROP_ID_widgetId:{
               onInitProp(propId);
               this._widgetId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_screenId:{
               onInitProp(propId);
               this._screenId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_widgetName:{
               onInitProp(propId);
               this._widgetName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_displayName:{
               onInitProp(propId);
               this._displayName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_componentType:{
               onInitProp(propId);
               this._componentType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_datasetRefId:{
               onInitProp(propId);
               this._datasetRefId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_x:{
               onInitProp(propId);
               this._x = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_y:{
               onInitProp(propId);
               this._y = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_w:{
               onInitProp(propId);
               this._w = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_h:{
               onInitProp(propId);
               this._h = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_z:{
               onInitProp(propId);
               this._z = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_widgetConfig:{
               onInitProp(propId);
               this._widgetConfig = (java.lang.String)value;
               
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
     * 组件ID: WIDGET_ID
     */
    public final java.lang.String getWidgetId(){
         onPropGet(PROP_ID_widgetId);
         return _widgetId;
    }

    /**
     * 组件ID: WIDGET_ID
     */
    public final void setWidgetId(java.lang.String value){
        if(onPropSet(PROP_ID_widgetId,value)){
            this._widgetId = value;
            internalClearRefs(PROP_ID_widgetId);
            orm_id();
        }
    }
    
    /**
     * 大屏ID: SCREEN_ID
     */
    public final java.lang.String getScreenId(){
         onPropGet(PROP_ID_screenId);
         return _screenId;
    }

    /**
     * 大屏ID: SCREEN_ID
     */
    public final void setScreenId(java.lang.String value){
        if(onPropSet(PROP_ID_screenId,value)){
            this._screenId = value;
            internalClearRefs(PROP_ID_screenId);
            
        }
    }
    
    /**
     * 组件名: WIDGET_NAME
     */
    public final java.lang.String getWidgetName(){
         onPropGet(PROP_ID_widgetName);
         return _widgetName;
    }

    /**
     * 组件名: WIDGET_NAME
     */
    public final void setWidgetName(java.lang.String value){
        if(onPropSet(PROP_ID_widgetName,value)){
            this._widgetName = value;
            internalClearRefs(PROP_ID_widgetName);
            
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
     * 组件类型: COMPONENT_TYPE
     */
    public final java.lang.String getComponentType(){
         onPropGet(PROP_ID_componentType);
         return _componentType;
    }

    /**
     * 组件类型: COMPONENT_TYPE
     */
    public final void setComponentType(java.lang.String value){
        if(onPropSet(PROP_ID_componentType,value)){
            this._componentType = value;
            internalClearRefs(PROP_ID_componentType);
            
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
     * X坐标: X
     */
    public final java.lang.Integer getX(){
         onPropGet(PROP_ID_x);
         return _x;
    }

    /**
     * X坐标: X
     */
    public final void setX(java.lang.Integer value){
        if(onPropSet(PROP_ID_x,value)){
            this._x = value;
            internalClearRefs(PROP_ID_x);
            
        }
    }
    
    /**
     * Y坐标: Y
     */
    public final java.lang.Integer getY(){
         onPropGet(PROP_ID_y);
         return _y;
    }

    /**
     * Y坐标: Y
     */
    public final void setY(java.lang.Integer value){
        if(onPropSet(PROP_ID_y,value)){
            this._y = value;
            internalClearRefs(PROP_ID_y);
            
        }
    }
    
    /**
     * 宽度: W
     */
    public final java.lang.Integer getW(){
         onPropGet(PROP_ID_w);
         return _w;
    }

    /**
     * 宽度: W
     */
    public final void setW(java.lang.Integer value){
        if(onPropSet(PROP_ID_w,value)){
            this._w = value;
            internalClearRefs(PROP_ID_w);
            
        }
    }
    
    /**
     * 高度: H
     */
    public final java.lang.Integer getH(){
         onPropGet(PROP_ID_h);
         return _h;
    }

    /**
     * 高度: H
     */
    public final void setH(java.lang.Integer value){
        if(onPropSet(PROP_ID_h,value)){
            this._h = value;
            internalClearRefs(PROP_ID_h);
            
        }
    }
    
    /**
     * 层级: Z
     */
    public final java.lang.Integer getZ(){
         onPropGet(PROP_ID_z);
         return _z;
    }

    /**
     * 层级: Z
     */
    public final void setZ(java.lang.Integer value){
        if(onPropSet(PROP_ID_z,value)){
            this._z = value;
            internalClearRefs(PROP_ID_z);
            
        }
    }
    
    /**
     * 组件配置: WIDGET_CONFIG
     */
    public final java.lang.String getWidgetConfig(){
         onPropGet(PROP_ID_widgetConfig);
         return _widgetConfig;
    }

    /**
     * 组件配置: WIDGET_CONFIG
     */
    public final void setWidgetConfig(java.lang.String value){
        if(onPropSet(PROP_ID_widgetConfig,value)){
            this._widgetConfig = value;
            internalClearRefs(PROP_ID_widgetConfig);
            
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
     * 大屏
     */
    public final io.nop.datav.dao.entity.NopDatavScreen getScreen(){
       return (io.nop.datav.dao.entity.NopDatavScreen)internalGetRefEntity(PROP_NAME_screen);
    }

    public final void setScreen(io.nop.datav.dao.entity.NopDatavScreen refEntity){
   
           if(refEntity == null){
           
                   this.setScreenId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_screen, refEntity,()->{
           
                           this.setScreenId(refEntity.getScreenId());
                       
           });
           }
       
    }
       
   private io.nop.orm.component.JsonOrmComponent _widgetConfigComponent;

   private static Map<String,Integer> COMPONENT_PROP_ID_MAP_widgetConfigComponent = new HashMap<>();
   static{
      
         COMPONENT_PROP_ID_MAP_widgetConfigComponent.put(io.nop.orm.component.JsonOrmComponent.PROP_NAME__jsonText,PROP_ID_widgetConfig);
      
   }

   public final io.nop.orm.component.JsonOrmComponent getWidgetConfigComponent(){
      if(_widgetConfigComponent == null){
          _widgetConfigComponent = new io.nop.orm.component.JsonOrmComponent();
          _widgetConfigComponent.bindToEntity(this, COMPONENT_PROP_ID_MAP_widgetConfigComponent);
      }
      return _widgetConfigComponent;
   }

}
// resume CPD analysis - CPD-ON
