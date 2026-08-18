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

import io.nop.auth.dao.entity.NopAuthMfaChallenge;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  MFA挑战码: nop_auth_mfa_challenge
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopAuthMfaChallenge extends DynamicOrmEntity{
    
    /* 挑战令牌: CHALLENGE_TOKEN VARCHAR */
    public static final String PROP_NAME_challengeToken = "challengeToken";
    public static final int PROP_ID_challengeToken = 1;
    
    /* 用户ID: USER_ID VARCHAR */
    public static final String PROP_NAME_userId = "userId";
    public static final int PROP_ID_userId = 2;
    
    /* MFA类型: MFA_TYPE VARCHAR */
    public static final String PROP_NAME_mfaType = "mfaType";
    public static final int PROP_ID_mfaType = 3;
    
    /* 登录方式: LOGIN_TYPE INTEGER */
    public static final String PROP_NAME_loginType = "loginType";
    public static final int PROP_ID_loginType = 4;
    
    /* 租户ID: TENANT_ID VARCHAR */
    public static final String PROP_NAME_tenantId = "tenantId";
    public static final int PROP_ID_tenantId = 5;
    
    /* 手机号: PHONE VARCHAR */
    public static final String PROP_NAME_phone = "phone";
    public static final int PROP_ID_phone = 6;
    
    /* 过期时间: EXPIRE_AT BIGINT */
    public static final String PROP_NAME_expireAt = "expireAt";
    public static final int PROP_ID_expireAt = 7;
    
    /* 失败计数: FAIL_COUNT INTEGER */
    public static final String PROP_NAME_failCount = "failCount";
    public static final int PROP_ID_failCount = 8;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 9;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 10;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 11;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 12;
    
    /* 场景: SCENE VARCHAR */
    public static final String PROP_NAME_scene = "scene";
    public static final int PROP_ID_scene = 13;
    
    /* 场景数据: PAYLOAD VARCHAR */
    public static final String PROP_NAME_payload = "payload";
    public static final int PROP_ID_payload = 14;
    
    /* 验证时间: VERIFIED_AT BIGINT */
    public static final String PROP_NAME_verifiedAt = "verifiedAt";
    public static final int PROP_ID_verifiedAt = 15;
    

    private static int _PROP_ID_BOUND = 16;

    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_challengeToken);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_challengeToken};

    private static final String[] PROP_ID_TO_NAME = new String[16];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_challengeToken] = PROP_NAME_challengeToken;
          PROP_NAME_TO_ID.put(PROP_NAME_challengeToken, PROP_ID_challengeToken);
      
          PROP_ID_TO_NAME[PROP_ID_userId] = PROP_NAME_userId;
          PROP_NAME_TO_ID.put(PROP_NAME_userId, PROP_ID_userId);
      
          PROP_ID_TO_NAME[PROP_ID_mfaType] = PROP_NAME_mfaType;
          PROP_NAME_TO_ID.put(PROP_NAME_mfaType, PROP_ID_mfaType);
      
          PROP_ID_TO_NAME[PROP_ID_loginType] = PROP_NAME_loginType;
          PROP_NAME_TO_ID.put(PROP_NAME_loginType, PROP_ID_loginType);
      
          PROP_ID_TO_NAME[PROP_ID_tenantId] = PROP_NAME_tenantId;
          PROP_NAME_TO_ID.put(PROP_NAME_tenantId, PROP_ID_tenantId);
      
          PROP_ID_TO_NAME[PROP_ID_phone] = PROP_NAME_phone;
          PROP_NAME_TO_ID.put(PROP_NAME_phone, PROP_ID_phone);
      
          PROP_ID_TO_NAME[PROP_ID_expireAt] = PROP_NAME_expireAt;
          PROP_NAME_TO_ID.put(PROP_NAME_expireAt, PROP_ID_expireAt);
      
          PROP_ID_TO_NAME[PROP_ID_failCount] = PROP_NAME_failCount;
          PROP_NAME_TO_ID.put(PROP_NAME_failCount, PROP_ID_failCount);
      
          PROP_ID_TO_NAME[PROP_ID_createdBy] = PROP_NAME_createdBy;
          PROP_NAME_TO_ID.put(PROP_NAME_createdBy, PROP_ID_createdBy);
      
          PROP_ID_TO_NAME[PROP_ID_createTime] = PROP_NAME_createTime;
          PROP_NAME_TO_ID.put(PROP_NAME_createTime, PROP_ID_createTime);
      
          PROP_ID_TO_NAME[PROP_ID_updatedBy] = PROP_NAME_updatedBy;
          PROP_NAME_TO_ID.put(PROP_NAME_updatedBy, PROP_ID_updatedBy);
      
          PROP_ID_TO_NAME[PROP_ID_updateTime] = PROP_NAME_updateTime;
          PROP_NAME_TO_ID.put(PROP_NAME_updateTime, PROP_ID_updateTime);
      
          PROP_ID_TO_NAME[PROP_ID_scene] = PROP_NAME_scene;
          PROP_NAME_TO_ID.put(PROP_NAME_scene, PROP_ID_scene);
      
          PROP_ID_TO_NAME[PROP_ID_payload] = PROP_NAME_payload;
          PROP_NAME_TO_ID.put(PROP_NAME_payload, PROP_ID_payload);
      
          PROP_ID_TO_NAME[PROP_ID_verifiedAt] = PROP_NAME_verifiedAt;
          PROP_NAME_TO_ID.put(PROP_NAME_verifiedAt, PROP_ID_verifiedAt);
      
    }

    
    /* 挑战令牌: CHALLENGE_TOKEN */
    private java.lang.String _challengeToken;
    
    /* 用户ID: USER_ID */
    private java.lang.String _userId;
    
    /* MFA类型: MFA_TYPE */
    private java.lang.String _mfaType;
    
    /* 登录方式: LOGIN_TYPE */
    private java.lang.Integer _loginType;
    
    /* 租户ID: TENANT_ID */
    private java.lang.String _tenantId;
    
    /* 手机号: PHONE */
    private java.lang.String _phone;
    
    /* 过期时间: EXPIRE_AT */
    private java.lang.Long _expireAt;
    
    /* 失败计数: FAIL_COUNT */
    private java.lang.Integer _failCount;
    
    /* 创建人: CREATED_BY */
    private java.lang.String _createdBy;
    
    /* 创建时间: CREATE_TIME */
    private java.sql.Timestamp _createTime;
    
    /* 修改人: UPDATED_BY */
    private java.lang.String _updatedBy;
    
    /* 修改时间: UPDATE_TIME */
    private java.sql.Timestamp _updateTime;
    
    /* 场景: SCENE */
    private java.lang.String _scene;
    
    /* 场景数据: PAYLOAD */
    private java.lang.String _payload;
    
    /* 验证时间: VERIFIED_AT */
    private java.lang.Long _verifiedAt;
    

    public _NopAuthMfaChallenge(){
        // for debug
    }

    protected NopAuthMfaChallenge newInstance(){
        NopAuthMfaChallenge entity = new NopAuthMfaChallenge();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopAuthMfaChallenge cloneInstance() {
        NopAuthMfaChallenge entity = newInstance();
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
      return "io.nop.auth.dao.entity.NopAuthMfaChallenge";
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
    
        return buildSimpleId(PROP_ID_challengeToken);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_challengeToken;
          
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
        
            case PROP_ID_challengeToken:
               return getChallengeToken();
        
            case PROP_ID_userId:
               return getUserId();
        
            case PROP_ID_mfaType:
               return getMfaType();
        
            case PROP_ID_loginType:
               return getLoginType();
        
            case PROP_ID_tenantId:
               return getTenantId();
        
            case PROP_ID_phone:
               return getPhone();
        
            case PROP_ID_expireAt:
               return getExpireAt();
        
            case PROP_ID_failCount:
               return getFailCount();
        
            case PROP_ID_createdBy:
               return getCreatedBy();
        
            case PROP_ID_createTime:
               return getCreateTime();
        
            case PROP_ID_updatedBy:
               return getUpdatedBy();
        
            case PROP_ID_updateTime:
               return getUpdateTime();
        
            case PROP_ID_scene:
               return getScene();
        
            case PROP_ID_payload:
               return getPayload();
        
            case PROP_ID_verifiedAt:
               return getVerifiedAt();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_challengeToken:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_challengeToken));
               }
               setChallengeToken(typedValue);
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
        
            case PROP_ID_mfaType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_mfaType));
               }
               setMfaType(typedValue);
               break;
            }
        
            case PROP_ID_loginType:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_loginType));
               }
               setLoginType(typedValue);
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
        
            case PROP_ID_phone:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_phone));
               }
               setPhone(typedValue);
               break;
            }
        
            case PROP_ID_expireAt:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_expireAt));
               }
               setExpireAt(typedValue);
               break;
            }
        
            case PROP_ID_failCount:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_failCount));
               }
               setFailCount(typedValue);
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
        
            case PROP_ID_scene:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_scene));
               }
               setScene(typedValue);
               break;
            }
        
            case PROP_ID_payload:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_payload));
               }
               setPayload(typedValue);
               break;
            }
        
            case PROP_ID_verifiedAt:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_verifiedAt));
               }
               setVerifiedAt(typedValue);
               break;
            }
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_challengeToken:{
               onInitProp(propId);
               this._challengeToken = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_userId:{
               onInitProp(propId);
               this._userId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_mfaType:{
               onInitProp(propId);
               this._mfaType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_loginType:{
               onInitProp(propId);
               this._loginType = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_tenantId:{
               onInitProp(propId);
               this._tenantId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_phone:{
               onInitProp(propId);
               this._phone = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_expireAt:{
               onInitProp(propId);
               this._expireAt = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_failCount:{
               onInitProp(propId);
               this._failCount = (java.lang.Integer)value;
               
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
        
            case PROP_ID_scene:{
               onInitProp(propId);
               this._scene = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_payload:{
               onInitProp(propId);
               this._payload = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_verifiedAt:{
               onInitProp(propId);
               this._verifiedAt = (java.lang.Long)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 挑战令牌: CHALLENGE_TOKEN
     */
    public final java.lang.String getChallengeToken(){
         onPropGet(PROP_ID_challengeToken);
         return _challengeToken;
    }

    /**
     * 挑战令牌: CHALLENGE_TOKEN
     */
    public final void setChallengeToken(java.lang.String value){
        if(onPropSet(PROP_ID_challengeToken,value)){
            this._challengeToken = value;
            internalClearRefs(PROP_ID_challengeToken);
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
     * 登录方式: LOGIN_TYPE
     */
    public final java.lang.Integer getLoginType(){
         onPropGet(PROP_ID_loginType);
         return _loginType;
    }

    /**
     * 登录方式: LOGIN_TYPE
     */
    public final void setLoginType(java.lang.Integer value){
        if(onPropSet(PROP_ID_loginType,value)){
            this._loginType = value;
            internalClearRefs(PROP_ID_loginType);
            
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
     * 手机号: PHONE
     */
    public final java.lang.String getPhone(){
         onPropGet(PROP_ID_phone);
         return _phone;
    }

    /**
     * 手机号: PHONE
     */
    public final void setPhone(java.lang.String value){
        if(onPropSet(PROP_ID_phone,value)){
            this._phone = value;
            internalClearRefs(PROP_ID_phone);
            
        }
    }
    
    /**
     * 过期时间: EXPIRE_AT
     */
    public final java.lang.Long getExpireAt(){
         onPropGet(PROP_ID_expireAt);
         return _expireAt;
    }

    /**
     * 过期时间: EXPIRE_AT
     */
    public final void setExpireAt(java.lang.Long value){
        if(onPropSet(PROP_ID_expireAt,value)){
            this._expireAt = value;
            internalClearRefs(PROP_ID_expireAt);
            
        }
    }
    
    /**
     * 失败计数: FAIL_COUNT
     */
    public final java.lang.Integer getFailCount(){
         onPropGet(PROP_ID_failCount);
         return _failCount;
    }

    /**
     * 失败计数: FAIL_COUNT
     */
    public final void setFailCount(java.lang.Integer value){
        if(onPropSet(PROP_ID_failCount,value)){
            this._failCount = value;
            internalClearRefs(PROP_ID_failCount);
            
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
     * 场景: SCENE
     */
    public final java.lang.String getScene(){
         onPropGet(PROP_ID_scene);
         return _scene;
    }

    /**
     * 场景: SCENE
     */
    public final void setScene(java.lang.String value){
        if(onPropSet(PROP_ID_scene,value)){
            this._scene = value;
            internalClearRefs(PROP_ID_scene);
            
        }
    }
    
    /**
     * 场景数据: PAYLOAD
     */
    public final java.lang.String getPayload(){
         onPropGet(PROP_ID_payload);
         return _payload;
    }

    /**
     * 场景数据: PAYLOAD
     */
    public final void setPayload(java.lang.String value){
        if(onPropSet(PROP_ID_payload,value)){
            this._payload = value;
            internalClearRefs(PROP_ID_payload);
            
        }
    }
    
    /**
     * 验证时间: VERIFIED_AT
     */
    public final java.lang.Long getVerifiedAt(){
         onPropGet(PROP_ID_verifiedAt);
         return _verifiedAt;
    }

    /**
     * 验证时间: VERIFIED_AT
     */
    public final void setVerifiedAt(java.lang.Long value){
        if(onPropSet(PROP_ID_verifiedAt,value)){
            this._verifiedAt = value;
            internalClearRefs(PROP_ID_verifiedAt);
            
        }
    }
    
}
// resume CPD analysis - CPD-ON
