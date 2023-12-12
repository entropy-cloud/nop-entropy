package io.nop.report.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.report.dao.entity.NopReportDataset;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  数据集定义: nop_report_dataset
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101"})
public class _NopReportDataset extends DynamicOrmEntity{
    
    /* 主键: DS_ID VARCHAR */
    public static final String PROP_NAME_dsId = "dsId";
    public static final int PROP_ID_dsId = 1;
    
    /* 数据集名称: DS_NAME VARCHAR */
    public static final String PROP_NAME_dsName = "dsName";
    public static final int PROP_ID_dsName = 2;
    
    /* 描述: DESCRIPTION VARCHAR */
    public static final String PROP_NAME_description = "description";
    public static final int PROP_ID_description = 3;
    
    /* 数据集类型: DS_TYPE VARCHAR */
    public static final String PROP_NAME_dsType = "dsType";
    public static final int PROP_ID_dsType = 4;
    
    /* 数据集配置: DS_CONFIG VARCHAR */
    public static final String PROP_NAME_dsConfig = "dsConfig";
    public static final int PROP_ID_dsConfig = 5;
    
    /* 数据集文本: DS_TEXT VARCHAR */
    public static final String PROP_NAME_dsText = "dsText";
    public static final int PROP_ID_dsText = 6;
    
    /* 数据集元数据: DS_META VARCHAR */
    public static final String PROP_NAME_dsMeta = "dsMeta";
    public static final int PROP_ID_dsMeta = 7;
    
    /* 数据集显示配置: DS_VIEW VARCHAR */
    public static final String PROP_NAME_dsView = "dsView";
    public static final int PROP_ID_dsView = 8;
    
    /* 状态: STATUS INTEGER */
    public static final String PROP_NAME_status = "status";
    public static final int PROP_ID_status = 9;
    
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

    

