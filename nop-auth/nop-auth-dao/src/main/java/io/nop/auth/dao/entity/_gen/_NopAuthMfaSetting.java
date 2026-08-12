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

import io.nop.auth.dao.entity.NopAuthMfaSetting;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  用户MFA配置: nop_auth_mfa_setting
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopAuthMfaSetting extends DynamicOrmEntity{
    
    /* 用户ID: USER_ID VARCHAR */
    public static final String PROP_NAME_userId = "userId";
    public static final int PROP_ID_userId = 1;
    
    /* MFA类型: MFA_TYPE VARCHAR */
    public static final String PROP_NAME_mfaType = "mfaType";
    public static final int PROP_ID_mfaType = 2;
    
    /* TOTP密钥: SECRET VARCHAR */
    public static final String PROP_NAME_secret = "secret";
    public static final int PROP_ID_secret = 3;
    
    /* 绑定状态: STATUS VARCHAR */
    public static final String PROP_NAME_status = "status";
    public static final int PROP_ID_status = 4;
    
    /* 绑定令牌: BIND_TOKEN VARCHAR */
    public static final String PROP_NAME_bindToken = "bindToken";
    public static final int PROP_ID_bindToken = 5;
    
    /* MFA手机号: PHONE VARCHAR */
    public static final String PROP_NAME_phone = "phone";
    public static final int PROP_ID_phone = 6;
    
    /* 最近验证窗口: LAST_VERIFIED_WINDOW BIGINT */
    public static final String PROP_NAME_lastVerifiedWindow = "lastVerifiedWindow";
    public static final int PROP_ID_lastVerifiedWindow = 7;
    
    /* 最近验证时间: LAST_VERIFIED_AT TIMESTAMP */
    public static final String PROP_NAME_lastVerifiedAt = "lastVerifiedAt";
    public static final int PROP_ID_lastVerifiedAt = 8;
    
    /* 删除标识: DEL_FLAG TINYINT */
    public static final String PROP_NAME_delFlag = "delFlag";
    public static final int PROP_ID_delFlag = 9;
    
    /* 数据版本: VERSION INTEGER */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 10;
    
    /* 租户ID: TENANT_ID VARCHAR */
    public static final String PROP_NAME_tenantId = "tenantId";
    public static final int PROP_ID_tenantId = 11;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 12;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 13;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 14;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 15;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 16;
    

    private static int _PROP_ID_BOUND = 17;

    
    /* relation: 用户 */
    public static final String PROP_NAME_user = "user";
    
    /* relation: 恢复码 */
    public static final String PROP_NAME_recoveryCodes = "recoveryCodes";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_userId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_userId};

    private static final String[] PROP_ID_TO_NAME = new String[17];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_userId] = PROP_NAME_userId;
          PROP_NAME_TO_ID.put(PROP_NAME_userId, PROP_ID_userId);
      
          PROP_ID_TO_NAME[PROP_ID_mfaType] = PROP_NAME_mfaType;
          PROP_NAME_TO_ID.put(PROP_NAME_mfaType, PROP_ID_mfaType);
      
          PROP_ID_TO_NAME[PROP_ID_secret] = PROP_NAME_secret;
          PROP_NAME_TO_ID.put(PROP_NAME_secret, PROP_ID_secret);
      
          PROP_ID_TO_NAME[PROP_ID_status] = PROP_NAME_status;
          PROP_NAME_TO_ID.put(PROP_NAME_status, PROP_ID_status);
      
          PROP_ID_TO_NAME[PROP_ID_bindToken] = PROP_NAME_bindToken;
          PROP_NAME_TO_ID.put(PROP_NAME_bindToken, PROP_ID_bindToken);
      
          PROP_ID_TO_NAME[PROP_ID_phone] = PROP_NAME_phone;
          PROP_NAME_TO_ID.put(PROP_NAME_phone, PROP_ID_phone);
      
          PROP_ID_TO_NAME[PROP_ID_lastVerifiedWindow] = PROP_NAME_lastVerifiedWindow;
          PROP_NAME_TO_ID.put(PROP_NAME_lastVerifiedWindow, PROP_ID_lastVerifiedWindow);
      
          PROP_ID_TO_NAME[PROP_ID_lastVerifiedAt] = PROP_NAME_lastVerifiedAt;
          PROP_NAME_TO_ID.put(PROP_NAME_lastVerifiedAt, PROP_ID_lastVerifiedAt);
      
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

    
    /* 用户ID: USER_ID */
    private java.lang.String _userId;
    
    /* MFA类型: MFA_TYPE */
    private java.lang.String _mfaType;
    
    /* TOTP密钥: SECRET */
    private java.lang.String _secret;
    
    /* 绑定状态: STATUS */
    private java.lang.String _status;
    
    /* 绑定令牌: BIND_TOKEN */
    private java.lang.String _bindToken;
    
    /* MFA手机号: PHONE */
    private java.lang.String _phone;
    
    /* 最近验证窗口: LAST_VERIFIED_WINDOW */
    private java.lang.Long _lastVerifiedWindow;
    
    /* 最近验证时间: LAST_VERIFIED_AT */
    private java.sql.Timestamp _lastVerifiedAt;
    
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
    

    public _NopAuthMfaSetting(){
        // for debug
    }

    protected NopAuthMfaSetting newInstance(){
        NopAuthMfaSetting entity = new NopAuthMfaSetting();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopAuthMfaSetting cloneInstance() {
        NopAuthMfaSetting entity = newInstance();
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
      return "io.nop.auth.dao.entity.NopAuthMfaSetting";
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
    
        return buildSimpleId(PROP_ID_userId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_userId;
          
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
        
            case PROP_ID_userId:
               return getUserId();
        
            case PROP_ID_mfaType:
               return getMfaType();
        
            case PROP_ID_secret:
               return getSecret();
        
            case PROP_ID_status:
               return getStatus();
        
            case PROP_ID_bindToken:
               return getBindToken();
        
            case PROP_ID_phone:
               return getPhone();
        
            case PROP_ID_lastVerifiedWindow:
               return getLastVerifiedWindow();
        
            case PROP_ID_lastVerifiedAt:
               return getLastVerifiedAt();
        
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
        
            case PROP_ID_userId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_userId));
               }
               setUserId(typedValue);
               break;
            }
        
            case PROP_ID_mfaType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_mfaType));
               }
               setMfaType(typedValue);
               break;
            }
        
            case PROP_ID_secret:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_secret));
               }
               setSecret(typedValue);
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
        
            case PROP_ID_bindToken:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_bindToken));
               }
               setBindToken(typedValue);
               break;
            }
        
            case PROP_ID_phone:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_phone));
               }
               setPhone(typedValue);
               break;
            }
        
            case PROP_ID_lastVerifiedWindow:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_lastVerifiedWindow));
               }
               setLastVerifiedWindow(typedValue);
               break;
            }
        
            case PROP_ID_lastVerifiedAt:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_lastVerifiedAt));
               }
               setLastVerifiedAt(typedValue);
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
        
            case PROP_ID_userId:{
               onInitProp(propId);
               this._userId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_mfaType:{
               onInitProp(propId);
               this._mfaType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_secret:{
               onInitProp(propId);
               this._secret = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_status:{
               onInitProp(propId);
               this._status = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_bindToken:{
               onInitProp(propId);
               this._bindToken = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_phone:{
               onInitProp(propId);
               this._phone = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_lastVerifiedWindow:{
               onInitProp(propId);
               this._lastVerifiedWindow = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_lastVerifiedAt:{
               onInitProp(propId);
               this._lastVerifiedAt = (java.sql.Timestamp)value;
               
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
            orm_id();
        }
    }
    
    /**
     * MFA类型: MFA_TYPE
     */
    public final java.lang.String getMfaType(){
         onPropGet(PROP_ID_mfaType);
         return _mfaType;
    }

    /**
     * MFA类型: MFA_TYPE
     */
    public final void setMfaType(java.lang.String value){
        if(onPropSet(PROP_ID_mfaType,value)){
            this._mfaType = value;
            internalClearRefs(PROP_ID_mfaType);
            
        }
    }
    
    /**
     * TOTP密钥: SECRET
     */
    public final java.lang.String getSecret(){
         onPropGet(PROP_ID_secret);
         return _secret;
    }

    /**
     * TOTP密钥: SECRET
     */
    public final void setSecret(java.lang.String value){
        if(onPropSet(PROP_ID_secret,value)){
            this._secret = value;
            internalClearRefs(PROP_ID_secret);
            
        }
    }
    
    /**
     * 绑定状态: STATUS
     */
    public final java.lang.String getStatus(){
         onPropGet(PROP_ID_status);
         return _status;
    }

    /**
     * 绑定状态: STATUS
     */
    public final void setStatus(java.lang.String value){
        if(onPropSet(PROP_ID_status,value)){
            this._status = value;
            internalClearRefs(PROP_ID_status);
            
        }
    }
    
    /**
     * 绑定令牌: BIND_TOKEN
     */
    public final java.lang.String getBindToken(){
         onPropGet(PROP_ID_bindToken);
         return _bindToken;
    }

    /**
     * 绑定令牌: BIND_TOKEN
     */
    public final void setBindToken(java.lang.String value){
        if(onPropSet(PROP_ID_bindToken,value)){
            this._bindToken = value;
            internalClearRefs(PROP_ID_bindToken);
            
        }
    }
    
    /**
     * MFA手机号: PHONE
     */
    public final java.lang.String getPhone(){
         onPropGet(PROP_ID_phone);
         return _phone;
    }

    /**
     * MFA手机号: PHONE
     */
    public final void setPhone(java.lang.String value){
        if(onPropSet(PROP_ID_phone,value)){
            this._phone = value;
            internalClearRefs(PROP_ID_phone);
            
        }
    }
    
    /**
     * 最近验证窗口: LAST_VERIFIED_WINDOW
     */
    public final java.lang.Long getLastVerifiedWindow(){
         onPropGet(PROP_ID_lastVerifiedWindow);
         return _lastVerifiedWindow;
    }

    /**
     * 最近验证窗口: LAST_VERIFIED_WINDOW
     */
    public final void setLastVerifiedWindow(java.lang.Long value){
        if(onPropSet(PROP_ID_lastVerifiedWindow,value)){
            this._lastVerifiedWindow = value;
            internalClearRefs(PROP_ID_lastVerifiedWindow);
            
        }
    }
    
    /**
     * 最近验证时间: LAST_VERIFIED_AT
     */
    public final java.sql.Timestamp getLastVerifiedAt(){
         onPropGet(PROP_ID_lastVerifiedAt);
         return _lastVerifiedAt;
    }

    /**
     * 最近验证时间: LAST_VERIFIED_AT
     */
    public final void setLastVerifiedAt(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_lastVerifiedAt,value)){
            this._lastVerifiedAt = value;
            internalClearRefs(PROP_ID_lastVerifiedAt);
            
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
       
    private final OrmEntitySet<io.nop.auth.dao.entity.NopAuthMfaRecoveryCode> _recoveryCodes = new OrmEntitySet<>(this, PROP_NAME_recoveryCodes,
        io.nop.auth.dao.entity.NopAuthMfaRecoveryCode.PROP_NAME_setting, null,io.nop.auth.dao.entity.NopAuthMfaRecoveryCode.class);

    /**
     * 恢复码。 refPropName: setting, keyProp: {rel.keyProp}
     */
    public final IOrmEntitySet<io.nop.auth.dao.entity.NopAuthMfaRecoveryCode> getRecoveryCodes(){
       return _recoveryCodes;
    }
       
}
// resume CPD analysis - CPD-ON
