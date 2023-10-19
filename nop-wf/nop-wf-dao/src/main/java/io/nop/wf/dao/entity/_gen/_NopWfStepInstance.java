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

import io.nop.wf.dao.entity.NopWfStepInstance;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  工作流步骤实例: nop_wf_step_instance
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public class _NopWfStepInstance extends DynamicOrmEntity{
    
    /* 步骤ID: STEP_ID VARCHAR */
    public static final String PROP_NAME_stepId = "stepId";
    public static final int PROP_ID_stepId = 1;
    
    /* 工作流实例ID: WF_ID VARCHAR */
    public static final String PROP_NAME_wfId = "wfId";
    public static final int PROP_ID_wfId = 2;
    
    /* 步骤类型: STEP_TYPE VARCHAR */
    public static final String PROP_NAME_stepType = "stepType";
    public static final int PROP_ID_stepType = 3;
    
    /* 步骤名称: STEP_NAME VARCHAR */
    public static final String PROP_NAME_stepName = "stepName";
    public static final int PROP_ID_stepName = 4;
    
    /* 状态: STATUS INTEGER */
    public static final String PROP_NAME_status = "status";
    public static final int PROP_ID_status = 5;
    
    /* 应用状态: APP_STATE VARCHAR */
    public static final String PROP_NAME_appState = "appState";
    public static final int PROP_ID_appState = 6;
    
    /* 子工作流ID: SUB_WF_ID VARCHAR */
    public static final String PROP_NAME_subWfId = "subWfId";
    public static final int PROP_ID_subWfId = 7;
    
    /* 子工作流名: SUB_WF_NAME VARCHAR */
    public static final String PROP_NAME_subWfName = "subWfName";
    public static final int PROP_ID_subWfName = 8;
    
    /* 子流程版本: SUB_WF_VERSION BIGINT */
    public static final String PROP_NAME_subWfVersion = "subWfVersion";
    public static final int PROP_ID_subWfVersion = 9;
    
    /* 是否已读: IS_READ BOOLEAN */
    public static final String PROP_NAME_isRead = "isRead";
    public static final int PROP_ID_isRead = 10;
    
    /* 参与者类型: ACTOR_TYPE VARCHAR */
    public static final String PROP_NAME_actorType = "actorType";
    public static final int PROP_ID_actorType = 11;
    
    /* 参与者ID: ACTOR_ID VARCHAR */
    public static final String PROP_NAME_actorId = "actorId";
    public static final int PROP_ID_actorId = 12;
    
    /* 参与者部门ID: ACTOR_DEPT_ID VARCHAR */
    public static final String PROP_NAME_actorDeptId = "actorDeptId";
    public static final int PROP_ID_actorDeptId = 13;
    
    /* 参与者姓名: ACTOR_NAME VARCHAR */
    public static final String PROP_NAME_actorName = "actorName";
    public static final int PROP_ID_actorName = 14;
    
    /* 拥有者ID: OWNER_ID VARCHAR */
    public static final String PROP_NAME_ownerId = "ownerId";
    public static final int PROP_ID_ownerId = 15;
    
    /* 拥有者姓名: OWNER_NAME VARCHAR */
    public static final String PROP_NAME_ownerName = "ownerName";
    public static final int PROP_ID_ownerName = 16;
    
    /* 分配者ID: ASSIGNER_ID VARCHAR */
    public static final String PROP_NAME_assignerId = "assignerId";
    public static final int PROP_ID_assignerId = 17;
    
    /* 分配者姓名: ASSIGNER_NAME VARCHAR */
    public static final String PROP_NAME_assignerName = "assignerName";
    public static final int PROP_ID_assignerName = 18;
    
    /* 调用者ID: CALLER_ID VARCHAR */
    public static final String PROP_NAME_callerId = "callerId";
    public static final int PROP_ID_callerId = 19;
    
    /* 调用者姓名: CALLER_NAME VARCHAR */
    public static final String PROP_NAME_callerName = "callerName";
    public static final int PROP_ID_callerName = 20;
    
    /* 取消人ID: CANCELLER_ID VARCHAR */
    public static final String PROP_NAME_cancellerId = "cancellerId";
    public static final int PROP_ID_cancellerId = 21;
    
    /* 取消人姓名: CANCELLER_NAME VARCHAR */
    public static final String PROP_NAME_cancellerName = "cancellerName";
    public static final int PROP_ID_cancellerName = 22;
    
    /* 来源操作: FROM_ACTION VARCHAR */
    public static final String PROP_NAME_fromAction = "fromAction";
    public static final int PROP_ID_fromAction = 23;
    
    /* 最后一次操作: LAST_ACTION VARCHAR */
    public static final String PROP_NAME_lastAction = "lastAction";
    public static final int PROP_ID_lastAction = 24;
    
    /* 开始时间: START_TIME TIMESTAMP */
    public static final String PROP_NAME_startTime = "startTime";
    public static final int PROP_ID_startTime = 25;
    
    /* 结束时间: FINISH_TIME TIMESTAMP */
    public static final String PROP_NAME_finishTime = "finishTime";
    public static final int PROP_ID_finishTime = 26;
    
    /* 到期时间: DUE_TIME TIMESTAMP */
    public static final String PROP_NAME_dueTime = "dueTime";
    public static final int PROP_ID_dueTime = 27;
    
    /* 读取时间: READ_TIME TIMESTAMP */
    public static final String PROP_NAME_readTime = "readTime";
    public static final int PROP_ID_readTime = 28;
    
    /* 优先级: PRIORITY INTEGER */
    public static final String PROP_NAME_priority = "priority";
    public static final int PROP_ID_priority = 29;
    
    /* 汇聚路径: JOIN_TRACE VARCHAR */
    public static final String PROP_NAME_joinTrace = "joinTrace";
    public static final int PROP_ID_joinTrace = 30;
    
    /* 标签: TAG_SET VARCHAR */
    public static final String PROP_NAME_tagSet = "tagSet";
    public static final int PROP_ID_tagSet = 31;
    
    /* 数据版本: VERSION INTEGER */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 32;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 33;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 34;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 35;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 36;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 37;
    

    private static int _PROP_ID_BOUND = 38;

    
    /* relation: 工作流实例 */
    public static final String PROP_NAME_wfInstance = "wfInstance";
    
    /* relation: 子流程实例 */
    public static final String PROP_NAME_subWfInstance = "subWfInstance";
    

    public static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_stepId);
    public static final int[] PK_PROP_IDS = new int[]{PROP_ID_stepId};

    private static final String[] PROP_ID_TO_NAME = new String[38];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_stepId] = PROP_NAME_stepId;
          PROP_NAME_TO_ID.put(PROP_NAME_stepId, PROP_ID_stepId);
      
          PROP_ID_TO_NAME[PROP_ID_wfId] = PROP_NAME_wfId;
          PROP_NAME_TO_ID.put(PROP_NAME_wfId, PROP_ID_wfId);
      
          PROP_ID_TO_NAME[PROP_ID_stepType] = PROP_NAME_stepType;
          PROP_NAME_TO_ID.put(PROP_NAME_stepType, PROP_ID_stepType);
      
          PROP_ID_TO_NAME[PROP_ID_stepName] = PROP_NAME_stepName;
          PROP_NAME_TO_ID.put(PROP_NAME_stepName, PROP_ID_stepName);
      
          PROP_ID_TO_NAME[PROP_ID_status] = PROP_NAME_status;
          PROP_NAME_TO_ID.put(PROP_NAME_status, PROP_ID_status);
      
          PROP_ID_TO_NAME[PROP_ID_appState] = PROP_NAME_appState;
          PROP_NAME_TO_ID.put(PROP_NAME_appState, PROP_ID_appState);
      
          PROP_ID_TO_NAME[PROP_ID_subWfId] = PROP_NAME_subWfId;
          PROP_NAME_TO_ID.put(PROP_NAME_subWfId, PROP_ID_subWfId);
      
          PROP_ID_TO_NAME[PROP_ID_subWfName] = PROP_NAME_subWfName;
          PROP_NAME_TO_ID.put(PROP_NAME_subWfName, PROP_ID_subWfName);
      
          PROP_ID_TO_NAME[PROP_ID_subWfVersion] = PROP_NAME_subWfVersion;
          PROP_NAME_TO_ID.put(PROP_NAME_subWfVersion, PROP_ID_subWfVersion);
      
          PROP_ID_TO_NAME[PROP_ID_isRead] = PROP_NAME_isRead;
          PROP_NAME_TO_ID.put(PROP_NAME_isRead, PROP_ID_isRead);
      
          PROP_ID_TO_NAME[PROP_ID_actorType] = PROP_NAME_actorType;
          PROP_NAME_TO_ID.put(PROP_NAME_actorType, PROP_ID_actorType);
      
          PROP_ID_TO_NAME[PROP_ID_actorId] = PROP_NAME_actorId;
          PROP_NAME_TO_ID.put(PROP_NAME_actorId, PROP_ID_actorId);
      
          PROP_ID_TO_NAME[PROP_ID_actorDeptId] = PROP_NAME_actorDeptId;
          PROP_NAME_TO_ID.put(PROP_NAME_actorDeptId, PROP_ID_actorDeptId);
      
          PROP_ID_TO_NAME[PROP_ID_actorName] = PROP_NAME_actorName;
          PROP_NAME_TO_ID.put(PROP_NAME_actorName, PROP_ID_actorName);
      
          PROP_ID_TO_NAME[PROP_ID_ownerId] = PROP_NAME_ownerId;
          PROP_NAME_TO_ID.put(PROP_NAME_ownerId, PROP_ID_ownerId);
      
          PROP_ID_TO_NAME[PROP_ID_ownerName] = PROP_NAME_ownerName;
          PROP_NAME_TO_ID.put(PROP_NAME_ownerName, PROP_ID_ownerName);
      
          PROP_ID_TO_NAME[PROP_ID_assignerId] = PROP_NAME_assignerId;
          PROP_NAME_TO_ID.put(PROP_NAME_assignerId, PROP_ID_assignerId);
      
          PROP_ID_TO_NAME[PROP_ID_assignerName] = PROP_NAME_assignerName;
          PROP_NAME_TO_ID.put(PROP_NAME_assignerName, PROP_ID_assignerName);
      
          PROP_ID_TO_NAME[PROP_ID_callerId] = PROP_NAME_callerId;
          PROP_NAME_TO_ID.put(PROP_NAME_callerId, PROP_ID_callerId);
      
          PROP_ID_TO_NAME[PROP_ID_callerName] = PROP_NAME_callerName;
          PROP_NAME_TO_ID.put(PROP_NAME_callerName, PROP_ID_callerName);
      
          PROP_ID_TO_NAME[PROP_ID_cancellerId] = PROP_NAME_cancellerId;
          PROP_NAME_TO_ID.put(PROP_NAME_cancellerId, PROP_ID_cancellerId);
      
          PROP_ID_TO_NAME[PROP_ID_cancellerName] = PROP_NAME_cancellerName;
          PROP_NAME_TO_ID.put(PROP_NAME_cancellerName, PROP_ID_cancellerName);
      
          PROP_ID_TO_NAME[PROP_ID_fromAction] = PROP_NAME_fromAction;
          PROP_NAME_TO_ID.put(PROP_NAME_fromAction, PROP_ID_fromAction);
      
          PROP_ID_TO_NAME[PROP_ID_lastAction] = PROP_NAME_lastAction;
          PROP_NAME_TO_ID.put(PROP_NAME_lastAction, PROP_ID_lastAction);
      
          PROP_ID_TO_NAME[PROP_ID_startTime] = PROP_NAME_startTime;
          PROP_NAME_TO_ID.put(PROP_NAME_startTime, PROP_ID_startTime);
      
          PROP_ID_TO_NAME[PROP_ID_finishTime] = PROP_NAME_finishTime;
          PROP_NAME_TO_ID.put(PROP_NAME_finishTime, PROP_ID_finishTime);
      
          PROP_ID_TO_NAME[PROP_ID_dueTime] = PROP_NAME_dueTime;
          PROP_NAME_TO_ID.put(PROP_NAME_dueTime, PROP_ID_dueTime);
      
          PROP_ID_TO_NAME[PROP_ID_readTime] = PROP_NAME_readTime;
          PROP_NAME_TO_ID.put(PROP_NAME_readTime, PROP_ID_readTime);
      
          PROP_ID_TO_NAME[PROP_ID_priority] = PROP_NAME_priority;
          PROP_NAME_TO_ID.put(PROP_NAME_priority, PROP_ID_priority);
      
          PROP_ID_TO_NAME[PROP_ID_joinTrace] = PROP_NAME_joinTrace;
          PROP_NAME_TO_ID.put(PROP_NAME_joinTrace, PROP_ID_joinTrace);
      
          PROP_ID_TO_NAME[PROP_ID_tagSet] = PROP_NAME_tagSet;
          PROP_NAME_TO_ID.put(PROP_NAME_tagSet, PROP_ID_tagSet);
      
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

    
    /* 步骤ID: STEP_ID */
    private java.lang.String _stepId;
    
    /* 工作流实例ID: WF_ID */
    private java.lang.String _wfId;
    
    /* 步骤类型: STEP_TYPE */
    private java.lang.String _stepType;
    
    /* 步骤名称: STEP_NAME */
    private java.lang.String _stepName;
    
    /* 状态: STATUS */
    private java.lang.Integer _status;
    
    /* 应用状态: APP_STATE */
    private java.lang.String _appState;
    
    /* 子工作流ID: SUB_WF_ID */
    private java.lang.String _subWfId;
    
    /* 子工作流名: SUB_WF_NAME */
    private java.lang.String _subWfName;
    
    /* 子流程版本: SUB_WF_VERSION */
    private java.lang.Long _subWfVersion;
    
    /* 是否已读: IS_READ */
    private java.lang.Boolean _isRead;
    
    /* 参与者类型: ACTOR_TYPE */
    private java.lang.String _actorType;
    
    /* 参与者ID: ACTOR_ID */
    private java.lang.String _actorId;
    
    /* 参与者部门ID: ACTOR_DEPT_ID */
    private java.lang.String _actorDeptId;
    
    /* 参与者姓名: ACTOR_NAME */
    private java.lang.String _actorName;
    
    /* 拥有者ID: OWNER_ID */
    private java.lang.String _ownerId;
    
    /* 拥有者姓名: OWNER_NAME */
    private java.lang.String _ownerName;
    
    /* 分配者ID: ASSIGNER_ID */
    private java.lang.String _assignerId;
    
    /* 分配者姓名: ASSIGNER_NAME */
    private java.lang.String _assignerName;
    
    /* 调用者ID: CALLER_ID */
    private java.lang.String _callerId;
    
    /* 调用者姓名: CALLER_NAME */
    private java.lang.String _callerName;
    
    /* 取消人ID: CANCELLER_ID */
    private java.lang.String _cancellerId;
    
    /* 取消人姓名: CANCELLER_NAME */
    private java.lang.String _cancellerName;
    
    /* 来源操作: FROM_ACTION */
    private java.lang.String _fromAction;
    
    /* 最后一次操作: LAST_ACTION */
    private java.lang.String _lastAction;
    
    /* 开始时间: START_TIME */
    private java.sql.Timestamp _startTime;
    
    /* 结束时间: FINISH_TIME */
    private java.sql.Timestamp _finishTime;
    
    /* 到期时间: DUE_TIME */
    private java.sql.Timestamp _dueTime;
    
    /* 读取时间: READ_TIME */
    private java.sql.Timestamp _readTime;
    
    /* 优先级: PRIORITY */
    private java.lang.Integer _priority;
    
    /* 汇聚路径: JOIN_TRACE */
    private java.lang.String _joinTrace;
    
    /* 标签: TAG_SET */
    private java.lang.String _tagSet;
    
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
    

    public _NopWfStepInstance(){
    }

    protected NopWfStepInstance newInstance(){
       return new NopWfStepInstance();
    }

    @Override
    public NopWfStepInstance cloneInstance() {
        NopWfStepInstance entity = newInstance();
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
      return "io.nop.wf.dao.entity.NopWfStepInstance";
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
    
        return buildSimpleId(PROP_ID_stepId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_stepId;
          
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
        
            case PROP_ID_stepId:
               return getStepId();
        
            case PROP_ID_wfId:
               return getWfId();
        
            case PROP_ID_stepType:
               return getStepType();
        
            case PROP_ID_stepName:
               return getStepName();
        
            case PROP_ID_status:
               return getStatus();
        
            case PROP_ID_appState:
               return getAppState();
        
            case PROP_ID_subWfId:
               return getSubWfId();
        
            case PROP_ID_subWfName:
               return getSubWfName();
        
            case PROP_ID_subWfVersion:
               return getSubWfVersion();
        
            case PROP_ID_isRead:
               return getIsRead();
        
            case PROP_ID_actorType:
               return getActorType();
        
            case PROP_ID_actorId:
               return getActorId();
        
            case PROP_ID_actorDeptId:
               return getActorDeptId();
        
            case PROP_ID_actorName:
               return getActorName();
        
            case PROP_ID_ownerId:
               return getOwnerId();
        
            case PROP_ID_ownerName:
               return getOwnerName();
        
            case PROP_ID_assignerId:
               return getAssignerId();
        
            case PROP_ID_assignerName:
               return getAssignerName();
        
            case PROP_ID_callerId:
               return getCallerId();
        
            case PROP_ID_callerName:
               return getCallerName();
        
            case PROP_ID_cancellerId:
               return getCancellerId();
        
            case PROP_ID_cancellerName:
               return getCancellerName();
        
            case PROP_ID_fromAction:
               return getFromAction();
        
            case PROP_ID_lastAction:
               return getLastAction();
        
            case PROP_ID_startTime:
               return getStartTime();
        
            case PROP_ID_finishTime:
               return getFinishTime();
        
            case PROP_ID_dueTime:
               return getDueTime();
        
            case PROP_ID_readTime:
               return getReadTime();
        
            case PROP_ID_priority:
               return getPriority();
        
            case PROP_ID_joinTrace:
               return getJoinTrace();
        
            case PROP_ID_tagSet:
               return getTagSet();
        
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
        
            case PROP_ID_stepId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_stepId));
               }
               setStepId(typedValue);
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
        
            case PROP_ID_stepType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_stepType));
               }
               setStepType(typedValue);
               break;
            }
        
            case PROP_ID_stepName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_stepName));
               }
               setStepName(typedValue);
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
        
            case PROP_ID_subWfId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_subWfId));
               }
               setSubWfId(typedValue);
               break;
            }
        
            case PROP_ID_subWfName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_subWfName));
               }
               setSubWfName(typedValue);
               break;
            }
        
            case PROP_ID_subWfVersion:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_subWfVersion));
               }
               setSubWfVersion(typedValue);
               break;
            }
        
            case PROP_ID_isRead:{
               java.lang.Boolean typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBoolean(value,
                       err-> newTypeConversionError(PROP_NAME_isRead));
               }
               setIsRead(typedValue);
               break;
            }
        
            case PROP_ID_actorType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_actorType));
               }
               setActorType(typedValue);
               break;
            }
        
            case PROP_ID_actorId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_actorId));
               }
               setActorId(typedValue);
               break;
            }
        
            case PROP_ID_actorDeptId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_actorDeptId));
               }
               setActorDeptId(typedValue);
               break;
            }
        
            case PROP_ID_actorName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_actorName));
               }
               setActorName(typedValue);
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
        
            case PROP_ID_assignerId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_assignerId));
               }
               setAssignerId(typedValue);
               break;
            }
        
            case PROP_ID_assignerName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_assignerName));
               }
               setAssignerName(typedValue);
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
        
            case PROP_ID_fromAction:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_fromAction));
               }
               setFromAction(typedValue);
               break;
            }
        
            case PROP_ID_lastAction:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_lastAction));
               }
               setLastAction(typedValue);
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
        
            case PROP_ID_finishTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_finishTime));
               }
               setFinishTime(typedValue);
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
        
            case PROP_ID_readTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_readTime));
               }
               setReadTime(typedValue);
               break;
            }
        
            case PROP_ID_priority:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_priority));
               }
               setPriority(typedValue);
               break;
            }
        
            case PROP_ID_joinTrace:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_joinTrace));
               }
               setJoinTrace(typedValue);
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
        
            case PROP_ID_stepId:{
               onInitProp(propId);
               this._stepId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_wfId:{
               onInitProp(propId);
               this._wfId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_stepType:{
               onInitProp(propId);
               this._stepType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_stepName:{
               onInitProp(propId);
               this._stepName = (java.lang.String)value;
               
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
        
            case PROP_ID_subWfId:{
               onInitProp(propId);
               this._subWfId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_subWfName:{
               onInitProp(propId);
               this._subWfName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_subWfVersion:{
               onInitProp(propId);
               this._subWfVersion = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_isRead:{
               onInitProp(propId);
               this._isRead = (java.lang.Boolean)value;
               
               break;
            }
        
            case PROP_ID_actorType:{
               onInitProp(propId);
               this._actorType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_actorId:{
               onInitProp(propId);
               this._actorId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_actorDeptId:{
               onInitProp(propId);
               this._actorDeptId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_actorName:{
               onInitProp(propId);
               this._actorName = (java.lang.String)value;
               
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
        
            case PROP_ID_assignerId:{
               onInitProp(propId);
               this._assignerId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_assignerName:{
               onInitProp(propId);
               this._assignerName = (java.lang.String)value;
               
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
        
            case PROP_ID_fromAction:{
               onInitProp(propId);
               this._fromAction = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_lastAction:{
               onInitProp(propId);
               this._lastAction = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_startTime:{
               onInitProp(propId);
               this._startTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_finishTime:{
               onInitProp(propId);
               this._finishTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_dueTime:{
               onInitProp(propId);
               this._dueTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_readTime:{
               onInitProp(propId);
               this._readTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_priority:{
               onInitProp(propId);
               this._priority = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_joinTrace:{
               onInitProp(propId);
               this._joinTrace = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_tagSet:{
               onInitProp(propId);
               this._tagSet = (java.lang.String)value;
               
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
     * 步骤ID: STEP_ID
     */
    public java.lang.String getStepId(){
         onPropGet(PROP_ID_stepId);
         return _stepId;
    }

    /**
     * 步骤ID: STEP_ID
     */
    public void setStepId(java.lang.String value){
        if(onPropSet(PROP_ID_stepId,value)){
            this._stepId = value;
            internalClearRefs(PROP_ID_stepId);
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
     * 步骤类型: STEP_TYPE
     */
    public java.lang.String getStepType(){
         onPropGet(PROP_ID_stepType);
         return _stepType;
    }

    /**
     * 步骤类型: STEP_TYPE
     */
    public void setStepType(java.lang.String value){
        if(onPropSet(PROP_ID_stepType,value)){
            this._stepType = value;
            internalClearRefs(PROP_ID_stepType);
            
        }
    }
    
    /**
     * 步骤名称: STEP_NAME
     */
    public java.lang.String getStepName(){
         onPropGet(PROP_ID_stepName);
         return _stepName;
    }

    /**
     * 步骤名称: STEP_NAME
     */
    public void setStepName(java.lang.String value){
        if(onPropSet(PROP_ID_stepName,value)){
            this._stepName = value;
            internalClearRefs(PROP_ID_stepName);
            
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
     * 子工作流ID: SUB_WF_ID
     */
    public java.lang.String getSubWfId(){
         onPropGet(PROP_ID_subWfId);
         return _subWfId;
    }

    /**
     * 子工作流ID: SUB_WF_ID
     */
    public void setSubWfId(java.lang.String value){
        if(onPropSet(PROP_ID_subWfId,value)){
            this._subWfId = value;
            internalClearRefs(PROP_ID_subWfId);
            
        }
    }
    
    /**
     * 子工作流名: SUB_WF_NAME
     */
    public java.lang.String getSubWfName(){
         onPropGet(PROP_ID_subWfName);
         return _subWfName;
    }

    /**
     * 子工作流名: SUB_WF_NAME
     */
    public void setSubWfName(java.lang.String value){
        if(onPropSet(PROP_ID_subWfName,value)){
            this._subWfName = value;
            internalClearRefs(PROP_ID_subWfName);
            
        }
    }
    
    /**
     * 子流程版本: SUB_WF_VERSION
     */
    public java.lang.Long getSubWfVersion(){
         onPropGet(PROP_ID_subWfVersion);
         return _subWfVersion;
    }

    /**
     * 子流程版本: SUB_WF_VERSION
     */
    public void setSubWfVersion(java.lang.Long value){
        if(onPropSet(PROP_ID_subWfVersion,value)){
            this._subWfVersion = value;
            internalClearRefs(PROP_ID_subWfVersion);
            
        }
    }
    
    /**
     * 是否已读: IS_READ
     */
    public java.lang.Boolean getIsRead(){
         onPropGet(PROP_ID_isRead);
         return _isRead;
    }

    /**
     * 是否已读: IS_READ
     */
    public void setIsRead(java.lang.Boolean value){
        if(onPropSet(PROP_ID_isRead,value)){
            this._isRead = value;
            internalClearRefs(PROP_ID_isRead);
            
        }
    }
    
    /**
     * 参与者类型: ACTOR_TYPE
     */
    public java.lang.String getActorType(){
         onPropGet(PROP_ID_actorType);
         return _actorType;
    }

    /**
     * 参与者类型: ACTOR_TYPE
     */
    public void setActorType(java.lang.String value){
        if(onPropSet(PROP_ID_actorType,value)){
            this._actorType = value;
            internalClearRefs(PROP_ID_actorType);
            
        }
    }
    
    /**
     * 参与者ID: ACTOR_ID
     */
    public java.lang.String getActorId(){
         onPropGet(PROP_ID_actorId);
         return _actorId;
    }

    /**
     * 参与者ID: ACTOR_ID
     */
    public void setActorId(java.lang.String value){
        if(onPropSet(PROP_ID_actorId,value)){
            this._actorId = value;
            internalClearRefs(PROP_ID_actorId);
            
        }
    }
    
    /**
     * 参与者部门ID: ACTOR_DEPT_ID
     */
    public java.lang.String getActorDeptId(){
         onPropGet(PROP_ID_actorDeptId);
         return _actorDeptId;
    }

    /**
     * 参与者部门ID: ACTOR_DEPT_ID
     */
    public void setActorDeptId(java.lang.String value){
        if(onPropSet(PROP_ID_actorDeptId,value)){
            this._actorDeptId = value;
            internalClearRefs(PROP_ID_actorDeptId);
            
        }
    }
    
    /**
     * 参与者姓名: ACTOR_NAME
     */
    public java.lang.String getActorName(){
         onPropGet(PROP_ID_actorName);
         return _actorName;
    }

    /**
     * 参与者姓名: ACTOR_NAME
     */
    public void setActorName(java.lang.String value){
        if(onPropSet(PROP_ID_actorName,value)){
            this._actorName = value;
            internalClearRefs(PROP_ID_actorName);
            
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
     * 分配者ID: ASSIGNER_ID
     */
    public java.lang.String getAssignerId(){
         onPropGet(PROP_ID_assignerId);
         return _assignerId;
    }

    /**
     * 分配者ID: ASSIGNER_ID
     */
    public void setAssignerId(java.lang.String value){
        if(onPropSet(PROP_ID_assignerId,value)){
            this._assignerId = value;
            internalClearRefs(PROP_ID_assignerId);
            
        }
    }
    
    /**
     * 分配者姓名: ASSIGNER_NAME
     */
    public java.lang.String getAssignerName(){
         onPropGet(PROP_ID_assignerName);
         return _assignerName;
    }

    /**
     * 分配者姓名: ASSIGNER_NAME
     */
    public void setAssignerName(java.lang.String value){
        if(onPropSet(PROP_ID_assignerName,value)){
            this._assignerName = value;
            internalClearRefs(PROP_ID_assignerName);
            
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
     * 取消人姓名: CANCELLER_NAME
     */
    public java.lang.String getCancellerName(){
         onPropGet(PROP_ID_cancellerName);
         return _cancellerName;
    }

    /**
     * 取消人姓名: CANCELLER_NAME
     */
    public void setCancellerName(java.lang.String value){
        if(onPropSet(PROP_ID_cancellerName,value)){
            this._cancellerName = value;
            internalClearRefs(PROP_ID_cancellerName);
            
        }
    }
    
    /**
     * 来源操作: FROM_ACTION
     */
    public java.lang.String getFromAction(){
         onPropGet(PROP_ID_fromAction);
         return _fromAction;
    }

    /**
     * 来源操作: FROM_ACTION
     */
    public void setFromAction(java.lang.String value){
        if(onPropSet(PROP_ID_fromAction,value)){
            this._fromAction = value;
            internalClearRefs(PROP_ID_fromAction);
            
        }
    }
    
    /**
     * 最后一次操作: LAST_ACTION
     */
    public java.lang.String getLastAction(){
         onPropGet(PROP_ID_lastAction);
         return _lastAction;
    }

    /**
     * 最后一次操作: LAST_ACTION
     */
    public void setLastAction(java.lang.String value){
        if(onPropSet(PROP_ID_lastAction,value)){
            this._lastAction = value;
            internalClearRefs(PROP_ID_lastAction);
            
        }
    }
    
    /**
     * 开始时间: START_TIME
     */
    public java.sql.Timestamp getStartTime(){
         onPropGet(PROP_ID_startTime);
         return _startTime;
    }

    /**
     * 开始时间: START_TIME
     */
    public void setStartTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_startTime,value)){
            this._startTime = value;
            internalClearRefs(PROP_ID_startTime);
            
        }
    }
    
    /**
     * 结束时间: FINISH_TIME
     */
    public java.sql.Timestamp getFinishTime(){
         onPropGet(PROP_ID_finishTime);
         return _finishTime;
    }

    /**
     * 结束时间: FINISH_TIME
     */
    public void setFinishTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_finishTime,value)){
            this._finishTime = value;
            internalClearRefs(PROP_ID_finishTime);
            
        }
    }
    
    /**
     * 到期时间: DUE_TIME
     */
    public java.sql.Timestamp getDueTime(){
         onPropGet(PROP_ID_dueTime);
         return _dueTime;
    }

    /**
     * 到期时间: DUE_TIME
     */
    public void setDueTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_dueTime,value)){
            this._dueTime = value;
            internalClearRefs(PROP_ID_dueTime);
            
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
     * 优先级: PRIORITY
     */
    public java.lang.Integer getPriority(){
         onPropGet(PROP_ID_priority);
         return _priority;
    }

    /**
     * 优先级: PRIORITY
     */
    public void setPriority(java.lang.Integer value){
        if(onPropSet(PROP_ID_priority,value)){
            this._priority = value;
            internalClearRefs(PROP_ID_priority);
            
        }
    }
    
    /**
     * 汇聚路径: JOIN_TRACE
     */
    public java.lang.String getJoinTrace(){
         onPropGet(PROP_ID_joinTrace);
         return _joinTrace;
    }

    /**
     * 汇聚路径: JOIN_TRACE
     */
    public void setJoinTrace(java.lang.String value){
        if(onPropSet(PROP_ID_joinTrace,value)){
            this._joinTrace = value;
            internalClearRefs(PROP_ID_joinTrace);
            
        }
    }
    
    /**
     * 标签: TAG_SET
     */
    public java.lang.String getTagSet(){
         onPropGet(PROP_ID_tagSet);
         return _tagSet;
    }

    /**
     * 标签: TAG_SET
     */
    public void setTagSet(java.lang.String value){
        if(onPropSet(PROP_ID_tagSet,value)){
            this._tagSet = value;
            internalClearRefs(PROP_ID_tagSet);
            
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
     * 子流程实例
     */
    public io.nop.wf.dao.entity.NopWfInstance getSubWfInstance(){
       return (io.nop.wf.dao.entity.NopWfInstance)internalGetRefEntity(PROP_NAME_subWfInstance);
    }

    public void setSubWfInstance(io.nop.wf.dao.entity.NopWfInstance refEntity){
       if(refEntity == null){
         
         this.setSubWfId(null);
         
       }else{
          internalSetRefEntity(PROP_NAME_subWfInstance, refEntity,()->{
             
                    this.setSubWfId(refEntity.getWfId());
                 
          });
       }
    }
       
}
// resume CPD analysis - CPD-ON