    public static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_dsId);
    public static final int[] PK_PROP_IDS = new int[]{PROP_ID_dsId};

    private static final String[] PROP_ID_TO_NAME = new String[16];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_dsId] = PROP_NAME_dsId;
          PROP_NAME_TO_ID.put(PROP_NAME_dsId, PROP_ID_dsId);
      
          PROP_ID_TO_NAME[PROP_ID_dsName] = PROP_NAME_dsName;
          PROP_NAME_TO_ID.put(PROP_NAME_dsName, PROP_ID_dsName);
      
          PROP_ID_TO_NAME[PROP_ID_description] = PROP_NAME_description;
          PROP_NAME_TO_ID.put(PROP_NAME_description, PROP_ID_description);
      
          PROP_ID_TO_NAME[PROP_ID_dsType] = PROP_NAME_dsType;
          PROP_NAME_TO_ID.put(PROP_NAME_dsType, PROP_ID_dsType);
      
          PROP_ID_TO_NAME[PROP_ID_dsConfig] = PROP_NAME_dsConfig;
          PROP_NAME_TO_ID.put(PROP_NAME_dsConfig, PROP_ID_dsConfig);
      
          PROP_ID_TO_NAME[PROP_ID_dsText] = PROP_NAME_dsText;
          PROP_NAME_TO_ID.put(PROP_NAME_dsText, PROP_ID_dsText);
      
          PROP_ID_TO_NAME[PROP_ID_dsMeta] = PROP_NAME_dsMeta;
          PROP_NAME_TO_ID.put(PROP_NAME_dsMeta, PROP_ID_dsMeta);
      
          PROP_ID_TO_NAME[PROP_ID_dsView] = PROP_NAME_dsView;
          PROP_NAME_TO_ID.put(PROP_NAME_dsView, PROP_ID_dsView);
      
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

    
    /* 主键: DS_ID */
    private java.lang.String _dsId;
    
    /* 数据集名称: DS_NAME */
    private java.lang.String _dsName;
    
    /* 描述: DESCRIPTION */
    private java.lang.String _description;
    
    /* 数据集类型: DS_TYPE */
    private java.lang.String _dsType;
    
    /* 数据集配置: DS_CONFIG */
    private java.lang.String _dsConfig;
    
    /* 数据集文本: DS_TEXT */
    private java.lang.String _dsText;
    
    /* 数据集元数据: DS_META */
    private java.lang.String _dsMeta;
    
    /* 数据集显示配置: DS_VIEW */
    private java.lang.String _dsView;
    
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
    

    public _NopReportDataset(){
    }

    protected NopReportDataset newInstance(){
       return new NopReportDataset();
    }

    @Override
    public NopReportDataset cloneInstance() {
        NopReportDataset entity = newInstance();
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
      return "io.nop.report.dao.entity.NopReportDataset";
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
    
        return buildSimpleId(PROP_ID_dsId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_dsId;
          
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
        
            case PROP_ID_dsId:
               return getDsId();
        
            case PROP_ID_dsName:
               return getDsName();
        
            case PROP_ID_description:
               return getDescription();
        
            case PROP_ID_dsType:
               return getDsType();
        
            case PROP_ID_dsConfig:
               return getDsConfig();
        
            case PROP_ID_dsText:
               return getDsText();
        
            case PROP_ID_dsMeta:
               return getDsMeta();
        
            case PROP_ID_dsView:
               return getDsView();
        
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
        
            case PROP_ID_dsId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_dsId));
               }
               setDsId(typedValue);
               break;
            }
        
            case PROP_ID_dsName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_dsName));
               }
               setDsName(typedValue);
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
        
            case PROP_ID_dsType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_dsType));
               }
               setDsType(typedValue);
               break;
            }
        
            case PROP_ID_dsConfig:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_dsConfig));
               }
               setDsConfig(typedValue);
               break;
            }
        
            case PROP_ID_dsText:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_dsText));
               }
               setDsText(typedValue);
               break;
            }
        
            case PROP_ID_dsMeta:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_dsMeta));
               }
               setDsMeta(typedValue);
               break;
            }
        
            case PROP_ID_dsView:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_dsView));
               }
               setDsView(typedValue);
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
        
            case PROP_ID_dsId:{
               onInitProp(propId);
               this._dsId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_dsName:{
               onInitProp(propId);
               this._dsName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_description:{
               onInitProp(propId);
               this._description = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_dsType:{
               onInitProp(propId);
               this._dsType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_dsConfig:{
               onInitProp(propId);
               this._dsConfig = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_dsText:{
               onInitProp(propId);
               this._dsText = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_dsMeta:{
               onInitProp(propId);
               this._dsMeta = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_dsView:{
               onInitProp(propId);
               this._dsView = (java.lang.String)value;
               
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
     * 主键: DS_ID
     */
    public java.lang.String getDsId(){
         onPropGet(PROP_ID_dsId);
         return _dsId;
    }

    /**
     * 主键: DS_ID
     */
    public void setDsId(java.lang.String value){
        if(onPropSet(PROP_ID_dsId,value)){
            this._dsId = value;
            internalClearRefs(PROP_ID_dsId);
            orm_id();
        }
    }
    
    /**
     * 数据集名称: DS_NAME
     */
    public java.lang.String getDsName(){
         onPropGet(PROP_ID_dsName);
         return _dsName;
    }

    /**
     * 数据集名称: DS_NAME
     */
    public void setDsName(java.lang.String value){
        if(onPropSet(PROP_ID_dsName,value)){
            this._dsName = value;
            internalClearRefs(PROP_ID_dsName);
            
        }
    }
    
    /**
     * 描述: DESCRIPTION
     */
    public java.lang.String getDescription(){
         onPropGet(PROP_ID_description);
         return _description;
    }

    /**
     * 描述: DESCRIPTION
     */
    public void setDescription(java.lang.String value){
        if(onPropSet(PROP_ID_description,value)){
            this._description = value;
            internalClearRefs(PROP_ID_description);
            
        }
    }
    
    /**
     * 数据集类型: DS_TYPE
     */
    public java.lang.String getDsType(){
         onPropGet(PROP_ID_dsType);
         return _dsType;
    }

    /**
     * 数据集类型: DS_TYPE
     */
    public void setDsType(java.lang.String value){
        if(onPropSet(PROP_ID_dsType,value)){
            this._dsType = value;
            internalClearRefs(PROP_ID_dsType);
            
        }
    }
    
    /**
     * 数据集配置: DS_CONFIG
     */
    public java.lang.String getDsConfig(){
         onPropGet(PROP_ID_dsConfig);
         return _dsConfig;
    }

    /**
     * 数据集配置: DS_CONFIG
     */
    public void setDsConfig(java.lang.String value){
        if(onPropSet(PROP_ID_dsConfig,value)){
            this._dsConfig = value;
            internalClearRefs(PROP_ID_dsConfig);
            
        }
    }
    
    /**
     * 数据集文本: DS_TEXT
     */
    public java.lang.String getDsText(){
         onPropGet(PROP_ID_dsText);
         return _dsText;
    }

    /**
     * 数据集文本: DS_TEXT
     */
    public void setDsText(java.lang.String value){
        if(onPropSet(PROP_ID_dsText,value)){
            this._dsText = value;
            internalClearRefs(PROP_ID_dsText);
            
        }
    }
    
    /**
     * 数据集元数据: DS_META
     */
    public java.lang.String getDsMeta(){
         onPropGet(PROP_ID_dsMeta);
         return _dsMeta;
    }

    /**
     * 数据集元数据: DS_META
     */
    public void setDsMeta(java.lang.String value){
        if(onPropSet(PROP_ID_dsMeta,value)){
            this._dsMeta = value;
            internalClearRefs(PROP_ID_dsMeta);
            
        }
    }
    
    /**
     * 数据集显示配置: DS_VIEW
     */
    public java.lang.String getDsView(){
         onPropGet(PROP_ID_dsView);
         return _dsView;
    }

    /**
     * 数据集显示配置: DS_VIEW
     */
    public void setDsView(java.lang.String value){
        if(onPropSet(PROP_ID_dsView,value)){
            this._dsView = value;
            internalClearRefs(PROP_ID_dsView);
            
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
    
}
// resume CPD analysis - CPD-ON
