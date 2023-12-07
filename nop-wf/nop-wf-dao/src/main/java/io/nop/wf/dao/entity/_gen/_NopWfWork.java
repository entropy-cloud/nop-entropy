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

import io.nop.wf.dao.entity.NopWfWork;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  代办工作: nop_wf_work
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116"})
public class _NopWfWork extends DynamicOrmEntity{
    
    /* 工作ID: WORK_ID VARCHAR */
    public static final String PROP_NAME_workId = "workId";
    public static final int PROP_ID_workId = 1;
    
    /* 工作流实例ID: WF_ID VARCHAR */
    public static final String PROP_NAME_wfId = "wfId";
    public static final int PROP_ID_wfId = 2;
    
    /* 工作流步骤ID: STEP_ID VARCHAR */
    public static final String PROP_NAME_stepId = "stepId";
    public static final int PROP_ID_stepId = 3;
    
    /* 工作类型: WORK_TYPE VARCHAR */
    public static final String PROP_NAME_workType = "workType";
    public static final int PROP_ID_workType = 4;
    
    /* 工作标题: TITLE VARCHAR */
    public static final String PROP_NAME_title = "title";
    public static final int PROP_ID_title = 5;
    
    /* 工作链接: LINK_URL VARCHAR */
    public static final String PROP_NAME_linkUrl = "linkUrl";
    public static final int PROP_ID_linkUrl = 6;
    
    /* 状态: STATUS INTEGER */
    public static final String PROP_NAME_status = "status";
    public static final int PROP_ID_status = 7;
    
    /* 拥有者ID: OWNER_ID VARCHAR */
    public static final String PROP_NAME_ownerId = "ownerId";
    public static final int PROP_ID_ownerId = 8;
    
    /* 拥有者姓名: OWNER_NAME VARCHAR */
    public static final String PROP_NAME_ownerName = "ownerName";
    public static final int PROP_ID_ownerName = 9;
    
    /* 调用者ID: CALLER_ID VARCHAR */
    public static final String PROP_NAME_callerId = "callerId";
    public static final int PROP_ID_callerId = 10;
    
    /* 调用者姓名: CALLER_NAME VARCHAR */
    public static final String PROP_NAME_callerName = "callerName";
    public static final int PROP_ID_callerName = 11;
    
    /* 读取时间: READ_TIME TIMESTAMP */
    public static final String PROP_NAME_readTime = "readTime";
    public static final int PROP_ID_readTime = 12;
    
    /* 完成时间: FINISH_TIME TIMESTAMP */
    public static final String PROP_NAME_finishTime = "finishTime";
    public static final int PROP_ID_finishTime = 13;
    
    /* 数据版本: VERSION INTEGER */
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

    
    /* relation: 工作流实例 */
    public static final String PROP_NAME_wfInstance = "wfInstance";
    
    /* relation: 工作流步骤实例 */
    public static final String PROP_NAME_wfStepInstance = "wfStepInstance";
    

