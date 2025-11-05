
CREATE TABLE nop_oauth_authorization(
  SID VARCHAR2(100) NOT NULL ,
  REGISTERED_CLIENT_ID VARCHAR2(100) NOT NULL ,
  PRINCIPAL_NAME VARCHAR2(200) NOT NULL ,
  AUTHORIZATION_GRANT_TYPE VARCHAR2(100) NOT NULL ,
  AUTHORIZED_SCOPES VARCHAR2(1000)  ,
  ATTRIBUTES CLOB  ,
  STATE VARCHAR2(500)  ,
  AUTHORIZATION_CODE_VALUE CLOB  ,
  AUTHORIZATION_CODE_ISSUED_AT TIMESTAMP  ,
  AUTHORIZATION_CODE_EXPIRES_AT TIMESTAMP  ,
  AUTHORIZATION_CODE_METADATA CLOB  ,
  ACCESS_TOKEN_VALUE CLOB  ,
  ACCESS_TOKEN_ISSUED_AT TIMESTAMP  ,
  ACCESS_TOKEN_EXPIRES_AT TIMESTAMP  ,
  ACCESS_TOKEN_METADATA CLOB  ,
  ACCESS_TOKEN_TYPE VARCHAR2(100)  ,
  ACCESS_TOKEN_SCOPES VARCHAR2(1000)  ,
  OIDC_ID_TOKEN_VALUE CLOB  ,
  OIDC_ID_TOKEN_ISSUED_AT TIMESTAMP  ,
  OIDC_ID_TOKEN_EXPIRES_AT TIMESTAMP  ,
  OIDC_ID_TOKEN_METADATA CLOB  ,
  REFRESH_TOKEN_VALUE CLOB  ,
  REFRESH_TOKEN_ISSUED_AT TIMESTAMP  ,
  REFRESH_TOKEN_EXPIRES_AT TIMESTAMP  ,
  REFRESH_TOKEN_METADATA CLOB  ,
  USER_CODE_VALUE CLOB  ,
  USER_CODE_ISSUED_AT TIMESTAMP  ,
  USER_CODE_EXPIRES_AT TIMESTAMP  ,
  USER_CODE_METADATA CLOB  ,
  DEVICE_CODE_VALUE CLOB  ,
  DEVICE_CODE_ISSUED_AT TIMESTAMP  ,
  DEVICE_CODE_EXPIRES_AT TIMESTAMP  ,
  DEVICE_CODE_METADATA CLOB  ,
  constraint PK_nop_oauth_authorization primary key (SID)
);

CREATE TABLE nop_oauth_authorization_consent(
  REGISTERED_CLIENT_ID VARCHAR2(100) NOT NULL ,
  PRINCIPAL_NAME VARCHAR2(200) NOT NULL ,
  AUTHORITIES VARCHAR2(1000) NOT NULL ,
  constraint PK_nop_oauth_authorization_consent primary key (REGISTERED_CLIENT_ID,PRINCIPAL_NAME)
);

