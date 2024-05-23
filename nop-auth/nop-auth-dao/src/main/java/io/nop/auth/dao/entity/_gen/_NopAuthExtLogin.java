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

import io.nop.auth.dao.entity.NopAuthExtLogin;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  扩展登录方式: nop_auth_ext_login
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopAuthExtLogin extends DynamicOrmEntity{
    
    /* ID: SID VARCHAR */
    public static final String PROP_NAME_sid = "sid";
    public static final int PROP_ID_sid = 1;
    
    /* 用户ID: USER_ID VARCHAR */
    public static final String PROP_NAME_userId = "userId";
    public static final int PROP_ID_userId = 2;
    
    /* 登录类型: LOGIN_TYPE INTEGER */
    public static final String PROP_NAME_loginType = "loginType";
    public static final int PROP_ID_loginType = 3;
    
    /* 登录标识: EXT_ID VARCHAR */
    public static final String PROP_NAME_extId = "extId";
    public static final int PROP_ID_extId = 4;
    
    /* 登录密码: CREDENTIAL VARCHAR */
    public static final String PROP_NAME_credential = "credential";
    public static final int PROP_ID_credential = 5;
    
    /* 是否已验证: VERIFIED BOOLEAN */
    public static final String PROP_NAME_verified = "verified";
    public static final int PROP_ID_verified = 6;
    
    /* 上次登录时间: LAST_LOGIN_TIME TIMESTAMP */
    public static final String PROP_NAME_lastLoginTime = "lastLoginTime";
    public static final int PROP_ID_lastLoginTime = 7;
    
    /* 上次登录IP: LAST_LOGIN_IP VARCHAR */
    public static final String PROP_NAME_lastLoginIp = "lastLoginIp";
    public static final int PROP_ID_lastLoginIp = 8;
    
    /* 删除标识: DEL_FLAG TINYINT */
    public static final String PROP_NAME_delFlag = "delFlag";
    public static final int PROP_ID_delFlag = 9;
    
    /* 数据版本: VERSION INTEGER */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 10;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 11;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 12;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 13;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 14;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 15;
    

    private static int _PROP_ID_BOUND = 16;

    
    /* relation: 用户 */
    public static final String PROP_NAME_user = "user";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_sid);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_sid};

    private static final String[] PROP_ID_TO_NAME = new String[16];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_sid] = PROP_NAME_sid;
          PROP_NAME_TO_ID.put(PROP_NAME_sid, PROP_ID_sid);
      
          PROP_ID_TO_NAME[PROP_ID_userId] = PROP_NAME_userId;
          PROP_NAME_TO_ID.put(PROP_NAME_userId, PROP_ID_userId);
      
          PROP_ID_TO_NAME[PROP_ID_loginType] = PROP_NAME_loginType;
          PROP_NAME_TO_ID.put(PROP_NAME_loginType, PROP_ID_loginType);
      
          PROP_ID_TO_NAME[PROP_ID_extId] = PROP_NAME_extId;
          PROP_NAME_TO_ID.put(PROP_NAME_extId, PROP_ID_extId);
      
          PROP_ID_TO_NAME[PROP_ID_credential] = PROP_NAME_credential;
          PROP_NAME_TO_ID.put(PROP_NAME_credential, PROP_ID_credential);
      
          PROP_ID_TO_NAME[PROP_ID_verified] = PROP_NAME_verified;
          PROP_NAME_TO_ID.put(PROP_NAME_verified, PROP_ID_verified);
      
          PROP_ID_TO_NAME[PROP_ID_lastLoginTime] = PROP_NAME_lastLoginTime;
          PROP_NAME_TO_ID.put(PROP_NAME_lastLoginTime, PROP_ID_lastLoginTime);
      
          PROP_ID_TO_NAME[PROP_ID_lastLoginIp] = PROP_NAME_lastLoginIp;
          PROP_NAME_TO_ID.put(PROP_NAME_lastLoginIp, PROP_ID_lastLoginIp);
      
          PROP_ID_TO_NAME[PROP_ID_delFlag] = PROP_NAME_delFlag;
          PROP_NAME_TO_ID.put(PROP_NAME_delFlag, PROP_ID_delFlag);
      
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

    
    /* ID: SID */
    private java.lang.String _sid;
    
    /* 用户ID: USER_ID */
    private java.lang.String _userId;
    
    /* 登录类型: LOGIN_TYPE */
    private java.lang.Integer _loginType;
    
    /* 登录标识: EXT_ID */
    private java.lang.String _extId;
    
    /* 登录密码: CREDENTIAL */
    private java.lang.String _credential;
    
    /* 是否已验证: VERIFIED */
    private java.lang.Boolean _verified;
    
    /* 上次登录时间: LAST_LOGIN_TIME */
    private java.sql.Timestamp _lastLoginTime;
    
    /* 上次登录IP: LAST_LOGIN_IP */
    private java.lang.String _lastLoginIp;
    
    /* 删除标识: DEL_FLAG */
    private java.lang.Byte _delFlag;
    
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
    

    public _NopAuthExtLogin(){
        // for debug
    }

    protected NopAuthExtLogin newInstance(){
       return new NopAuthExtLogin();
    }

    @Override
    public NopAuthExtLogin cloneInstance() {
        NopAuthExtLogin entity = newInstance();
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
      return "io.nop.auth.dao.entity.NopAuthExtLogin";
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
        
            case PROP_ID_loginType:
               return getLoginType();
        
            case PROP_ID_extId:
               return getExtId();
        
            case PROP_ID_credential:
               return getCredential();
        
            case PROP_ID_verified:
               return getVerified();
        
            case PROP_ID_lastLoginTime:
               return getLastLoginTime();
        
            case PROP_ID_lastLoginIp:
               return getLastLoginIp();
        
            case PROP_ID_delFlag:
               return getDelFlag();
        
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
        
            case PROP_ID_loginType:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_loginType));
               }
               setLoginType(typedValue);
               break;
            }
        
            case PROP_ID_extId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_extId));
               }
               setExtId(typedValue);
               break;
            }
        
            case PROP_ID_credential:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_credential));
               }
               setCredential(typedValue);
               break;
            }
        
            case PROP_ID_verified:{
               java.lang.Boolean typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBoolean(value,
                       err-> newTypeConversionError(PROP_NAME_verified));
               }
               setVerified(typedValue);
               break;
            }
        
            case PROP_ID_lastLoginTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_lastLoginTime));
               }
               setLastLoginTime(typedValue);
               break;
            }
        
            case PROP_ID_lastLoginIp:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_lastLoginIp));
               }
               setLastLoginIp(typedValue);
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
        
            case PROP_ID_loginType:{
               onInitProp(propId);
               this._loginType = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_extId:{
               onInitProp(propId);
               this._extId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_credential:{
               onInitProp(propId);
               this._credential = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_verified:{
               onInitProp(propId);
               this._verified = (java.lang.Boolean)value;
               
               break;
            }
        
            case PROP_ID_lastLoginTime:{
               onInitProp(propId);
               this._lastLoginTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_lastLoginIp:{
               onInitProp(propId);
               this._lastLoginIp = (java.lang.String)value;
               
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
     * ID: SID
     */
    public java.lang.String getSid(){
         onPropGet(PROP_ID_sid);
         return _sid;
    }

    /**
     * ID: SID
     */
    public void setSid(java.lang.String value){
        if(onPropSet(PROP_ID_sid,value)){
            this._sid = value;
            internalClearRefs(PROP_ID_sid);
            orm_id();
        }
    }
    
    /**
     * 用户ID: USER_ID
     */
    public java.lang.String getUserId(){
         onPropGet(PROP_ID_userId);
         return _userId;
    }

    /**
     * 用户ID: USER_ID
     */
    public void setUserId(java.lang.String value){
        if(onPropSet(PROP_ID_userId,value)){
            this._userId = value;
            internalClearRefs(PROP_ID_userId);
            
        }
    }
    
    /**
     * 登录类型: LOGIN_TYPE
     */
    public java.lang.Integer getLoginType(){
         onPropGet(PROP_ID_loginType);
         return _loginType;
    }

    /**
     * 登录类型: LOGIN_TYPE
     */
    public void setLoginType(java.lang.Integer value){
        if(onPropSet(PROP_ID_loginType,value)){
            this._loginType = value;
            internalClearRefs(PROP_ID_loginType);
            
        }
    }
    
    /**
     * 登录标识: EXT_ID
     */
    public java.lang.String getExtId(){
         onPropGet(PROP_ID_extId);
         return _extId;
    }

    /**
     * 登录标识: EXT_ID
     */
    public void setExtId(java.lang.String value){
        if(onPropSet(PROP_ID_extId,value)){
            this._extId = value;
            internalClearRefs(PROP_ID_extId);
            
        }
    }
    
    /**
     * 登录密码: CREDENTIAL
     */
    public java.lang.String getCredential(){
         onPropGet(PROP_ID_credential);
         return _credential;
    }

    /**
     * 登录密码: CREDENTIAL
     */
    public void setCredential(java.lang.String value){
        if(onPropSet(PROP_ID_credential,value)){
            this._credential = value;
            internalClearRefs(PROP_ID_credential);
            
        }
    }
    
    /**
     * 是否已验证: VERIFIED
     */
    public java.lang.Boolean getVerified(){
         onPropGet(PROP_ID_verified);
         return _verified;
    }

    /**
     * 是否已验证: VERIFIED
     */
    public void setVerified(java.lang.Boolean value){
        if(onPropSet(PROP_ID_verified,value)){
            this._verified = value;
            internalClearRefs(PROP_ID_verified);
            
        }
    }
    
    /**
     * 上次登录时间: LAST_LOGIN_TIME
     */
    public java.sql.Timestamp getLastLoginTime(){
         onPropGet(PROP_ID_lastLoginTime);
         return _lastLoginTime;
    }

    /**
     * 上次登录时间: LAST_LOGIN_TIME
     */
    public void setLastLoginTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_lastLoginTime,value)){
            this._lastLoginTime = value;
            internalClearRefs(PROP_ID_lastLoginTime);
            
        }
    }
    
    /**
     * 上次登录IP: LAST_LOGIN_IP
     */
    public java.lang.String getLastLoginIp(){
         onPropGet(PROP_ID_lastLoginIp);
         return _lastLoginIp;
    }

    /**
     * 上次登录IP: LAST_LOGIN_IP
     */
    public void setLastLoginIp(java.lang.String value){
        if(onPropSet(PROP_ID_lastLoginIp,value)){
            this._lastLoginIp = value;
            internalClearRefs(PROP_ID_lastLoginIp);
            
        }
    }
    
    /**
     * 删除标识: DEL_FLAG
     */
    public java.lang.Byte getDelFlag(){
         onPropGet(PROP_ID_delFlag);
         return _delFlag;
    }

    /**
     * 删除标识: DEL_FLAG
     */
    public void setDelFlag(java.lang.Byte value){
        if(onPropSet(PROP_ID_delFlag,value)){
            this._delFlag = value;
            internalClearRefs(PROP_ID_delFlag);
            
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
     * 用户
     */
    public io.nop.auth.dao.entity.NopAuthUser getUser(){
       return (io.nop.auth.dao.entity.NopAuthUser)internalGetRefEntity(PROP_NAME_user);
    }

    public void setUser(io.nop.auth.dao.entity.NopAuthUser refEntity){
   
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
