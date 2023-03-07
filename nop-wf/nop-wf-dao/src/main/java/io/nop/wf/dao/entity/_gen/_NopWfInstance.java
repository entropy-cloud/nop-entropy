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

import io.nop.wf.dao.entity.NopWfInstance;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  工作流模型定义: nop_wf_instance
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public class _NopWfInstance extends DynamicOrmEntity{
    
    /* 主键: WF_ID VARCHAR */
    public static final String PROP_NAME_wfId = "wfId";
    public static final int PROP_ID_wfId = 1;
    
    /* 工作流名称: WF_NAME VARCHAR */
    public static final String PROP_NAME_wfName = "wfName";
    public static final int PROP_ID_wfName = 2;
    
    /* 工作流版本: WF_VERSION VARCHAR */
    public static final String PROP_NAME_wfVersion = "wfVersion";
    public static final int PROP_ID_wfVersion = 3;
    
    /* 工作流参数: WF_PARAMS VARCHAR */
    public static final String PROP_NAME_wfParams = "wfParams";
    public static final int PROP_ID_wfParams = 4;
    
    /* 实例标题: TITLE VARCHAR */
    public static final String PROP_NAME_title = "title";
    public static final int PROP_ID_title = 5;
    
    /* 状态: STATUS INTEGER */
    public static final String PROP_NAME_status = "status";
    public static final int PROP_ID_status = 6;
    
    /* 应用状态: APP_STATE VARCHAR */
    public static final String PROP_NAME_appState = "appState";
    public static final int PROP_ID_appState = 7;
    
    /* 启动时间: START_TIME TIMESTAMP */
    public static final String PROP_NAME_startTime = "startTime";
    public static final int PROP_ID_startTime = 8;
    
    /* 结束时间: END_TIME TIMESTAMP */
    public static final String PROP_NAME_endTime = "endTime";
    public static final int PROP_ID_endTime = 9;
    
    /* 暂停时间: SUSPEND_TIME TIMESTAMP */
    public static final String PROP_NAME_suspendTime = "suspendTime";
    public static final int PROP_ID_suspendTime = 10;
    
    /* 完成时限: DUE_TIME TIMESTAMP */
    public static final String PROP_NAME_dueTime = "dueTime";
    public static final int PROP_ID_dueTime = 11;
    
    /* 业务唯一键: BIZ_KEY VARCHAR */
    public static final String PROP_NAME_bizKey = "bizKey";
    public static final int PROP_ID_bizKey = 12;
    
    /* 业务对象名: BIZ_OBJ_NAME VARCHAR */
    public static final String PROP_NAME_bizObjName = "bizObjName";
    public static final int PROP_ID_bizObjName = 13;
    
    /* 业务对象ID: BIZ_OBJ_ID VARCHAR */
    public static final String PROP_NAME_bizObjId = "bizObjId";
    public static final int PROP_ID_bizObjId = 14;
    
    /* 父工作流名称: PARENT_WF_NAME VARCHAR */
    public static final String PROP_NAME_parentWfName = "parentWfName";
    public static final int PROP_ID_parentWfName = 15;
    
    /* 父流程版本: PARENT_WF_VERSION VARCHAR */
    public static final String PROP_NAME_parentWfVersion = "parentWfVersion";
    public static final int PROP_ID_parentWfVersion = 16;
    
    /* 父流程ID: PARENT_WF_ID VARCHAR */
    public static final String PROP_NAME_parentWfId = "parentWfId";
    public static final int PROP_ID_parentWfId = 17;
    
    /* 父流程步骤ID: PARENT_STEP_ID VARCHAR */
    public static final String PROP_NAME_parentStepId = "parentStepId";
    public static final int PROP_ID_parentStepId = 18;
    
    /* 启动人ID: STARTER_ID VARCHAR */
    public static final String PROP_NAME_starterId = "starterId";
    public static final int PROP_ID_starterId = 19;
    
    /* 启动人: STARTER_NAME VARCHAR */
    public static final String PROP_NAME_starterName = "starterName";
    public static final int PROP_ID_starterName = 20;
    
    /* 启动人单位ID: STARTER_DEPT_ID VARCHAR */
    public static final String PROP_NAME_starterDeptId = "starterDeptId";
    public static final int PROP_ID_starterDeptId = 21;
    
    /* 取消人ID: CANCELLER_ID VARCHAR */
    public static final String PROP_NAME_cancellerId = "cancellerId";
    public static final int PROP_ID_cancellerId = 22;
    
    /* 取消人: CANCELLER_NAME VARCHAR */
    public static final String PROP_NAME_cancellerName = "cancellerName";
    public static final int PROP_ID_cancellerName = 23;
    
    /* 暂停人ID: SUSPENDER_ID VARCHAR */
    public static final String PROP_NAME_suspenderId = "suspenderId";
    public static final int PROP_ID_suspenderId = 24;
    
    /* 暂停人: SUSPENDER_NAME VARCHAR */
    public static final String PROP_NAME_suspenderName = "suspenderName";
    public static final int PROP_ID_suspenderName = 25;
    
    /* 管理者类型: MANAGER_TYPE VARCHAR */
    public static final String PROP_NAME_managerType = "managerType";
    public static final int PROP_ID_managerType = 26;
    
    /* 管理者单位ID: MANAGER_DEPT_ID VARCHAR */
    public static final String PROP_NAME_managerDeptId = "managerDeptId";
    public static final int PROP_ID_managerDeptId = 27;
    
    /* 管理者: MANAGER_NAME VARCHAR */
    public static final String PROP_NAME_managerName = "managerName";
    public static final int PROP_ID_managerName = 28;
    
    /* 管理者ID: MANAGER_ID VARCHAR */
    public static final String PROP_NAME_managerId = "managerId";
    public static final int PROP_ID_managerId = 29;
    
    /* 数据版本: VERSION INTEGER */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 30;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 31;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 32;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 33;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 34;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 35;
    

    private static int _PROP_ID_BOUND = 36;

    
    /* relation: 父流程 */
    public static final String PROP_NAME_wfInstance = "wfInstance";
    

    public static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_wfId);
    public static final int[] PK_PROP_IDS = new int[]{PROP_ID_wfId};

    private static final String[] PROP_ID_TO_NAME = new String[36];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_wfId] = PROP_NAME_wfId;
          PROP_NAME_TO_ID.put(PROP_NAME_wfId, PROP_ID_wfId);
      
          PROP_ID_TO_NAME[PROP_ID_wfName] = PROP_NAME_wfName;
          PROP_NAME_TO_ID.put(PROP_NAME_wfName, PROP_ID_wfName);
      
          PROP_ID_TO_NAME[PROP_ID_wfVersion] = PROP_NAME_wfVersion;
          PROP_NAME_TO_ID.put(PROP_NAME_wfVersion, PROP_ID_wfVersion);
      
          PROP_ID_TO_NAME[PROP_ID_wfParams] = PROP_NAME_wfParams;
          PROP_NAME_TO_ID.put(PROP_NAME_wfParams, PROP_ID_wfParams);
      
          PROP_ID_TO_NAME[PROP_ID_title] = PROP_NAME_title;
          PROP_NAME_TO_ID.put(PROP_NAME_title, PROP_ID_title);
      
          PROP_ID_TO_NAME[PROP_ID_status] = PROP_NAME_status;
          PROP_NAME_TO_ID.put(PROP_NAME_status, PROP_ID_status);
      
          PROP_ID_TO_NAME[PROP_ID_appState] = PROP_NAME_appState;
          PROP_NAME_TO_ID.put(PROP_NAME_appState, PROP_ID_appState);
      
          PROP_ID_TO_NAME[PROP_ID_startTime] = PROP_NAME_startTime;
          PROP_NAME_TO_ID.put(PROP_NAME_startTime, PROP_ID_startTime);
      
          PROP_ID_TO_NAME[PROP_ID_endTime] = PROP_NAME_endTime;
          PROP_NAME_TO_ID.put(PROP_NAME_endTime, PROP_ID_endTime);
      
          PROP_ID_TO_NAME[PROP_ID_suspendTime] = PROP_NAME_suspendTime;
          PROP_NAME_TO_ID.put(PROP_NAME_suspendTime, PROP_ID_suspendTime);
      
          PROP_ID_TO_NAME[PROP_ID_dueTime] = PROP_NAME_dueTime;
          PROP_NAME_TO_ID.put(PROP_NAME_dueTime, PROP_ID_dueTime);
      
          PROP_ID_TO_NAME[PROP_ID_bizKey] = PROP_NAME_bizKey;
          PROP_NAME_TO_ID.put(PROP_NAME_bizKey, PROP_ID_bizKey);
      
          PROP_ID_TO_NAME[PROP_ID_bizObjName] = PROP_NAME_bizObjName;
          PROP_NAME_TO_ID.put(PROP_NAME_bizObjName, PROP_ID_bizObjName);
      
          PROP_ID_TO_NAME[PROP_ID_bizObjId] = PROP_NAME_bizObjId;
          PROP_NAME_TO_ID.put(PROP_NAME_bizObjId, PROP_ID_bizObjId);
      
          PROP_ID_TO_NAME[PROP_ID_parentWfName] = PROP_NAME_parentWfName;
          PROP_NAME_TO_ID.put(PROP_NAME_parentWfName, PROP_ID_parentWfName);
      
          PROP_ID_TO_NAME[PROP_ID_parentWfVersion] = PROP_NAME_parentWfVersion;
          PROP_NAME_TO_ID.put(PROP_NAME_parentWfVersion, PROP_ID_parentWfVersion);
      
          PROP_ID_TO_NAME[PROP_ID_parentWfId] = PROP_NAME_parentWfId;
          PROP_NAME_TO_ID.put(PROP_NAME_parentWfId, PROP_ID_parentWfId);
      
          PROP_ID_TO_NAME[PROP_ID_parentStepId] = PROP_NAME_parentStepId;
          PROP_NAME_TO_ID.put(PROP_NAME_parentStepId, PROP_ID_parentStepId);
      
          PROP_ID_TO_NAME[PROP_ID_starterId] = PROP_NAME_starterId;
          PROP_NAME_TO_ID.put(PROP_NAME_starterId, PROP_ID_starterId);
      
          PROP_ID_TO_NAME[PROP_ID_starterName] = PROP_NAME_starterName;
          PROP_NAME_TO_ID.put(PROP_NAME_starterName, PROP_ID_starterName);
      
          PROP_ID_TO_NAME[PROP_ID_starterDeptId] = PROP_NAME_starterDeptId;
          PROP_NAME_TO_ID.put(PROP_NAME_starterDeptId, PROP_ID_starterDeptId);
      
          PROP_ID_TO_NAME[PROP_ID_cancellerId] = PROP_NAME_cancellerId;
          PROP_NAME_TO_ID.put(PROP_NAME_cancellerId, PROP_ID_cancellerId);
      
          PROP_ID_TO_NAME[PROP_ID_cancellerName] = PROP_NAME_cancellerName;
          PROP_NAME_TO_ID.put(PROP_NAME_cancellerName, PROP_ID_cancellerName);
      
          PROP_ID_TO_NAME[PROP_ID_suspenderId] = PROP_NAME_suspenderId;
          PROP_NAME_TO_ID.put(PROP_NAME_suspenderId, PROP_ID_suspenderId);
      
          PROP_ID_TO_NAME[PROP_ID_suspenderName] = PROP_NAME_suspenderName;
          PROP_NAME_TO_ID.put(PROP_NAME_suspenderName, PROP_ID_suspenderName);
      
          PROP_ID_TO_NAME[PROP_ID_managerType] = PROP_NAME_managerType;
          PROP_NAME_TO_ID.put(PROP_NAME_managerType, PROP_ID_managerType);
      
          PROP_ID_TO_NAME[PROP_ID_managerDeptId] = PROP_NAME_managerDeptId;
          PROP_NAME_TO_ID.put(PROP_NAME_managerDeptId, PROP_ID_managerDeptId);
      
          PROP_ID_TO_NAME[PROP_ID_managerName] = PROP_NAME_managerName;
          PROP_NAME_TO_ID.put(PROP_NAME_managerName, PROP_ID_managerName);
      
          PROP_ID_TO_NAME[PROP_ID_managerId] = PROP_NAME_managerId;
          PROP_NAME_TO_ID.put(PROP_NAME_managerId, PROP_ID_managerId);
      
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

    
    /* 主键: WF_ID */
    private java.lang.String _wfId;
    
    /* 工作流名称: WF_NAME */
    private java.lang.String _wfName;
    
    /* 工作流版本: WF_VERSION */
    private java.lang.String _wfVersion;
    
    /* 工作流参数: WF_PARAMS */
    private java.lang.String _wfParams;
    
    /* 实例标题: TITLE */
    private java.lang.String _title;
    
    /* 状态: STATUS */
    private java.lang.Integer _status;
    
    /* 应用状态: APP_STATE */
    private java.lang.String _appState;
    
    /* 启动时间: START_TIME */
    private java.sql.Timestamp _startTime;
    
    /* 结束时间: END_TIME */
    private java.sql.Timestamp _endTime;
    
    /* 暂停时间: SUSPEND_TIME */
    private java.sql.Timestamp _suspendTime;
    
    /* 完成时限: DUE_TIME */
    private java.sql.Timestamp _dueTime;
    
    /* 业务唯一键: BIZ_KEY */
    private java.lang.String _bizKey;
    
    /* 业务对象名: BIZ_OBJ_NAME */
    private java.lang.String _bizObjName;
    
    /* 业务对象ID: BIZ_OBJ_ID */
    private java.lang.String _bizObjId;
    
    /* 父工作流名称: PARENT_WF_NAME */
    private java.lang.String _parentWfName;
    
    /* 父流程版本: PARENT_WF_VERSION */
    private java.lang.String _parentWfVersion;
    
    /* 父流程ID: PARENT_WF_ID */
    private java.lang.String _parentWfId;
    
    /* 父流程步骤ID: PARENT_STEP_ID */
    private java.lang.String _parentStepId;
    
    /* 启动人ID: STARTER_ID */
    private java.lang.String _starterId;
    
    /* 启动人: STARTER_NAME */
    private java.lang.String _starterName;
    
    /* 启动人单位ID: STARTER_DEPT_ID */
    private java.lang.String _starterDeptId;
    
    /* 取消人ID: CANCELLER_ID */
    private java.lang.String _cancellerId;
    
    /* 取消人: CANCELLER_NAME */
    private java.lang.String _cancellerName;
    
    /* 暂停人ID: SUSPENDER_ID */
    private java.lang.String _suspenderId;
    
    /* 暂停人: SUSPENDER_NAME */
    private java.lang.String _suspenderName;
    
    /* 管理者类型: MANAGER_TYPE */
    private java.lang.String _managerType;
    
    /* 管理者单位ID: MANAGER_DEPT_ID */
    private java.lang.String _managerDeptId;
    
    /* 管理者: MANAGER_NAME */
    private java.lang.String _managerName;
    
    /* 管理者ID: MANAGER_ID */
    private java.lang.String _managerId;
    
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
    

    public _NopWfInstance(){
    }

    protected NopWfInstance newInstance(){
       return new NopWfInstance();
    }

    @Override
    public NopWfInstance cloneInstance() {
        NopWfInstance entity = newInstance();
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
      return "io.nop.wf.dao.entity.NopWfInstance";
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
    
        return buildSimpleId(PROP_ID_wfId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_wfId;
          
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
        
            case PROP_ID_wfId:
               return getWfId();
        
            case PROP_ID_wfName:
               return getWfName();
        
            case PROP_ID_wfVersion:
               return getWfVersion();
        
            case PROP_ID_wfParams:
               return getWfParams();
        
            case PROP_ID_title:
               return getTitle();
        
            case PROP_ID_status:
               return getStatus();
        
            case PROP_ID_appState:
               return getAppState();
        
            case PROP_ID_startTime:
               return getStartTime();
        
            case PROP_ID_endTime:
               return getEndTime();
        
            case PROP_ID_suspendTime:
               return getSuspendTime();
        
            case PROP_ID_dueTime:
               return getDueTime();
        
            case PROP_ID_bizKey:
               return getBizKey();
        
            case PROP_ID_bizObjName:
               return getBizObjName();
        
            case PROP_ID_bizObjId:
               return getBizObjId();
        
            case PROP_ID_parentWfName:
               return getParentWfName();
        
            case PROP_ID_parentWfVersion:
               return getParentWfVersion();
        
            case PROP_ID_parentWfId:
               return getParentWfId();
        
            case PROP_ID_parentStepId:
               return getParentStepId();
        
            case PROP_ID_starterId:
               return getStarterId();
        
            case PROP_ID_starterName:
               return getStarterName();
        
            case PROP_ID_starterDeptId:
               return getStarterDeptId();
        
            case PROP_ID_cancellerId:
               return getCancellerId();
        
            case PROP_ID_cancellerName:
               return getCancellerName();
        
            case PROP_ID_suspenderId:
               return getSuspenderId();
        
            case PROP_ID_suspenderName:
               return getSuspenderName();
        
            case PROP_ID_managerType:
               return getManagerType();
        
            case PROP_ID_managerDeptId:
               return getManagerDeptId();
        
            case PROP_ID_managerName:
               return getManagerName();
        
            case PROP_ID_managerId:
               return getManagerId();
        
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
        
            case PROP_ID_wfId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_wfId));
               }
               setWfId(typedValue);
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
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_wfVersion));
               }
               setWfVersion(typedValue);
               break;
            }
        
            case PROP_ID_wfParams:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_wfParams));
               }
               setWfParams(typedValue);
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
        
            case PROP_ID_status:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_status));
               }
               setStatus(typedValue);
               break;
            }
        
            case PROP_ID_appState:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_appState));
               }
               setAppState(typedValue);
               break;
            }
        
            case PROP_ID_startTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_startTime));
               }
               setStartTime(typedValue);
               break;
            }
        
            case PROP_ID_endTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_endTime));
               }
               setEndTime(typedValue);
               break;
            }
        
            case PROP_ID_suspendTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_suspendTime));
               }
               setSuspendTime(typedValue);
               break;
            }
        
            case PROP_ID_dueTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_dueTime));
               }
               setDueTime(typedValue);
               break;
            }
        
            case PROP_ID_bizKey:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_bizKey));
               }
               setBizKey(typedValue);
               break;
            }
        
            case PROP_ID_bizObjName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_bizObjName));
               }
               setBizObjName(typedValue);
               break;
            }
        
            case PROP_ID_bizObjId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_bizObjId));
               }
               setBizObjId(typedValue);
               break;
            }
        
            case PROP_ID_parentWfName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_parentWfName));
               }
               setParentWfName(typedValue);
               break;
            }
        
            case PROP_ID_parentWfVersion:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_parentWfVersion));
               }
               setParentWfVersion(typedValue);
               break;
            }
        
            case PROP_ID_parentWfId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_parentWfId));
               }
               setParentWfId(typedValue);
               break;
            }
        
            case PROP_ID_parentStepId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_parentStepId));
               }
               setParentStepId(typedValue);
               break;
            }
        
            case PROP_ID_starterId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_starterId));
               }
               setStarterId(typedValue);
               break;
            }
        
            case PROP_ID_starterName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_starterName));
               }
               setStarterName(typedValue);
               break;
            }
        
            case PROP_ID_starterDeptId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_starterDeptId));
               }
               setStarterDeptId(typedValue);
               break;
            }
        
            case PROP_ID_cancellerId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_cancellerId));
               }
               setCancellerId(typedValue);
               break;
            }
        
            case PROP_ID_cancellerName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_cancellerName));
               }
               setCancellerName(typedValue);
               break;
            }
        
            case PROP_ID_suspenderId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_suspenderId));
               }
               setSuspenderId(typedValue);
               break;
            }
        
            case PROP_ID_suspenderName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_suspenderName));
               }
               setSuspenderName(typedValue);
               break;
            }
        
            case PROP_ID_managerType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_managerType));
               }
               setManagerType(typedValue);
               break;
            }
        
            case PROP_ID_managerDeptId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_managerDeptId));
               }
               setManagerDeptId(typedValue);
               break;
            }
        
            case PROP_ID_managerName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_managerName));
               }
               setManagerName(typedValue);
               break;
            }
        
            case PROP_ID_managerId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_managerId));
               }
               setManagerId(typedValue);
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
        
            case PROP_ID_wfId:{
               onInitProp(propId);
               this._wfId = (java.lang.String)value;
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
               this._wfVersion = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_wfParams:{
               onInitProp(propId);
               this._wfParams = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_title:{
               onInitProp(propId);
               this._title = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_status:{
               onInitProp(propId);
               this._status = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_appState:{
               onInitProp(propId);
               this._appState = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_startTime:{
               onInitProp(propId);
               this._startTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_endTime:{
               onInitProp(propId);
               this._endTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_suspendTime:{
               onInitProp(propId);
               this._suspendTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_dueTime:{
               onInitProp(propId);
               this._dueTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_bizKey:{
               onInitProp(propId);
               this._bizKey = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_bizObjName:{
               onInitProp(propId);
               this._bizObjName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_bizObjId:{
               onInitProp(propId);
               this._bizObjId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_parentWfName:{
               onInitProp(propId);
               this._parentWfName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_parentWfVersion:{
               onInitProp(propId);
               this._parentWfVersion = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_parentWfId:{
               onInitProp(propId);
               this._parentWfId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_parentStepId:{
               onInitProp(propId);
               this._parentStepId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_starterId:{
               onInitProp(propId);
               this._starterId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_starterName:{
               onInitProp(propId);
               this._starterName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_starterDeptId:{
               onInitProp(propId);
               this._starterDeptId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_cancellerId:{
               onInitProp(propId);
               this._cancellerId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_cancellerName:{
               onInitProp(propId);
               this._cancellerName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_suspenderId:{
               onInitProp(propId);
               this._suspenderId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_suspenderName:{
               onInitProp(propId);
               this._suspenderName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_managerType:{
               onInitProp(propId);
               this._managerType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_managerDeptId:{
               onInitProp(propId);
               this._managerDeptId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_managerName:{
               onInitProp(propId);
               this._managerName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_managerId:{
               onInitProp(propId);
               this._managerId = (java.lang.String)value;
               
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
     * 主键: WF_ID
     */
    public java.lang.String getWfId(){
         onPropGet(PROP_ID_wfId);
         return _wfId;
    }

    /**
     * 主键: WF_ID
     */
    public void setWfId(java.lang.String value){
        if(onPropSet(PROP_ID_wfId,value)){
            this._wfId = value;
            internalClearRefs(PROP_ID_wfId);
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
    public java.lang.String getWfVersion(){
         onPropGet(PROP_ID_wfVersion);
         return _wfVersion;
    }

    /**
     * 工作流版本: WF_VERSION
     */
    public void setWfVersion(java.lang.String value){
        if(onPropSet(PROP_ID_wfVersion,value)){
            this._wfVersion = value;
            internalClearRefs(PROP_ID_wfVersion);
            
        }
    }
    
    /**
     * 工作流参数: WF_PARAMS
     */
    public java.lang.String getWfParams(){
         onPropGet(PROP_ID_wfParams);
         return _wfParams;
    }

    /**
     * 工作流参数: WF_PARAMS
     */
    public void setWfParams(java.lang.String value){
        if(onPropSet(PROP_ID_wfParams,value)){
            this._wfParams = value;
            internalClearRefs(PROP_ID_wfParams);
            
        }
    }
    
    /**
     * 实例标题: TITLE
     */
    public java.lang.String getTitle(){
         onPropGet(PROP_ID_title);
         return _title;
    }

    /**
     * 实例标题: TITLE
     */
    public void setTitle(java.lang.String value){
        if(onPropSet(PROP_ID_title,value)){
            this._title = value;
            internalClearRefs(PROP_ID_title);
            
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
     * 应用状态: APP_STATE
     */
    public java.lang.String getAppState(){
         onPropGet(PROP_ID_appState);
         return _appState;
    }

    /**
     * 应用状态: APP_STATE
     */
    public void setAppState(java.lang.String value){
        if(onPropSet(PROP_ID_appState,value)){
            this._appState = value;
            internalClearRefs(PROP_ID_appState);
            
        }
    }
    
    /**
     * 启动时间: START_TIME
     */
    public java.sql.Timestamp getStartTime(){
         onPropGet(PROP_ID_startTime);
         return _startTime;
    }

    /**
     * 启动时间: START_TIME
     */
    public void setStartTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_startTime,value)){
            this._startTime = value;
            internalClearRefs(PROP_ID_startTime);
            
        }
    }
    
    /**
     * 结束时间: END_TIME
     */
    public java.sql.Timestamp getEndTime(){
         onPropGet(PROP_ID_endTime);
         return _endTime;
    }

    /**
     * 结束时间: END_TIME
     */
    public void setEndTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_endTime,value)){
            this._endTime = value;
            internalClearRefs(PROP_ID_endTime);
            
        }
    }
    
    /**
     * 暂停时间: SUSPEND_TIME
     */
    public java.sql.Timestamp getSuspendTime(){
         onPropGet(PROP_ID_suspendTime);
         return _suspendTime;
    }

    /**
     * 暂停时间: SUSPEND_TIME
     */
    public void setSuspendTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_suspendTime,value)){
            this._suspendTime = value;
            internalClearRefs(PROP_ID_suspendTime);
            
        }
    }
    
    /**
     * 完成时限: DUE_TIME
     */
    public java.sql.Timestamp getDueTime(){
         onPropGet(PROP_ID_dueTime);
         return _dueTime;
    }

    /**
     * 完成时限: DUE_TIME
     */
    public void setDueTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_dueTime,value)){
            this._dueTime = value;
            internalClearRefs(PROP_ID_dueTime);
            
        }
    }
    
    /**
     * 业务唯一键: BIZ_KEY
     */
    public java.lang.String getBizKey(){
         onPropGet(PROP_ID_bizKey);
         return _bizKey;
    }

    /**
     * 业务唯一键: BIZ_KEY
     */
    public void setBizKey(java.lang.String value){
        if(onPropSet(PROP_ID_bizKey,value)){
            this._bizKey = value;
            internalClearRefs(PROP_ID_bizKey);
            
        }
    }
    
    /**
     * 业务对象名: BIZ_OBJ_NAME
     */
    public java.lang.String getBizObjName(){
         onPropGet(PROP_ID_bizObjName);
         return _bizObjName;
    }

    /**
     * 业务对象名: BIZ_OBJ_NAME
     */
    public void setBizObjName(java.lang.String value){
        if(onPropSet(PROP_ID_bizObjName,value)){
            this._bizObjName = value;
            internalClearRefs(PROP_ID_bizObjName);
            
        }
    }
    
    /**
     * 业务对象ID: BIZ_OBJ_ID
     */
    public java.lang.String getBizObjId(){
         onPropGet(PROP_ID_bizObjId);
         return _bizObjId;
    }

    /**
     * 业务对象ID: BIZ_OBJ_ID
     */
    public void setBizObjId(java.lang.String value){
        if(onPropSet(PROP_ID_bizObjId,value)){
            this._bizObjId = value;
            internalClearRefs(PROP_ID_bizObjId);
            
        }
    }
    
    /**
     * 父工作流名称: PARENT_WF_NAME
     */
    public java.lang.String getParentWfName(){
         onPropGet(PROP_ID_parentWfName);
         return _parentWfName;
    }

    /**
     * 父工作流名称: PARENT_WF_NAME
     */
    public void setParentWfName(java.lang.String value){
        if(onPropSet(PROP_ID_parentWfName,value)){
            this._parentWfName = value;
            internalClearRefs(PROP_ID_parentWfName);
            
        }
    }
    
    /**
     * 父流程版本: PARENT_WF_VERSION
     */
    public java.lang.String getParentWfVersion(){
         onPropGet(PROP_ID_parentWfVersion);
         return _parentWfVersion;
    }

    /**
     * 父流程版本: PARENT_WF_VERSION
     */
    public void setParentWfVersion(java.lang.String value){
        if(onPropSet(PROP_ID_parentWfVersion,value)){
            this._parentWfVersion = value;
            internalClearRefs(PROP_ID_parentWfVersion);
            
        }
    }
    
    /**
     * 父流程ID: PARENT_WF_ID
     */
    public java.lang.String getParentWfId(){
         onPropGet(PROP_ID_parentWfId);
         return _parentWfId;
    }

    /**
     * 父流程ID: PARENT_WF_ID
     */
    public void setParentWfId(java.lang.String value){
        if(onPropSet(PROP_ID_parentWfId,value)){
            this._parentWfId = value;
            internalClearRefs(PROP_ID_parentWfId);
            
        }
    }
    
    /**
     * 父流程步骤ID: PARENT_STEP_ID
     */
    public java.lang.String getParentStepId(){
         onPropGet(PROP_ID_parentStepId);
         return _parentStepId;
    }

    /**
     * 父流程步骤ID: PARENT_STEP_ID
     */
    public void setParentStepId(java.lang.String value){
        if(onPropSet(PROP_ID_parentStepId,value)){
            this._parentStepId = value;
            internalClearRefs(PROP_ID_parentStepId);
            
        }
    }
    
    /**
     * 启动人ID: STARTER_ID
     */
    public java.lang.String getStarterId(){
         onPropGet(PROP_ID_starterId);
         return _starterId;
    }

    /**
     * 启动人ID: STARTER_ID
     */
    public void setStarterId(java.lang.String value){
        if(onPropSet(PROP_ID_starterId,value)){
            this._starterId = value;
            internalClearRefs(PROP_ID_starterId);
            
        }
    }
    
    /**
     * 启动人: STARTER_NAME
     */
    public java.lang.String getStarterName(){
         onPropGet(PROP_ID_starterName);
         return _starterName;
    }

    /**
     * 启动人: STARTER_NAME
     */
    public void setStarterName(java.lang.String value){
        if(onPropSet(PROP_ID_starterName,value)){
            this._starterName = value;
            internalClearRefs(PROP_ID_starterName);
            
        }
    }
    
    /**
     * 启动人单位ID: STARTER_DEPT_ID
     */
    public java.lang.String getStarterDeptId(){
         onPropGet(PROP_ID_starterDeptId);
         return _starterDeptId;
    }

    /**
     * 启动人单位ID: STARTER_DEPT_ID
     */
    public void setStarterDeptId(java.lang.String value){
        if(onPropSet(PROP_ID_starterDeptId,value)){
            this._starterDeptId = value;
            internalClearRefs(PROP_ID_starterDeptId);
            
        }
    }
    
    /**
     * 取消人ID: CANCELLER_ID
     */
    public java.lang.String getCancellerId(){
         onPropGet(PROP_ID_cancellerId);
         return _cancellerId;
    }

    /**
     * 取消人ID: CANCELLER_ID
     */
    public void setCancellerId(java.lang.String value){
        if(onPropSet(PROP_ID_cancellerId,value)){
            this._cancellerId = value;
            internalClearRefs(PROP_ID_cancellerId);
            
        }
    }
    
    /**
     * 取消人: CANCELLER_NAME
     */
    public java.lang.String getCancellerName(){
         onPropGet(PROP_ID_cancellerName);
         return _cancellerName;
    }

    /**
     * 取消人: CANCELLER_NAME
     */
    public void setCancellerName(java.lang.String value){
        if(onPropSet(PROP_ID_cancellerName,value)){
            this._cancellerName = value;
            internalClearRefs(PROP_ID_cancellerName);
            
        }
    }
    
    /**
     * 暂停人ID: SUSPENDER_ID
     */
    public java.lang.String getSuspenderId(){
         onPropGet(PROP_ID_suspenderId);
         return _suspenderId;
    }

    /**
     * 暂停人ID: SUSPENDER_ID
     */
    public void setSuspenderId(java.lang.String value){
        if(onPropSet(PROP_ID_suspenderId,value)){
            this._suspenderId = value;
            internalClearRefs(PROP_ID_suspenderId);
            
        }
    }
    
    /**
     * 暂停人: SUSPENDER_NAME
     */
    public java.lang.String getSuspenderName(){
         onPropGet(PROP_ID_suspenderName);
         return _suspenderName;
    }

    /**
     * 暂停人: SUSPENDER_NAME
     */
    public void setSuspenderName(java.lang.String value){
        if(onPropSet(PROP_ID_suspenderName,value)){
            this._suspenderName = value;
            internalClearRefs(PROP_ID_suspenderName);
            
        }
    }
    
    /**
     * 管理者类型: MANAGER_TYPE
     */
    public java.lang.String getManagerType(){
         onPropGet(PROP_ID_managerType);
         return _managerType;
    }

    /**
     * 管理者类型: MANAGER_TYPE
     */
    public void setManagerType(java.lang.String value){
        if(onPropSet(PROP_ID_managerType,value)){
            this._managerType = value;
            internalClearRefs(PROP_ID_managerType);
            
        }
    }
    
    /**
     * 管理者单位ID: MANAGER_DEPT_ID
     */
    public java.lang.String getManagerDeptId(){
         onPropGet(PROP_ID_managerDeptId);
         return _managerDeptId;
    }

    /**
     * 管理者单位ID: MANAGER_DEPT_ID
     */
    public void setManagerDeptId(java.lang.String value){
        if(onPropSet(PROP_ID_managerDeptId,value)){
            this._managerDeptId = value;
            internalClearRefs(PROP_ID_managerDeptId);
            
        }
    }
    
    /**
     * 管理者: MANAGER_NAME
     */
    public java.lang.String getManagerName(){
         onPropGet(PROP_ID_managerName);
         return _managerName;
    }

    /**
     * 管理者: MANAGER_NAME
     */
    public void setManagerName(java.lang.String value){
        if(onPropSet(PROP_ID_managerName,value)){
            this._managerName = value;
            internalClearRefs(PROP_ID_managerName);
            
        }
    }
    
    /**
     * 管理者ID: MANAGER_ID
     */
    public java.lang.String getManagerId(){
         onPropGet(PROP_ID_managerId);
         return _managerId;
    }

    /**
     * 管理者ID: MANAGER_ID
     */
    public void setManagerId(java.lang.String value){
        if(onPropSet(PROP_ID_managerId,value)){
            this._managerId = value;
            internalClearRefs(PROP_ID_managerId);
            
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
     * 父流程
     */
    public io.nop.wf.dao.entity.NopWfInstance getWfInstance(){
       return (io.nop.wf.dao.entity.NopWfInstance)internalGetRefEntity(PROP_NAME_wfInstance);
    }

    public void setWfInstance(io.nop.wf.dao.entity.NopWfInstance refEntity){
       if(refEntity == null){
         
         this.setParentWfId(null);
         
       }else{
          internalSetRefEntity(PROP_NAME_wfInstance, refEntity,()->{
             
                    this.setParentWfId(refEntity.getWfId());
                 
          });
       }
    }
       
}
// resume CPD analysis - CPD-ON
