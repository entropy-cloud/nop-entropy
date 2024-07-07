package io.nop.wf.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.wf.dao.entity.NopWfDefinition;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  工作流模型定义: nop_wf_definition
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopWfDefinition extends DynamicOrmEntity{
    
    /* 主键: WF_DEF_ID VARCHAR */
    public static final String PROP_NAME_wfDefId = "wfDefId";
    public static final int PROP_ID_wfDefId = 1;
    
    /* 工作流名称: WF_NAME VARCHAR */
    public static final String PROP_NAME_wfName = "wfName";
    public static final int PROP_ID_wfName = 2;
    
    /* 工作流版本: WF_VERSION BIGINT */
    public static final String PROP_NAME_wfVersion = "wfVersion";
    public static final int PROP_ID_wfVersion = 3;
    
    /* 显示名称: DISPLAY_NAME VARCHAR */
    public static final String PROP_NAME_displayName = "displayName";
    public static final int PROP_ID_displayName = 4;
    
    /* 描述: DESCRIPTION VARCHAR */
    public static final String PROP_NAME_description = "description";
    public static final int PROP_ID_description = 5;
    
    /* 模型文本: MODEL_TEXT VARCHAR */
    public static final String PROP_NAME_modelText = "modelText";
    public static final int PROP_ID_modelText = 6;
    
    /* 状态: STATUS INTEGER */
    public static final String PROP_NAME_status = "status";
    public static final int PROP_ID_status = 7;
    
    /* 发布人: PUBLISHED_BY VARCHAR */
    public static final String PROP_NAME_publishedBy = "publishedBy";
    public static final int PROP_ID_publishedBy = 8;
    
    /* 发布时间: PUBLISH_TIME DATETIME */
    public static final String PROP_NAME_publishTime = "publishTime";
    public static final int PROP_ID_publishTime = 9;
    
    /* 归档人: ARCHIVED_BY VARCHAR */
    public static final String PROP_NAME_archivedBy = "archivedBy";
    public static final int PROP_ID_archivedBy = 10;
    
    /* 归档时间: ARCHIVE_TIME DATETIME */
    public static final String PROP_NAME_archiveTime = "archiveTime";
    public static final int PROP_ID_archiveTime = 11;
    
    /* 是否已废弃: IS_DEPRECATED BOOLEAN */
    public static final String PROP_NAME_isDeprecated = "isDeprecated";
    public static final int PROP_ID_isDeprecated = 12;
    
    /* 数据版本: VERSION INTEGER */
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

    
    /* relation: 工作流定义权限 */
    public static final String PROP_NAME_definitionAuths = "definitionAuths";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_wfDefId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_wfDefId};

    private static final String[] PROP_ID_TO_NAME = new String[19];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_wfDefId] = PROP_NAME_wfDefId;
          PROP_NAME_TO_ID.put(PROP_NAME_wfDefId, PROP_ID_wfDefId);
      
          PROP_ID_TO_NAME[PROP_ID_wfName] = PROP_NAME_wfName;
          PROP_NAME_TO_ID.put(PROP_NAME_wfName, PROP_ID_wfName);
      
          PROP_ID_TO_NAME[PROP_ID_wfVersion] = PROP_NAME_wfVersion;
          PROP_NAME_TO_ID.put(PROP_NAME_wfVersion, PROP_ID_wfVersion);
      
          PROP_ID_TO_NAME[PROP_ID_displayName] = PROP_NAME_displayName;
          PROP_NAME_TO_ID.put(PROP_NAME_displayName, PROP_ID_displayName);
      
          PROP_ID_TO_NAME[PROP_ID_description] = PROP_NAME_description;
          PROP_NAME_TO_ID.put(PROP_NAME_description, PROP_ID_description);
      
          PROP_ID_TO_NAME[PROP_ID_modelText] = PROP_NAME_modelText;
          PROP_NAME_TO_ID.put(PROP_NAME_modelText, PROP_ID_modelText);
      
          PROP_ID_TO_NAME[PROP_ID_status] = PROP_NAME_status;
          PROP_NAME_TO_ID.put(PROP_NAME_status, PROP_ID_status);
      
          PROP_ID_TO_NAME[PROP_ID_publishedBy] = PROP_NAME_publishedBy;
          PROP_NAME_TO_ID.put(PROP_NAME_publishedBy, PROP_ID_publishedBy);
      
          PROP_ID_TO_NAME[PROP_ID_publishTime] = PROP_NAME_publishTime;
          PROP_NAME_TO_ID.put(PROP_NAME_publishTime, PROP_ID_publishTime);
      
          PROP_ID_TO_NAME[PROP_ID_archivedBy] = PROP_NAME_archivedBy;
          PROP_NAME_TO_ID.put(PROP_NAME_archivedBy, PROP_ID_archivedBy);
      
          PROP_ID_TO_NAME[PROP_ID_archiveTime] = PROP_NAME_archiveTime;
          PROP_NAME_TO_ID.put(PROP_NAME_archiveTime, PROP_ID_archiveTime);
      
          PROP_ID_TO_NAME[PROP_ID_isDeprecated] = PROP_NAME_isDeprecated;
          PROP_NAME_TO_ID.put(PROP_NAME_isDeprecated, PROP_ID_isDeprecated);
      
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

    
    /* 主键: WF_DEF_ID */
    private java.lang.String _wfDefId;
    
    /* 工作流名称: WF_NAME */
    private java.lang.String _wfName;
    
    /* 工作流版本: WF_VERSION */
    private java.lang.Long _wfVersion;
    
    /* 显示名称: DISPLAY_NAME */
    private java.lang.String _displayName;
    
    /* 描述: DESCRIPTION */
    private java.lang.String _description;
    
    /* 模型文本: MODEL_TEXT */
    private java.lang.String _modelText;
    
    /* 状态: STATUS */
    private java.lang.Integer _status;
    
    /* 发布人: PUBLISHED_BY */
    private java.lang.String _publishedBy;
    
    /* 发布时间: PUBLISH_TIME */
    private java.time.LocalDateTime _publishTime;
    
    /* 归档人: ARCHIVED_BY */
    private java.lang.String _archivedBy;
    
    /* 归档时间: ARCHIVE_TIME */
    private java.time.LocalDateTime _archiveTime;
    
    /* 是否已废弃: IS_DEPRECATED */
    private java.lang.Boolean _isDeprecated;
    
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
    

    public _NopWfDefinition(){
        // for debug
    }

    protected NopWfDefinition newInstance(){
        NopWfDefinition entity = new NopWfDefinition();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopWfDefinition cloneInstance() {
        NopWfDefinition entity = newInstance();
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
      return "io.nop.wf.dao.entity.NopWfDefinition";
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
    
        return buildSimpleId(PROP_ID_wfDefId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_wfDefId;
          
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
        
            case PROP_ID_wfDefId:
               return getWfDefId();
        
            case PROP_ID_wfName:
               return getWfName();
        
            case PROP_ID_wfVersion:
               return getWfVersion();
        
            case PROP_ID_displayName:
               return getDisplayName();
        
            case PROP_ID_description:
               return getDescription();
        
            case PROP_ID_modelText:
               return getModelText();
        
            case PROP_ID_status:
               return getStatus();
        
            case PROP_ID_publishedBy:
               return getPublishedBy();
        
            case PROP_ID_publishTime:
               return getPublishTime();
        
            case PROP_ID_archivedBy:
               return getArchivedBy();
        
            case PROP_ID_archiveTime:
               return getArchiveTime();
        
            case PROP_ID_isDeprecated:
               return getIsDeprecated();
        
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
        
            case PROP_ID_wfDefId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_wfDefId));
               }
               setWfDefId(typedValue);
               break;
            }
        
            case PROP_ID_wfName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_wfName));
               }
               setWfName(typedValue);
               break;
            }
        
            case PROP_ID_wfVersion:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_wfVersion));
               }
               setWfVersion(typedValue);
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
        
            case PROP_ID_modelText:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_modelText));
               }
               setModelText(typedValue);
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
        
            case PROP_ID_publishedBy:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_publishedBy));
               }
               setPublishedBy(typedValue);
               break;
            }
        
            case PROP_ID_publishTime:{
               java.time.LocalDateTime typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLocalDateTime(value,
                       err-> newTypeConversionError(PROP_NAME_publishTime));
               }
               setPublishTime(typedValue);
               break;
            }
        
            case PROP_ID_archivedBy:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_archivedBy));
               }
               setArchivedBy(typedValue);
               break;
            }
        
            case PROP_ID_archiveTime:{
               java.time.LocalDateTime typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLocalDateTime(value,
                       err-> newTypeConversionError(PROP_NAME_archiveTime));
               }
               setArchiveTime(typedValue);
               break;
            }
        
            case PROP_ID_isDeprecated:{
               java.lang.Boolean typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBoolean(value,
                       err-> newTypeConversionError(PROP_NAME_isDeprecated));
               }
               setIsDeprecated(typedValue);
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
        
            case PROP_ID_wfDefId:{
               onInitProp(propId);
               this._wfDefId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_wfName:{
               onInitProp(propId);
               this._wfName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_wfVersion:{
               onInitProp(propId);
               this._wfVersion = (java.lang.Long)value;
               
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
        
            case PROP_ID_modelText:{
               onInitProp(propId);
               this._modelText = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_status:{
               onInitProp(propId);
               this._status = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_publishedBy:{
               onInitProp(propId);
               this._publishedBy = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_publishTime:{
               onInitProp(propId);
               this._publishTime = (java.time.LocalDateTime)value;
               
               break;
            }
        
            case PROP_ID_archivedBy:{
               onInitProp(propId);
               this._archivedBy = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_archiveTime:{
               onInitProp(propId);
               this._archiveTime = (java.time.LocalDateTime)value;
               
               break;
            }
        
            case PROP_ID_isDeprecated:{
               onInitProp(propId);
               this._isDeprecated = (java.lang.Boolean)value;
               
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
     * 主键: WF_DEF_ID
     */
    public java.lang.String getWfDefId(){
         onPropGet(PROP_ID_wfDefId);
         return _wfDefId;
    }

    /**
     * 主键: WF_DEF_ID
     */
    public void setWfDefId(java.lang.String value){
        if(onPropSet(PROP_ID_wfDefId,value)){
            this._wfDefId = value;
            internalClearRefs(PROP_ID_wfDefId);
            orm_id();
        }
    }
    
    /**
     * 工作流名称: WF_NAME
     */
    public java.lang.String getWfName(){
         onPropGet(PROP_ID_wfName);
         return _wfName;
    }

    /**
     * 工作流名称: WF_NAME
     */
    public void setWfName(java.lang.String value){
        if(onPropSet(PROP_ID_wfName,value)){
            this._wfName = value;
            internalClearRefs(PROP_ID_wfName);
            
        }
    }
    
    /**
     * 工作流版本: WF_VERSION
     */
    public java.lang.Long getWfVersion(){
         onPropGet(PROP_ID_wfVersion);
         return _wfVersion;
    }

    /**
     * 工作流版本: WF_VERSION
     */
    public void setWfVersion(java.lang.Long value){
        if(onPropSet(PROP_ID_wfVersion,value)){
            this._wfVersion = value;
            internalClearRefs(PROP_ID_wfVersion);
            
        }
    }
    
    /**
     * 显示名称: DISPLAY_NAME
     */
    public java.lang.String getDisplayName(){
         onPropGet(PROP_ID_displayName);
         return _displayName;
    }

    /**
     * 显示名称: DISPLAY_NAME
     */
    public void setDisplayName(java.lang.String value){
        if(onPropSet(PROP_ID_displayName,value)){
            this._displayName = value;
            internalClearRefs(PROP_ID_displayName);
            
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
     * 模型文本: MODEL_TEXT
     */
    public java.lang.String getModelText(){
         onPropGet(PROP_ID_modelText);
         return _modelText;
    }

    /**
     * 模型文本: MODEL_TEXT
     */
    public void setModelText(java.lang.String value){
        if(onPropSet(PROP_ID_modelText,value)){
            this._modelText = value;
            internalClearRefs(PROP_ID_modelText);
            
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
     * 发布人: PUBLISHED_BY
     */
    public java.lang.String getPublishedBy(){
         onPropGet(PROP_ID_publishedBy);
         return _publishedBy;
    }

    /**
     * 发布人: PUBLISHED_BY
     */
    public void setPublishedBy(java.lang.String value){
        if(onPropSet(PROP_ID_publishedBy,value)){
            this._publishedBy = value;
            internalClearRefs(PROP_ID_publishedBy);
            
        }
    }
    
    /**
     * 发布时间: PUBLISH_TIME
     */
    public java.time.LocalDateTime getPublishTime(){
         onPropGet(PROP_ID_publishTime);
         return _publishTime;
    }

    /**
     * 发布时间: PUBLISH_TIME
     */
    public void setPublishTime(java.time.LocalDateTime value){
        if(onPropSet(PROP_ID_publishTime,value)){
            this._publishTime = value;
            internalClearRefs(PROP_ID_publishTime);
            
        }
    }
    
    /**
     * 归档人: ARCHIVED_BY
     */
    public java.lang.String getArchivedBy(){
         onPropGet(PROP_ID_archivedBy);
         return _archivedBy;
    }

    /**
     * 归档人: ARCHIVED_BY
     */
    public void setArchivedBy(java.lang.String value){
        if(onPropSet(PROP_ID_archivedBy,value)){
            this._archivedBy = value;
            internalClearRefs(PROP_ID_archivedBy);
            
        }
    }
    
    /**
     * 归档时间: ARCHIVE_TIME
     */
    public java.time.LocalDateTime getArchiveTime(){
         onPropGet(PROP_ID_archiveTime);
         return _archiveTime;
    }

    /**
     * 归档时间: ARCHIVE_TIME
     */
    public void setArchiveTime(java.time.LocalDateTime value){
        if(onPropSet(PROP_ID_archiveTime,value)){
            this._archiveTime = value;
            internalClearRefs(PROP_ID_archiveTime);
            
        }
    }
    
    /**
     * 是否已废弃: IS_DEPRECATED
     */
    public java.lang.Boolean getIsDeprecated(){
         onPropGet(PROP_ID_isDeprecated);
         return _isDeprecated;
    }

    /**
     * 是否已废弃: IS_DEPRECATED
     */
    public void setIsDeprecated(java.lang.Boolean value){
        if(onPropSet(PROP_ID_isDeprecated,value)){
            this._isDeprecated = value;
            internalClearRefs(PROP_ID_isDeprecated);
            
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
    
    private final OrmEntitySet<io.nop.wf.dao.entity.NopWfDefinitionAuth> _definitionAuths = new OrmEntitySet<>(this, PROP_NAME_definitionAuths,
        io.nop.wf.dao.entity.NopWfDefinitionAuth.PROP_NAME_wfDefinition, null,io.nop.wf.dao.entity.NopWfDefinitionAuth.class);

    /**
     * 工作流定义权限。 refPropName: wfDefinition, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<io.nop.wf.dao.entity.NopWfDefinitionAuth> getDefinitionAuths(){
       return _definitionAuths;
    }
       
}
// resume CPD analysis - CPD-ON
