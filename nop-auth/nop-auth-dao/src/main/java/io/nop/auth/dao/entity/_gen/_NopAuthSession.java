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

import io.nop.auth.dao.entity.NopAuthSession;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  会话日志: nop_auth_session
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopAuthSession extends DynamicOrmEntity{
    
    /* 会话ID: SESSION_ID VARCHAR */
    public static final String PROP_NAME_sessionId = "sessionId";
    public static final int PROP_ID_sessionId = 1;
    
    /* 用户ID: USER_ID VARCHAR */
    public static final String PROP_NAME_userId = "userId";
    public static final int PROP_ID_userId = 2;
    
    /* 用户名: USER_NAME VARCHAR */
    public static final String PROP_NAME_userName = "userName";
    public static final int PROP_ID_userName = 3;
    
    /* 租户ID: TENANT_ID VARCHAR */
    public static final String PROP_NAME_tenantId = "tenantId";
    public static final int PROP_ID_tenantId = 4;
    
    /* 登录地址: LOGIN_ADDR VARCHAR */
    public static final String PROP_NAME_loginAddr = "loginAddr";
    public static final int PROP_ID_loginAddr = 5;
    
    /* 登录设备: LOGIN_DEVICE VARCHAR */
    public static final String PROP_NAME_loginDevice = "loginDevice";
    public static final int PROP_ID_loginDevice = 6;
    
    /* 应用程序: LOGIN_APP VARCHAR */
    public static final String PROP_NAME_loginApp = "loginApp";
    public static final int PROP_ID_loginApp = 7;
    
    /* 操作系统: LOGIN_OS VARCHAR */
    public static final String PROP_NAME_loginOs = "loginOs";
    public static final int PROP_ID_loginOs = 8;
    
    /* 登录时间: LOGIN_TIME TIMESTAMP */
    public static final String PROP_NAME_loginTime = "loginTime";
    public static final int PROP_ID_loginTime = 9;
    
    /* 登录方式: LOGIN_TYPE INTEGER */
    public static final String PROP_NAME_loginType = "loginType";
    public static final int PROP_ID_loginType = 10;
    
    /* 退出时间: LOGOUT_TIME TIMESTAMP */
    public static final String PROP_NAME_logoutTime = "logoutTime";
    public static final int PROP_ID_logoutTime = 11;
    
    /* 退出方式: LOGOUT_TYPE INTEGER */
    public static final String PROP_NAME_logoutType = "logoutType";
    public static final int PROP_ID_logoutType = 12;
    
    /* 退出操作人: LOGOUT_BY VARCHAR */
    public static final String PROP_NAME_logoutBy = "logoutBy";
    public static final int PROP_ID_logoutBy = 13;
    
    /* 最后访问时间: LAST_ACCESS_TIME DATETIME */
    public static final String PROP_NAME_lastAccessTime = "lastAccessTime";
    public static final int PROP_ID_lastAccessTime = 14;
    
    /* 访问令牌: ACCESS_TOKEN VARCHAR */
    public static final String PROP_NAME_accessToken = "accessToken";
    public static final int PROP_ID_accessToken = 15;
    
    /* 刷新令牌: REFRESH_TOKEN VARCHAR */
    public static final String PROP_NAME_refreshToken = "refreshToken";
    public static final int PROP_ID_refreshToken = 16;
    
    /* 缓存数据: CACHE_DATA VARCHAR */
    public static final String PROP_NAME_cacheData = "cacheData";
    public static final int PROP_ID_cacheData = 17;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 18;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 19;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 20;
    

    private static int _PROP_ID_BOUND = 21;

    
    /* relation: 用户 */
    public static final String PROP_NAME_user = "user";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_sessionId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_sessionId};

    private static final String[] PROP_ID_TO_NAME = new String[21];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_sessionId] = PROP_NAME_sessionId;
          PROP_NAME_TO_ID.put(PROP_NAME_sessionId, PROP_ID_sessionId);
      
          PROP_ID_TO_NAME[PROP_ID_userId] = PROP_NAME_userId;
          PROP_NAME_TO_ID.put(PROP_NAME_userId, PROP_ID_userId);
      
          PROP_ID_TO_NAME[PROP_ID_userName] = PROP_NAME_userName;
          PROP_NAME_TO_ID.put(PROP_NAME_userName, PROP_ID_userName);
      
          PROP_ID_TO_NAME[PROP_ID_tenantId] = PROP_NAME_tenantId;
          PROP_NAME_TO_ID.put(PROP_NAME_tenantId, PROP_ID_tenantId);
      
          PROP_ID_TO_NAME[PROP_ID_loginAddr] = PROP_NAME_loginAddr;
          PROP_NAME_TO_ID.put(PROP_NAME_loginAddr, PROP_ID_loginAddr);
      
          PROP_ID_TO_NAME[PROP_ID_loginDevice] = PROP_NAME_loginDevice;
          PROP_NAME_TO_ID.put(PROP_NAME_loginDevice, PROP_ID_loginDevice);
      
          PROP_ID_TO_NAME[PROP_ID_loginApp] = PROP_NAME_loginApp;
          PROP_NAME_TO_ID.put(PROP_NAME_loginApp, PROP_ID_loginApp);
      
          PROP_ID_TO_NAME[PROP_ID_loginOs] = PROP_NAME_loginOs;
          PROP_NAME_TO_ID.put(PROP_NAME_loginOs, PROP_ID_loginOs);
      
          PROP_ID_TO_NAME[PROP_ID_loginTime] = PROP_NAME_loginTime;
          PROP_NAME_TO_ID.put(PROP_NAME_loginTime, PROP_ID_loginTime);
      
          PROP_ID_TO_NAME[PROP_ID_loginType] = PROP_NAME_loginType;
          PROP_NAME_TO_ID.put(PROP_NAME_loginType, PROP_ID_loginType);
      
          PROP_ID_TO_NAME[PROP_ID_logoutTime] = PROP_NAME_logoutTime;
          PROP_NAME_TO_ID.put(PROP_NAME_logoutTime, PROP_ID_logoutTime);
      
          PROP_ID_TO_NAME[PROP_ID_logoutType] = PROP_NAME_logoutType;
          PROP_NAME_TO_ID.put(PROP_NAME_logoutType, PROP_ID_logoutType);
      
          PROP_ID_TO_NAME[PROP_ID_logoutBy] = PROP_NAME_logoutBy;
          PROP_NAME_TO_ID.put(PROP_NAME_logoutBy, PROP_ID_logoutBy);
      
          PROP_ID_TO_NAME[PROP_ID_lastAccessTime] = PROP_NAME_lastAccessTime;
          PROP_NAME_TO_ID.put(PROP_NAME_lastAccessTime, PROP_ID_lastAccessTime);
      
          PROP_ID_TO_NAME[PROP_ID_accessToken] = PROP_NAME_accessToken;
          PROP_NAME_TO_ID.put(PROP_NAME_accessToken, PROP_ID_accessToken);
      
          PROP_ID_TO_NAME[PROP_ID_refreshToken] = PROP_NAME_refreshToken;
          PROP_NAME_TO_ID.put(PROP_NAME_refreshToken, PROP_ID_refreshToken);
      
          PROP_ID_TO_NAME[PROP_ID_cacheData] = PROP_NAME_cacheData;
          PROP_NAME_TO_ID.put(PROP_NAME_cacheData, PROP_ID_cacheData);
      
          PROP_ID_TO_NAME[PROP_ID_createdBy] = PROP_NAME_createdBy;
          PROP_NAME_TO_ID.put(PROP_NAME_createdBy, PROP_ID_createdBy);
      
          PROP_ID_TO_NAME[PROP_ID_createTime] = PROP_NAME_createTime;
          PROP_NAME_TO_ID.put(PROP_NAME_createTime, PROP_ID_createTime);
      
          PROP_ID_TO_NAME[PROP_ID_remark] = PROP_NAME_remark;
          PROP_NAME_TO_ID.put(PROP_NAME_remark, PROP_ID_remark);
      
    }

    
    /* 会话ID: SESSION_ID */
    private java.lang.String _sessionId;
    
    /* 用户ID: USER_ID */
    private java.lang.String _userId;
    
    /* 用户名: USER_NAME */
    private java.lang.String _userName;
    
    /* 租户ID: TENANT_ID */
    private java.lang.String _tenantId;
    
    /* 登录地址: LOGIN_ADDR */
    private java.lang.String _loginAddr;
    
    /* 登录设备: LOGIN_DEVICE */
    private java.lang.String _loginDevice;
    
    /* 应用程序: LOGIN_APP */
    private java.lang.String _loginApp;
    
    /* 操作系统: LOGIN_OS */
    private java.lang.String _loginOs;
    
    /* 登录时间: LOGIN_TIME */
    private java.sql.Timestamp _loginTime;
    
    /* 登录方式: LOGIN_TYPE */
    private java.lang.Integer _loginType;
    
    /* 退出时间: LOGOUT_TIME */
    private java.sql.Timestamp _logoutTime;
    
    /* 退出方式: LOGOUT_TYPE */
    private java.lang.Integer _logoutType;
    
    /* 退出操作人: LOGOUT_BY */
    private java.lang.String _logoutBy;
    
    /* 最后访问时间: LAST_ACCESS_TIME */
    private java.time.LocalDateTime _lastAccessTime;
    
    /* 访问令牌: ACCESS_TOKEN */
    private java.lang.String _accessToken;
    
    /* 刷新令牌: REFRESH_TOKEN */
    private java.lang.String _refreshToken;
    
    /* 缓存数据: CACHE_DATA */
    private java.lang.String _cacheData;
    
    /* 创建人: CREATED_BY */
    private java.lang.String _createdBy;
    
    /* 创建时间: CREATE_TIME */
    private java.sql.Timestamp _createTime;
    
    /* 备注: REMARK */
    private java.lang.String _remark;
    

    public _NopAuthSession(){
        // for debug
    }

    protected NopAuthSession newInstance(){
        NopAuthSession entity = new NopAuthSession();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopAuthSession cloneInstance() {
        NopAuthSession entity = newInstance();
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
      return "io.nop.auth.dao.entity.NopAuthSession";
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
    
        return buildSimpleId(PROP_ID_sessionId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_sessionId;
          
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
        
            case PROP_ID_sessionId:
               return getSessionId();
        
            case PROP_ID_userId:
               return getUserId();
        
            case PROP_ID_userName:
               return getUserName();
        
            case PROP_ID_tenantId:
               return getTenantId();
        
            case PROP_ID_loginAddr:
               return getLoginAddr();
        
            case PROP_ID_loginDevice:
               return getLoginDevice();
        
            case PROP_ID_loginApp:
               return getLoginApp();
        
            case PROP_ID_loginOs:
               return getLoginOs();
        
            case PROP_ID_loginTime:
               return getLoginTime();
        
            case PROP_ID_loginType:
               return getLoginType();
        
            case PROP_ID_logoutTime:
               return getLogoutTime();
        
            case PROP_ID_logoutType:
               return getLogoutType();
        
            case PROP_ID_logoutBy:
               return getLogoutBy();
        
            case PROP_ID_lastAccessTime:
               return getLastAccessTime();
        
            case PROP_ID_accessToken:
               return getAccessToken();
        
            case PROP_ID_refreshToken:
               return getRefreshToken();
        
            case PROP_ID_cacheData:
               return getCacheData();
        
            case PROP_ID_createdBy:
               return getCreatedBy();
        
            case PROP_ID_createTime:
               return getCreateTime();
        
            case PROP_ID_remark:
               return getRemark();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_sessionId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_sessionId));
               }
               setSessionId(typedValue);
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
        
            case PROP_ID_userName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_userName));
               }
               setUserName(typedValue);
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
        
            case PROP_ID_loginAddr:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_loginAddr));
               }
               setLoginAddr(typedValue);
               break;
            }
        
            case PROP_ID_loginDevice:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_loginDevice));
               }
               setLoginDevice(typedValue);
               break;
            }
        
            case PROP_ID_loginApp:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_loginApp));
               }
               setLoginApp(typedValue);
               break;
            }
        
            case PROP_ID_loginOs:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_loginOs));
               }
               setLoginOs(typedValue);
               break;
            }
        
            case PROP_ID_loginTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_loginTime));
               }
               setLoginTime(typedValue);
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
        
            case PROP_ID_logoutTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_logoutTime));
               }
               setLogoutTime(typedValue);
               break;
            }
        
            case PROP_ID_logoutType:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_logoutType));
               }
               setLogoutType(typedValue);
               break;
            }
        
            case PROP_ID_logoutBy:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_logoutBy));
               }
               setLogoutBy(typedValue);
               break;
            }
        
            case PROP_ID_lastAccessTime:{
               java.time.LocalDateTime typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLocalDateTime(value,
                       err-> newTypeConversionError(PROP_NAME_lastAccessTime));
               }
               setLastAccessTime(typedValue);
               break;
            }
        
            case PROP_ID_accessToken:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_accessToken));
               }
               setAccessToken(typedValue);
               break;
            }
        
            case PROP_ID_refreshToken:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_refreshToken));
               }
               setRefreshToken(typedValue);
               break;
            }
        
            case PROP_ID_cacheData:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_cacheData));
               }
               setCacheData(typedValue);
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
        
            case PROP_ID_sessionId:{
               onInitProp(propId);
               this._sessionId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_userId:{
               onInitProp(propId);
               this._userId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_userName:{
               onInitProp(propId);
               this._userName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_tenantId:{
               onInitProp(propId);
               this._tenantId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_loginAddr:{
               onInitProp(propId);
               this._loginAddr = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_loginDevice:{
               onInitProp(propId);
               this._loginDevice = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_loginApp:{
               onInitProp(propId);
               this._loginApp = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_loginOs:{
               onInitProp(propId);
               this._loginOs = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_loginTime:{
               onInitProp(propId);
               this._loginTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_loginType:{
               onInitProp(propId);
               this._loginType = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_logoutTime:{
               onInitProp(propId);
               this._logoutTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_logoutType:{
               onInitProp(propId);
               this._logoutType = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_logoutBy:{
               onInitProp(propId);
               this._logoutBy = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_lastAccessTime:{
               onInitProp(propId);
               this._lastAccessTime = (java.time.LocalDateTime)value;
               
               break;
            }
        
            case PROP_ID_accessToken:{
               onInitProp(propId);
               this._accessToken = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_refreshToken:{
               onInitProp(propId);
               this._refreshToken = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_cacheData:{
               onInitProp(propId);
               this._cacheData = (java.lang.String)value;
               
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
     * 会话ID: SESSION_ID
     */
    public final java.lang.String getSessionId(){
         onPropGet(PROP_ID_sessionId);
         return _sessionId;
    }

    /**
     * 会话ID: SESSION_ID
     */
    public final void setSessionId(java.lang.String value){
        if(onPropSet(PROP_ID_sessionId,value)){
            this._sessionId = value;
            internalClearRefs(PROP_ID_sessionId);
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
     * 用户名: USER_NAME
     */
    public final java.lang.String getUserName(){
         onPropGet(PROP_ID_userName);
         return _userName;
    }

    /**
     * 用户名: USER_NAME
     */
    public final void setUserName(java.lang.String value){
        if(onPropSet(PROP_ID_userName,value)){
            this._userName = value;
            internalClearRefs(PROP_ID_userName);
            
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
     * 登录地址: LOGIN_ADDR
     */
    public final java.lang.String getLoginAddr(){
         onPropGet(PROP_ID_loginAddr);
         return _loginAddr;
    }

    /**
     * 登录地址: LOGIN_ADDR
     */
    public final void setLoginAddr(java.lang.String value){
        if(onPropSet(PROP_ID_loginAddr,value)){
            this._loginAddr = value;
            internalClearRefs(PROP_ID_loginAddr);
            
        }
    }
    
    /**
     * 登录设备: LOGIN_DEVICE
     */
    public final java.lang.String getLoginDevice(){
         onPropGet(PROP_ID_loginDevice);
         return _loginDevice;
    }

    /**
     * 登录设备: LOGIN_DEVICE
     */
    public final void setLoginDevice(java.lang.String value){
        if(onPropSet(PROP_ID_loginDevice,value)){
            this._loginDevice = value;
            internalClearRefs(PROP_ID_loginDevice);
            
        }
    }
    
    /**
     * 应用程序: LOGIN_APP
     */
    public final java.lang.String getLoginApp(){
         onPropGet(PROP_ID_loginApp);
         return _loginApp;
    }

    /**
     * 应用程序: LOGIN_APP
     */
    public final void setLoginApp(java.lang.String value){
        if(onPropSet(PROP_ID_loginApp,value)){
            this._loginApp = value;
            internalClearRefs(PROP_ID_loginApp);
            
        }
    }
    
    /**
     * 操作系统: LOGIN_OS
     */
    public final java.lang.String getLoginOs(){
         onPropGet(PROP_ID_loginOs);
         return _loginOs;
    }

    /**
     * 操作系统: LOGIN_OS
     */
    public final void setLoginOs(java.lang.String value){
        if(onPropSet(PROP_ID_loginOs,value)){
            this._loginOs = value;
            internalClearRefs(PROP_ID_loginOs);
            
        }
    }
    
    /**
     * 登录时间: LOGIN_TIME
     */
    public final java.sql.Timestamp getLoginTime(){
         onPropGet(PROP_ID_loginTime);
         return _loginTime;
    }

    /**
     * 登录时间: LOGIN_TIME
     */
    public final void setLoginTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_loginTime,value)){
            this._loginTime = value;
            internalClearRefs(PROP_ID_loginTime);
            
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
     * 退出时间: LOGOUT_TIME
     */
    public final java.sql.Timestamp getLogoutTime(){
         onPropGet(PROP_ID_logoutTime);
         return _logoutTime;
    }

    /**
     * 退出时间: LOGOUT_TIME
     */
    public final void setLogoutTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_logoutTime,value)){
            this._logoutTime = value;
            internalClearRefs(PROP_ID_logoutTime);
            
        }
    }
    
    /**
     * 退出方式: LOGOUT_TYPE
     */
    public final java.lang.Integer getLogoutType(){
         onPropGet(PROP_ID_logoutType);
         return _logoutType;
    }

    /**
     * 退出方式: LOGOUT_TYPE
     */
    public final void setLogoutType(java.lang.Integer value){
        if(onPropSet(PROP_ID_logoutType,value)){
            this._logoutType = value;
            internalClearRefs(PROP_ID_logoutType);
            
        }
    }
    
    /**
     * 退出操作人: LOGOUT_BY
     */
    public final java.lang.String getLogoutBy(){
         onPropGet(PROP_ID_logoutBy);
         return _logoutBy;
    }

    /**
     * 退出操作人: LOGOUT_BY
     */
    public final void setLogoutBy(java.lang.String value){
        if(onPropSet(PROP_ID_logoutBy,value)){
            this._logoutBy = value;
            internalClearRefs(PROP_ID_logoutBy);
            
        }
    }
    
    /**
     * 最后访问时间: LAST_ACCESS_TIME
     */
    public final java.time.LocalDateTime getLastAccessTime(){
         onPropGet(PROP_ID_lastAccessTime);
         return _lastAccessTime;
    }

    /**
     * 最后访问时间: LAST_ACCESS_TIME
     */
    public final void setLastAccessTime(java.time.LocalDateTime value){
        if(onPropSet(PROP_ID_lastAccessTime,value)){
            this._lastAccessTime = value;
            internalClearRefs(PROP_ID_lastAccessTime);
            
        }
    }
    
    /**
     * 访问令牌: ACCESS_TOKEN
     */
    public final java.lang.String getAccessToken(){
         onPropGet(PROP_ID_accessToken);
         return _accessToken;
    }

    /**
     * 访问令牌: ACCESS_TOKEN
     */
    public final void setAccessToken(java.lang.String value){
        if(onPropSet(PROP_ID_accessToken,value)){
            this._accessToken = value;
            internalClearRefs(PROP_ID_accessToken);
            
        }
    }
    
    /**
     * 刷新令牌: REFRESH_TOKEN
     */
    public final java.lang.String getRefreshToken(){
         onPropGet(PROP_ID_refreshToken);
         return _refreshToken;
    }

    /**
     * 刷新令牌: REFRESH_TOKEN
     */
    public final void setRefreshToken(java.lang.String value){
        if(onPropSet(PROP_ID_refreshToken,value)){
            this._refreshToken = value;
            internalClearRefs(PROP_ID_refreshToken);
            
        }
    }
    
    /**
     * 缓存数据: CACHE_DATA
     */
    public final java.lang.String getCacheData(){
         onPropGet(PROP_ID_cacheData);
         return _cacheData;
    }

    /**
     * 缓存数据: CACHE_DATA
     */
    public final void setCacheData(java.lang.String value){
        if(onPropSet(PROP_ID_cacheData,value)){
            this._cacheData = value;
            internalClearRefs(PROP_ID_cacheData);
            
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
