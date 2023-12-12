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
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101"})
public class _NopWfInstance extends DynamicOrmEntity{
    
    /* 主键: WF_ID VARCHAR */
    public static final String PROP_NAME_wfId = "wfId";
    public static final int PROP_ID_wfId = 1;
    
    /* 工作流名称: WF_NAME VARCHAR */
    public static final String PROP_NAME_wfName = "wfName";
    public static final int PROP_ID_wfName = 2;
    
    /* 工作流版本: WF_VERSION BIGINT */
    public static final String PROP_NAME_wfVersion = "wfVersion";
    public static final int PROP_ID_wfVersion = 3;
    
    /* 工作流参数: WF_PARAMS VARCHAR */
    public static final String PROP_NAME_wfParams = "wfParams";
    public static final int PROP_ID_wfParams = 4;
    
    /* 工作流分组: WF_GROUP VARCHAR */
    public static final String PROP_NAME_wfGroup = "wfGroup";
    public static final int PROP_ID_wfGroup = 5;
    
    /* 工作分类: WORK_SCOPE VARCHAR */
    public static final String PROP_NAME_workScope = "workScope";
    public static final int PROP_ID_workScope = 6;
    
    /* 实例标题: TITLE VARCHAR */
    public static final String PROP_NAME_title = "title";
    public static final int PROP_ID_title = 7;
    
    /* 状态: STATUS INTEGER */
    public static final String PROP_NAME_status = "status";
    public static final int PROP_ID_status = 8;
    
    /* 应用状态: APP_STATE VARCHAR */
    public static final String PROP_NAME_appState = "appState";
    public static final int PROP_ID_appState = 9;
    
    /* 启动时间: START_TIME TIMESTAMP */
    public static final String PROP_NAME_startTime = "startTime";
    public static final int PROP_ID_startTime = 10;
    
    /* 结束时间: END_TIME TIMESTAMP */
    public static final String PROP_NAME_endTime = "endTime";
    public static final int PROP_ID_endTime = 11;
    
    /* 完成时限: DUE_TIME TIMESTAMP */
    public static final String PROP_NAME_dueTime = "dueTime";
    public static final int PROP_ID_dueTime = 12;
    
    /* 业务唯一键: BIZ_KEY VARCHAR */
    public static final String PROP_NAME_bizKey = "bizKey";
    public static final int PROP_ID_bizKey = 13;
    
    /* 业务对象名: BIZ_OBJ_NAME VARCHAR */
    public static final String PROP_NAME_bizObjName = "bizObjName";
    public static final int PROP_ID_bizObjName = 14;
    
    /* 业务对象ID: BIZ_OBJ_ID VARCHAR */
    public static final String PROP_NAME_bizObjId = "bizObjId";
    public static final int PROP_ID_bizObjId = 15;
    
    /* 父工作流名称: PARENT_WF_NAME VARCHAR */
    public static final String PROP_NAME_parentWfName = "parentWfName";
    public static final int PROP_ID_parentWfName = 16;
    
    /* 父流程版本: PARENT_WF_VERSION BIGINT */
    public static final String PROP_NAME_parentWfVersion = "parentWfVersion";
    public static final int PROP_ID_parentWfVersion = 17;
    
    /* 父流程ID: PARENT_WF_ID VARCHAR */
    public static final String PROP_NAME_parentWfId = "parentWfId";
    public static final int PROP_ID_parentWfId = 18;
    
    /* 父流程步骤ID: PARENT_STEP_ID VARCHAR */
    public static final String PROP_NAME_parentStepId = "parentStepId";
    public static final int PROP_ID_parentStepId = 19;
    
    /* 启动人ID: STARTER_ID VARCHAR */
    public static final String PROP_NAME_starterId = "starterId";
    public static final int PROP_ID_starterId = 20;
    
    /* 启动人: STARTER_NAME VARCHAR */
    public static final String PROP_NAME_starterName = "starterName";
    public static final int PROP_ID_starterName = 21;
    
    /* 启动人单位ID: STARTER_DEPT_ID VARCHAR */
    public static final String PROP_NAME_starterDeptId = "starterDeptId";
    public static final int PROP_ID_starterDeptId = 22;
    
    /* 上次操作者ID: LAST_OPERATOR_ID VARCHAR */
    public static final String PROP_NAME_lastOperatorId = "lastOperatorId";
    public static final int PROP_ID_lastOperatorId = 23;
    