CREATE TABLE nop_oauth_registered_client(
  SID VARCHAR2(100) NOT NULL ,
  CLIENT_ID VARCHAR2(100) NOT NULL ,
  CLIENT_ID_ISSUED_AT TIMESTAMP NOT NULL ,
  CLIENT_SECRET VARCHAR2(200)  ,
  CLIENT_SECRET_EXPIRES_AT TIMESTAMP  ,
  CLIENT_NAME VARCHAR2(200) NOT NULL ,
  CLIENT_AUTHENTICATION_METHODS VARCHAR2(1000) NOT NULL ,
  AUTHORIZATION_GRANT_TYPES VARCHAR2(1000) NOT NULL ,
  REDIRECT_URIS VARCHAR2(1000)  ,
  POST_LOGOUT_REDIRECT_URIS VARCHAR2(1000)  ,
  SCOPES VARCHAR2(1000) NOT NULL ,
  CLIENT_SETTINGS VARCHAR2(2000) NOT NULL ,
  TOKEN_SETTINGS VARCHAR2(2000) NOT NULL ,
  constraint PK_nop_oauth_registered_client primary key (SID)
);


      COMMENT ON TABLE nop_oauth_authorization IS 'Oauth认证记录';
                
      COMMENT ON COLUMN nop_oauth_authorization.SID IS 'Id';
                    
      COMMENT ON COLUMN nop_oauth_authorization.REGISTERED_CLIENT_ID IS '客户端ID';
                    
      COMMENT ON COLUMN nop_oauth_authorization.PRINCIPAL_NAME IS '客户端名称';
                    
      COMMENT ON COLUMN nop_oauth_authorization.AUTHORIZATION_GRANT_TYPE IS '授权类型';
                    
      COMMENT ON COLUMN nop_oauth_authorization.AUTHORIZED_SCOPES IS '授权范围';
                    
      COMMENT ON COLUMN nop_oauth_authorization.ATTRIBUTES IS '扩展属性';
                    
      COMMENT ON COLUMN nop_oauth_authorization.STATE IS '状态码';
                    
      COMMENT ON COLUMN nop_oauth_authorization.AUTHORIZATION_CODE_VALUE IS 'AuthorizationCode值';
                    
      COMMENT ON COLUMN nop_oauth_authorization.AUTHORIZATION_CODE_ISSUED_AT IS 'AuthorizationCode发放时间';
                    
      COMMENT ON COLUMN nop_oauth_authorization.AUTHORIZATION_CODE_EXPIRES_AT IS 'AuthorizationCode过期使劲按';
                    
      COMMENT ON COLUMN nop_oauth_authorization.AUTHORIZATION_CODE_METADATA IS 'AuthorizationCode元数据';
                    
      COMMENT ON COLUMN nop_oauth_authorization.ACCESS_TOKEN_VALUE IS 'AccessToken';
                    
      COMMENT ON COLUMN nop_oauth_authorization.ACCESS_TOKEN_ISSUED_AT IS 'AccessToken发放时间';
                    
      COMMENT ON COLUMN nop_oauth_authorization.ACCESS_TOKEN_EXPIRES_AT IS 'AccessToken过期时间';
                    
      COMMENT ON COLUMN nop_oauth_authorization.ACCESS_TOKEN_METADATA IS 'AccessToken元数据';
                    
      COMMENT ON COLUMN nop_oauth_authorization.ACCESS_TOKEN_TYPE IS 'AccessToken类型';
                    
      COMMENT ON COLUMN nop_oauth_authorization.ACCESS_TOKEN_SCOPES IS 'AccessToken权限范围';
                    
      COMMENT ON COLUMN nop_oauth_authorization.OIDC_ID_TOKEN_VALUE IS 'OidcIdToken值';
                    
      COMMENT ON COLUMN nop_oauth_authorization.OIDC_ID_TOKEN_ISSUED_AT IS 'OidcIdToken发放时间';
                    
      COMMENT ON COLUMN nop_oauth_authorization.OIDC_ID_TOKEN_EXPIRES_AT IS 'OidcIdToken过期时间';
                    
      COMMENT ON COLUMN nop_oauth_authorization.OIDC_ID_TOKEN_METADATA IS 'OidcIdToken元数据';
                    
      COMMENT ON COLUMN nop_oauth_authorization.REFRESH_TOKEN_VALUE IS 'RefreshToken值';
                    
      COMMENT ON COLUMN nop_oauth_authorization.REFRESH_TOKEN_ISSUED_AT IS 'RefreshToken发放时间';
                    
      COMMENT ON COLUMN nop_oauth_authorization.REFRESH_TOKEN_EXPIRES_AT IS 'RefreshToken过期时间';
                    
      COMMENT ON COLUMN nop_oauth_authorization.REFRESH_TOKEN_METADATA IS 'RefreshToken元数据';
                    
      COMMENT ON COLUMN nop_oauth_authorization.USER_CODE_VALUE IS 'UserCode值';
                    
      COMMENT ON COLUMN nop_oauth_authorization.USER_CODE_ISSUED_AT IS 'UserCode发放时间';
                    
      COMMENT ON COLUMN nop_oauth_authorization.USER_CODE_EXPIRES_AT IS 'UserCode过期时间';
                    
      COMMENT ON COLUMN nop_oauth_authorization.USER_CODE_METADATA IS 'UserCode元数据';
                    
      COMMENT ON COLUMN nop_oauth_authorization.DEVICE_CODE_VALUE IS 'DeviceCode值';
                    
      COMMENT ON COLUMN nop_oauth_authorization.DEVICE_CODE_ISSUED_AT IS 'DeviceCode发放时间';
                    
      COMMENT ON COLUMN nop_oauth_authorization.DEVICE_CODE_EXPIRES_AT IS 'DeviceCode过期时间';
                    
      COMMENT ON COLUMN nop_oauth_authorization.DEVICE_CODE_METADATA IS 'DeviceCode元数据';
                    
      COMMENT ON TABLE nop_oauth_authorization_consent IS 'Oauth许可';
                
      COMMENT ON COLUMN nop_oauth_authorization_consent.REGISTERED_CLIENT_ID IS '注册客户端ID';
                    
      COMMENT ON COLUMN nop_oauth_authorization_consent.PRINCIPAL_NAME IS '客户端名称';
                    
      COMMENT ON COLUMN nop_oauth_authorization_consent.AUTHORITIES IS '扩展属性';
                    
      COMMENT ON TABLE nop_oauth_registered_client IS 'Oauth注册客户端';
                
      COMMENT ON COLUMN nop_oauth_registered_client.SID IS 'Id';
                    
      COMMENT ON COLUMN nop_oauth_registered_client.CLIENT_ID IS '客户端ID';
                    
      COMMENT ON COLUMN nop_oauth_registered_client.CLIENT_ID_ISSUED_AT IS '客户端ID发放时间';
                    
      COMMENT ON COLUMN nop_oauth_registered_client.CLIENT_SECRET IS '客户端密码';
                    
      COMMENT ON COLUMN nop_oauth_registered_client.CLIENT_SECRET_EXPIRES_AT IS '客户端密码过期时间';
                    
      COMMENT ON COLUMN nop_oauth_registered_client.CLIENT_NAME IS '客户端名称';
                    
      COMMENT ON COLUMN nop_oauth_registered_client.CLIENT_AUTHENTICATION_METHODS IS '客户端授权方法';
                    
      COMMENT ON COLUMN nop_oauth_registered_client.AUTHORIZATION_GRANT_TYPES IS '客户端认证类型';
                    
      COMMENT ON COLUMN nop_oauth_registered_client.REDIRECT_URIS IS '重定向URI';
                    
      COMMENT ON COLUMN nop_oauth_registered_client.POST_LOGOUT_REDIRECT_URIS IS 'Logout重定向URI';
                    
      COMMENT ON COLUMN nop_oauth_registered_client.SCOPES IS '授权范围';
                    
      COMMENT ON COLUMN nop_oauth_registered_client.CLIENT_SETTINGS IS '客户端设置';
                    
      COMMENT ON COLUMN nop_oauth_registered_client.TOKEN_SETTINGS IS 'Token设置';
                    
