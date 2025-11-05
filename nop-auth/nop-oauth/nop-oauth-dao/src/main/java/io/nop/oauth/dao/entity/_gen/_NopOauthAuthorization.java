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

import io.nop.oauth.dao.entity.NopOauthAuthorization;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  Oauth认证记录: nop_oauth_authorization
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopOauthAuthorization extends DynamicOrmEntity{
    
    /* Id: SID VARCHAR */
    public static final String PROP_NAME_sid = "sid";
    public static final int PROP_ID_sid = 1;
    
    /* 客户端ID: REGISTERED_CLIENT_ID VARCHAR */
    public static final String PROP_NAME_registeredClientId = "registeredClientId";
    public static final int PROP_ID_registeredClientId = 2;
    
    /* 客户端名称: PRINCIPAL_NAME VARCHAR */
    public static final String PROP_NAME_principalName = "principalName";
    public static final int PROP_ID_principalName = 3;
    
    /* 授权类型: AUTHORIZATION_GRANT_TYPE VARCHAR */
    public static final String PROP_NAME_authorizationGrantType = "authorizationGrantType";
    public static final int PROP_ID_authorizationGrantType = 4;
    
    /* 授权范围: AUTHORIZED_SCOPES VARCHAR */
    public static final String PROP_NAME_authorizedScopes = "authorizedScopes";
    public static final int PROP_ID_authorizedScopes = 5;
    
    /* 扩展属性: ATTRIBUTES VARCHAR */
    public static final String PROP_NAME_attributes = "attributes";
    public static final int PROP_ID_attributes = 6;
    
    /* 状态码: STATE VARCHAR */
    public static final String PROP_NAME_state = "state";
    public static final int PROP_ID_state = 7;
    
    /* AuthorizationCode值: AUTHORIZATION_CODE_VALUE VARCHAR */
    public static final String PROP_NAME_authorizationCodeValue = "authorizationCodeValue";
    public static final int PROP_ID_authorizationCodeValue = 8;
    
    /* AuthorizationCode发放时间: AUTHORIZATION_CODE_ISSUED_AT TIMESTAMP */
    public static final String PROP_NAME_authorizationCodeIssuedAt = "authorizationCodeIssuedAt";
    public static final int PROP_ID_authorizationCodeIssuedAt = 9;
    
    /* AuthorizationCode过期使劲按: AUTHORIZATION_CODE_EXPIRES_AT TIMESTAMP */
    public static final String PROP_NAME_authorizationCodeExpiresAt = "authorizationCodeExpiresAt";
    public static final int PROP_ID_authorizationCodeExpiresAt = 10;
    
    /* AuthorizationCode元数据: AUTHORIZATION_CODE_METADATA VARCHAR */
    public static final String PROP_NAME_authorizationCodeMetadata = "authorizationCodeMetadata";
    public static final int PROP_ID_authorizationCodeMetadata = 11;
    
    /* AccessToken: ACCESS_TOKEN_VALUE VARCHAR */
    public static final String PROP_NAME_accessTokenValue = "accessTokenValue";
    public static final int PROP_ID_accessTokenValue = 12;
    
    /* AccessToken发放时间: ACCESS_TOKEN_ISSUED_AT TIMESTAMP */
    public static final String PROP_NAME_accessTokenIssuedAt = "accessTokenIssuedAt";
    public static final int PROP_ID_accessTokenIssuedAt = 13;
    
    /* AccessToken过期时间: ACCESS_TOKEN_EXPIRES_AT TIMESTAMP */
    public static final String PROP_NAME_accessTokenExpiresAt = "accessTokenExpiresAt";
    public static final int PROP_ID_accessTokenExpiresAt = 14;
    
    /* AccessToken元数据: ACCESS_TOKEN_METADATA VARCHAR */
    public static final String PROP_NAME_accessTokenMetadata = "accessTokenMetadata";
    public static final int PROP_ID_accessTokenMetadata = 15;
    
    /* AccessToken类型: ACCESS_TOKEN_TYPE VARCHAR */
    public static final String PROP_NAME_accessTokenType = "accessTokenType";
    public static final int PROP_ID_accessTokenType = 16;
    
    /* AccessToken权限范围: ACCESS_TOKEN_SCOPES VARCHAR */
    public static final String PROP_NAME_accessTokenScopes = "accessTokenScopes";
    public static final int PROP_ID_accessTokenScopes = 17;
    
    /* OidcIdToken值: OIDC_ID_TOKEN_VALUE VARCHAR */
    public static final String PROP_NAME_oidcIdTokenValue = "oidcIdTokenValue";
    public static final int PROP_ID_oidcIdTokenValue = 18;
    
    /* OidcIdToken发放时间: OIDC_ID_TOKEN_ISSUED_AT TIMESTAMP */
    public static final String PROP_NAME_oidcIdTokenIssuedAt = "oidcIdTokenIssuedAt";
    public static final int PROP_ID_oidcIdTokenIssuedAt = 19;
    
    /* OidcIdToken过期时间: OIDC_ID_TOKEN_EXPIRES_AT TIMESTAMP */
    public static final String PROP_NAME_oidcIdTokenExpiresAt = "oidcIdTokenExpiresAt";
    public static final int PROP_ID_oidcIdTokenExpiresAt = 20;
    
    /* OidcIdToken元数据: OIDC_ID_TOKEN_METADATA VARCHAR */
    public static final String PROP_NAME_oidcIdTokenMetadata = "oidcIdTokenMetadata";
    public static final int PROP_ID_oidcIdTokenMetadata = 21;
    
    /* RefreshToken值: REFRESH_TOKEN_VALUE VARCHAR */
    public static final String PROP_NAME_refreshTokenValue = "refreshTokenValue";
    public static final int PROP_ID_refreshTokenValue = 22;
    
    /* RefreshToken发放时间: REFRESH_TOKEN_ISSUED_AT TIMESTAMP */
    public static final String PROP_NAME_refreshTokenIssuedAt = "refreshTokenIssuedAt";
    public static final int PROP_ID_refreshTokenIssuedAt = 23;
    
    /* RefreshToken过期时间: REFRESH_TOKEN_EXPIRES_AT TIMESTAMP */
    public static final String PROP_NAME_refreshTokenExpiresAt = "refreshTokenExpiresAt";
    public static final int PROP_ID_refreshTokenExpiresAt = 24;
    
    /* RefreshToken元数据: REFRESH_TOKEN_METADATA VARCHAR */
    public static final String PROP_NAME_refreshTokenMetadata = "refreshTokenMetadata";
    public static final int PROP_ID_refreshTokenMetadata = 25;
    
    /* UserCode值: USER_CODE_VALUE VARCHAR */
    public static final String PROP_NAME_userCodeValue = "userCodeValue";
    public static final int PROP_ID_userCodeValue = 26;
    
    /* UserCode发放时间: USER_CODE_ISSUED_AT TIMESTAMP */
    public static final String PROP_NAME_userCodeIssuedAt = "userCodeIssuedAt";
    public static final int PROP_ID_userCodeIssuedAt = 27;
    
    /* UserCode过期时间: USER_CODE_EXPIRES_AT TIMESTAMP */
    public static final String PROP_NAME_userCodeExpiresAt = "userCodeExpiresAt";
    public static final int PROP_ID_userCodeExpiresAt = 28;
    
    /* UserCode元数据: USER_CODE_METADATA VARCHAR */
    public static final String PROP_NAME_userCodeMetadata = "userCodeMetadata";
    public static final int PROP_ID_userCodeMetadata = 29;
    
    /* DeviceCode值: DEVICE_CODE_VALUE VARCHAR */
    public static final String PROP_NAME_deviceCodeValue = "deviceCodeValue";
    public static final int PROP_ID_deviceCodeValue = 30;
    
    /* DeviceCode发放时间: DEVICE_CODE_ISSUED_AT TIMESTAMP */
    public static final String PROP_NAME_deviceCodeIssuedAt = "deviceCodeIssuedAt";
    public static final int PROP_ID_deviceCodeIssuedAt = 31;
    
    /* DeviceCode过期时间: DEVICE_CODE_EXPIRES_AT TIMESTAMP */
    public static final String PROP_NAME_deviceCodeExpiresAt = "deviceCodeExpiresAt";
    public static final int PROP_ID_deviceCodeExpiresAt = 32;
    
    /* DeviceCode元数据: DEVICE_CODE_METADATA VARCHAR */
    public static final String PROP_NAME_deviceCodeMetadata = "deviceCodeMetadata";
    public static final int PROP_ID_deviceCodeMetadata = 33;
    

    private static int _PROP_ID_BOUND = 34;

    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_sid);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_sid};

    private static final String[] PROP_ID_TO_NAME = new String[34];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_sid] = PROP_NAME_sid;
          PROP_NAME_TO_ID.put(PROP_NAME_sid, PROP_ID_sid);
      
          PROP_ID_TO_NAME[PROP_ID_registeredClientId] = PROP_NAME_registeredClientId;
          PROP_NAME_TO_ID.put(PROP_NAME_registeredClientId, PROP_ID_registeredClientId);
      
          PROP_ID_TO_NAME[PROP_ID_principalName] = PROP_NAME_principalName;
          PROP_NAME_TO_ID.put(PROP_NAME_principalName, PROP_ID_principalName);
      
          PROP_ID_TO_NAME[PROP_ID_authorizationGrantType] = PROP_NAME_authorizationGrantType;
          PROP_NAME_TO_ID.put(PROP_NAME_authorizationGrantType, PROP_ID_authorizationGrantType);
      
          PROP_ID_TO_NAME[PROP_ID_authorizedScopes] = PROP_NAME_authorizedScopes;
          PROP_NAME_TO_ID.put(PROP_NAME_authorizedScopes, PROP_ID_authorizedScopes);
      
          PROP_ID_TO_NAME[PROP_ID_attributes] = PROP_NAME_attributes;
          PROP_NAME_TO_ID.put(PROP_NAME_attributes, PROP_ID_attributes);
      
          PROP_ID_TO_NAME[PROP_ID_state] = PROP_NAME_state;
          PROP_NAME_TO_ID.put(PROP_NAME_state, PROP_ID_state);
      
          PROP_ID_TO_NAME[PROP_ID_authorizationCodeValue] = PROP_NAME_authorizationCodeValue;
          PROP_NAME_TO_ID.put(PROP_NAME_authorizationCodeValue, PROP_ID_authorizationCodeValue);
      
          PROP_ID_TO_NAME[PROP_ID_authorizationCodeIssuedAt] = PROP_NAME_authorizationCodeIssuedAt;
          PROP_NAME_TO_ID.put(PROP_NAME_authorizationCodeIssuedAt, PROP_ID_authorizationCodeIssuedAt);
      
          PROP_ID_TO_NAME[PROP_ID_authorizationCodeExpiresAt] = PROP_NAME_authorizationCodeExpiresAt;
          PROP_NAME_TO_ID.put(PROP_NAME_authorizationCodeExpiresAt, PROP_ID_authorizationCodeExpiresAt);
      
          PROP_ID_TO_NAME[PROP_ID_authorizationCodeMetadata] = PROP_NAME_authorizationCodeMetadata;
          PROP_NAME_TO_ID.put(PROP_NAME_authorizationCodeMetadata, PROP_ID_authorizationCodeMetadata);
      
          PROP_ID_TO_NAME[PROP_ID_accessTokenValue] = PROP_NAME_accessTokenValue;
          PROP_NAME_TO_ID.put(PROP_NAME_accessTokenValue, PROP_ID_accessTokenValue);
      
          PROP_ID_TO_NAME[PROP_ID_accessTokenIssuedAt] = PROP_NAME_accessTokenIssuedAt;
          PROP_NAME_TO_ID.put(PROP_NAME_accessTokenIssuedAt, PROP_ID_accessTokenIssuedAt);
      
          PROP_ID_TO_NAME[PROP_ID_accessTokenExpiresAt] = PROP_NAME_accessTokenExpiresAt;
          PROP_NAME_TO_ID.put(PROP_NAME_accessTokenExpiresAt, PROP_ID_accessTokenExpiresAt);
      
          PROP_ID_TO_NAME[PROP_ID_accessTokenMetadata] = PROP_NAME_accessTokenMetadata;
          PROP_NAME_TO_ID.put(PROP_NAME_accessTokenMetadata, PROP_ID_accessTokenMetadata);
      
          PROP_ID_TO_NAME[PROP_ID_accessTokenType] = PROP_NAME_accessTokenType;
          PROP_NAME_TO_ID.put(PROP_NAME_accessTokenType, PROP_ID_accessTokenType);
      
          PROP_ID_TO_NAME[PROP_ID_accessTokenScopes] = PROP_NAME_accessTokenScopes;
          PROP_NAME_TO_ID.put(PROP_NAME_accessTokenScopes, PROP_ID_accessTokenScopes);
      
          PROP_ID_TO_NAME[PROP_ID_oidcIdTokenValue] = PROP_NAME_oidcIdTokenValue;
          PROP_NAME_TO_ID.put(PROP_NAME_oidcIdTokenValue, PROP_ID_oidcIdTokenValue);
      
          PROP_ID_TO_NAME[PROP_ID_oidcIdTokenIssuedAt] = PROP_NAME_oidcIdTokenIssuedAt;
          PROP_NAME_TO_ID.put(PROP_NAME_oidcIdTokenIssuedAt, PROP_ID_oidcIdTokenIssuedAt);
      
          PROP_ID_TO_NAME[PROP_ID_oidcIdTokenExpiresAt] = PROP_NAME_oidcIdTokenExpiresAt;
          PROP_NAME_TO_ID.put(PROP_NAME_oidcIdTokenExpiresAt, PROP_ID_oidcIdTokenExpiresAt);
      
          PROP_ID_TO_NAME[PROP_ID_oidcIdTokenMetadata] = PROP_NAME_oidcIdTokenMetadata;
          PROP_NAME_TO_ID.put(PROP_NAME_oidcIdTokenMetadata, PROP_ID_oidcIdTokenMetadata);
      
          PROP_ID_TO_NAME[PROP_ID_refreshTokenValue] = PROP_NAME_refreshTokenValue;
          PROP_NAME_TO_ID.put(PROP_NAME_refreshTokenValue, PROP_ID_refreshTokenValue);
      
          PROP_ID_TO_NAME[PROP_ID_refreshTokenIssuedAt] = PROP_NAME_refreshTokenIssuedAt;
          PROP_NAME_TO_ID.put(PROP_NAME_refreshTokenIssuedAt, PROP_ID_refreshTokenIssuedAt);
      
          PROP_ID_TO_NAME[PROP_ID_refreshTokenExpiresAt] = PROP_NAME_refreshTokenExpiresAt;
          PROP_NAME_TO_ID.put(PROP_NAME_refreshTokenExpiresAt, PROP_ID_refreshTokenExpiresAt);
      
          PROP_ID_TO_NAME[PROP_ID_refreshTokenMetadata] = PROP_NAME_refreshTokenMetadata;
          PROP_NAME_TO_ID.put(PROP_NAME_refreshTokenMetadata, PROP_ID_refreshTokenMetadata);
      
          PROP_ID_TO_NAME[PROP_ID_userCodeValue] = PROP_NAME_userCodeValue;
          PROP_NAME_TO_ID.put(PROP_NAME_userCodeValue, PROP_ID_userCodeValue);
      
          PROP_ID_TO_NAME[PROP_ID_userCodeIssuedAt] = PROP_NAME_userCodeIssuedAt;
          PROP_NAME_TO_ID.put(PROP_NAME_userCodeIssuedAt, PROP_ID_userCodeIssuedAt);
      
          PROP_ID_TO_NAME[PROP_ID_userCodeExpiresAt] = PROP_NAME_userCodeExpiresAt;
          PROP_NAME_TO_ID.put(PROP_NAME_userCodeExpiresAt, PROP_ID_userCodeExpiresAt);
      
          PROP_ID_TO_NAME[PROP_ID_userCodeMetadata] = PROP_NAME_userCodeMetadata;
          PROP_NAME_TO_ID.put(PROP_NAME_userCodeMetadata, PROP_ID_userCodeMetadata);
      
          PROP_ID_TO_NAME[PROP_ID_deviceCodeValue] = PROP_NAME_deviceCodeValue;
          PROP_NAME_TO_ID.put(PROP_NAME_deviceCodeValue, PROP_ID_deviceCodeValue);
      
          PROP_ID_TO_NAME[PROP_ID_deviceCodeIssuedAt] = PROP_NAME_deviceCodeIssuedAt;
          PROP_NAME_TO_ID.put(PROP_NAME_deviceCodeIssuedAt, PROP_ID_deviceCodeIssuedAt);
      
          PROP_ID_TO_NAME[PROP_ID_deviceCodeExpiresAt] = PROP_NAME_deviceCodeExpiresAt;
          PROP_NAME_TO_ID.put(PROP_NAME_deviceCodeExpiresAt, PROP_ID_deviceCodeExpiresAt);
      
          PROP_ID_TO_NAME[PROP_ID_deviceCodeMetadata] = PROP_NAME_deviceCodeMetadata;
          PROP_NAME_TO_ID.put(PROP_NAME_deviceCodeMetadata, PROP_ID_deviceCodeMetadata);
      
    }

    
    /* Id: SID */
    private java.lang.String _sid;
    
    /* 客户端ID: REGISTERED_CLIENT_ID */
    private java.lang.String _registeredClientId;
    
    /* 客户端名称: PRINCIPAL_NAME */
    private java.lang.String _principalName;
    
    /* 授权类型: AUTHORIZATION_GRANT_TYPE */
    private java.lang.String _authorizationGrantType;
    
    /* 授权范围: AUTHORIZED_SCOPES */
    private java.lang.String _authorizedScopes;
    
    /* 扩展属性: ATTRIBUTES */
    private java.lang.String _attributes;
    
    /* 状态码: STATE */
    private java.lang.String _state;
    
    /* AuthorizationCode值: AUTHORIZATION_CODE_VALUE */
    private java.lang.String _authorizationCodeValue;
    
    /* AuthorizationCode发放时间: AUTHORIZATION_CODE_ISSUED_AT */
    private java.sql.Timestamp _authorizationCodeIssuedAt;
    
    /* AuthorizationCode过期使劲按: AUTHORIZATION_CODE_EXPIRES_AT */
    private java.sql.Timestamp _authorizationCodeExpiresAt;
    
    /* AuthorizationCode元数据: AUTHORIZATION_CODE_METADATA */
    private java.lang.String _authorizationCodeMetadata;
    
    /* AccessToken: ACCESS_TOKEN_VALUE */
    private java.lang.String _accessTokenValue;
    
    /* AccessToken发放时间: ACCESS_TOKEN_ISSUED_AT */
    private java.sql.Timestamp _accessTokenIssuedAt;
    
    /* AccessToken过期时间: ACCESS_TOKEN_EXPIRES_AT */
    private java.sql.Timestamp _accessTokenExpiresAt;
    
    /* AccessToken元数据: ACCESS_TOKEN_METADATA */
    private java.lang.String _accessTokenMetadata;
    
    /* AccessToken类型: ACCESS_TOKEN_TYPE */
    private java.lang.String _accessTokenType;
    
    /* AccessToken权限范围: ACCESS_TOKEN_SCOPES */
    private java.lang.String _accessTokenScopes;
    
    /* OidcIdToken值: OIDC_ID_TOKEN_VALUE */
    private java.lang.String _oidcIdTokenValue;
    
    /* OidcIdToken发放时间: OIDC_ID_TOKEN_ISSUED_AT */
    private java.sql.Timestamp _oidcIdTokenIssuedAt;
    
    /* OidcIdToken过期时间: OIDC_ID_TOKEN_EXPIRES_AT */
    private java.sql.Timestamp _oidcIdTokenExpiresAt;
    
    /* OidcIdToken元数据: OIDC_ID_TOKEN_METADATA */
    private java.lang.String _oidcIdTokenMetadata;
    
    /* RefreshToken值: REFRESH_TOKEN_VALUE */
    private java.lang.String _refreshTokenValue;
    
    /* RefreshToken发放时间: REFRESH_TOKEN_ISSUED_AT */
    private java.sql.Timestamp _refreshTokenIssuedAt;
    
    /* RefreshToken过期时间: REFRESH_TOKEN_EXPIRES_AT */
    private java.sql.Timestamp _refreshTokenExpiresAt;
    
    /* RefreshToken元数据: REFRESH_TOKEN_METADATA */
    private java.lang.String _refreshTokenMetadata;
    
    /* UserCode值: USER_CODE_VALUE */
    private java.lang.String _userCodeValue;
    
    /* UserCode发放时间: USER_CODE_ISSUED_AT */
    private java.sql.Timestamp _userCodeIssuedAt;
    
    /* UserCode过期时间: USER_CODE_EXPIRES_AT */
    private java.sql.Timestamp _userCodeExpiresAt;
    
    /* UserCode元数据: USER_CODE_METADATA */
    private java.lang.String _userCodeMetadata;
    
    /* DeviceCode值: DEVICE_CODE_VALUE */
    private java.lang.String _deviceCodeValue;
    
    /* DeviceCode发放时间: DEVICE_CODE_ISSUED_AT */
    private java.sql.Timestamp _deviceCodeIssuedAt;
    
    /* DeviceCode过期时间: DEVICE_CODE_EXPIRES_AT */
    private java.sql.Timestamp _deviceCodeExpiresAt;
    
    /* DeviceCode元数据: DEVICE_CODE_METADATA */
    private java.lang.String _deviceCodeMetadata;
    

    public _NopOauthAuthorization(){
        // for debug
    }

    protected NopOauthAuthorization newInstance(){
        NopOauthAuthorization entity = new NopOauthAuthorization();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopOauthAuthorization cloneInstance() {
        NopOauthAuthorization entity = newInstance();
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
      return "io.nop.oauth.dao.entity.NopOauthAuthorization";
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
        
            case PROP_ID_registeredClientId:
               return getRegisteredClientId();
        
            case PROP_ID_principalName:
               return getPrincipalName();
        
            case PROP_ID_authorizationGrantType:
               return getAuthorizationGrantType();
        
            case PROP_ID_authorizedScopes:
               return getAuthorizedScopes();
        
            case PROP_ID_attributes:
               return getAttributes();
        
            case PROP_ID_state:
               return getState();
        
            case PROP_ID_authorizationCodeValue:
               return getAuthorizationCodeValue();
        
            case PROP_ID_authorizationCodeIssuedAt:
               return getAuthorizationCodeIssuedAt();
        
            case PROP_ID_authorizationCodeExpiresAt:
               return getAuthorizationCodeExpiresAt();
        
            case PROP_ID_authorizationCodeMetadata:
               return getAuthorizationCodeMetadata();
        
            case PROP_ID_accessTokenValue:
               return getAccessTokenValue();
        
            case PROP_ID_accessTokenIssuedAt:
               return getAccessTokenIssuedAt();
        
            case PROP_ID_accessTokenExpiresAt:
               return getAccessTokenExpiresAt();
        
            case PROP_ID_accessTokenMetadata:
               return getAccessTokenMetadata();
        
            case PROP_ID_accessTokenType:
               return getAccessTokenType();
        
            case PROP_ID_accessTokenScopes:
               return getAccessTokenScopes();
        
            case PROP_ID_oidcIdTokenValue:
               return getOidcIdTokenValue();
        
            case PROP_ID_oidcIdTokenIssuedAt:
               return getOidcIdTokenIssuedAt();
        
            case PROP_ID_oidcIdTokenExpiresAt:
               return getOidcIdTokenExpiresAt();
        
            case PROP_ID_oidcIdTokenMetadata:
               return getOidcIdTokenMetadata();
        
            case PROP_ID_refreshTokenValue:
               return getRefreshTokenValue();
        
            case PROP_ID_refreshTokenIssuedAt:
               return getRefreshTokenIssuedAt();
        
            case PROP_ID_refreshTokenExpiresAt:
               return getRefreshTokenExpiresAt();
        
            case PROP_ID_refreshTokenMetadata:
               return getRefreshTokenMetadata();
        
            case PROP_ID_userCodeValue:
               return getUserCodeValue();
        
            case PROP_ID_userCodeIssuedAt:
               return getUserCodeIssuedAt();
        
            case PROP_ID_userCodeExpiresAt:
               return getUserCodeExpiresAt();
        
            case PROP_ID_userCodeMetadata:
               return getUserCodeMetadata();
        
            case PROP_ID_deviceCodeValue:
               return getDeviceCodeValue();
        
            case PROP_ID_deviceCodeIssuedAt:
               return getDeviceCodeIssuedAt();
        
            case PROP_ID_deviceCodeExpiresAt:
               return getDeviceCodeExpiresAt();
        
            case PROP_ID_deviceCodeMetadata:
               return getDeviceCodeMetadata();
        
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
        
            case PROP_ID_registeredClientId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_registeredClientId));
               }
               setRegisteredClientId(typedValue);
               break;
            }
        
            case PROP_ID_principalName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_principalName));
               }
               setPrincipalName(typedValue);
               break;
            }
        
            case PROP_ID_authorizationGrantType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_authorizationGrantType));
               }
               setAuthorizationGrantType(typedValue);
               break;
            }
        
            case PROP_ID_authorizedScopes:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_authorizedScopes));
               }
               setAuthorizedScopes(typedValue);
               break;
            }
        
            case PROP_ID_attributes:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_attributes));
               }
               setAttributes(typedValue);
               break;
            }
        
            case PROP_ID_state:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_state));
               }
               setState(typedValue);
               break;
            }
        
            case PROP_ID_authorizationCodeValue:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_authorizationCodeValue));
               }
               setAuthorizationCodeValue(typedValue);
               break;
            }
        
            case PROP_ID_authorizationCodeIssuedAt:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_authorizationCodeIssuedAt));
               }
               setAuthorizationCodeIssuedAt(typedValue);
               break;
            }
        
            case PROP_ID_authorizationCodeExpiresAt:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_authorizationCodeExpiresAt));
               }
               setAuthorizationCodeExpiresAt(typedValue);
               break;
            }
        
            case PROP_ID_authorizationCodeMetadata:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_authorizationCodeMetadata));
               }
               setAuthorizationCodeMetadata(typedValue);
               break;
            }
        
            case PROP_ID_accessTokenValue:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_accessTokenValue));
               }
               setAccessTokenValue(typedValue);
               break;
            }
        
            case PROP_ID_accessTokenIssuedAt:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_accessTokenIssuedAt));
               }
               setAccessTokenIssuedAt(typedValue);
               break;
            }
        
            case PROP_ID_accessTokenExpiresAt:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_accessTokenExpiresAt));
               }
               setAccessTokenExpiresAt(typedValue);
               break;
            }
        
            case PROP_ID_accessTokenMetadata:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_accessTokenMetadata));
               }
               setAccessTokenMetadata(typedValue);
               break;
            }
        
            case PROP_ID_accessTokenType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_accessTokenType));
               }
               setAccessTokenType(typedValue);
               break;
            }
        
            case PROP_ID_accessTokenScopes:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_accessTokenScopes));
               }
               setAccessTokenScopes(typedValue);
               break;
            }
        
            case PROP_ID_oidcIdTokenValue:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_oidcIdTokenValue));
               }
               setOidcIdTokenValue(typedValue);
               break;
            }
        
            case PROP_ID_oidcIdTokenIssuedAt:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_oidcIdTokenIssuedAt));
               }
               setOidcIdTokenIssuedAt(typedValue);
               break;
            }
        
            case PROP_ID_oidcIdTokenExpiresAt:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_oidcIdTokenExpiresAt));
               }
               setOidcIdTokenExpiresAt(typedValue);
               break;
            }
        
            case PROP_ID_oidcIdTokenMetadata:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_oidcIdTokenMetadata));
               }
               setOidcIdTokenMetadata(typedValue);
               break;
            }
        
            case PROP_ID_refreshTokenValue:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_refreshTokenValue));
               }
               setRefreshTokenValue(typedValue);
               break;
            }
        
            case PROP_ID_refreshTokenIssuedAt:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_refreshTokenIssuedAt));
               }
               setRefreshTokenIssuedAt(typedValue);
               break;
            }
        
            case PROP_ID_refreshTokenExpiresAt:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_refreshTokenExpiresAt));
               }
               setRefreshTokenExpiresAt(typedValue);
               break;
            }
        
            case PROP_ID_refreshTokenMetadata:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_refreshTokenMetadata));
               }
               setRefreshTokenMetadata(typedValue);
               break;
            }
        
            case PROP_ID_userCodeValue:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_userCodeValue));
               }
               setUserCodeValue(typedValue);
               break;
            }
        
            case PROP_ID_userCodeIssuedAt:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_userCodeIssuedAt));
               }
               setUserCodeIssuedAt(typedValue);
               break;
            }
        
            case PROP_ID_userCodeExpiresAt:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_userCodeExpiresAt));
               }
               setUserCodeExpiresAt(typedValue);
               break;
            }
        
            case PROP_ID_userCodeMetadata:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_userCodeMetadata));
               }
               setUserCodeMetadata(typedValue);
               break;
            }
        
            case PROP_ID_deviceCodeValue:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_deviceCodeValue));
               }
               setDeviceCodeValue(typedValue);
               break;
            }
        
            case PROP_ID_deviceCodeIssuedAt:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_deviceCodeIssuedAt));
               }
               setDeviceCodeIssuedAt(typedValue);
               break;
            }
        
            case PROP_ID_deviceCodeExpiresAt:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_deviceCodeExpiresAt));
               }
               setDeviceCodeExpiresAt(typedValue);
               break;
            }
        
            case PROP_ID_deviceCodeMetadata:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_deviceCodeMetadata));
               }
               setDeviceCodeMetadata(typedValue);
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
        
            case PROP_ID_registeredClientId:{
               onInitProp(propId);
               this._registeredClientId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_principalName:{
               onInitProp(propId);
               this._principalName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_authorizationGrantType:{
               onInitProp(propId);
               this._authorizationGrantType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_authorizedScopes:{
               onInitProp(propId);
               this._authorizedScopes = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_attributes:{
               onInitProp(propId);
               this._attributes = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_state:{
               onInitProp(propId);
               this._state = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_authorizationCodeValue:{
               onInitProp(propId);
               this._authorizationCodeValue = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_authorizationCodeIssuedAt:{
               onInitProp(propId);
               this._authorizationCodeIssuedAt = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_authorizationCodeExpiresAt:{
               onInitProp(propId);
               this._authorizationCodeExpiresAt = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_authorizationCodeMetadata:{
               onInitProp(propId);
               this._authorizationCodeMetadata = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_accessTokenValue:{
               onInitProp(propId);
               this._accessTokenValue = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_accessTokenIssuedAt:{
               onInitProp(propId);
               this._accessTokenIssuedAt = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_accessTokenExpiresAt:{
               onInitProp(propId);
               this._accessTokenExpiresAt = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_accessTokenMetadata:{
               onInitProp(propId);
               this._accessTokenMetadata = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_accessTokenType:{
               onInitProp(propId);
               this._accessTokenType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_accessTokenScopes:{
               onInitProp(propId);
               this._accessTokenScopes = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_oidcIdTokenValue:{
               onInitProp(propId);
               this._oidcIdTokenValue = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_oidcIdTokenIssuedAt:{
               onInitProp(propId);
               this._oidcIdTokenIssuedAt = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_oidcIdTokenExpiresAt:{
               onInitProp(propId);
               this._oidcIdTokenExpiresAt = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_oidcIdTokenMetadata:{
               onInitProp(propId);
               this._oidcIdTokenMetadata = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_refreshTokenValue:{
               onInitProp(propId);
               this._refreshTokenValue = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_refreshTokenIssuedAt:{
               onInitProp(propId);
               this._refreshTokenIssuedAt = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_refreshTokenExpiresAt:{
               onInitProp(propId);
               this._refreshTokenExpiresAt = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_refreshTokenMetadata:{
               onInitProp(propId);
               this._refreshTokenMetadata = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_userCodeValue:{
               onInitProp(propId);
               this._userCodeValue = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_userCodeIssuedAt:{
               onInitProp(propId);
               this._userCodeIssuedAt = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_userCodeExpiresAt:{
               onInitProp(propId);
               this._userCodeExpiresAt = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_userCodeMetadata:{
               onInitProp(propId);
               this._userCodeMetadata = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_deviceCodeValue:{
               onInitProp(propId);
               this._deviceCodeValue = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_deviceCodeIssuedAt:{
               onInitProp(propId);
               this._deviceCodeIssuedAt = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_deviceCodeExpiresAt:{
               onInitProp(propId);
               this._deviceCodeExpiresAt = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_deviceCodeMetadata:{
               onInitProp(propId);
               this._deviceCodeMetadata = (java.lang.String)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * Id: SID
     */
    public final java.lang.String getSid(){
         onPropGet(PROP_ID_sid);
         return _sid;
    }

    /**
     * Id: SID
     */
    public final void setSid(java.lang.String value){
        if(onPropSet(PROP_ID_sid,value)){
            this._sid = value;
            internalClearRefs(PROP_ID_sid);
            orm_id();
        }
    }
    
    /**
     * 客户端ID: REGISTERED_CLIENT_ID
     */
    public final java.lang.String getRegisteredClientId(){
         onPropGet(PROP_ID_registeredClientId);
         return _registeredClientId;
    }

    /**
     * 客户端ID: REGISTERED_CLIENT_ID
     */
    public final void setRegisteredClientId(java.lang.String value){
        if(onPropSet(PROP_ID_registeredClientId,value)){
            this._registeredClientId = value;
            internalClearRefs(PROP_ID_registeredClientId);
            
        }
    }
    
    /**
     * 客户端名称: PRINCIPAL_NAME
     */
    public final java.lang.String getPrincipalName(){
         onPropGet(PROP_ID_principalName);
         return _principalName;
    }

    /**
     * 客户端名称: PRINCIPAL_NAME
     */
    public final void setPrincipalName(java.lang.String value){
        if(onPropSet(PROP_ID_principalName,value)){
            this._principalName = value;
            internalClearRefs(PROP_ID_principalName);
            
        }
    }
    
    /**
     * 授权类型: AUTHORIZATION_GRANT_TYPE
     */
    public final java.lang.String getAuthorizationGrantType(){
         onPropGet(PROP_ID_authorizationGrantType);
         return _authorizationGrantType;
    }

    /**
     * 授权类型: AUTHORIZATION_GRANT_TYPE
     */
    public final void setAuthorizationGrantType(java.lang.String value){
        if(onPropSet(PROP_ID_authorizationGrantType,value)){
            this._authorizationGrantType = value;
            internalClearRefs(PROP_ID_authorizationGrantType);
            
        }
    }
    
    /**
     * 授权范围: AUTHORIZED_SCOPES
     */
    public final java.lang.String getAuthorizedScopes(){
         onPropGet(PROP_ID_authorizedScopes);
         return _authorizedScopes;
    }

    /**
     * 授权范围: AUTHORIZED_SCOPES
     */
    public final void setAuthorizedScopes(java.lang.String value){
        if(onPropSet(PROP_ID_authorizedScopes,value)){
            this._authorizedScopes = value;
            internalClearRefs(PROP_ID_authorizedScopes);
            
        }
    }
    
    /**
     * 扩展属性: ATTRIBUTES
     */
    public final java.lang.String getAttributes(){
         onPropGet(PROP_ID_attributes);
         return _attributes;
    }

    /**
     * 扩展属性: ATTRIBUTES
     */
    public final void setAttributes(java.lang.String value){
        if(onPropSet(PROP_ID_attributes,value)){
            this._attributes = value;
            internalClearRefs(PROP_ID_attributes);
            
        }
    }
    
    /**
     * 状态码: STATE
     */
    public final java.lang.String getState(){
         onPropGet(PROP_ID_state);
         return _state;
    }

    /**
     * 状态码: STATE
     */
    public final void setState(java.lang.String value){
        if(onPropSet(PROP_ID_state,value)){
            this._state = value;
            internalClearRefs(PROP_ID_state);
            
        }
    }
    
    /**
     * AuthorizationCode值: AUTHORIZATION_CODE_VALUE
     */
    public final java.lang.String getAuthorizationCodeValue(){
         onPropGet(PROP_ID_authorizationCodeValue);
         return _authorizationCodeValue;
    }

    /**
     * AuthorizationCode值: AUTHORIZATION_CODE_VALUE
     */
    public final void setAuthorizationCodeValue(java.lang.String value){
        if(onPropSet(PROP_ID_authorizationCodeValue,value)){
            this._authorizationCodeValue = value;
            internalClearRefs(PROP_ID_authorizationCodeValue);
            
        }
    }
    
    /**
     * AuthorizationCode发放时间: AUTHORIZATION_CODE_ISSUED_AT
     */
    public final java.sql.Timestamp getAuthorizationCodeIssuedAt(){
         onPropGet(PROP_ID_authorizationCodeIssuedAt);
         return _authorizationCodeIssuedAt;
    }

    /**
     * AuthorizationCode发放时间: AUTHORIZATION_CODE_ISSUED_AT
     */
    public final void setAuthorizationCodeIssuedAt(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_authorizationCodeIssuedAt,value)){
            this._authorizationCodeIssuedAt = value;
            internalClearRefs(PROP_ID_authorizationCodeIssuedAt);
            
        }
    }
    
    /**
     * AuthorizationCode过期使劲按: AUTHORIZATION_CODE_EXPIRES_AT
     */
    public final java.sql.Timestamp getAuthorizationCodeExpiresAt(){
         onPropGet(PROP_ID_authorizationCodeExpiresAt);
         return _authorizationCodeExpiresAt;
    }

    /**
     * AuthorizationCode过期使劲按: AUTHORIZATION_CODE_EXPIRES_AT
     */
    public final void setAuthorizationCodeExpiresAt(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_authorizationCodeExpiresAt,value)){
            this._authorizationCodeExpiresAt = value;
            internalClearRefs(PROP_ID_authorizationCodeExpiresAt);
            
        }
    }
    
    /**
     * AuthorizationCode元数据: AUTHORIZATION_CODE_METADATA
     */
    public final java.lang.String getAuthorizationCodeMetadata(){
         onPropGet(PROP_ID_authorizationCodeMetadata);
         return _authorizationCodeMetadata;
    }

    /**
     * AuthorizationCode元数据: AUTHORIZATION_CODE_METADATA
     */
    public final void setAuthorizationCodeMetadata(java.lang.String value){
        if(onPropSet(PROP_ID_authorizationCodeMetadata,value)){
            this._authorizationCodeMetadata = value;
            internalClearRefs(PROP_ID_authorizationCodeMetadata);
            
        }
    }
    
    /**
     * AccessToken: ACCESS_TOKEN_VALUE
     */
    public final java.lang.String getAccessTokenValue(){
         onPropGet(PROP_ID_accessTokenValue);
         return _accessTokenValue;
    }

    /**
     * AccessToken: ACCESS_TOKEN_VALUE
     */
    public final void setAccessTokenValue(java.lang.String value){
        if(onPropSet(PROP_ID_accessTokenValue,value)){
            this._accessTokenValue = value;
            internalClearRefs(PROP_ID_accessTokenValue);
            
        }
    }
    
    /**
     * AccessToken发放时间: ACCESS_TOKEN_ISSUED_AT
     */
    public final java.sql.Timestamp getAccessTokenIssuedAt(){
         onPropGet(PROP_ID_accessTokenIssuedAt);
         return _accessTokenIssuedAt;
    }

    /**
     * AccessToken发放时间: ACCESS_TOKEN_ISSUED_AT
     */
    public final void setAccessTokenIssuedAt(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_accessTokenIssuedAt,value)){
            this._accessTokenIssuedAt = value;
            internalClearRefs(PROP_ID_accessTokenIssuedAt);
            
        }
    }
    
    /**
     * AccessToken过期时间: ACCESS_TOKEN_EXPIRES_AT
     */
    public final java.sql.Timestamp getAccessTokenExpiresAt(){
         onPropGet(PROP_ID_accessTokenExpiresAt);
         return _accessTokenExpiresAt;
    }

    /**
     * AccessToken过期时间: ACCESS_TOKEN_EXPIRES_AT
     */
    public final void setAccessTokenExpiresAt(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_accessTokenExpiresAt,value)){
            this._accessTokenExpiresAt = value;
            internalClearRefs(PROP_ID_accessTokenExpiresAt);
            
        }
    }
    
    /**
     * AccessToken元数据: ACCESS_TOKEN_METADATA
     */
    public final java.lang.String getAccessTokenMetadata(){
         onPropGet(PROP_ID_accessTokenMetadata);
         return _accessTokenMetadata;
    }

    /**
     * AccessToken元数据: ACCESS_TOKEN_METADATA
     */
    public final void setAccessTokenMetadata(java.lang.String value){
        if(onPropSet(PROP_ID_accessTokenMetadata,value)){
            this._accessTokenMetadata = value;
            internalClearRefs(PROP_ID_accessTokenMetadata);
            
        }
    }
    
    /**
     * AccessToken类型: ACCESS_TOKEN_TYPE
     */
    public final java.lang.String getAccessTokenType(){
         onPropGet(PROP_ID_accessTokenType);
         return _accessTokenType;
    }

    /**
     * AccessToken类型: ACCESS_TOKEN_TYPE
     */
    public final void setAccessTokenType(java.lang.String value){
        if(onPropSet(PROP_ID_accessTokenType,value)){
            this._accessTokenType = value;
            internalClearRefs(PROP_ID_accessTokenType);
            
        }
    }
    
    /**
     * AccessToken权限范围: ACCESS_TOKEN_SCOPES
     */
    public final java.lang.String getAccessTokenScopes(){
         onPropGet(PROP_ID_accessTokenScopes);
         return _accessTokenScopes;
    }

    /**
     * AccessToken权限范围: ACCESS_TOKEN_SCOPES
     */
    public final void setAccessTokenScopes(java.lang.String value){
        if(onPropSet(PROP_ID_accessTokenScopes,value)){
            this._accessTokenScopes = value;
            internalClearRefs(PROP_ID_accessTokenScopes);
            
        }
    }
    
    /**
     * OidcIdToken值: OIDC_ID_TOKEN_VALUE
     */
    public final java.lang.String getOidcIdTokenValue(){
         onPropGet(PROP_ID_oidcIdTokenValue);
         return _oidcIdTokenValue;
    }

    /**
     * OidcIdToken值: OIDC_ID_TOKEN_VALUE
     */
    public final void setOidcIdTokenValue(java.lang.String value){
        if(onPropSet(PROP_ID_oidcIdTokenValue,value)){
            this._oidcIdTokenValue = value;
            internalClearRefs(PROP_ID_oidcIdTokenValue);
            
        }
    }
    
    /**
     * OidcIdToken发放时间: OIDC_ID_TOKEN_ISSUED_AT
     */
    public final java.sql.Timestamp getOidcIdTokenIssuedAt(){
         onPropGet(PROP_ID_oidcIdTokenIssuedAt);
         return _oidcIdTokenIssuedAt;
    }

    /**
     * OidcIdToken发放时间: OIDC_ID_TOKEN_ISSUED_AT
     */
    public final void setOidcIdTokenIssuedAt(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_oidcIdTokenIssuedAt,value)){
            this._oidcIdTokenIssuedAt = value;
            internalClearRefs(PROP_ID_oidcIdTokenIssuedAt);
            
        }
    }
    
    /**
     * OidcIdToken过期时间: OIDC_ID_TOKEN_EXPIRES_AT
     */
    public final java.sql.Timestamp getOidcIdTokenExpiresAt(){
         onPropGet(PROP_ID_oidcIdTokenExpiresAt);
         return _oidcIdTokenExpiresAt;
    }

    /**
     * OidcIdToken过期时间: OIDC_ID_TOKEN_EXPIRES_AT
     */
    public final void setOidcIdTokenExpiresAt(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_oidcIdTokenExpiresAt,value)){
            this._oidcIdTokenExpiresAt = value;
            internalClearRefs(PROP_ID_oidcIdTokenExpiresAt);
            
        }
    }
    
    /**
     * OidcIdToken元数据: OIDC_ID_TOKEN_METADATA
     */
    public final java.lang.String getOidcIdTokenMetadata(){
         onPropGet(PROP_ID_oidcIdTokenMetadata);
         return _oidcIdTokenMetadata;
    }

    /**
     * OidcIdToken元数据: OIDC_ID_TOKEN_METADATA
     */
    public final void setOidcIdTokenMetadata(java.lang.String value){
        if(onPropSet(PROP_ID_oidcIdTokenMetadata,value)){
            this._oidcIdTokenMetadata = value;
            internalClearRefs(PROP_ID_oidcIdTokenMetadata);
            
        }
    }
    
    /**
     * RefreshToken值: REFRESH_TOKEN_VALUE
     */
    public final java.lang.String getRefreshTokenValue(){
         onPropGet(PROP_ID_refreshTokenValue);
         return _refreshTokenValue;
    }

    /**
     * RefreshToken值: REFRESH_TOKEN_VALUE
     */
    public final void setRefreshTokenValue(java.lang.String value){
        if(onPropSet(PROP_ID_refreshTokenValue,value)){
            this._refreshTokenValue = value;
            internalClearRefs(PROP_ID_refreshTokenValue);
            
        }
    }
    
    /**
     * RefreshToken发放时间: REFRESH_TOKEN_ISSUED_AT
     */
    public final java.sql.Timestamp getRefreshTokenIssuedAt(){
         onPropGet(PROP_ID_refreshTokenIssuedAt);
         return _refreshTokenIssuedAt;
    }

    /**
     * RefreshToken发放时间: REFRESH_TOKEN_ISSUED_AT
     */
    public final void setRefreshTokenIssuedAt(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_refreshTokenIssuedAt,value)){
            this._refreshTokenIssuedAt = value;
            internalClearRefs(PROP_ID_refreshTokenIssuedAt);
            
        }
    }
    
    /**
     * RefreshToken过期时间: REFRESH_TOKEN_EXPIRES_AT
     */
    public final java.sql.Timestamp getRefreshTokenExpiresAt(){
         onPropGet(PROP_ID_refreshTokenExpiresAt);
         return _refreshTokenExpiresAt;
    }

    /**
     * RefreshToken过期时间: REFRESH_TOKEN_EXPIRES_AT
     */
    public final void setRefreshTokenExpiresAt(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_refreshTokenExpiresAt,value)){
            this._refreshTokenExpiresAt = value;
            internalClearRefs(PROP_ID_refreshTokenExpiresAt);
            
        }
    }
    
    /**
     * RefreshToken元数据: REFRESH_TOKEN_METADATA
     */
    public final java.lang.String getRefreshTokenMetadata(){
         onPropGet(PROP_ID_refreshTokenMetadata);
         return _refreshTokenMetadata;
    }

    /**
     * RefreshToken元数据: REFRESH_TOKEN_METADATA
     */
    public final void setRefreshTokenMetadata(java.lang.String value){
        if(onPropSet(PROP_ID_refreshTokenMetadata,value)){
            this._refreshTokenMetadata = value;
            internalClearRefs(PROP_ID_refreshTokenMetadata);
            
        }
    }
    
    /**
     * UserCode值: USER_CODE_VALUE
     */
    public final java.lang.String getUserCodeValue(){
         onPropGet(PROP_ID_userCodeValue);
         return _userCodeValue;
    }

    /**
     * UserCode值: USER_CODE_VALUE
     */
    public final void setUserCodeValue(java.lang.String value){
        if(onPropSet(PROP_ID_userCodeValue,value)){
            this._userCodeValue = value;
            internalClearRefs(PROP_ID_userCodeValue);
            
        }
    }
    
    /**
     * UserCode发放时间: USER_CODE_ISSUED_AT
     */
    public final java.sql.Timestamp getUserCodeIssuedAt(){
         onPropGet(PROP_ID_userCodeIssuedAt);
         return _userCodeIssuedAt;
    }

    /**
     * UserCode发放时间: USER_CODE_ISSUED_AT
     */
    public final void setUserCodeIssuedAt(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_userCodeIssuedAt,value)){
            this._userCodeIssuedAt = value;
            internalClearRefs(PROP_ID_userCodeIssuedAt);
            
        }
    }
    
    /**
     * UserCode过期时间: USER_CODE_EXPIRES_AT
     */
    public final java.sql.Timestamp getUserCodeExpiresAt(){
         onPropGet(PROP_ID_userCodeExpiresAt);
         return _userCodeExpiresAt;
    }

    /**
     * UserCode过期时间: USER_CODE_EXPIRES_AT
     */
    public final void setUserCodeExpiresAt(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_userCodeExpiresAt,value)){
            this._userCodeExpiresAt = value;
            internalClearRefs(PROP_ID_userCodeExpiresAt);
            
        }
    }
    
    /**
     * UserCode元数据: USER_CODE_METADATA
     */
    public final java.lang.String getUserCodeMetadata(){
         onPropGet(PROP_ID_userCodeMetadata);
         return _userCodeMetadata;
    }

    /**
     * UserCode元数据: USER_CODE_METADATA
     */
    public final void setUserCodeMetadata(java.lang.String value){
        if(onPropSet(PROP_ID_userCodeMetadata,value)){
            this._userCodeMetadata = value;
            internalClearRefs(PROP_ID_userCodeMetadata);
            
        }
    }
    
    /**
     * DeviceCode值: DEVICE_CODE_VALUE
     */
    public final java.lang.String getDeviceCodeValue(){
         onPropGet(PROP_ID_deviceCodeValue);
         return _deviceCodeValue;
    }

    /**
     * DeviceCode值: DEVICE_CODE_VALUE
     */
    public final void setDeviceCodeValue(java.lang.String value){
        if(onPropSet(PROP_ID_deviceCodeValue,value)){
            this._deviceCodeValue = value;
            internalClearRefs(PROP_ID_deviceCodeValue);
            
        }
    }
    
    /**
     * DeviceCode发放时间: DEVICE_CODE_ISSUED_AT
     */
    public final java.sql.Timestamp getDeviceCodeIssuedAt(){
         onPropGet(PROP_ID_deviceCodeIssuedAt);
         return _deviceCodeIssuedAt;
    }

    /**
     * DeviceCode发放时间: DEVICE_CODE_ISSUED_AT
     */
    public final void setDeviceCodeIssuedAt(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_deviceCodeIssuedAt,value)){
            this._deviceCodeIssuedAt = value;
            internalClearRefs(PROP_ID_deviceCodeIssuedAt);
            
        }
    }
    
    /**
     * DeviceCode过期时间: DEVICE_CODE_EXPIRES_AT
     */
    public final java.sql.Timestamp getDeviceCodeExpiresAt(){
         onPropGet(PROP_ID_deviceCodeExpiresAt);
         return _deviceCodeExpiresAt;
    }

    /**
     * DeviceCode过期时间: DEVICE_CODE_EXPIRES_AT
     */
    public final void setDeviceCodeExpiresAt(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_deviceCodeExpiresAt,value)){
            this._deviceCodeExpiresAt = value;
            internalClearRefs(PROP_ID_deviceCodeExpiresAt);
            
        }
    }
    
    /**
     * DeviceCode元数据: DEVICE_CODE_METADATA
     */
    public final java.lang.String getDeviceCodeMetadata(){
         onPropGet(PROP_ID_deviceCodeMetadata);
         return _deviceCodeMetadata;
    }

    /**
     * DeviceCode元数据: DEVICE_CODE_METADATA
     */
    public final void setDeviceCodeMetadata(java.lang.String value){
        if(onPropSet(PROP_ID_deviceCodeMetadata,value)){
            this._deviceCodeMetadata = value;
            internalClearRefs(PROP_ID_deviceCodeMetadata);
            
        }
    }
    
}
// resume CPD analysis - CPD-ON
