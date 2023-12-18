package io.nop.tcc.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.tcc.dao.entity.NopTccBranchRecord;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  TCC事务分支记录: nop_tcc_branch_record
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopTccBranchRecord extends DynamicOrmEntity{
    
    /* 事务分支ID: BRANCH_ID VARCHAR */
    public static final String PROP_NAME_branchId = "branchId";
    public static final int PROP_ID_branchId = 1;
    
    /* 事务ID: TXN_ID VARCHAR */
    public static final String PROP_NAME_txnId = "txnId";
    public static final int PROP_ID_txnId = 2;
    
    /* 事务分支序号: BRANCH_NO INTEGER */
    public static final String PROP_NAME_branchNo = "branchNo";
    public static final int PROP_ID_branchNo = 3;
    
    /* 父分支ID: PARENT_BRANCH_ID VARCHAR */
    public static final String PROP_NAME_parentBranchId = "parentBranchId";
    public static final int PROP_ID_parentBranchId = 4;
    
    /* 状态: STATUS INTEGER */
    public static final String PROP_NAME_status = "status";
    public static final int PROP_ID_status = 5;
    
    /* 过期时间: EXPIRE_TIME TIMESTAMP */
    public static final String PROP_NAME_expireTime = "expireTime";
    public static final int PROP_ID_expireTime = 6;
    
    /* 服务名: SERVICE_NAME VARCHAR */
    public static final String PROP_NAME_serviceName = "serviceName";
    public static final int PROP_ID_serviceName = 7;
    
    /* 服务方法: SERVICE_METHOD VARCHAR */
    public static final String PROP_NAME_serviceMethod = "serviceMethod";
    public static final int PROP_ID_serviceMethod = 8;
    
    /* 确认方法: CONFIRM_METHOD VARCHAR */
    public static final String PROP_NAME_confirmMethod = "confirmMethod";
    public static final int PROP_ID_confirmMethod = 9;
    
    /* 取消方法: CANCEL_METHOD VARCHAR */
    public static final String PROP_NAME_cancelMethod = "cancelMethod";
    public static final int PROP_ID_cancelMethod = 10;
    
    /* 请求数据: REQUEST_DATA VARCHAR */
    public static final String PROP_NAME_requestData = "requestData";
    public static final int PROP_ID_requestData = 11;
    
    /* 错误码: ERROR_CODE VARCHAR */
    public static final String PROP_NAME_errorCode = "errorCode";
    public static final int PROP_ID_errorCode = 12;
    
    /* 错误消息: ERROR_MESSAGE VARCHAR */
    public static final String PROP_NAME_errorMessage = "errorMessage";
    public static final int PROP_ID_errorMessage = 13;
    
    /* 错误堆栈: ERROR_STACK VARCHAR */
    public static final String PROP_NAME_errorStack = "errorStack";
    public static final int PROP_ID_errorStack = 14;
    
    /* 开始时间: BEGIN_TIME TIMESTAMP */
    public static final String PROP_NAME_beginTime = "beginTime";
    public static final int PROP_ID_beginTime = 15;
    
    /* 结束时间: END_TIME TIMESTAMP */
    public static final String PROP_NAME_endTime = "endTime";
    public static final int PROP_ID_endTime = 16;
    
    /* 提交阶段错误码: COMMIT_ERROR_CODE VARCHAR */
    public static final String PROP_NAME_commitErrorCode = "commitErrorCode";
    public static final int PROP_ID_commitErrorCode = 17;
    
    /* 提交阶段错误消息: COMMIT_ERROR_MESSAGE VARCHAR */
    public static final String PROP_NAME_commitErrorMessage = "commitErrorMessage";
    public static final int PROP_ID_commitErrorMessage = 18;
    
    /* 提交阶段错误堆栈: COMMIT_ERROR_STACK VARCHAR */
    public static final String PROP_NAME_commitErrorStack = "commitErrorStack";
    public static final int PROP_ID_commitErrorStack = 19;
    
    /* 取消阶段错误码: CANCEL_ERROR_CODE VARCHAR */
    public static final String PROP_NAME_cancelErrorCode = "cancelErrorCode";
    public static final int PROP_ID_cancelErrorCode = 20;
    
    /* 取消阶段错误消息: CANCEL_ERROR_MESSAGE VARCHAR */
    public static final String PROP_NAME_cancelErrorMessage = "cancelErrorMessage";
    public static final int PROP_ID_cancelErrorMessage = 21;
    
    /* 取消阶段错误堆栈: CANCEL_ERROR_STACK VARCHAR */
    public static final String PROP_NAME_cancelErrorStack = "cancelErrorStack";
    public static final int PROP_ID_cancelErrorStack = 22;
    
    /* 重试次数: RETRY_TIMES INTEGER */
    public static final String PROP_NAME_retryTimes = "retryTimes";
    public static final int PROP_ID_retryTimes = 23;
    
    /* 最大重试次数: MAX_RETRY_TIMES INTEGER */
    public static final String PROP_NAME_maxRetryTimes = "maxRetryTimes";
    public static final int PROP_ID_maxRetryTimes = 24;
    
    /* 下次重试时间: NEXT_RETRY_TIME TIMESTAMP */
    public static final String PROP_NAME_nextRetryTime = "nextRetryTime";
    public static final int PROP_ID_nextRetryTime = 25;
    
    /* 数据版本: VERSION INTEGER */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 26;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 27;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 28;
    

    private static int _PROP_ID_BOUND = 29;

    
    /* relation: 事务记录 */
    public static final String PROP_NAME_tccRecord = "tccRecord";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_branchId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_branchId};

    private static final String[] PROP_ID_TO_NAME = new String[29];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_branchId] = PROP_NAME_branchId;
          PROP_NAME_TO_ID.put(PROP_NAME_branchId, PROP_ID_branchId);
      
          PROP_ID_TO_NAME[PROP_ID_txnId] = PROP_NAME_txnId;
          PROP_NAME_TO_ID.put(PROP_NAME_txnId, PROP_ID_txnId);
      
          PROP_ID_TO_NAME[PROP_ID_branchNo] = PROP_NAME_branchNo;
          PROP_NAME_TO_ID.put(PROP_NAME_branchNo, PROP_ID_branchNo);
      
          PROP_ID_TO_NAME[PROP_ID_parentBranchId] = PROP_NAME_parentBranchId;
          PROP_NAME_TO_ID.put(PROP_NAME_parentBranchId, PROP_ID_parentBranchId);
      
          PROP_ID_TO_NAME[PROP_ID_status] = PROP_NAME_status;
          PROP_NAME_TO_ID.put(PROP_NAME_status, PROP_ID_status);
      
          PROP_ID_TO_NAME[PROP_ID_expireTime] = PROP_NAME_expireTime;
          PROP_NAME_TO_ID.put(PROP_NAME_expireTime, PROP_ID_expireTime);
      
          PROP_ID_TO_NAME[PROP_ID_serviceName] = PROP_NAME_serviceName;
          PROP_NAME_TO_ID.put(PROP_NAME_serviceName, PROP_ID_serviceName);
      
          PROP_ID_TO_NAME[PROP_ID_serviceMethod] = PROP_NAME_serviceMethod;
          PROP_NAME_TO_ID.put(PROP_NAME_serviceMethod, PROP_ID_serviceMethod);
      
          PROP_ID_TO_NAME[PROP_ID_confirmMethod] = PROP_NAME_confirmMethod;
          PROP_NAME_TO_ID.put(PROP_NAME_confirmMethod, PROP_ID_confirmMethod);
      
          PROP_ID_TO_NAME[PROP_ID_cancelMethod] = PROP_NAME_cancelMethod;
          PROP_NAME_TO_ID.put(PROP_NAME_cancelMethod, PROP_ID_cancelMethod);
      
          PROP_ID_TO_NAME[PROP_ID_requestData] = PROP_NAME_requestData;
          PROP_NAME_TO_ID.put(PROP_NAME_requestData, PROP_ID_requestData);
      
          PROP_ID_TO_NAME[PROP_ID_errorCode] = PROP_NAME_errorCode;
          PROP_NAME_TO_ID.put(PROP_NAME_errorCode, PROP_ID_errorCode);
      
          PROP_ID_TO_NAME[PROP_ID_errorMessage] = PROP_NAME_errorMessage;
          PROP_NAME_TO_ID.put(PROP_NAME_errorMessage, PROP_ID_errorMessage);
      
          PROP_ID_TO_NAME[PROP_ID_errorStack] = PROP_NAME_errorStack;
          PROP_NAME_TO_ID.put(PROP_NAME_errorStack, PROP_ID_errorStack);
      
          PROP_ID_TO_NAME[PROP_ID_beginTime] = PROP_NAME_beginTime;
          PROP_NAME_TO_ID.put(PROP_NAME_beginTime, PROP_ID_beginTime);
      
          PROP_ID_TO_NAME[PROP_ID_endTime] = PROP_NAME_endTime;
          PROP_NAME_TO_ID.put(PROP_NAME_endTime, PROP_ID_endTime);
      
          PROP_ID_TO_NAME[PROP_ID_commitErrorCode] = PROP_NAME_commitErrorCode;
          PROP_NAME_TO_ID.put(PROP_NAME_commitErrorCode, PROP_ID_commitErrorCode);
      
          PROP_ID_TO_NAME[PROP_ID_commitErrorMessage] = PROP_NAME_commitErrorMessage;
          PROP_NAME_TO_ID.put(PROP_NAME_commitErrorMessage, PROP_ID_commitErrorMessage);
      
          PROP_ID_TO_NAME[PROP_ID_commitErrorStack] = PROP_NAME_commitErrorStack;
          PROP_NAME_TO_ID.put(PROP_NAME_commitErrorStack, PROP_ID_commitErrorStack);
      
          PROP_ID_TO_NAME[PROP_ID_cancelErrorCode] = PROP_NAME_cancelErrorCode;
          PROP_NAME_TO_ID.put(PROP_NAME_cancelErrorCode, PROP_ID_cancelErrorCode);
      
          PROP_ID_TO_NAME[PROP_ID_cancelErrorMessage] = PROP_NAME_cancelErrorMessage;
          PROP_NAME_TO_ID.put(PROP_NAME_cancelErrorMessage, PROP_ID_cancelErrorMessage);
      
          PROP_ID_TO_NAME[PROP_ID_cancelErrorStack] = PROP_NAME_cancelErrorStack;
          PROP_NAME_TO_ID.put(PROP_NAME_cancelErrorStack, PROP_ID_cancelErrorStack);
      
          PROP_ID_TO_NAME[PROP_ID_retryTimes] = PROP_NAME_retryTimes;
          PROP_NAME_TO_ID.put(PROP_NAME_retryTimes, PROP_ID_retryTimes);
      
          PROP_ID_TO_NAME[PROP_ID_maxRetryTimes] = PROP_NAME_maxRetryTimes;
          PROP_NAME_TO_ID.put(PROP_NAME_maxRetryTimes, PROP_ID_maxRetryTimes);
      
          PROP_ID_TO_NAME[PROP_ID_nextRetryTime] = PROP_NAME_nextRetryTime;
          PROP_NAME_TO_ID.put(PROP_NAME_nextRetryTime, PROP_ID_nextRetryTime);
      
          PROP_ID_TO_NAME[PROP_ID_version] = PROP_NAME_version;
          PROP_NAME_TO_ID.put(PROP_NAME_version, PROP_ID_version);
      
          PROP_ID_TO_NAME[PROP_ID_createTime] = PROP_NAME_createTime;
          PROP_NAME_TO_ID.put(PROP_NAME_createTime, PROP_ID_createTime);
      
          PROP_ID_TO_NAME[PROP_ID_updateTime] = PROP_NAME_updateTime;
          PROP_NAME_TO_ID.put(PROP_NAME_updateTime, PROP_ID_updateTime);
      
    }

    
    /* 事务分支ID: BRANCH_ID */
    private java.lang.String _branchId;
    
    /* 事务ID: TXN_ID */
    private java.lang.String _txnId;
    
    /* 事务分支序号: BRANCH_NO */
    private java.lang.Integer _branchNo;
    
    /* 父分支ID: PARENT_BRANCH_ID */
    private java.lang.String _parentBranchId;
    
    /* 状态: STATUS */
    private java.lang.Integer _status;
    
    /* 过期时间: EXPIRE_TIME */
    private java.sql.Timestamp _expireTime;
    
    /* 服务名: SERVICE_NAME */
    private java.lang.String _serviceName;
    
    /* 服务方法: SERVICE_METHOD */
    private java.lang.String _serviceMethod;
    
    /* 确认方法: CONFIRM_METHOD */
    private java.lang.String _confirmMethod;
    
    /* 取消方法: CANCEL_METHOD */
    private java.lang.String _cancelMethod;
    
    /* 请求数据: REQUEST_DATA */
    private java.lang.String _requestData;
    
    /* 错误码: ERROR_CODE */
    private java.lang.String _errorCode;
    
    /* 错误消息: ERROR_MESSAGE */
    private java.lang.String _errorMessage;
    
    /* 错误堆栈: ERROR_STACK */
    private java.lang.String _errorStack;
    
    /* 开始时间: BEGIN_TIME */
    private java.sql.Timestamp _beginTime;
    
    /* 结束时间: END_TIME */
    private java.sql.Timestamp _endTime;
    
    /* 提交阶段错误码: COMMIT_ERROR_CODE */
    private java.lang.String _commitErrorCode;
    
    /* 提交阶段错误消息: COMMIT_ERROR_MESSAGE */
    private java.lang.String _commitErrorMessage;
    
    /* 提交阶段错误堆栈: COMMIT_ERROR_STACK */
    private java.lang.String _commitErrorStack;
    
    /* 取消阶段错误码: CANCEL_ERROR_CODE */
    private java.lang.String _cancelErrorCode;
    
    /* 取消阶段错误消息: CANCEL_ERROR_MESSAGE */
    private java.lang.String _cancelErrorMessage;
    
    /* 取消阶段错误堆栈: CANCEL_ERROR_STACK */
    private java.lang.String _cancelErrorStack;
    
    /* 重试次数: RETRY_TIMES */
    private java.lang.Integer _retryTimes;
    
    /* 最大重试次数: MAX_RETRY_TIMES */
    private java.lang.Integer _maxRetryTimes;
    
    /* 下次重试时间: NEXT_RETRY_TIME */
    private java.sql.Timestamp _nextRetryTime;
    
    /* 数据版本: VERSION */
    private java.lang.Integer _version;
    
    /* 创建时间: CREATE_TIME */
    private java.sql.Timestamp _createTime;
    
    /* 修改时间: UPDATE_TIME */
    private java.sql.Timestamp _updateTime;
    

    public _NopTccBranchRecord(){
        // for debug
    }

    protected NopTccBranchRecord newInstance(){
       return new NopTccBranchRecord();
    }

    @Override
    public NopTccBranchRecord cloneInstance() {
        NopTccBranchRecord entity = newInstance();
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
      return "io.nop.tcc.dao.entity.NopTccBranchRecord";
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
    
        return buildSimpleId(PROP_ID_branchId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_branchId;
          
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
        
            case PROP_ID_branchId:
               return getBranchId();
        
            case PROP_ID_txnId:
               return getTxnId();
        
            case PROP_ID_branchNo:
               return getBranchNo();
        
            case PROP_ID_parentBranchId:
               return getParentBranchId();
        
            case PROP_ID_status:
               return getStatus();
        
            case PROP_ID_expireTime:
               return getExpireTime();
        
            case PROP_ID_serviceName:
               return getServiceName();
        
            case PROP_ID_serviceMethod:
               return getServiceMethod();
        
            case PROP_ID_confirmMethod:
               return getConfirmMethod();
        
            case PROP_ID_cancelMethod:
               return getCancelMethod();
        
            case PROP_ID_requestData:
               return getRequestData();
        
            case PROP_ID_errorCode:
               return getErrorCode();
        
            case PROP_ID_errorMessage:
               return getErrorMessage();
        
            case PROP_ID_errorStack:
               return getErrorStack();
        
            case PROP_ID_beginTime:
               return getBeginTime();
        
            case PROP_ID_endTime:
               return getEndTime();
        
            case PROP_ID_commitErrorCode:
               return getCommitErrorCode();
        
            case PROP_ID_commitErrorMessage:
               return getCommitErrorMessage();
        
            case PROP_ID_commitErrorStack:
               return getCommitErrorStack();
        
            case PROP_ID_cancelErrorCode:
               return getCancelErrorCode();
        
            case PROP_ID_cancelErrorMessage:
               return getCancelErrorMessage();
        
            case PROP_ID_cancelErrorStack:
               return getCancelErrorStack();
        
            case PROP_ID_retryTimes:
               return getRetryTimes();
        
            case PROP_ID_maxRetryTimes:
               return getMaxRetryTimes();
        
            case PROP_ID_nextRetryTime:
               return getNextRetryTime();
        
            case PROP_ID_version:
               return getVersion();
        
            case PROP_ID_createTime:
               return getCreateTime();
        
            case PROP_ID_updateTime:
               return getUpdateTime();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_branchId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_branchId));
               }
               setBranchId(typedValue);
               break;
            }
        
            case PROP_ID_txnId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_txnId));
               }
               setTxnId(typedValue);
               break;
            }
        
            case PROP_ID_branchNo:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_branchNo));
               }
               setBranchNo(typedValue);
               break;
            }
        
            case PROP_ID_parentBranchId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_parentBranchId));
               }
               setParentBranchId(typedValue);
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
        
            case PROP_ID_expireTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_expireTime));
               }
               setExpireTime(typedValue);
               break;
            }
        
            case PROP_ID_serviceName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_serviceName));
               }
               setServiceName(typedValue);
               break;
            }
        
            case PROP_ID_serviceMethod:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_serviceMethod));
               }
               setServiceMethod(typedValue);
               break;
            }
        
            case PROP_ID_confirmMethod:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_confirmMethod));
               }
               setConfirmMethod(typedValue);
               break;
            }
        
            case PROP_ID_cancelMethod:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_cancelMethod));
               }
               setCancelMethod(typedValue);
               break;
            }
        
            case PROP_ID_requestData:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_requestData));
               }
               setRequestData(typedValue);
               break;
            }
        
            case PROP_ID_errorCode:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_errorCode));
               }
               setErrorCode(typedValue);
               break;
            }
        
            case PROP_ID_errorMessage:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_errorMessage));
               }
               setErrorMessage(typedValue);
               break;
            }
        
            case PROP_ID_errorStack:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_errorStack));
               }
               setErrorStack(typedValue);
               break;
            }
        
            case PROP_ID_beginTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_beginTime));
               }
               setBeginTime(typedValue);
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
        
            case PROP_ID_commitErrorCode:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_commitErrorCode));
               }
               setCommitErrorCode(typedValue);
               break;
            }
        
            case PROP_ID_commitErrorMessage:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_commitErrorMessage));
               }
               setCommitErrorMessage(typedValue);
               break;
            }
        
            case PROP_ID_commitErrorStack:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_commitErrorStack));
               }
               setCommitErrorStack(typedValue);
               break;
            }
        
            case PROP_ID_cancelErrorCode:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_cancelErrorCode));
               }
               setCancelErrorCode(typedValue);
               break;
            }
        
            case PROP_ID_cancelErrorMessage:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_cancelErrorMessage));
               }
               setCancelErrorMessage(typedValue);
               break;
            }
        
            case PROP_ID_cancelErrorStack:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_cancelErrorStack));
               }
               setCancelErrorStack(typedValue);
               break;
            }
        
            case PROP_ID_retryTimes:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_retryTimes));
               }
               setRetryTimes(typedValue);
               break;
            }
        
            case PROP_ID_maxRetryTimes:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_maxRetryTimes));
               }
               setMaxRetryTimes(typedValue);
               break;
            }
        
            case PROP_ID_nextRetryTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_nextRetryTime));
               }
               setNextRetryTime(typedValue);
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
        
            case PROP_ID_createTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_createTime));
               }
               setCreateTime(typedValue);
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
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_branchId:{
               onInitProp(propId);
               this._branchId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_txnId:{
               onInitProp(propId);
               this._txnId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_branchNo:{
               onInitProp(propId);
               this._branchNo = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_parentBranchId:{
               onInitProp(propId);
               this._parentBranchId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_status:{
               onInitProp(propId);
               this._status = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_expireTime:{
               onInitProp(propId);
               this._expireTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_serviceName:{
               onInitProp(propId);
               this._serviceName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_serviceMethod:{
               onInitProp(propId);
               this._serviceMethod = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_confirmMethod:{
               onInitProp(propId);
               this._confirmMethod = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_cancelMethod:{
               onInitProp(propId);
               this._cancelMethod = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_requestData:{
               onInitProp(propId);
               this._requestData = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_errorCode:{
               onInitProp(propId);
               this._errorCode = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_errorMessage:{
               onInitProp(propId);
               this._errorMessage = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_errorStack:{
               onInitProp(propId);
               this._errorStack = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_beginTime:{
               onInitProp(propId);
               this._beginTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_endTime:{
               onInitProp(propId);
               this._endTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_commitErrorCode:{
               onInitProp(propId);
               this._commitErrorCode = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_commitErrorMessage:{
               onInitProp(propId);
               this._commitErrorMessage = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_commitErrorStack:{
               onInitProp(propId);
               this._commitErrorStack = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_cancelErrorCode:{
               onInitProp(propId);
               this._cancelErrorCode = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_cancelErrorMessage:{
               onInitProp(propId);
               this._cancelErrorMessage = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_cancelErrorStack:{
               onInitProp(propId);
               this._cancelErrorStack = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_retryTimes:{
               onInitProp(propId);
               this._retryTimes = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_maxRetryTimes:{
               onInitProp(propId);
               this._maxRetryTimes = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_nextRetryTime:{
               onInitProp(propId);
               this._nextRetryTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_version:{
               onInitProp(propId);
               this._version = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_createTime:{
               onInitProp(propId);
               this._createTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_updateTime:{
               onInitProp(propId);
               this._updateTime = (java.sql.Timestamp)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 事务分支ID: BRANCH_ID
     */
    public java.lang.String getBranchId(){
         onPropGet(PROP_ID_branchId);
         return _branchId;
    }

    /**
     * 事务分支ID: BRANCH_ID
     */
    public void setBranchId(java.lang.String value){
        if(onPropSet(PROP_ID_branchId,value)){
            this._branchId = value;
            internalClearRefs(PROP_ID_branchId);
            orm_id();
        }
    }
    
    /**
     * 事务ID: TXN_ID
     */
    public java.lang.String getTxnId(){
         onPropGet(PROP_ID_txnId);
         return _txnId;
    }

    /**
     * 事务ID: TXN_ID
     */
    public void setTxnId(java.lang.String value){
        if(onPropSet(PROP_ID_txnId,value)){
            this._txnId = value;
            internalClearRefs(PROP_ID_txnId);
            
        }
    }
    
    /**
     * 事务分支序号: BRANCH_NO
     */
    public java.lang.Integer getBranchNo(){
         onPropGet(PROP_ID_branchNo);
         return _branchNo;
    }

    /**
     * 事务分支序号: BRANCH_NO
     */
    public void setBranchNo(java.lang.Integer value){
        if(onPropSet(PROP_ID_branchNo,value)){
            this._branchNo = value;
            internalClearRefs(PROP_ID_branchNo);
            
        }
    }
    
    /**
     * 父分支ID: PARENT_BRANCH_ID
     */
    public java.lang.String getParentBranchId(){
         onPropGet(PROP_ID_parentBranchId);
         return _parentBranchId;
    }

    /**
     * 父分支ID: PARENT_BRANCH_ID
     */
    public void setParentBranchId(java.lang.String value){
        if(onPropSet(PROP_ID_parentBranchId,value)){
            this._parentBranchId = value;
            internalClearRefs(PROP_ID_parentBranchId);
            
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
     * 过期时间: EXPIRE_TIME
     */
    public java.sql.Timestamp getExpireTime(){
         onPropGet(PROP_ID_expireTime);
         return _expireTime;
    }

    /**
     * 过期时间: EXPIRE_TIME
     */
    public void setExpireTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_expireTime,value)){
            this._expireTime = value;
            internalClearRefs(PROP_ID_expireTime);
            
        }
    }
    
    /**
     * 服务名: SERVICE_NAME
     */
    public java.lang.String getServiceName(){
         onPropGet(PROP_ID_serviceName);
         return _serviceName;
    }

    /**
     * 服务名: SERVICE_NAME
     */
    public void setServiceName(java.lang.String value){
        if(onPropSet(PROP_ID_serviceName,value)){
            this._serviceName = value;
            internalClearRefs(PROP_ID_serviceName);
            
        }
    }
    
    /**
     * 服务方法: SERVICE_METHOD
     */
    public java.lang.String getServiceMethod(){
         onPropGet(PROP_ID_serviceMethod);
         return _serviceMethod;
    }

    /**
     * 服务方法: SERVICE_METHOD
     */
    public void setServiceMethod(java.lang.String value){
        if(onPropSet(PROP_ID_serviceMethod,value)){
            this._serviceMethod = value;
            internalClearRefs(PROP_ID_serviceMethod);
            
        }
    }
    
    /**
     * 确认方法: CONFIRM_METHOD
     */
    public java.lang.String getConfirmMethod(){
         onPropGet(PROP_ID_confirmMethod);
         return _confirmMethod;
    }

    /**
     * 确认方法: CONFIRM_METHOD
     */
    public void setConfirmMethod(java.lang.String value){
        if(onPropSet(PROP_ID_confirmMethod,value)){
            this._confirmMethod = value;
            internalClearRefs(PROP_ID_confirmMethod);
            
        }
    }
    
    /**
     * 取消方法: CANCEL_METHOD
     */
    public java.lang.String getCancelMethod(){
         onPropGet(PROP_ID_cancelMethod);
         return _cancelMethod;
    }

    /**
     * 取消方法: CANCEL_METHOD
     */
    public void setCancelMethod(java.lang.String value){
        if(onPropSet(PROP_ID_cancelMethod,value)){
            this._cancelMethod = value;
            internalClearRefs(PROP_ID_cancelMethod);
            
        }
    }
    
    /**
     * 请求数据: REQUEST_DATA
     */
    public java.lang.String getRequestData(){
         onPropGet(PROP_ID_requestData);
         return _requestData;
    }

    /**
     * 请求数据: REQUEST_DATA
     */
    public void setRequestData(java.lang.String value){
        if(onPropSet(PROP_ID_requestData,value)){
            this._requestData = value;
            internalClearRefs(PROP_ID_requestData);
            
        }
    }
    
    /**
     * 错误码: ERROR_CODE
     */
    public java.lang.String getErrorCode(){
         onPropGet(PROP_ID_errorCode);
         return _errorCode;
    }

    /**
     * 错误码: ERROR_CODE
     */
    public void setErrorCode(java.lang.String value){
        if(onPropSet(PROP_ID_errorCode,value)){
            this._errorCode = value;
            internalClearRefs(PROP_ID_errorCode);
            
        }
    }
    
    /**
     * 错误消息: ERROR_MESSAGE
     */
    public java.lang.String getErrorMessage(){
         onPropGet(PROP_ID_errorMessage);
         return _errorMessage;
    }

    /**
     * 错误消息: ERROR_MESSAGE
     */
    public void setErrorMessage(java.lang.String value){
        if(onPropSet(PROP_ID_errorMessage,value)){
            this._errorMessage = value;
            internalClearRefs(PROP_ID_errorMessage);
            
        }
    }
    
    /**
     * 错误堆栈: ERROR_STACK
     */
    public java.lang.String getErrorStack(){
         onPropGet(PROP_ID_errorStack);
         return _errorStack;
    }

    /**
     * 错误堆栈: ERROR_STACK
     */
    public void setErrorStack(java.lang.String value){
        if(onPropSet(PROP_ID_errorStack,value)){
            this._errorStack = value;
            internalClearRefs(PROP_ID_errorStack);
            
        }
    }
    
    /**
     * 开始时间: BEGIN_TIME
     */
    public java.sql.Timestamp getBeginTime(){
         onPropGet(PROP_ID_beginTime);
         return _beginTime;
    }

    /**
     * 开始时间: BEGIN_TIME
     */
    public void setBeginTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_beginTime,value)){
            this._beginTime = value;
            internalClearRefs(PROP_ID_beginTime);
            
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
     * 提交阶段错误码: COMMIT_ERROR_CODE
     */
    public java.lang.String getCommitErrorCode(){
         onPropGet(PROP_ID_commitErrorCode);
         return _commitErrorCode;
    }

    /**
     * 提交阶段错误码: COMMIT_ERROR_CODE
     */
    public void setCommitErrorCode(java.lang.String value){
        if(onPropSet(PROP_ID_commitErrorCode,value)){
            this._commitErrorCode = value;
            internalClearRefs(PROP_ID_commitErrorCode);
            
        }
    }
    
    /**
     * 提交阶段错误消息: COMMIT_ERROR_MESSAGE
     */
    public java.lang.String getCommitErrorMessage(){
         onPropGet(PROP_ID_commitErrorMessage);
         return _commitErrorMessage;
    }

    /**
     * 提交阶段错误消息: COMMIT_ERROR_MESSAGE
     */
    public void setCommitErrorMessage(java.lang.String value){
        if(onPropSet(PROP_ID_commitErrorMessage,value)){
            this._commitErrorMessage = value;
            internalClearRefs(PROP_ID_commitErrorMessage);
            
        }
    }
    
    /**
     * 提交阶段错误堆栈: COMMIT_ERROR_STACK
     */
    public java.lang.String getCommitErrorStack(){
         onPropGet(PROP_ID_commitErrorStack);
         return _commitErrorStack;
    }

    /**
     * 提交阶段错误堆栈: COMMIT_ERROR_STACK
     */
    public void setCommitErrorStack(java.lang.String value){
        if(onPropSet(PROP_ID_commitErrorStack,value)){
            this._commitErrorStack = value;
            internalClearRefs(PROP_ID_commitErrorStack);
            
        }
    }
    
    /**
     * 取消阶段错误码: CANCEL_ERROR_CODE
     */
    public java.lang.String getCancelErrorCode(){
         onPropGet(PROP_ID_cancelErrorCode);
         return _cancelErrorCode;
    }

    /**
     * 取消阶段错误码: CANCEL_ERROR_CODE
     */
    public void setCancelErrorCode(java.lang.String value){
        if(onPropSet(PROP_ID_cancelErrorCode,value)){
            this._cancelErrorCode = value;
            internalClearRefs(PROP_ID_cancelErrorCode);
            
        }
    }
    
    /**
     * 取消阶段错误消息: CANCEL_ERROR_MESSAGE
     */
    public java.lang.String getCancelErrorMessage(){
         onPropGet(PROP_ID_cancelErrorMessage);
         return _cancelErrorMessage;
    }

    /**
     * 取消阶段错误消息: CANCEL_ERROR_MESSAGE
     */
    public void setCancelErrorMessage(java.lang.String value){
        if(onPropSet(PROP_ID_cancelErrorMessage,value)){
            this._cancelErrorMessage = value;
            internalClearRefs(PROP_ID_cancelErrorMessage);
            
        }
    }
    
    /**
     * 取消阶段错误堆栈: CANCEL_ERROR_STACK
     */
    public java.lang.String getCancelErrorStack(){
         onPropGet(PROP_ID_cancelErrorStack);
         return _cancelErrorStack;
    }

    /**
     * 取消阶段错误堆栈: CANCEL_ERROR_STACK
     */
    public void setCancelErrorStack(java.lang.String value){
        if(onPropSet(PROP_ID_cancelErrorStack,value)){
            this._cancelErrorStack = value;
            internalClearRefs(PROP_ID_cancelErrorStack);
            
        }
    }
    
    /**
     * 重试次数: RETRY_TIMES
     */
    public java.lang.Integer getRetryTimes(){
         onPropGet(PROP_ID_retryTimes);
         return _retryTimes;
    }

    /**
     * 重试次数: RETRY_TIMES
     */
    public void setRetryTimes(java.lang.Integer value){
        if(onPropSet(PROP_ID_retryTimes,value)){
            this._retryTimes = value;
            internalClearRefs(PROP_ID_retryTimes);
            
        }
    }
    
    /**
     * 最大重试次数: MAX_RETRY_TIMES
     */
    public java.lang.Integer getMaxRetryTimes(){
         onPropGet(PROP_ID_maxRetryTimes);
         return _maxRetryTimes;
    }

    /**
     * 最大重试次数: MAX_RETRY_TIMES
     */
    public void setMaxRetryTimes(java.lang.Integer value){
        if(onPropSet(PROP_ID_maxRetryTimes,value)){
            this._maxRetryTimes = value;
            internalClearRefs(PROP_ID_maxRetryTimes);
            
        }
    }
    
    /**
     * 下次重试时间: NEXT_RETRY_TIME
     */
    public java.sql.Timestamp getNextRetryTime(){
         onPropGet(PROP_ID_nextRetryTime);
         return _nextRetryTime;
    }

    /**
     * 下次重试时间: NEXT_RETRY_TIME
     */
    public void setNextRetryTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_nextRetryTime,value)){
            this._nextRetryTime = value;
            internalClearRefs(PROP_ID_nextRetryTime);
            
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
     * 事务记录
     */
    public io.nop.tcc.dao.entity.NopTccRecord getTccRecord(){
       return (io.nop.tcc.dao.entity.NopTccRecord)internalGetRefEntity(PROP_NAME_tccRecord);
    }

    public void setTccRecord(io.nop.tcc.dao.entity.NopTccRecord refEntity){
       if(refEntity == null){
         
         this.setTxnId(null);
         
       }else{
          internalSetRefEntity(PROP_NAME_tccRecord, refEntity,()->{
             
                    this.setTxnId(refEntity.getTxnId());
                 
          });
       }
    }
       
}
// resume CPD analysis - CPD-ON
