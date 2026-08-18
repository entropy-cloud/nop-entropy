package io.nop.auth.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.auth.dao.entity.NopAuthMfaCredential;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  WebAuthn凭证: nop_auth_mfa_credential
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopAuthMfaCredential extends DynamicOrmEntity{
    
    /* 主键: SID VARCHAR */
    public static final String PROP_NAME_sid = "sid";
    public static final int PROP_ID_sid = 1;
    
    /* 用户ID: USER_ID VARCHAR */
    public static final String PROP_NAME_userId = "userId";
    public static final int PROP_ID_userId = 2;
    
    /* 凭证ID: CREDENTIAL_ID VARCHAR */
    public static final String PROP_NAME_credentialId = "credentialId";
    public static final int PROP_ID_credentialId = 3;
    
    /* COSE公钥: PUBLIC_KEY VARCHAR */
    public static final String PROP_NAME_publicKey = "publicKey";
    public static final int PROP_ID_publicKey = 4;
    
    /* 签名计数: SIGN_COUNT BIGINT */
    public static final String PROP_NAME_signCount = "signCount";
    public static final int PROP_ID_signCount = 5;
    
    /* 传输方式: TRANSPORTS VARCHAR */
    public static final String PROP_NAME_transports = "transports";
    public static final int PROP_ID_transports = 6;
    
    /* 设备名称: NAME VARCHAR */
    public static final String PROP_NAME_name = "name";
    public static final int PROP_ID_name = 7;
    
    /* 凭证状态: STATUS VARCHAR */
    public static final String PROP_NAME_status = "status";
    public static final int PROP_ID_status = 8;
    
    /* 最近使用时间: LAST_USED_AT TIMESTAMP */
    public static final String PROP_NAME_lastUsedAt = "lastUsedAt";
    public static final int PROP_ID_lastUsedAt = 9;
    
    /* 删除标识: DEL_FLAG TINYINT */
    public static final String PROP_NAME_delFlag = "delFlag";
    public static final int PROP_ID_delFlag = 10;
    
    /* 数据版本: VERSION INTEGER */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 11;
    
    /* 租户ID: TENANT_ID VARCHAR */
    public static final String PROP_NAME_tenantId = "tenantId";
    public static final int PROP_ID_tenantId = 12;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 13;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 14;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 15;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 16;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 17;
    

    private static int _PROP_ID_BOUND = 18;

    
    /* relation: 用户 */
    public static final String PROP_NAME_user = "user";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_sid);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_sid};

    private static final String[] PROP_ID_TO_NAME = new String[18];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_sid] = PROP_NAME_sid;
          PROP_NAME_TO_ID.put(PROP_NAME_sid, PROP_ID_sid);
      
          PROP_ID_TO_NAME[PROP_ID_userId] = PROP_NAME_userId;
          PROP_NAME_TO_ID.put(PROP_NAME_userId, PROP_ID_userId);
      
          PROP_ID_TO_NAME[PROP_ID_credentialId] = PROP_NAME_credentialId;
          PROP_NAME_TO_ID.put(PROP_NAME_credentialId, PROP_ID_credentialId);
      
          PROP_ID_TO_NAME[PROP_ID_publicKey] = PROP_NAME_publicKey;
          PROP_NAME_TO_ID.put(PROP_NAME_publicKey, PROP_ID_publicKey);
      
          PROP_ID_TO_NAME[PROP_ID_signCount] = PROP_NAME_signCount;
          PROP_NAME_TO_ID.put(PROP_NAME_signCount, PROP_ID_signCount);
      
          PROP_ID_TO_NAME[PROP_ID_transports] = PROP_NAME_transports;
          PROP_NAME_TO_ID.put(PROP_NAME_transports, PROP_ID_transports);
      
          PROP_ID_TO_NAME[PROP_ID_name] = PROP_NAME_name;
          PROP_NAME_TO_ID.put(PROP_NAME_name, PROP_ID_name);
      
          PROP_ID_TO_NAME[PROP_ID_status] = PROP_NAME_status;
          PROP_NAME_TO_ID.put(PROP_NAME_status, PROP_ID_status);
      
          PROP_ID_TO_NAME[PROP_ID_lastUsedAt] = PROP_NAME_lastUsedAt;
          PROP_NAME_TO_ID.put(PROP_NAME_lastUsedAt, PROP_ID_lastUsedAt);
      
          PROP_ID_TO_NAME[PROP_ID_delFlag] = PROP_NAME_delFlag;
          PROP_NAME_TO_ID.put(PROP_NAME_delFlag, PROP_ID_delFlag);
      
          PROP_ID_TO_NAME[PROP_ID_version] = PROP_NAME_version;
          PROP_NAME_TO_ID.put(PROP_NAME_version, PROP_ID_version);
      
          PROP_ID_TO_NAME[PROP_ID_tenantId] = PROP_NAME_tenantId;
          PROP_NAME_TO_ID.put(PROP_NAME_tenantId, PROP_ID_tenantId);
      
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

    
    /* 主键: SID */
    private java.lang.String _sid;
    
    /* 用户ID: USER_ID */
    private java.lang.String _userId;
    
    /* 凭证ID: CREDENTIAL_ID */
    private java.lang.String _credentialId;
    
    /* COSE公钥: PUBLIC_KEY */
    private java.lang.String _publicKey;
    
    /* 签名计数: SIGN_COUNT */
    private java.lang.Long _signCount;
    
    /* 传输方式: TRANSPORTS */
    private java.lang.String _transports;
    
    /* 设备名称: NAME */
    private java.lang.String _name;
    
    /* 凭证状态: STATUS */
    private java.lang.String _status;
    
    /* 最近使用时间: LAST_USED_AT */
    private java.sql.Timestamp _lastUsedAt;
    
    /* 删除标识: DEL_FLAG */
    private java.lang.Byte _delFlag;
    
    /* 数据版本: VERSION */
    private java.lang.Integer _version;
    
    /* 租户ID: TENANT_ID */
    private java.lang.String _tenantId;
    
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
    

    public _NopAuthMfaCredential(){
        // for debug
    }

    protected NopAuthMfaCredential newInstance(){
        NopAuthMfaCredential entity = new NopAuthMfaCredential();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopAuthMfaCredential cloneInstance() {
        NopAuthMfaCredential entity = newInstance();
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
      return "io.nop.auth.dao.entity.NopAuthMfaCredential";
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
    
        return buildSimpleId(PROP_ID_sid);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_sid;
          
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
        
            case PROP_ID_sid:
               return getSid();
        
            case PROP_ID_userId:
               return getUserId();
        
            case PROP_ID_credentialId:
               return getCredentialId();
        
            case PROP_ID_publicKey:
               return getPublicKey();
        
            case PROP_ID_signCount:
               return getSignCount();
        
            case PROP_ID_transports:
               return getTransports();
        
            case PROP_ID_name:
               return getName();
        
            case PROP_ID_status:
               return getStatus();
        
            case PROP_ID_lastUsedAt:
               return getLastUsedAt();
        
            case PROP_ID_delFlag:
               return getDelFlag();
        
            case PROP_ID_version:
               return getVersion();
        
            case PROP_ID_tenantId:
               return getTenantId();
        
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
        
            case PROP_ID_sid:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_sid));
               }
               setSid(typedValue);
               break;
            }
        
            case PROP_ID_userId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_userId));
               }
               setUserId(typedValue);
               break;
            }
        
            case PROP_ID_credentialId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_credentialId));
               }
               setCredentialId(typedValue);
               break;
            }
        
            case PROP_ID_publicKey:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_publicKey));
               }
               setPublicKey(typedValue);
               break;
            }
        
            case PROP_ID_signCount:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_signCount));
               }
               setSignCount(typedValue);
               break;
            }
        
            case PROP_ID_transports:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_transports));
               }
               setTransports(typedValue);
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
        
            case PROP_ID_status:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_status));
               }
               setStatus(typedValue);
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
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_version));
               }
               setVersion(typedValue);
               break;
            }
        
            case PROP_ID_tenantId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_tenantId));
               }
               setTenantId(typedValue);
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
        
            case PROP_ID_sid:{
               onInitProp(propId);
               this._sid = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_userId:{
               onInitProp(propId);
               this._userId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_credentialId:{
               onInitProp(propId);
               this._credentialId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_publicKey:{
               onInitProp(propId);
               this._publicKey = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_signCount:{
               onInitProp(propId);
               this._signCount = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_transports:{
               onInitProp(propId);
               this._transports = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_name:{
               onInitProp(propId);
               this._name = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_status:{
               onInitProp(propId);
               this._status = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_lastUsedAt:{
               onInitProp(propId);
               this._lastUsedAt = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_delFlag:{
               onInitProp(propId);
               this._delFlag = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_version:{
               onInitProp(propId);
               this._version = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_tenantId:{
               onInitProp(propId);
               this._tenantId = (java.lang.String)value;
               
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
     * 主键: SID
     */
    public final java.lang.String getSid(){
         onPropGet(PROP_ID_sid);
         return _sid;
    }

    /**
     * 主键: SID
     */
    public final void setSid(java.lang.String value){
        if(onPropSet(PROP_ID_sid,value)){
            this._sid = value;
            internalClearRefs(PROP_ID_sid);
            orm_id();
        }
    }
    
    /**
     * 用户ID: USER_ID
     */
    public final java.lang.String getUserId(){
         onPropGet(PROP_ID_userId);
         return _userId;
    }

    /**
     * 用户ID: USER_ID
     */
    public final void setUserId(java.lang.String value){
        if(onPropSet(PROP_ID_userId,value)){
            this._userId = value;
            internalClearRefs(PROP_ID_userId);
            
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
            
        }
    }
    
    /**
     * COSE公钥: PUBLIC_KEY
     */
    public final java.lang.String getPublicKey(){
         onPropGet(PROP_ID_publicKey);
         return _publicKey;
    }

    /**
     * COSE公钥: PUBLIC_KEY
     */
    public final void setPublicKey(java.lang.String value){
        if(onPropSet(PROP_ID_publicKey,value)){
            this._publicKey = value;
            internalClearRefs(PROP_ID_publicKey);
            
        }
    }
    
    /**
     * 签名计数: SIGN_COUNT
     */
    public final java.lang.Long getSignCount(){
         onPropGet(PROP_ID_signCount);
         return _signCount;
    }

    /**
     * 签名计数: SIGN_COUNT
     */
    public final void setSignCount(java.lang.Long value){
        if(onPropSet(PROP_ID_signCount,value)){
            this._signCount = value;
            internalClearRefs(PROP_ID_signCount);
            
        }
    }
    
    /**
     * 传输方式: TRANSPORTS
     */
    public final java.lang.String getTransports(){
         onPropGet(PROP_ID_transports);
         return _transports;
    }

    /**
     * 传输方式: TRANSPORTS
     */
    public final void setTransports(java.lang.String value){
        if(onPropSet(PROP_ID_transports,value)){
            this._transports = value;
            internalClearRefs(PROP_ID_transports);
            
        }
    }
    
    /**
     * 设备名称: NAME
     */
    public final java.lang.String getName(){
         onPropGet(PROP_ID_name);
         return _name;
    }

    /**
     * 设备名称: NAME
     */
    public final void setName(java.lang.String value){
        if(onPropSet(PROP_ID_name,value)){
            this._name = value;
            internalClearRefs(PROP_ID_name);
            
        }
    }
    
    /**
     * 凭证状态: STATUS
     */
    public final java.lang.String getStatus(){
         onPropGet(PROP_ID_status);
         return _status;
    }

    /**
     * 凭证状态: STATUS
     */
    public final void setStatus(java.lang.String value){
        if(onPropSet(PROP_ID_status,value)){
            this._status = value;
            internalClearRefs(PROP_ID_status);
            
        }
    }
    
    /**
     * 最近使用时间: LAST_USED_AT
     */
    public final java.sql.Timestamp getLastUsedAt(){
         onPropGet(PROP_ID_lastUsedAt);
         return _lastUsedAt;
    }

    /**
     * 最近使用时间: LAST_USED_AT
     */
    public final void setLastUsedAt(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_lastUsedAt,value)){
            this._lastUsedAt = value;
            internalClearRefs(PROP_ID_lastUsedAt);
            
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
     * 数据版本: VERSION
     */
    public final java.lang.Integer getVersion(){
         onPropGet(PROP_ID_version);
         return _version;
    }

    /**
     * 数据版本: VERSION
     */
    public final void setVersion(java.lang.Integer value){
        if(onPropSet(PROP_ID_version,value)){
            this._version = value;
            internalClearRefs(PROP_ID_version);
            
        }
    }
    
    /**
     * 租户ID: TENANT_ID
     */
    public final java.lang.String getTenantId(){
         onPropGet(PROP_ID_tenantId);
         return _tenantId;
    }

    /**
     * 租户ID: TENANT_ID
     */
    public final void setTenantId(java.lang.String value){
        if(onPropSet(PROP_ID_tenantId,value)){
            this._tenantId = value;
            internalClearRefs(PROP_ID_tenantId);
            
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
     * 用户
     */
    public final io.nop.auth.dao.entity.NopAuthUser getUser(){
       return (io.nop.auth.dao.entity.NopAuthUser)internalGetRefEntity(PROP_NAME_user);
    }

    public final void setUser(io.nop.auth.dao.entity.NopAuthUser refEntity){
   
           if(refEntity == null){
           
                   this.setUserId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_user, refEntity,()->{
           
                           this.setUserId(refEntity.getUserId());
                       
           });
           }
       
    }
       
}
// resume CPD analysis - CPD-ON