    /* 上次操作者: LAST_OPERATOR_NAME VARCHAR */
    public static final String PROP_NAME_lastOperatorName = "lastOperatorName";
    public static final int PROP_ID_lastOperatorName = 24;
    
    /* 上次操作者单位ID: LAST_OPERATOR_DEPT_ID VARCHAR */
    public static final String PROP_NAME_lastOperatorDeptId = "lastOperatorDeptId";
    public static final int PROP_ID_lastOperatorDeptId = 25;
    
    /* 上次操作时间: LAST_OPERATE_TIME TIMESTAMP */
    public static final String PROP_NAME_lastOperateTime = "lastOperateTime";
    public static final int PROP_ID_lastOperateTime = 26;
    
    /* 管理者类型: MANAGER_TYPE VARCHAR */
    public static final String PROP_NAME_managerType = "managerType";
    public static final int PROP_ID_managerType = 27;
    
    /* 管理者单位ID: MANAGER_DEPT_ID VARCHAR */
    public static final String PROP_NAME_managerDeptId = "managerDeptId";
    public static final int PROP_ID_managerDeptId = 28;
    
    /* 管理者: MANAGER_NAME VARCHAR */
    public static final String PROP_NAME_managerName = "managerName";
    public static final int PROP_ID_managerName = 29;
    
    /* 管理者ID: MANAGER_ID VARCHAR */
    public static final String PROP_NAME_managerId = "managerId";
    public static final int PROP_ID_managerId = 30;
    
    /* 优先级: PRIORITY INTEGER */
    public static final String PROP_NAME_priority = "priority";
    public static final int PROP_ID_priority = 31;
    
    /* 信号集合: SIGNAL_TEXT VARCHAR */
    public static final String PROP_NAME_signalText = "signalText";
    public static final int PROP_ID_signalText = 32;
    
    /* 标签: TAG_TEXT VARCHAR */
    public static final String PROP_NAME_tagText = "tagText";
    public static final int PROP_ID_tagText = 33;
    
    /* 数据版本: VERSION INTEGER */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 34;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 35;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 36;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 37;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 38;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 40;
    

    private static int _PROP_ID_BOUND = 41;

    
    /* relation: 父流程 */
    public static final String PROP_NAME_wfInstance = "wfInstance";
    
    /* relation:  */
    public static final String PROP_NAME_statusHistories = "statusHistories";
    
    /* relation:  */
    public static final String PROP_NAME_steps = "steps";
    
    /* relation:  */
    public static final String PROP_NAME_outputs = "outputs";
    
    /* relation:  */
    public static final String PROP_NAME_globalVars = "globalVars";
    
    /* relation:  */
    public static final String PROP_NAME_works = "works";
    

