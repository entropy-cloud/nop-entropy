package io.nop.oauth.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.oauth.dao.entity.NopOauthRegisteredClient;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  Oauth注册客户端: nop_oauth_registered_client
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopOauthRegisteredClient extends DynamicOrmEntity{
    
    /* Id: ID VARCHAR */
    public static final String PROP_NAME_id = "id";
    public static final int PROP_ID_id = 1;
    
    /* 客户端ID: CLIENT_ID VARCHAR */
    public static final String PROP_NAME_clientId = "clientId";
    public static final int PROP_ID_clientId = 2;
    
    /* 客户端ID发放时间: CLIENT_ID_ISSUED_AT TIMESTAMP */
    public static final String PROP_NAME_clientIdIssuedAt = "clientIdIssuedAt";
    public static final int PROP_ID_clientIdIssuedAt = 3;
    
    /* 客户端密码: CLIENT_SECRET VARCHAR */
    public static final String PROP_NAME_clientSecret = "clientSecret";
    public static final int PROP_ID_clientSecret = 4;
    
    /* 客户端密码过期时间: CLIENT_SECRET_EXPIRES_AT TIMESTAMP */
    public static final String PROP_NAME_clientSecretExpiresAt = "clientSecretExpiresAt";
    public static final int PROP_ID_clientSecretExpiresAt = 5;
    
    /* 客户端名称: CLIENT_NAME VARCHAR */
    public static final String PROP_NAME_clientName = "clientName";
    public static final int PROP_ID_clientName = 6;
    
    /* 客户端授权方法: CLIENT_AUTHENTICATION_METHODS VARCHAR */
    public static final String PROP_NAME_clientAuthenticationMethods = "clientAuthenticationMethods";
    public static final int PROP_ID_clientAuthenticationMethods = 7;
    
    /* 客户端认证类型: AUTHORIZATION_GRANT_TYPES VARCHAR */
    public static final String PROP_NAME_authorizationGrantTypes = "authorizationGrantTypes";
    public static final int PROP_ID_authorizationGrantTypes = 8;
    
    /* 重定向URI: REDIRECT_URIS VARCHAR */
    public static final String PROP_NAME_redirectUris = "redirectUris";
    public static final int PROP_ID_redirectUris = 9;
    
    /* Logout重定向URI: POST_LOGOUT_REDIRECT_URIS VARCHAR */
    public static final String PROP_NAME_postLogoutRedirectUris = "postLogoutRedirectUris";
    public static final int PROP_ID_postLogoutRedirectUris = 10;
    
    /* 授权范围: SCOPES VARCHAR */
    public static final String PROP_NAME_scopes = "scopes";
    public static final int PROP_ID_scopes = 11;
    
    /* 客户端设置: CLIENT_SETTINGS VARCHAR */
    public static final String PROP_NAME_clientSettings = "clientSettings";
    public static final int PROP_ID_clientSettings = 12;
    
    /* Token设置: TOKEN_SETTINGS VARCHAR */
    public static final String PROP_NAME_tokenSettings = "tokenSettings";
    public static final int PROP_ID_tokenSettings = 13;
    

    private static int _PROP_ID_BOUND = 14;

    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_id);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_id};

    private static final String[] PROP_ID_TO_NAME = new String[14];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_id] = PROP_NAME_id;
          PROP_NAME_TO_ID.put(PROP_NAME_id, PROP_ID_id);
      
          PROP_ID_TO_NAME[PROP_ID_clientId] = PROP_NAME_clientId;
          PROP_NAME_TO_ID.put(PROP_NAME_clientId, PROP_ID_clientId);
      
          PROP_ID_TO_NAME[PROP_ID_clientIdIssuedAt] = PROP_NAME_clientIdIssuedAt;
          PROP_NAME_TO_ID.put(PROP_NAME_clientIdIssuedAt, PROP_ID_clientIdIssuedAt);
      
          PROP_ID_TO_NAME[PROP_ID_clientSecret] = PROP_NAME_clientSecret;
          PROP_NAME_TO_ID.put(PROP_NAME_clientSecret, PROP_ID_clientSecret);
      
          PROP_ID_TO_NAME[PROP_ID_clientSecretExpiresAt] = PROP_NAME_clientSecretExpiresAt;
          PROP_NAME_TO_ID.put(PROP_NAME_clientSecretExpiresAt, PROP_ID_clientSecretExpiresAt);
      
          PROP_ID_TO_NAME[PROP_ID_clientName] = PROP_NAME_clientName;
          PROP_NAME_TO_ID.put(PROP_NAME_clientName, PROP_ID_clientName);
      
          PROP_ID_TO_NAME[PROP_ID_clientAuthenticationMethods] = PROP_NAME_clientAuthenticationMethods;
          PROP_NAME_TO_ID.put(PROP_NAME_clientAuthenticationMethods, PROP_ID_clientAuthenticationMethods);
      
          PROP_ID_TO_NAME[PROP_ID_authorizationGrantTypes] = PROP_NAME_authorizationGrantTypes;
          PROP_NAME_TO_ID.put(PROP_NAME_authorizationGrantTypes, PROP_ID_authorizationGrantTypes);
      
          PROP_ID_TO_NAME[PROP_ID_redirectUris] = PROP_NAME_redirectUris;
          PROP_NAME_TO_ID.put(PROP_NAME_redirectUris, PROP_ID_redirectUris);
      
          PROP_ID_TO_NAME[PROP_ID_postLogoutRedirectUris] = PROP_NAME_postLogoutRedirectUris;
          PROP_NAME_TO_ID.put(PROP_NAME_postLogoutRedirectUris, PROP_ID_postLogoutRedirectUris);
      
          PROP_ID_TO_NAME[PROP_ID_scopes] = PROP_NAME_scopes;
          PROP_NAME_TO_ID.put(PROP_NAME_scopes, PROP_ID_scopes);
      
          PROP_ID_TO_NAME[PROP_ID_clientSettings] = PROP_NAME_clientSettings;
          PROP_NAME_TO_ID.put(PROP_NAME_clientSettings, PROP_ID_clientSettings);
      
          PROP_ID_TO_NAME[PROP_ID_tokenSettings] = PROP_NAME_tokenSettings;
          PROP_NAME_TO_ID.put(PROP_NAME_tokenSettings, PROP_ID_tokenSettings);
      
    }

    
    /* Id: ID */
    private java.lang.String _id;
    
    /* 客户端ID: CLIENT_ID */
    private java.lang.String _clientId;
    
    /* 客户端ID发放时间: CLIENT_ID_ISSUED_AT */
    private java.sql.Timestamp _clientIdIssuedAt;
    
    /* 客户端密码: CLIENT_SECRET */
    private java.lang.String _clientSecret;
    
    /* 客户端密码过期时间: CLIENT_SECRET_EXPIRES_AT */
    private java.sql.Timestamp _clientSecretExpiresAt;
    
    /* 客户端名称: CLIENT_NAME */
    private java.lang.String _clientName;
    
    /* 客户端授权方法: CLIENT_AUTHENTICATION_METHODS */
    private java.lang.String _clientAuthenticationMethods;
    
    /* 客户端认证类型: AUTHORIZATION_GRANT_TYPES */
    private java.lang.String _authorizationGrantTypes;
    
    /* 重定向URI: REDIRECT_URIS */
    private java.lang.String _redirectUris;
    
    /* Logout重定向URI: POST_LOGOUT_REDIRECT_URIS */
    private java.lang.String _postLogoutRedirectUris;
    
    /* 授权范围: SCOPES */
    private java.lang.String _scopes;
    
    /* 客户端设置: CLIENT_SETTINGS */
    private java.lang.String _clientSettings;
    
    /* Token设置: TOKEN_SETTINGS */
    private java.lang.String _tokenSettings;
    

    public _NopOauthRegisteredClient(){
        // for debug
    }

    protected NopOauthRegisteredClient newInstance(){
        NopOauthRegisteredClient entity = new NopOauthRegisteredClient();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopOauthRegisteredClient cloneInstance() {
        NopOauthRegisteredClient entity = newInstance();
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
      return "io.nop.oauth.dao.entity.NopOauthRegisteredClient";
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
    
        return buildSimpleId(PROP_ID_id);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_id;
          
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
        
            case PROP_ID_id:
               return getId();
        
            case PROP_ID_clientId:
               return getClientId();
        
            case PROP_ID_clientIdIssuedAt:
               return getClientIdIssuedAt();
        
            case PROP_ID_clientSecret:
               return getClientSecret();
        
            case PROP_ID_clientSecretExpiresAt:
               return getClientSecretExpiresAt();
        
            case PROP_ID_clientName:
               return getClientName();
        
            case PROP_ID_clientAuthenticationMethods:
               return getClientAuthenticationMethods();
        
            case PROP_ID_authorizationGrantTypes:
               return getAuthorizationGrantTypes();
        
            case PROP_ID_redirectUris:
               return getRedirectUris();
        
            case PROP_ID_postLogoutRedirectUris:
               return getPostLogoutRedirectUris();
        
            case PROP_ID_scopes:
               return getScopes();
        
            case PROP_ID_clientSettings:
               return getClientSettings();
        
            case PROP_ID_tokenSettings:
               return getTokenSettings();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_id:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_id));
               }
               setId(typedValue);
               break;
            }
        
            case PROP_ID_clientId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_clientId));
               }
               setClientId(typedValue);
               break;
            }
        
            case PROP_ID_clientIdIssuedAt:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_clientIdIssuedAt));
               }
               setClientIdIssuedAt(typedValue);
               break;
            }
        
            case PROP_ID_clientSecret:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_clientSecret));
               }
               setClientSecret(typedValue);
               break;
            }
        
            case PROP_ID_clientSecretExpiresAt:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_clientSecretExpiresAt));
               }
               setClientSecretExpiresAt(typedValue);
               break;
            }
        
            case PROP_ID_clientName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_clientName));
               }
               setClientName(typedValue);
               break;
            }
        
            case PROP_ID_clientAuthenticationMethods:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_clientAuthenticationMethods));
               }
               setClientAuthenticationMethods(typedValue);
               break;
            }
        
            case PROP_ID_authorizationGrantTypes:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_authorizationGrantTypes));
               }
               setAuthorizationGrantTypes(typedValue);
               break;
            }
        
            case PROP_ID_redirectUris:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_redirectUris));
               }
               setRedirectUris(typedValue);
               break;
            }
        
            case PROP_ID_postLogoutRedirectUris:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_postLogoutRedirectUris));
               }
               setPostLogoutRedirectUris(typedValue);
               break;
            }
        
            case PROP_ID_scopes:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_scopes));
               }
               setScopes(typedValue);
               break;
            }
        
            case PROP_ID_clientSettings:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_clientSettings));
               }
               setClientSettings(typedValue);
               break;
            }
        
            case PROP_ID_tokenSettings:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_tokenSettings));
               }
               setTokenSettings(typedValue);
               break;
            }
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_id:{
               onInitProp(propId);
               this._id = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_clientId:{
               onInitProp(propId);
               this._clientId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_clientIdIssuedAt:{
               onInitProp(propId);
               this._clientIdIssuedAt = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_clientSecret:{
               onInitProp(propId);
               this._clientSecret = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_clientSecretExpiresAt:{
               onInitProp(propId);
               this._clientSecretExpiresAt = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_clientName:{
               onInitProp(propId);
               this._clientName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_clientAuthenticationMethods:{
               onInitProp(propId);
               this._clientAuthenticationMethods = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_authorizationGrantTypes:{
               onInitProp(propId);
               this._authorizationGrantTypes = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_redirectUris:{
               onInitProp(propId);
               this._redirectUris = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_postLogoutRedirectUris:{
               onInitProp(propId);
               this._postLogoutRedirectUris = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_scopes:{
               onInitProp(propId);
               this._scopes = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_clientSettings:{
               onInitProp(propId);
               this._clientSettings = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_tokenSettings:{
               onInitProp(propId);
               this._tokenSettings = (java.lang.String)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * Id: ID
     */
    public java.lang.String getId(){
         onPropGet(PROP_ID_id);
         return _id;
    }

    /**
     * Id: ID
     */
    public void setId(java.lang.String value){
        if(onPropSet(PROP_ID_id,value)){
            this._id = value;
            internalClearRefs(PROP_ID_id);
            orm_id();
        }
    }
    
    /**
     * 客户端ID: CLIENT_ID
     */
    public java.lang.String getClientId(){
         onPropGet(PROP_ID_clientId);
         return _clientId;
    }

    /**
     * 客户端ID: CLIENT_ID
     */
    public void setClientId(java.lang.String value){
        if(onPropSet(PROP_ID_clientId,value)){
            this._clientId = value;
            internalClearRefs(PROP_ID_clientId);
            
        }
    }
    
    /**
     * 客户端ID发放时间: CLIENT_ID_ISSUED_AT
     */
    public java.sql.Timestamp getClientIdIssuedAt(){
         onPropGet(PROP_ID_clientIdIssuedAt);
         return _clientIdIssuedAt;
    }

    /**
     * 客户端ID发放时间: CLIENT_ID_ISSUED_AT
     */
    public void setClientIdIssuedAt(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_clientIdIssuedAt,value)){
            this._clientIdIssuedAt = value;
            internalClearRefs(PROP_ID_clientIdIssuedAt);
            
        }
    }
    
    /**
     * 客户端密码: CLIENT_SECRET
     */
    public java.lang.String getClientSecret(){
         onPropGet(PROP_ID_clientSecret);
         return _clientSecret;
    }

    /**
     * 客户端密码: CLIENT_SECRET
     */
    public void setClientSecret(java.lang.String value){
        if(onPropSet(PROP_ID_clientSecret,value)){
            this._clientSecret = value;
            internalClearRefs(PROP_ID_clientSecret);
            
        }
    }
    
    /**
     * 客户端密码过期时间: CLIENT_SECRET_EXPIRES_AT
     */
    public java.sql.Timestamp getClientSecretExpiresAt(){
         onPropGet(PROP_ID_clientSecretExpiresAt);
         return _clientSecretExpiresAt;
    }

    /**
     * 客户端密码过期时间: CLIENT_SECRET_EXPIRES_AT
     */
    public void setClientSecretExpiresAt(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_clientSecretExpiresAt,value)){
            this._clientSecretExpiresAt = value;
            internalClearRefs(PROP_ID_clientSecretExpiresAt);
            
        }
    }
    
    /**
     * 客户端名称: CLIENT_NAME
     */
    public java.lang.String getClientName(){
         onPropGet(PROP_ID_clientName);
         return _clientName;
    }

    /**
     * 客户端名称: CLIENT_NAME
     */
    public void setClientName(java.lang.String value){
        if(onPropSet(PROP_ID_clientName,value)){
            this._clientName = value;
            internalClearRefs(PROP_ID_clientName);
            
        }
    }
    
    /**
     * 客户端授权方法: CLIENT_AUTHENTICATION_METHODS
     */
    public java.lang.String getClientAuthenticationMethods(){
         onPropGet(PROP_ID_clientAuthenticationMethods);
         return _clientAuthenticationMethods;
    }

    /**
     * 客户端授权方法: CLIENT_AUTHENTICATION_METHODS
     */
    public void setClientAuthenticationMethods(java.lang.String value){
        if(onPropSet(PROP_ID_clientAuthenticationMethods,value)){
            this._clientAuthenticationMethods = value;
            internalClearRefs(PROP_ID_clientAuthenticationMethods);
            
        }
    }
    
    /**
     * 客户端认证类型: AUTHORIZATION_GRANT_TYPES
     */
    public java.lang.String getAuthorizationGrantTypes(){
         onPropGet(PROP_ID_authorizationGrantTypes);
         return _authorizationGrantTypes;
    }

    /**
     * 客户端认证类型: AUTHORIZATION_GRANT_TYPES
     */
    public void setAuthorizationGrantTypes(java.lang.String value){
        if(onPropSet(PROP_ID_authorizationGrantTypes,value)){
            this._authorizationGrantTypes = value;
            internalClearRefs(PROP_ID_authorizationGrantTypes);
            
        }
    }
    
    /**
     * 重定向URI: REDIRECT_URIS
     */
    public java.lang.String getRedirectUris(){
         onPropGet(PROP_ID_redirectUris);
         return _redirectUris;
    }

    /**
     * 重定向URI: REDIRECT_URIS
     */
    public void setRedirectUris(java.lang.String value){
        if(onPropSet(PROP_ID_redirectUris,value)){
            this._redirectUris = value;
            internalClearRefs(PROP_ID_redirectUris);
            
        }
    }
    
    /**
     * Logout重定向URI: POST_LOGOUT_REDIRECT_URIS
     */
    public java.lang.String getPostLogoutRedirectUris(){
         onPropGet(PROP_ID_postLogoutRedirectUris);
         return _postLogoutRedirectUris;
    }

    /**
     * Logout重定向URI: POST_LOGOUT_REDIRECT_URIS
     */
    public void setPostLogoutRedirectUris(java.lang.String value){
        if(onPropSet(PROP_ID_postLogoutRedirectUris,value)){
            this._postLogoutRedirectUris = value;
            internalClearRefs(PROP_ID_postLogoutRedirectUris);
            
        }
    }
    
    /**
     * 授权范围: SCOPES
     */
    public java.lang.String getScopes(){
         onPropGet(PROP_ID_scopes);
         return _scopes;
    }

    /**
     * 授权范围: SCOPES
     */
    public void setScopes(java.lang.String value){
        if(onPropSet(PROP_ID_scopes,value)){
            this._scopes = value;
            internalClearRefs(PROP_ID_scopes);
            
        }
    }
    
    /**
     * 客户端设置: CLIENT_SETTINGS
     */
    public java.lang.String getClientSettings(){
         onPropGet(PROP_ID_clientSettings);
         return _clientSettings;
    }

    /**
     * 客户端设置: CLIENT_SETTINGS
     */
    public void setClientSettings(java.lang.String value){
        if(onPropSet(PROP_ID_clientSettings,value)){
            this._clientSettings = value;
            internalClearRefs(PROP_ID_clientSettings);
            
        }
    }
    
    /**
     * Token设置: TOKEN_SETTINGS
     */
    public java.lang.String getTokenSettings(){
         onPropGet(PROP_ID_tokenSettings);
         return _tokenSettings;
    }

    /**
     * Token设置: TOKEN_SETTINGS
     */
    public void setTokenSettings(java.lang.String value){
        if(onPropSet(PROP_ID_tokenSettings,value)){
            this._tokenSettings = value;
            internalClearRefs(PROP_ID_tokenSettings);
            
        }
    }
    
}
// resume CPD analysis - CPD-ON