    public static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_workId);
    public static final int[] PK_PROP_IDS = new int[]{PROP_ID_workId};

    private static final String[] PROP_ID_TO_NAME = new String[20];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_workId] = PROP_NAME_workId;
          PROP_NAME_TO_ID.put(PROP_NAME_workId, PROP_ID_workId);
      
          PROP_ID_TO_NAME[PROP_ID_wfId] = PROP_NAME_wfId;
          PROP_NAME_TO_ID.put(PROP_NAME_wfId, PROP_ID_wfId);
      
          PROP_ID_TO_NAME[PROP_ID_stepId] = PROP_NAME_stepId;
          PROP_NAME_TO_ID.put(PROP_NAME_stepId, PROP_ID_stepId);
      
          PROP_ID_TO_NAME[PROP_ID_workType] = PROP_NAME_workType;
          PROP_NAME_TO_ID.put(PROP_NAME_workType, PROP_ID_workType);
      
          PROP_ID_TO_NAME[PROP_ID_title] = PROP_NAME_title;
          PROP_NAME_TO_ID.put(PROP_NAME_title, PROP_ID_title);
      
          PROP_ID_TO_NAME[PROP_ID_linkUrl] = PROP_NAME_linkUrl;
          PROP_NAME_TO_ID.put(PROP_NAME_linkUrl, PROP_ID_linkUrl);
      
          PROP_ID_TO_NAME[PROP_ID_status] = PROP_NAME_status;
          PROP_NAME_TO_ID.put(PROP_NAME_status, PROP_ID_status);
      
          PROP_ID_TO_NAME[PROP_ID_ownerId] = PROP_NAME_ownerId;
          PROP_NAME_TO_ID.put(PROP_NAME_ownerId, PROP_ID_ownerId);
      
          PROP_ID_TO_NAME[PROP_ID_ownerName] = PROP_NAME_ownerName;
          PROP_NAME_TO_ID.put(PROP_NAME_ownerName, PROP_ID_ownerName);
      
          PROP_ID_TO_NAME[PROP_ID_callerId] = PROP_NAME_callerId;
          PROP_NAME_TO_ID.put(PROP_NAME_callerId, PROP_ID_callerId);
      
          PROP_ID_TO_NAME[PROP_ID_callerName] = PROP_NAME_callerName;
          PROP_NAME_TO_ID.put(PROP_NAME_callerName, PROP_ID_callerName);
      
          PROP_ID_TO_NAME[PROP_ID_readTime] = PROP_NAME_readTime;
          PROP_NAME_TO_ID.put(PROP_NAME_readTime, PROP_ID_readTime);
      
          PROP_ID_TO_NAME[PROP_ID_finishTime] = PROP_NAME_finishTime;
          PROP_NAME_TO_ID.put(PROP_NAME_finishTime, PROP_ID_finishTime);
      
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

    
    /* 工作ID: WORK_ID */
    private java.lang.String _workId;
    
    /* 工作流实例ID: WF_ID */
    private java.lang.String _wfId;
    
    /* 工作流步骤ID: STEP_ID */
    private java.lang.String _stepId;
    
    /* 工作类型: WORK_TYPE */
    private java.lang.String _workType;
    
    /* 工作标题: TITLE */
    private java.lang.String _title;
    
    /* 工作链接: LINK_URL */
    private java.lang.String _linkUrl;
    
    /* 状态: STATUS */
    private java.lang.Integer _status;
    
    /* 拥有者ID: OWNER_ID */
    private java.lang.String _ownerId;
    
    /* 拥有者姓名: OWNER_NAME */
    private java.lang.String _ownerName;
    
    /* 调用者ID: CALLER_ID */
    private java.lang.String _callerId;
    
    /* 调用者姓名: CALLER_NAME */
    private java.lang.String _callerName;
    
    /* 读取时间: READ_TIME */
    private java.sql.Timestamp _readTime;
    
    /* 完成时间: FINISH_TIME */
    private java.sql.Timestamp _finishTime;
    
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
    

    public _NopWfWork(){
    }

    protected NopWfWork newInstance(){
       return new NopWfWork();
    }

    @Override
    public NopWfWork cloneInstance() {
        NopWfWork entity = newInstance();
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
      return "io.nop.wf.dao.entity.NopWfWork";
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
    
        return buildSimpleId(PROP_ID_workId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_workId;
          
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
        
            case PROP_ID_workId:
               return getWorkId();
        
            case PROP_ID_wfId:
               return getWfId();
        
            case PROP_ID_stepId:
               return getStepId();
        
            case PROP_ID_workType:
               return getWorkType();
        
            case PROP_ID_title:
               return getTitle();
        
            case PROP_ID_linkUrl:
               return getLinkUrl();
        
            case PROP_ID_status:
               return getStatus();
        
            case PROP_ID_ownerId:
               return getOwnerId();
        
            case PROP_ID_ownerName:
               return getOwnerName();
        
            case PROP_ID_callerId:
               return getCallerId();
        
            case PROP_ID_callerName:
               return getCallerName();
        
            case PROP_ID_readTime:
               return getReadTime();
        
            case PROP_ID_finishTime:
               return getFinishTime();
        
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
        
            case PROP_ID_workId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_workId));
               }
               setWorkId(typedValue);
               break;
            }
        
            case PROP_ID_wfId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_wfId));
               }
               setWfId(typedValue);
               break;
            }
        
            case PROP_ID_stepId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_stepId));
               }
               setStepId(typedValue);
               break;
            }
        
            case PROP_ID_workType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_workType));
               }
               setWorkType(typedValue);
               break;
            }
        
            case PROP_ID_title:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_title));
               }
               setTitle(typedValue);
               break;
            }
        
            case PROP_ID_linkUrl:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_linkUrl));
               }
               setLinkUrl(typedValue);
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
        
            case PROP_ID_ownerId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_ownerId));
               }
               setOwnerId(typedValue);
               break;
            }
        
            case PROP_ID_ownerName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_ownerName));
               }
               setOwnerName(typedValue);
               break;
            }
        
            case PROP_ID_callerId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_callerId));
               }
               setCallerId(typedValue);
               break;
            }
        
            case PROP_ID_callerName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_callerName));
               }
               setCallerName(typedValue);
               break;
            }
        
            case PROP_ID_readTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_readTime));
               }
               setReadTime(typedValue);
               break;
            }
        
            case PROP_ID_finishTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_finishTime));
               }
               setFinishTime(typedValue);
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
        
            case PROP_ID_workId:{
               onInitProp(propId);
               this._workId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_wfId:{
               onInitProp(propId);
               this._wfId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_stepId:{
               onInitProp(propId);
               this._stepId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_workType:{
               onInitProp(propId);
               this._workType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_title:{
               onInitProp(propId);
               this._title = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_linkUrl:{
               onInitProp(propId);
               this._linkUrl = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_status:{
               onInitProp(propId);
               this._status = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_ownerId:{
               onInitProp(propId);
               this._ownerId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_ownerName:{
               onInitProp(propId);
               this._ownerName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_callerId:{
               onInitProp(propId);
               this._callerId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_callerName:{
               onInitProp(propId);
               this._callerName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_readTime:{
               onInitProp(propId);
               this._readTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_finishTime:{
               onInitProp(propId);
               this._finishTime = (java.sql.Timestamp)value;
               
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
     * 工作ID: WORK_ID
     */
    public java.lang.String getWorkId(){
         onPropGet(PROP_ID_workId);
         return _workId;
    }

    /**
     * 工作ID: WORK_ID
     */
    public void setWorkId(java.lang.String value){
        if(onPropSet(PROP_ID_workId,value)){
            this._workId = value;
            internalClearRefs(PROP_ID_workId);
            orm_id();
        }
    }
    
    /**
     * 工作流实例ID: WF_ID
     */
    public java.lang.String getWfId(){
         onPropGet(PROP_ID_wfId);
         return _wfId;
    }

    /**
     * 工作流实例ID: WF_ID
     */
    public void setWfId(java.lang.String value){
        if(onPropSet(PROP_ID_wfId,value)){
            this._wfId = value;
            internalClearRefs(PROP_ID_wfId);
            
        }
    }
    
    /**
     * 工作流步骤ID: STEP_ID
     */
    public java.lang.String getStepId(){
         onPropGet(PROP_ID_stepId);
         return _stepId;
    }

    /**
     * 工作流步骤ID: STEP_ID
     */
    public void setStepId(java.lang.String value){
        if(onPropSet(PROP_ID_stepId,value)){
            this._stepId = value;
            internalClearRefs(PROP_ID_stepId);
            
        }
    }
    
    /**
     * 工作类型: WORK_TYPE
     */
    public java.lang.String getWorkType(){
         onPropGet(PROP_ID_workType);
         return _workType;
    }

    /**
     * 工作类型: WORK_TYPE
     */
    public void setWorkType(java.lang.String value){
        if(onPropSet(PROP_ID_workType,value)){
            this._workType = value;
            internalClearRefs(PROP_ID_workType);
            
        }
    }
    
    /**
     * 工作标题: TITLE
     */
    public java.lang.String getTitle(){
         onPropGet(PROP_ID_title);
         return _title;
    }

    /**
     * 工作标题: TITLE
     */
    public void setTitle(java.lang.String value){
        if(onPropSet(PROP_ID_title,value)){
            this._title = value;
            internalClearRefs(PROP_ID_title);
            
        }
    }
    
    /**
     * 工作链接: LINK_URL
     */
    public java.lang.String getLinkUrl(){
         onPropGet(PROP_ID_linkUrl);
         return _linkUrl;
    }

    /**
     * 工作链接: LINK_URL
     */
    public void setLinkUrl(java.lang.String value){
        if(onPropSet(PROP_ID_linkUrl,value)){
            this._linkUrl = value;
            internalClearRefs(PROP_ID_linkUrl);
            
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
     * 拥有者ID: OWNER_ID
     */
    public java.lang.String getOwnerId(){
         onPropGet(PROP_ID_ownerId);
         return _ownerId;
    }

    /**
     * 拥有者ID: OWNER_ID
     */
    public void setOwnerId(java.lang.String value){
        if(onPropSet(PROP_ID_ownerId,value)){
            this._ownerId = value;
            internalClearRefs(PROP_ID_ownerId);
            
        }
    }
    
    /**
     * 拥有者姓名: OWNER_NAME
     */
    public java.lang.String getOwnerName(){
         onPropGet(PROP_ID_ownerName);
         return _ownerName;
    }

    /**
     * 拥有者姓名: OWNER_NAME
     */
    public void setOwnerName(java.lang.String value){
        if(onPropSet(PROP_ID_ownerName,value)){
            this._ownerName = value;
            internalClearRefs(PROP_ID_ownerName);
            
        }
    }
    
    /**
     * 调用者ID: CALLER_ID
     */
    public java.lang.String getCallerId(){
         onPropGet(PROP_ID_callerId);
         return _callerId;
    }

    /**
     * 调用者ID: CALLER_ID
     */
    public void setCallerId(java.lang.String value){
        if(onPropSet(PROP_ID_callerId,value)){
            this._callerId = value;
            internalClearRefs(PROP_ID_callerId);
            
        }
    }
    
    /**
     * 调用者姓名: CALLER_NAME
     */
    public java.lang.String getCallerName(){
         onPropGet(PROP_ID_callerName);
         return _callerName;
    }

    /**
     * 调用者姓名: CALLER_NAME
     */
    public void setCallerName(java.lang.String value){
        if(onPropSet(PROP_ID_callerName,value)){
            this._callerName = value;
            internalClearRefs(PROP_ID_callerName);
            
        }
    }
    
    /**
     * 读取时间: READ_TIME
     */
    public java.sql.Timestamp getReadTime(){
         onPropGet(PROP_ID_readTime);
         return _readTime;
    }

    /**
     * 读取时间: READ_TIME
     */
    public void setReadTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_readTime,value)){
            this._readTime = value;
            internalClearRefs(PROP_ID_readTime);
            
        }
    }
    
    /**
     * 完成时间: FINISH_TIME
     */
    public java.sql.Timestamp getFinishTime(){
         onPropGet(PROP_ID_finishTime);
         return _finishTime;
    }

    /**
     * 完成时间: FINISH_TIME
     */
    public void setFinishTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_finishTime,value)){
            this._finishTime = value;
            internalClearRefs(PROP_ID_finishTime);
            
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
     * 工作流实例
     */
    public io.nop.wf.dao.entity.NopWfInstance getWfInstance(){
       return (io.nop.wf.dao.entity.NopWfInstance)internalGetRefEntity(PROP_NAME_wfInstance);
    }

    public void setWfInstance(io.nop.wf.dao.entity.NopWfInstance refEntity){
       if(refEntity == null){
         
         this.setWfId(null);
         
       }else{
          internalSetRefEntity(PROP_NAME_wfInstance, refEntity,()->{
             
                    this.setWfId(refEntity.getWfId());
                 
          });
       }
    }
       
    /**
     * 工作流步骤实例
     */
    public io.nop.wf.dao.entity.NopWfStepInstance getWfStepInstance(){
       return (io.nop.wf.dao.entity.NopWfStepInstance)internalGetRefEntity(PROP_NAME_wfStepInstance);
    }

    public void setWfStepInstance(io.nop.wf.dao.entity.NopWfStepInstance refEntity){
       if(refEntity == null){
         
         this.setStepId(null);
         
       }else{
          internalSetRefEntity(PROP_NAME_wfStepInstance, refEntity,()->{
             
                    this.setStepId(refEntity.getStepId());
                 
          });
       }
    }
       
}
// resume CPD analysis - CPD-ON
