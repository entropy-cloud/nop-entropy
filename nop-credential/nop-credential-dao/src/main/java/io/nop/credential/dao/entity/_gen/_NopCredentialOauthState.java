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

import io.nop.credential.dao.entity.NopCredentialOauthState;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  OAuth授权State绑定: nop_credential_oauth_state
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopCredentialOauthState extends DynamicOrmEntity{
    
    /* State令牌: STATE VARCHAR */
    public static final String PROP_NAME_state = "state";
    public static final int PROP_ID_state = 1;
    
    /* 凭证ID: CREDENTIAL_ID VARCHAR */
    public static final String PROP_NAME_credentialId = "credentialId";
    public static final int PROP_ID_credentialId = 2;
    
    /* 发起人: USER_ID VARCHAR */
    public static final String PROP_NAME_userId = "userId";
    public static final int PROP_ID_userId = 3;
    
    /* 过期时间: EXPIRE_AT BIGINT */
    public static final String PROP_NAME_expireAt = "expireAt";
    public static final int PROP_ID_expireAt = 4;
    
    /* 已消费: CONSUMED TINYINT */
    public static final String PROP_NAME_consumed = "consumed";
    public static final int PROP_ID_consumed = 5;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 6;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 7;
    

    private static int _PROP_ID_BOUND = 8;

    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_state);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_state};

    private static final String[] PROP_ID_TO_NAME = new String[8];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_state] = PROP_NAME_state;
          PROP_NAME_TO_ID.put(PROP_NAME_state, PROP_ID_state);
      
          PROP_ID_TO_NAME[PROP_ID_credentialId] = PROP_NAME_credentialId;
          PROP_NAME_TO_ID.put(PROP_NAME_credentialId, PROP_ID_credentialId);
      
          PROP_ID_TO_NAME[PROP_ID_userId] = PROP_NAME_userId;
          PROP_NAME_TO_ID.put(PROP_NAME_userId, PROP_ID_userId);
      
          PROP_ID_TO_NAME[PROP_ID_expireAt] = PROP_NAME_expireAt;
          PROP_NAME_TO_ID.put(PROP_NAME_expireAt, PROP_ID_expireAt);
      
          PROP_ID_TO_NAME[PROP_ID_consumed] = PROP_NAME_consumed;
          PROP_NAME_TO_ID.put(PROP_NAME_consumed, PROP_ID_consumed);
      
          PROP_ID_TO_NAME[PROP_ID_createTime] = PROP_NAME_createTime;
          PROP_NAME_TO_ID.put(PROP_NAME_createTime, PROP_ID_createTime);
      
          PROP_ID_TO_NAME[PROP_ID_createdBy] = PROP_NAME_createdBy;
          PROP_NAME_TO_ID.put(PROP_NAME_createdBy, PROP_ID_createdBy);
      
    }

    
    /* State令牌: STATE */
    private java.lang.String _state;
    
    /* 凭证ID: CREDENTIAL_ID */
    private java.lang.String _credentialId;
    
    /* 发起人: USER_ID */
    private java.lang.String _userId;
    
    /* 过期时间: EXPIRE_AT */
    private java.lang.Long _expireAt;
    
    /* 已消费: CONSUMED */
    private java.lang.Byte _consumed;
    
    /* 创建时间: CREATE_TIME */
    private java.sql.Timestamp _createTime;
    
    /* 创建人: CREATED_BY */
    private java.lang.String _createdBy;
    

    public _NopCredentialOauthState(){
        // for debug
    }

    protected NopCredentialOauthState newInstance(){
        NopCredentialOauthState entity = new NopCredentialOauthState();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopCredentialOauthState cloneInstance() {
        NopCredentialOauthState entity = newInstance();
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
      return "io.nop.credential.dao.entity.NopCredentialOauthState";
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
    
        return buildSimpleId(PROP_ID_state);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_state;
          
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
        
            case PROP_ID_state:
               return getState();
        
            case PROP_ID_credentialId:
               return getCredentialId();
        
            case PROP_ID_userId:
               return getUserId();
        
            case PROP_ID_expireAt:
               return getExpireAt();
        
            case PROP_ID_consumed:
               return getConsumed();
        
            case PROP_ID_createTime:
               return getCreateTime();
        
            case PROP_ID_createdBy:
               return getCreatedBy();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_state:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_state));
               }
               setState(typedValue);
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
        
            case PROP_ID_userId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_userId));
               }
               setUserId(typedValue);
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
        
            case PROP_ID_consumed:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_consumed));
               }
               setConsumed(typedValue);
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
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_state:{
               onInitProp(propId);
               this._state = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_credentialId:{
               onInitProp(propId);
               this._credentialId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_userId:{
               onInitProp(propId);
               this._userId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_expireAt:{
               onInitProp(propId);
               this._expireAt = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_consumed:{
               onInitProp(propId);
               this._consumed = (java.lang.Byte)value;
               
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
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * State令牌: STATE
     */
    public final java.lang.String getState(){
         onPropGet(PROP_ID_state);
         return _state;
    }

    /**
     * State令牌: STATE
     */
    public final void setState(java.lang.String value){
        if(onPropSet(PROP_ID_state,value)){
            this._state = value;
            internalClearRefs(PROP_ID_state);
            orm_id();
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
     * 发起人: USER_ID
     */
    public final java.lang.String getUserId(){
         onPropGet(PROP_ID_userId);
         return _userId;
    }

    /**
     * 发起人: USER_ID
     */
    public final void setUserId(java.lang.String value){
        if(onPropSet(PROP_ID_userId,value)){
            this._userId = value;
            internalClearRefs(PROP_ID_userId);
            
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
     * 已消费: CONSUMED
     */
    public final java.lang.Byte getConsumed(){
         onPropGet(PROP_ID_consumed);
         return _consumed;
    }

    /**
     * 已消费: CONSUMED
     */
    public final void setConsumed(java.lang.Byte value){
        if(onPropSet(PROP_ID_consumed,value)){
            this._consumed = value;
            internalClearRefs(PROP_ID_consumed);
            
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
    
}
// resume CPD analysis - CPD-ON
