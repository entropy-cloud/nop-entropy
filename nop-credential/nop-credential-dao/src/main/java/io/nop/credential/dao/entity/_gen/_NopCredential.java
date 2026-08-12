package io.nop.credential.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.credential.dao.entity.NopCredential;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  加密凭证: nop_credential
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopCredential extends DynamicOrmEntity{
    
    /* 凭证ID: CREDENTIAL_ID VARCHAR */
    public static final String PROP_NAME_credentialId = "credentialId";
    public static final int PROP_ID_credentialId = 1;
    
    /* 凭证名: CREDENTIAL_NAME VARCHAR */
    public static final String PROP_NAME_name = "name";
    public static final int PROP_ID_name = 2;
    
    /* 凭证类型: TYPE_NAME VARCHAR */
    public static final String PROP_NAME_typeName = "typeName";
    public static final int PROP_ID_typeName = 3;
    
    /* 加密数据: DATA VARCHAR */
    public static final String PROP_NAME_data = "data";
    public static final int PROP_ID_data = 4;
    
    /* 状态: STATUS VARCHAR */
    public static final String PROP_NAME_status = "status";
    public static final int PROP_ID_status = 5;
    
    /* 删除标识: DEL_FLAG TINYINT */
    public static final String PROP_NAME_delFlag = "delFlag";
    public static final int PROP_ID_delFlag = 6;
    
    /* 使用范围: USAGE_SCOPE VARCHAR */
    public static final String PROP_NAME_usageScope = "usageScope";
    public static final int PROP_ID_usageScope = 7;
    
    /* 最后使用时间: LAST_USED_AT TIMESTAMP */
    public static final String PROP_NAME_lastUsedAt = "lastUsedAt";
    public static final int PROP_ID_lastUsedAt = 8;
    
    /* 过期时间: EXPIRE_AT TIMESTAMP */
    public static final String PROP_NAME_expireAt = "expireAt";
    public static final int PROP_ID_expireAt = 9;
    
    /* 连通性测试结果: TEST_RESULT VARCHAR */
    public static final String PROP_NAME_testResult = "testResult";
    public static final int PROP_ID_testResult = 10;
    
    /* 版本: VERSION INTEGER */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 11;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 12;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 13;
    
    /* 更新时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 14;
    
    /* 更新人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 15;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 16;
    

    private static int _PROP_ID_BOUND = 17;

    
    /* relation: 凭证使用记录 */
    public static final String PROP_NAME_usages = "usages";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_credentialId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_credentialId};

    private static final String[] PROP_ID_TO_NAME = new String[17];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_credentialId] = PROP_NAME_credentialId;
          PROP_NAME_TO_ID.put(PROP_NAME_credentialId, PROP_ID_credentialId);
      
          PROP_ID_TO_NAME[PROP_ID_name] = PROP_NAME_name;
          PROP_NAME_TO_ID.put(PROP_NAME_name, PROP_ID_name);
      
          PROP_ID_TO_NAME[PROP_ID_typeName] = PROP_NAME_typeName;
          PROP_NAME_TO_ID.put(PROP_NAME_typeName, PROP_ID_typeName);
      
          PROP_ID_TO_NAME[PROP_ID_data] = PROP_NAME_data;
          PROP_NAME_TO_ID.put(PROP_NAME_data, PROP_ID_data);
      
          PROP_ID_TO_NAME[PROP_ID_status] = PROP_NAME_status;
          PROP_NAME_TO_ID.put(PROP_NAME_status, PROP_ID_status);
      
          PROP_ID_TO_NAME[PROP_ID_delFlag] = PROP_NAME_delFlag;
          PROP_NAME_TO_ID.put(PROP_NAME_delFlag, PROP_ID_delFlag);
      
          PROP_ID_TO_NAME[PROP_ID_usageScope] = PROP_NAME_usageScope;
          PROP_NAME_TO_ID.put(PROP_NAME_usageScope, PROP_ID_usageScope);
      
          PROP_ID_TO_NAME[PROP_ID_lastUsedAt] = PROP_NAME_lastUsedAt;
          PROP_NAME_TO_ID.put(PROP_NAME_lastUsedAt, PROP_ID_lastUsedAt);
      
          PROP_ID_TO_NAME[PROP_ID_expireAt] = PROP_NAME_expireAt;
          PROP_NAME_TO_ID.put(PROP_NAME_expireAt, PROP_ID_expireAt);
      
          PROP_ID_TO_NAME[PROP_ID_testResult] = PROP_NAME_testResult;
          PROP_NAME_TO_ID.put(PROP_NAME_testResult, PROP_ID_testResult);
      
          PROP_ID_TO_NAME[PROP_ID_version] = PROP_NAME_version;
          PROP_NAME_TO_ID.put(PROP_NAME_version, PROP_ID_version);
      
          PROP_ID_TO_NAME[PROP_ID_createTime] = PROP_NAME_createTime;
          PROP_NAME_TO_ID.put(PROP_NAME_createTime, PROP_ID_createTime);
      
          PROP_ID_TO_NAME[PROP_ID_createdBy] = PROP_NAME_createdBy;
          PROP_NAME_TO_ID.put(PROP_NAME_createdBy, PROP_ID_createdBy);
      
          PROP_ID_TO_NAME[PROP_ID_updateTime] = PROP_NAME_updateTime;
          PROP_NAME_TO_ID.put(PROP_NAME_updateTime, PROP_ID_updateTime);
      
          PROP_ID_TO_NAME[PROP_ID_updatedBy] = PROP_NAME_updatedBy;
          PROP_NAME_TO_ID.put(PROP_NAME_updatedBy, PROP_ID_updatedBy);
      
          PROP_ID_TO_NAME[PROP_ID_remark] = PROP_NAME_remark;
          PROP_NAME_TO_ID.put(PROP_NAME_remark, PROP_ID_remark);
      
    }

    
    /* 凭证ID: CREDENTIAL_ID */
    private java.lang.String _credentialId;
    
    /* 凭证名: CREDENTIAL_NAME */
    private java.lang.String _name;
    
    /* 凭证类型: TYPE_NAME */
    private java.lang.String _typeName;
    
    /* 加密数据: DATA */
    private java.lang.String _data;
    
    /* 状态: STATUS */
    private java.lang.String _status;
    
    /* 删除标识: DEL_FLAG */
    private java.lang.Byte _delFlag;
    
    /* 使用范围: USAGE_SCOPE */
    private java.lang.String _usageScope;
    
    /* 最后使用时间: LAST_USED_AT */
    private java.sql.Timestamp _lastUsedAt;
    
    /* 过期时间: EXPIRE_AT */
    private java.sql.Timestamp _expireAt;
    
    /* 连通性测试结果: TEST_RESULT */
    private java.lang.String _testResult;
    
    /* 版本: VERSION */
    private java.lang.Integer _version;
    
    /* 创建时间: CREATE_TIME */
    private java.sql.Timestamp _createTime;
    
    /* 创建人: CREATED_BY */
    private java.lang.String _createdBy;
    
    /* 更新时间: UPDATE_TIME */
    private java.sql.Timestamp _updateTime;
    
    /* 更新人: UPDATED_BY */
    private java.lang.String _updatedBy;
    
    /* 备注: REMARK */
    private java.lang.String _remark;
    

    public _NopCredential(){
        // for debug
    }

    protected NopCredential newInstance(){
        NopCredential entity = new NopCredential();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopCredential cloneInstance() {
        NopCredential entity = newInstance();
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
      return "io.nop.credential.dao.entity.NopCredential";
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
    
        return buildSimpleId(PROP_ID_credentialId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_credentialId;
          
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
        
            case PROP_ID_credentialId:
               return getCredentialId();
        
            case PROP_ID_name:
               return getName();
        
            case PROP_ID_typeName:
               return getTypeName();
        
            case PROP_ID_data:
               return getData();
        
            case PROP_ID_status:
               return getStatus();
        
            case PROP_ID_delFlag:
               return getDelFlag();
        
            case PROP_ID_usageScope:
               return getUsageScope();
        
            case PROP_ID_lastUsedAt:
               return getLastUsedAt();
        
            case PROP_ID_expireAt:
               return getExpireAt();
        
            case PROP_ID_testResult:
               return getTestResult();
        
            case PROP_ID_version:
               return getVersion();
        
            case PROP_ID_createTime:
               return getCreateTime();
        
            case PROP_ID_createdBy:
               return getCreatedBy();
        
            case PROP_ID_updateTime:
               return getUpdateTime();
        
            case PROP_ID_updatedBy:
               return getUpdatedBy();
        
            case PROP_ID_remark:
               return getRemark();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_credentialId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_credentialId));
               }
               setCredentialId(typedValue);
               break;
            }
        
            case PROP_ID_name:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_name));
               }
               setName(typedValue);
               break;
            }
        
            case PROP_ID_typeName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_typeName));
               }
               setTypeName(typedValue);
               break;
            }
        
            case PROP_ID_data:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_data));
               }
               setData(typedValue);
               break;
            }
        
            case PROP_ID_status:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_status));
               }
               setStatus(typedValue);
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
        
            case PROP_ID_usageScope:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_usageScope));
               }
               setUsageScope(typedValue);
               break;
            }
        
            case PROP_ID_lastUsedAt:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_lastUsedAt));
               }
               setLastUsedAt(typedValue);
               break;
            }
        
            case PROP_ID_expireAt:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_expireAt));
               }
               setExpireAt(typedValue);
               break;
            }
        
            case PROP_ID_testResult:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_testResult));
               }
               setTestResult(typedValue);
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
        
            case PROP_ID_createdBy:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_createdBy));
               }
               setCreatedBy(typedValue);
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
        
            case PROP_ID_updatedBy:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_updatedBy));
               }
               setUpdatedBy(typedValue);
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
        
            case PROP_ID_credentialId:{
               onInitProp(propId);
               this._credentialId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_name:{
               onInitProp(propId);
               this._name = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_typeName:{
               onInitProp(propId);
               this._typeName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_data:{
               onInitProp(propId);
               this._data = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_status:{
               onInitProp(propId);
               this._status = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_delFlag:{
               onInitProp(propId);
               this._delFlag = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_usageScope:{
               onInitProp(propId);
               this._usageScope = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_lastUsedAt:{
               onInitProp(propId);
               this._lastUsedAt = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_expireAt:{
               onInitProp(propId);
               this._expireAt = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_testResult:{
               onInitProp(propId);
               this._testResult = (java.lang.String)value;
               
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
        
            case PROP_ID_createdBy:{
               onInitProp(propId);
               this._createdBy = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_updateTime:{
               onInitProp(propId);
               this._updateTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_updatedBy:{
               onInitProp(propId);
               this._updatedBy = (java.lang.String)value;
               
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
     * 凭证ID: CREDENTIAL_ID
     */
    public final java.lang.String getCredentialId(){
         onPropGet(PROP_ID_credentialId);
         return _credentialId;
    }

    /**
     * 凭证ID: CREDENTIAL_ID
     */
    public final void setCredentialId(java.lang.String value){
        if(onPropSet(PROP_ID_credentialId,value)){
            this._credentialId = value;
            internalClearRefs(PROP_ID_credentialId);
            orm_id();
        }
    }
    
    /**
     * 凭证名: CREDENTIAL_NAME
     */
    public final java.lang.String getName(){
         onPropGet(PROP_ID_name);
         return _name;
    }

    /**
     * 凭证名: CREDENTIAL_NAME
     */
    public final void setName(java.lang.String value){
        if(onPropSet(PROP_ID_name,value)){
            this._name = value;
            internalClearRefs(PROP_ID_name);
            
        }
    }
    
    /**
     * 凭证类型: TYPE_NAME
     */
    public final java.lang.String getTypeName(){
         onPropGet(PROP_ID_typeName);
         return _typeName;
    }

    /**
     * 凭证类型: TYPE_NAME
     */
    public final void setTypeName(java.lang.String value){
        if(onPropSet(PROP_ID_typeName,value)){
            this._typeName = value;
            internalClearRefs(PROP_ID_typeName);
            
        }
    }
    
    /**
     * 加密数据: DATA
     */
    public final java.lang.String getData(){
         onPropGet(PROP_ID_data);
         return _data;
    }

    /**
     * 加密数据: DATA
     */
    public final void setData(java.lang.String value){
        if(onPropSet(PROP_ID_data,value)){
            this._data = value;
            internalClearRefs(PROP_ID_data);
            
        }
    }
    
    /**
     * 状态: STATUS
     */
    public final java.lang.String getStatus(){
         onPropGet(PROP_ID_status);
         return _status;
    }

    /**
     * 状态: STATUS
     */
    public final void setStatus(java.lang.String value){
        if(onPropSet(PROP_ID_status,value)){
            this._status = value;
            internalClearRefs(PROP_ID_status);
            
        }
    }
    
    /**
     * 删除标识: DEL_FLAG
     */
    public final java.lang.Byte getDelFlag(){
         onPropGet(PROP_ID_delFlag);
         return _delFlag;
    }

    /**
     * 删除标识: DEL_FLAG
     */
    public final void setDelFlag(java.lang.Byte value){
        if(onPropSet(PROP_ID_delFlag,value)){
            this._delFlag = value;
            internalClearRefs(PROP_ID_delFlag);
            
        }
    }
    
    /**
     * 使用范围: USAGE_SCOPE
     */
    public final java.lang.String getUsageScope(){
         onPropGet(PROP_ID_usageScope);
         return _usageScope;
    }

    /**
     * 使用范围: USAGE_SCOPE
     */
    public final void setUsageScope(java.lang.String value){
        if(onPropSet(PROP_ID_usageScope,value)){
            this._usageScope = value;
            internalClearRefs(PROP_ID_usageScope);
            
        }
    }
    
    /**
     * 最后使用时间: LAST_USED_AT
     */
    public final java.sql.Timestamp getLastUsedAt(){
         onPropGet(PROP_ID_lastUsedAt);
         return _lastUsedAt;
    }

    /**
     * 最后使用时间: LAST_USED_AT
     */
    public final void setLastUsedAt(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_lastUsedAt,value)){
            this._lastUsedAt = value;
            internalClearRefs(PROP_ID_lastUsedAt);
            
        }
    }
    
    /**
     * 过期时间: EXPIRE_AT
     */
    public final java.sql.Timestamp getExpireAt(){
         onPropGet(PROP_ID_expireAt);
         return _expireAt;
    }

    /**
     * 过期时间: EXPIRE_AT
     */
    public final void setExpireAt(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_expireAt,value)){
            this._expireAt = value;
            internalClearRefs(PROP_ID_expireAt);
            
        }
    }
    
    /**
     * 连通性测试结果: TEST_RESULT
     */
    public final java.lang.String getTestResult(){
         onPropGet(PROP_ID_testResult);
         return _testResult;
    }

    /**
     * 连通性测试结果: TEST_RESULT
     */
    public final void setTestResult(java.lang.String value){
        if(onPropSet(PROP_ID_testResult,value)){
            this._testResult = value;
            internalClearRefs(PROP_ID_testResult);
            
        }
    }
    
    /**
     * 版本: VERSION
     */
    public final java.lang.Integer getVersion(){
         onPropGet(PROP_ID_version);
         return _version;
    }

    /**
     * 版本: VERSION
     */
    public final void setVersion(java.lang.Integer value){
        if(onPropSet(PROP_ID_version,value)){
            this._version = value;
            internalClearRefs(PROP_ID_version);
            
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
     * 更新时间: UPDATE_TIME
     */
    public final java.sql.Timestamp getUpdateTime(){
         onPropGet(PROP_ID_updateTime);
         return _updateTime;
    }

    /**
     * 更新时间: UPDATE_TIME
     */
    public final void setUpdateTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_updateTime,value)){
            this._updateTime = value;
            internalClearRefs(PROP_ID_updateTime);
            
        }
    }
    
    /**
     * 更新人: UPDATED_BY
     */
    public final java.lang.String getUpdatedBy(){
         onPropGet(PROP_ID_updatedBy);
         return _updatedBy;
    }

    /**
     * 更新人: UPDATED_BY
     */
    public final void setUpdatedBy(java.lang.String value){
        if(onPropSet(PROP_ID_updatedBy,value)){
            this._updatedBy = value;
            internalClearRefs(PROP_ID_updatedBy);
            
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
    
    private final OrmEntitySet<io.nop.credential.dao.entity.NopCredentialUsage> _usages = new OrmEntitySet<>(this, PROP_NAME_usages,
        io.nop.credential.dao.entity.NopCredentialUsage.PROP_NAME_credential, null,io.nop.credential.dao.entity.NopCredentialUsage.class);

    /**
     * 凭证使用记录。 refPropName: credential, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.credential.dao.entity.NopCredentialUsage> getUsages(){
       return _usages;
    }
       
}
// resume CPD analysis - CPD-ON
