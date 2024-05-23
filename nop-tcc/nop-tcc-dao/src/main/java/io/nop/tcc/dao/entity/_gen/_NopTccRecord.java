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

import io.nop.tcc.dao.entity.NopTccRecord;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  TCC事务记录: nop_tcc_record
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopTccRecord extends DynamicOrmEntity{
    
    /* 事务ID: TXN_ID VARCHAR */
    public static final String PROP_NAME_txnId = "txnId";
    public static final int PROP_ID_txnId = 1;
    
    /* 事务分组: TXN_GROUP VARCHAR */
    public static final String PROP_NAME_txnGroup = "txnGroup";
    public static final int PROP_ID_txnGroup = 2;
    
    /* 事务名: TXN_NAME VARCHAR */
    public static final String PROP_NAME_txnName = "txnName";
    public static final int PROP_ID_txnName = 3;
    
    /* 状态: STATUS INTEGER */
    public static final String PROP_NAME_status = "status";
    public static final int PROP_ID_status = 4;
    
    /* 过期时间: EXPIRE_TIME TIMESTAMP */
    public static final String PROP_NAME_expireTime = "expireTime";
    public static final int PROP_ID_expireTime = 5;
    
    /* 应用ID: APP_ID VARCHAR */
    public static final String PROP_NAME_appId = "appId";
    public static final int PROP_ID_appId = 6;
    
    /* 应用数据: APP_DATA VARCHAR */
    public static final String PROP_NAME_appData = "appData";
    public static final int PROP_ID_appData = 7;
    
    /* 开始时间: BEGIN_TIME TIMESTAMP */
    public static final String PROP_NAME_beginTime = "beginTime";
    public static final int PROP_ID_beginTime = 8;
    
    /* 结束时间: END_TIME TIMESTAMP */
    public static final String PROP_NAME_endTime = "endTime";
    public static final int PROP_ID_endTime = 9;
    
    /* 错误码: ERROR_CODE VARCHAR */
    public static final String PROP_NAME_errorCode = "errorCode";
    public static final int PROP_ID_errorCode = 10;
    
    /* 错误消息: ERROR_MESSAGE VARCHAR */
    public static final String PROP_NAME_errorMessage = "errorMessage";
    public static final int PROP_ID_errorMessage = 11;
    
    /* 错误堆栈: ERROR_STACK VARCHAR */
    public static final String PROP_NAME_errorStack = "errorStack";
    public static final int PROP_ID_errorStack = 12;
    
    /* 数据版本: VERSION INTEGER */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 13;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 14;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 15;
    

    private static int _PROP_ID_BOUND = 16;

    
    /* relation: 分支事务记录 */
    public static final String PROP_NAME_branchRecords = "branchRecords";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_txnId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_txnId};

    private static final String[] PROP_ID_TO_NAME = new String[16];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_txnId] = PROP_NAME_txnId;
          PROP_NAME_TO_ID.put(PROP_NAME_txnId, PROP_ID_txnId);
      
          PROP_ID_TO_NAME[PROP_ID_txnGroup] = PROP_NAME_txnGroup;
          PROP_NAME_TO_ID.put(PROP_NAME_txnGroup, PROP_ID_txnGroup);
      
          PROP_ID_TO_NAME[PROP_ID_txnName] = PROP_NAME_txnName;
          PROP_NAME_TO_ID.put(PROP_NAME_txnName, PROP_ID_txnName);
      
          PROP_ID_TO_NAME[PROP_ID_status] = PROP_NAME_status;
          PROP_NAME_TO_ID.put(PROP_NAME_status, PROP_ID_status);
      
          PROP_ID_TO_NAME[PROP_ID_expireTime] = PROP_NAME_expireTime;
          PROP_NAME_TO_ID.put(PROP_NAME_expireTime, PROP_ID_expireTime);
      
          PROP_ID_TO_NAME[PROP_ID_appId] = PROP_NAME_appId;
          PROP_NAME_TO_ID.put(PROP_NAME_appId, PROP_ID_appId);
      
          PROP_ID_TO_NAME[PROP_ID_appData] = PROP_NAME_appData;
          PROP_NAME_TO_ID.put(PROP_NAME_appData, PROP_ID_appData);
      
          PROP_ID_TO_NAME[PROP_ID_beginTime] = PROP_NAME_beginTime;
          PROP_NAME_TO_ID.put(PROP_NAME_beginTime, PROP_ID_beginTime);
      
          PROP_ID_TO_NAME[PROP_ID_endTime] = PROP_NAME_endTime;
          PROP_NAME_TO_ID.put(PROP_NAME_endTime, PROP_ID_endTime);
      
          PROP_ID_TO_NAME[PROP_ID_errorCode] = PROP_NAME_errorCode;
          PROP_NAME_TO_ID.put(PROP_NAME_errorCode, PROP_ID_errorCode);
      
          PROP_ID_TO_NAME[PROP_ID_errorMessage] = PROP_NAME_errorMessage;
          PROP_NAME_TO_ID.put(PROP_NAME_errorMessage, PROP_ID_errorMessage);
      
          PROP_ID_TO_NAME[PROP_ID_errorStack] = PROP_NAME_errorStack;
          PROP_NAME_TO_ID.put(PROP_NAME_errorStack, PROP_ID_errorStack);
      
          PROP_ID_TO_NAME[PROP_ID_version] = PROP_NAME_version;
          PROP_NAME_TO_ID.put(PROP_NAME_version, PROP_ID_version);
      
          PROP_ID_TO_NAME[PROP_ID_createTime] = PROP_NAME_createTime;
          PROP_NAME_TO_ID.put(PROP_NAME_createTime, PROP_ID_createTime);
      
          PROP_ID_TO_NAME[PROP_ID_updateTime] = PROP_NAME_updateTime;
          PROP_NAME_TO_ID.put(PROP_NAME_updateTime, PROP_ID_updateTime);
      
    }

    
    /* 事务ID: TXN_ID */
    private java.lang.String _txnId;
    
    /* 事务分组: TXN_GROUP */
    private java.lang.String _txnGroup;
    
    /* 事务名: TXN_NAME */
    private java.lang.String _txnName;
    
    /* 状态: STATUS */
    private java.lang.Integer _status;
    
    /* 过期时间: EXPIRE_TIME */
    private java.sql.Timestamp _expireTime;
    
    /* 应用ID: APP_ID */
    private java.lang.String _appId;
    
    /* 应用数据: APP_DATA */
    private java.lang.String _appData;
    
    /* 开始时间: BEGIN_TIME */
    private java.sql.Timestamp _beginTime;
    
    /* 结束时间: END_TIME */
    private java.sql.Timestamp _endTime;
    
    /* 错误码: ERROR_CODE */
    private java.lang.String _errorCode;
    
    /* 错误消息: ERROR_MESSAGE */
    private java.lang.String _errorMessage;
    
    /* 错误堆栈: ERROR_STACK */
    private java.lang.String _errorStack;
    
    /* 数据版本: VERSION */
    private java.lang.Integer _version;
    
    /* 创建时间: CREATE_TIME */
    private java.sql.Timestamp _createTime;
    
    /* 修改时间: UPDATE_TIME */
    private java.sql.Timestamp _updateTime;
    

    public _NopTccRecord(){
        // for debug
    }

    protected NopTccRecord newInstance(){
       return new NopTccRecord();
    }

    @Override
    public NopTccRecord cloneInstance() {
        NopTccRecord entity = newInstance();
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
      return "io.nop.tcc.dao.entity.NopTccRecord";
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
    
        return buildSimpleId(PROP_ID_txnId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_txnId;
          
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
        
            case PROP_ID_txnId:
               return getTxnId();
        
            case PROP_ID_txnGroup:
               return getTxnGroup();
        
            case PROP_ID_txnName:
               return getTxnName();
        
            case PROP_ID_status:
               return getStatus();
        
            case PROP_ID_expireTime:
               return getExpireTime();
        
            case PROP_ID_appId:
               return getAppId();
        
            case PROP_ID_appData:
               return getAppData();
        
            case PROP_ID_beginTime:
               return getBeginTime();
        
            case PROP_ID_endTime:
               return getEndTime();
        
            case PROP_ID_errorCode:
               return getErrorCode();
        
            case PROP_ID_errorMessage:
               return getErrorMessage();
        
            case PROP_ID_errorStack:
               return getErrorStack();
        
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
        
            case PROP_ID_txnId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_txnId));
               }
               setTxnId(typedValue);
               break;
            }
        
            case PROP_ID_txnGroup:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_txnGroup));
               }
               setTxnGroup(typedValue);
               break;
            }
        
            case PROP_ID_txnName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_txnName));
               }
               setTxnName(typedValue);
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
        
            case PROP_ID_appId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_appId));
               }
               setAppId(typedValue);
               break;
            }
        
            case PROP_ID_appData:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_appData));
               }
               setAppData(typedValue);
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
        
            case PROP_ID_txnId:{
               onInitProp(propId);
               this._txnId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_txnGroup:{
               onInitProp(propId);
               this._txnGroup = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_txnName:{
               onInitProp(propId);
               this._txnName = (java.lang.String)value;
               
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
        
            case PROP_ID_appId:{
               onInitProp(propId);
               this._appId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_appData:{
               onInitProp(propId);
               this._appData = (java.lang.String)value;
               
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
            orm_id();
        }
    }
    
    /**
     * 事务分组: TXN_GROUP
     */
    public java.lang.String getTxnGroup(){
         onPropGet(PROP_ID_txnGroup);
         return _txnGroup;
    }

    /**
     * 事务分组: TXN_GROUP
     */
    public void setTxnGroup(java.lang.String value){
        if(onPropSet(PROP_ID_txnGroup,value)){
            this._txnGroup = value;
            internalClearRefs(PROP_ID_txnGroup);
            
        }
    }
    
    /**
     * 事务名: TXN_NAME
     */
    public java.lang.String getTxnName(){
         onPropGet(PROP_ID_txnName);
         return _txnName;
    }

    /**
     * 事务名: TXN_NAME
     */
    public void setTxnName(java.lang.String value){
        if(onPropSet(PROP_ID_txnName,value)){
            this._txnName = value;
            internalClearRefs(PROP_ID_txnName);
            
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
     * 应用ID: APP_ID
     */
    public java.lang.String getAppId(){
         onPropGet(PROP_ID_appId);
         return _appId;
    }

    /**
     * 应用ID: APP_ID
     */
    public void setAppId(java.lang.String value){
        if(onPropSet(PROP_ID_appId,value)){
            this._appId = value;
            internalClearRefs(PROP_ID_appId);
            
        }
    }
    
    /**
     * 应用数据: APP_DATA
     */
    public java.lang.String getAppData(){
         onPropGet(PROP_ID_appData);
         return _appData;
    }

    /**
     * 应用数据: APP_DATA
     */
    public void setAppData(java.lang.String value){
        if(onPropSet(PROP_ID_appData,value)){
            this._appData = value;
            internalClearRefs(PROP_ID_appData);
            
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
    
    private final OrmEntitySet<io.nop.tcc.dao.entity.NopTccBranchRecord> _branchRecords = new OrmEntitySet<>(this, PROP_NAME_branchRecords,
        io.nop.tcc.dao.entity.NopTccBranchRecord.PROP_NAME_tccRecord, null,io.nop.tcc.dao.entity.NopTccBranchRecord.class);

    /**
     * 分支事务记录。 refPropName: tccRecord, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<io.nop.tcc.dao.entity.NopTccBranchRecord> getBranchRecords(){
       return _branchRecords;
    }
       
}
// resume CPD analysis - CPD-ON