    public static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_wfId);
    public static final int[] PK_PROP_IDS = new int[]{PROP_ID_wfId};

    private static final String[] PROP_ID_TO_NAME = new String[41];
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
      
          PROP_ID_TO_NAME[PROP_ID_wfGroup] = PROP_NAME_wfGroup;
          PROP_NAME_TO_ID.put(PROP_NAME_wfGroup, PROP_ID_wfGroup);
      
          PROP_ID_TO_NAME[PROP_ID_workScope] = PROP_NAME_workScope;
          PROP_NAME_TO_ID.put(PROP_NAME_workScope, PROP_ID_workScope);
      
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
      
          PROP_ID_TO_NAME[PROP_ID_lastOperatorId] = PROP_NAME_lastOperatorId;
          PROP_NAME_TO_ID.put(PROP_NAME_lastOperatorId, PROP_ID_lastOperatorId);
      
          PROP_ID_TO_NAME[PROP_ID_lastOperatorName] = PROP_NAME_lastOperatorName;
          PROP_NAME_TO_ID.put(PROP_NAME_lastOperatorName, PROP_ID_lastOperatorName);
      
          PROP_ID_TO_NAME[PROP_ID_lastOperatorDeptId] = PROP_NAME_lastOperatorDeptId;
          PROP_NAME_TO_ID.put(PROP_NAME_lastOperatorDeptId, PROP_ID_lastOperatorDeptId);
      
          PROP_ID_TO_NAME[PROP_ID_lastOperateTime] = PROP_NAME_lastOperateTime;
          PROP_NAME_TO_ID.put(PROP_NAME_lastOperateTime, PROP_ID_lastOperateTime);
      
          PROP_ID_TO_NAME[PROP_ID_managerType] = PROP_NAME_managerType;
          PROP_NAME_TO_ID.put(PROP_NAME_managerType, PROP_ID_managerType);
      
          PROP_ID_TO_NAME[PROP_ID_managerDeptId] = PROP_NAME_managerDeptId;
          PROP_NAME_TO_ID.put(PROP_NAME_managerDeptId, PROP_ID_managerDeptId);
      
          PROP_ID_TO_NAME[PROP_ID_managerName] = PROP_NAME_managerName;
          PROP_NAME_TO_ID.put(PROP_NAME_managerName, PROP_ID_managerName);
      
          PROP_ID_TO_NAME[PROP_ID_managerId] = PROP_NAME_managerId;
          PROP_NAME_TO_ID.put(PROP_NAME_managerId, PROP_ID_managerId);
      
          PROP_ID_TO_NAME[PROP_ID_priority] = PROP_NAME_priority;
          PROP_NAME_TO_ID.put(PROP_NAME_priority, PROP_ID_priority);
      
          PROP_ID_TO_NAME[PROP_ID_signalText] = PROP_NAME_signalText;
          PROP_NAME_TO_ID.put(PROP_NAME_signalText, PROP_ID_signalText);
      
          PROP_ID_TO_NAME[PROP_ID_tagText] = PROP_NAME_tagText;
          PROP_NAME_TO_ID.put(PROP_NAME_tagText, PROP_ID_tagText);
      
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
    private java.lang.Long _wfVersion;
    
    /* 工作流参数: WF_PARAMS */
    private java.lang.String _wfParams;
    
    /* 工作流分组: WF_GROUP */
    private java.lang.String _wfGroup;
    
    /* 工作分类: WORK_SCOPE */
    private java.lang.String _workScope;
    
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
    private java.lang.Long _parentWfVersion;
    
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
    
    /* 上次操作者ID: LAST_OPERATOR_ID */
    private java.lang.String _lastOperatorId;
    
    /* 上次操作者: LAST_OPERATOR_NAME */
    private java.lang.String _lastOperatorName;
    
    /* 上次操作者单位ID: LAST_OPERATOR_DEPT_ID */
    private java.lang.String _lastOperatorDeptId;
    
    /* 上次操作时间: LAST_OPERATE_TIME */
    private java.sql.Timestamp _lastOperateTime;
    
    /* 管理者类型: MANAGER_TYPE */
    private java.lang.String _managerType;
    
    /* 管理者单位ID: MANAGER_DEPT_ID */
    private java.lang.String _managerDeptId;
    
    /* 管理者: MANAGER_NAME */
    private java.lang.String _managerName;
    
    /* 管理者ID: MANAGER_ID */
    private java.lang.String _managerId;
    
    /* 优先级: PRIORITY */
    private java.lang.Integer _priority;
    
    /* 信号集合: SIGNAL_TEXT */
    private java.lang.String _signalText;
    
    /* 标签: TAG_TEXT */
    private java.lang.String _tagText;
    
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
        
            case PROP_ID_wfGroup:
               return getWfGroup();
        
            case PROP_ID_workScope:
               return getWorkScope();
        
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
        
            case PROP_ID_lastOperatorId:
               return getLastOperatorId();
        
            case PROP_ID_lastOperatorName:
               return getLastOperatorName();
        
            case PROP_ID_lastOperatorDeptId:
               return getLastOperatorDeptId();
        
            case PROP_ID_lastOperateTime:
               return getLastOperateTime();
        
            case PROP_ID_managerType:
               return getManagerType();
        
            case PROP_ID_managerDeptId:
               return getManagerDeptId();
        
            case PROP_ID_managerName:
               return getManagerName();
        
            case PROP_ID_managerId:
               return getManagerId();
        
            case PROP_ID_priority:
               return getPriority();
        
            case PROP_ID_signalText:
               return getSignalText();
        
            case PROP_ID_tagText:
               return getTagText();
        
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
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
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
        
            case PROP_ID_wfGroup:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_wfGroup));
               }
               setWfGroup(typedValue);
               break;
            }
        
            case PROP_ID_workScope:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_workScope));
               }
               setWorkScope(typedValue);
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
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
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
        
            case PROP_ID_lastOperatorId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_lastOperatorId));
               }
               setLastOperatorId(typedValue);
               break;
            }
        
            case PROP_ID_lastOperatorName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_lastOperatorName));
               }
               setLastOperatorName(typedValue);
               break;
            }
        
            case PROP_ID_lastOperatorDeptId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_lastOperatorDeptId));
               }
               setLastOperatorDeptId(typedValue);
               break;
            }
        
            case PROP_ID_lastOperateTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_lastOperateTime));
               }
               setLastOperateTime(typedValue);
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
        
            case PROP_ID_priority:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_priority));
               }
               setPriority(typedValue);
               break;
            }
        
            case PROP_ID_signalText:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_signalText));
               }
               setSignalText(typedValue);
               break;
            }
        
            case PROP_ID_tagText:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_tagText));
               }
               setTagText(typedValue);
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
               this._wfVersion = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_wfParams:{
               onInitProp(propId);
               this._wfParams = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_wfGroup:{
               onInitProp(propId);
               this._wfGroup = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_workScope:{
               onInitProp(propId);
               this._workScope = (java.lang.String)value;
               
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
               this._parentWfVersion = (java.lang.Long)value;
               
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
        
            case PROP_ID_lastOperatorId:{
               onInitProp(propId);
               this._lastOperatorId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_lastOperatorName:{
               onInitProp(propId);
               this._lastOperatorName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_lastOperatorDeptId:{
               onInitProp(propId);
               this._lastOperatorDeptId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_lastOperateTime:{
               onInitProp(propId);
               this._lastOperateTime = (java.sql.Timestamp)value;
               
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
        
            case PROP_ID_priority:{
               onInitProp(propId);
               this._priority = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_signalText:{
               onInitProp(propId);
               this._signalText = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_tagText:{
               onInitProp(propId);
               this._tagText = (java.lang.String)value;
               
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
     * 工作流分组: WF_GROUP
     */
    public java.lang.String getWfGroup(){
         onPropGet(PROP_ID_wfGroup);
         return _wfGroup;
    }

    /**
     * 工作流分组: WF_GROUP
     */
    public void setWfGroup(java.lang.String value){
        if(onPropSet(PROP_ID_wfGroup,value)){
            this._wfGroup = value;
            internalClearRefs(PROP_ID_wfGroup);
            
        }
    }
    
    /**
     * 工作分类: WORK_SCOPE
     */
    public java.lang.String getWorkScope(){
         onPropGet(PROP_ID_workScope);
         return _workScope;
    }

    /**
     * 工作分类: WORK_SCOPE
     */
    public void setWorkScope(java.lang.String value){
        if(onPropSet(PROP_ID_workScope,value)){
            this._workScope = value;
            internalClearRefs(PROP_ID_workScope);
            
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
    public java.lang.Long getParentWfVersion(){
         onPropGet(PROP_ID_parentWfVersion);
         return _parentWfVersion;
    }

    /**
     * 父流程版本: PARENT_WF_VERSION
     */
    public void setParentWfVersion(java.lang.Long value){
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
     * 上次操作者ID: LAST_OPERATOR_ID
     */
    public java.lang.String getLastOperatorId(){
         onPropGet(PROP_ID_lastOperatorId);
         return _lastOperatorId;
    }

    /**
     * 上次操作者ID: LAST_OPERATOR_ID
     */
    public void setLastOperatorId(java.lang.String value){
        if(onPropSet(PROP_ID_lastOperatorId,value)){
            this._lastOperatorId = value;
            internalClearRefs(PROP_ID_lastOperatorId);
            
        }
    }
    
    /**
     * 上次操作者: LAST_OPERATOR_NAME
     */
    public java.lang.String getLastOperatorName(){
         onPropGet(PROP_ID_lastOperatorName);
         return _lastOperatorName;
    }

    /**
     * 上次操作者: LAST_OPERATOR_NAME
     */
    public void setLastOperatorName(java.lang.String value){
        if(onPropSet(PROP_ID_lastOperatorName,value)){
            this._lastOperatorName = value;
            internalClearRefs(PROP_ID_lastOperatorName);
            
        }
    }
    
    /**
     * 上次操作者单位ID: LAST_OPERATOR_DEPT_ID
     */
    public java.lang.String getLastOperatorDeptId(){
         onPropGet(PROP_ID_lastOperatorDeptId);
         return _lastOperatorDeptId;
    }

    /**
     * 上次操作者单位ID: LAST_OPERATOR_DEPT_ID
     */
    public void setLastOperatorDeptId(java.lang.String value){
        if(onPropSet(PROP_ID_lastOperatorDeptId,value)){
            this._lastOperatorDeptId = value;
            internalClearRefs(PROP_ID_lastOperatorDeptId);
            
        }
    }
    
    /**
     * 上次操作时间: LAST_OPERATE_TIME
     */
    public java.sql.Timestamp getLastOperateTime(){
         onPropGet(PROP_ID_lastOperateTime);
         return _lastOperateTime;
    }

    /**
     * 上次操作时间: LAST_OPERATE_TIME
     */
    public void setLastOperateTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_lastOperateTime,value)){
            this._lastOperateTime = value;
            internalClearRefs(PROP_ID_lastOperateTime);
            
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
     * 信号集合: SIGNAL_TEXT
     */
    public java.lang.String getSignalText(){
         onPropGet(PROP_ID_signalText);
         return _signalText;
    }

    /**
     * 信号集合: SIGNAL_TEXT
     */
    public void setSignalText(java.lang.String value){
        if(onPropSet(PROP_ID_signalText,value)){
            this._signalText = value;
            internalClearRefs(PROP_ID_signalText);
            
        }
    }
    
    /**
     * 标签: TAG_TEXT
     */
    public java.lang.String getTagText(){
         onPropGet(PROP_ID_tagText);
         return _tagText;
    }

    /**
     * 标签: TAG_TEXT
     */
    public void setTagText(java.lang.String value){
        if(onPropSet(PROP_ID_tagText,value)){
            this._tagText = value;
            internalClearRefs(PROP_ID_tagText);
            
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
       
    private final OrmEntitySet<io.nop.wf.dao.entity.NopWfStatusHistory> _statusHistories = new OrmEntitySet<>(this, PROP_NAME_statusHistories,
        io.nop.wf.dao.entity.NopWfStatusHistory.PROP_NAME_wfInstance, null,io.nop.wf.dao.entity.NopWfStatusHistory.class);

    /**
     * 。 refPropName: wfInstance, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<io.nop.wf.dao.entity.NopWfStatusHistory> getStatusHistories(){
       return _statusHistories;
    }
       
    private final OrmEntitySet<io.nop.wf.dao.entity.NopWfStepInstance> _steps = new OrmEntitySet<>(this, PROP_NAME_steps,
        io.nop.wf.dao.entity.NopWfStepInstance.PROP_NAME_wfInstance, null,io.nop.wf.dao.entity.NopWfStepInstance.class);

    /**
     * 。 refPropName: wfInstance, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<io.nop.wf.dao.entity.NopWfStepInstance> getSteps(){
       return _steps;
    }
       
    private final OrmEntitySet<io.nop.wf.dao.entity.NopWfOutput> _outputs = new OrmEntitySet<>(this, PROP_NAME_outputs,
        io.nop.wf.dao.entity.NopWfOutput.PROP_NAME_wfInstance, null,io.nop.wf.dao.entity.NopWfOutput.class);

    /**
     * 。 refPropName: wfInstance, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<io.nop.wf.dao.entity.NopWfOutput> getOutputs(){
       return _outputs;
    }
       
    private final OrmEntitySet<io.nop.wf.dao.entity.NopWfVar> _globalVars = new OrmEntitySet<>(this, PROP_NAME_globalVars,
        io.nop.wf.dao.entity.NopWfVar.PROP_NAME_wfInstance, null,io.nop.wf.dao.entity.NopWfVar.class);

    /**
     * 。 refPropName: wfInstance, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<io.nop.wf.dao.entity.NopWfVar> getGlobalVars(){
       return _globalVars;
    }
       
    private final OrmEntitySet<io.nop.wf.dao.entity.NopWfWork> _works = new OrmEntitySet<>(this, PROP_NAME_works,
        io.nop.wf.dao.entity.NopWfWork.PROP_NAME_wfInstance, null,io.nop.wf.dao.entity.NopWfWork.class);

    /**
     * 。 refPropName: wfInstance, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<io.nop.wf.dao.entity.NopWfWork> getWorks(){
       return _works;
    }
       
}
// resume CPD analysis - CPD-ON
