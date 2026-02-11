
CREATE TABLE nop_oauth_authorization(
  sid VARCHAR(100) NOT NULL ,
  registered_client_id VARCHAR(100) NOT NULL ,
  principal_name VARCHAR(200) NOT NULL ,
  authorization_grant_type VARCHAR(100) NOT NULL ,
  authorized_scopes VARCHAR(1000)  ,
  attributes TEXT  ,
  state VARCHAR(500)  ,
  authorization_code_value TEXT  ,
  authorization_code_issued_at TIMESTAMP  ,
  authorization_code_expires_at TIMESTAMP  ,
  authorization_code_metadata TEXT  ,
  access_token_value TEXT  ,
  access_token_issued_at TIMESTAMP  ,
  access_token_expires_at TIMESTAMP  ,
  access_token_metadata TEXT  ,
  access_token_type VARCHAR(100)  ,
  access_token_scopes VARCHAR(1000)  ,
  oidc_id_token_value TEXT  ,
  oidc_id_token_issued_at TIMESTAMP  ,
  oidc_id_token_expires_at TIMESTAMP  ,
  oidc_id_token_metadata TEXT  ,
  refresh_token_value TEXT  ,
  refresh_token_issued_at TIMESTAMP  ,
  refresh_token_expires_at TIMESTAMP  ,
  refresh_token_metadata TEXT  ,
  user_code_value TEXT  ,
  user_code_issued_at TIMESTAMP  ,
  user_code_expires_at TIMESTAMP  ,
  user_code_metadata TEXT  ,
  device_code_value TEXT  ,
  device_code_issued_at TIMESTAMP  ,
  device_code_expires_at TIMESTAMP  ,
  device_code_metadata TEXT  ,
  constraint PK_nop_oauth_authorization primary key (sid)
);

CREATE TABLE nop_oauth_authorization_consent(
  registered_client_id VARCHAR(100) NOT NULL ,
  principal_name VARCHAR(200) NOT NULL ,
  authorities VARCHAR(1000) NOT NULL ,
  constraint PK_nop_oauth_authorization_consent primary key (registered_client_id,principal_name)
);

CREATE TABLE nop_oauth_registered_client(
  sid VARCHAR(100) NOT NULL ,
  client_id VARCHAR(100) NOT NULL ,
  client_id_issued_at TIMESTAMP NOT NULL ,
  client_secret VARCHAR(200)  ,
  client_secret_expires_at TIMESTAMP  ,
  client_name VARCHAR(200) NOT NULL ,
  client_authentication_methods VARCHAR(1000) NOT NULL ,
  authorization_grant_types VARCHAR(1000) NOT NULL ,
  redirect_uris VARCHAR(1000)  ,
  post_logout_redirect_uris VARCHAR(1000)  ,
  scopes VARCHAR(1000) NOT NULL ,
  client_settings VARCHAR(2000) NOT NULL ,
  token_settings VARCHAR(2000) NOT NULL ,
  constraint PK_nop_oauth_registered_client primary key (sid)
);


      COMMENT ON TABLE nop_oauth_authorization IS 'Oauth认证记录';
                
      COMMENT ON COLUMN nop_oauth_authorization.sid IS 'Id';
                    
      COMMENT ON COLUMN nop_oauth_authorization.registered_client_id IS '客户端ID';
                    
      COMMENT ON COLUMN nop_oauth_authorization.principal_name IS '客户端名称';
                    
      COMMENT ON COLUMN nop_oauth_authorization.authorization_grant_type IS '授权类型';
                    
      COMMENT ON COLUMN nop_oauth_authorization.authorized_scopes IS '授权范围';
                    
      COMMENT ON COLUMN nop_oauth_authorization.attributes IS '扩展属性';
                    
      COMMENT ON COLUMN nop_oauth_authorization.state IS '状态码';
                    
      COMMENT ON COLUMN nop_oauth_authorization.authorization_code_value IS 'AuthorizationCode值';
                    
      COMMENT ON COLUMN nop_oauth_authorization.authorization_code_issued_at IS 'AuthorizationCode发放时间';
                    
      COMMENT ON COLUMN nop_oauth_authorization.authorization_code_expires_at IS 'AuthorizationCode过期使劲按';
                    
      COMMENT ON COLUMN nop_oauth_authorization.authorization_code_metadata IS 'AuthorizationCode元数据';
                    
      COMMENT ON COLUMN nop_oauth_authorization.access_token_value IS 'AccessToken';
                    
      COMMENT ON COLUMN nop_oauth_authorization.access_token_issued_at IS 'AccessToken发放时间';
                    
      COMMENT ON COLUMN nop_oauth_authorization.access_token_expires_at IS 'AccessToken过期时间';
                    
      COMMENT ON COLUMN nop_oauth_authorization.access_token_metadata IS 'AccessToken元数据';
                    
      COMMENT ON COLUMN nop_oauth_authorization.access_token_type IS 'AccessToken类型';
                    
      COMMENT ON COLUMN nop_oauth_authorization.access_token_scopes IS 'AccessToken权限范围';
                    
      COMMENT ON COLUMN nop_oauth_authorization.oidc_id_token_value IS 'OidcIdToken值';
                    
      COMMENT ON COLUMN nop_oauth_authorization.oidc_id_token_issued_at IS 'OidcIdToken发放时间';
                    
      COMMENT ON COLUMN nop_oauth_authorization.oidc_id_token_expires_at IS 'OidcIdToken过期时间';
                    
      COMMENT ON COLUMN nop_oauth_authorization.oidc_id_token_metadata IS 'OidcIdToken元数据';
                    
      COMMENT ON COLUMN nop_oauth_authorization.refresh_token_value IS 'RefreshToken值';
                    
      COMMENT ON COLUMN nop_oauth_authorization.refresh_token_issued_at IS 'RefreshToken发放时间';
                    
      COMMENT ON COLUMN nop_oauth_authorization.refresh_token_expires_at IS 'RefreshToken过期时间';
                    
      COMMENT ON COLUMN nop_oauth_authorization.refresh_token_metadata IS 'RefreshToken元数据';
                    
      COMMENT ON COLUMN nop_oauth_authorization.user_code_value IS 'UserCode值';
                    
      COMMENT ON COLUMN nop_oauth_authorization.user_code_issued_at IS 'UserCode发放时间';
                    
      COMMENT ON COLUMN nop_oauth_authorization.user_code_expires_at IS 'UserCode过期时间';
                    
      COMMENT ON COLUMN nop_oauth_authorization.user_code_metadata IS 'UserCode元数据';
                    
      COMMENT ON COLUMN nop_oauth_authorization.device_code_value IS 'DeviceCode值';
                    
      COMMENT ON COLUMN nop_oauth_authorization.device_code_issued_at IS 'DeviceCode发放时间';
                    
      COMMENT ON COLUMN nop_oauth_authorization.device_code_expires_at IS 'DeviceCode过期时间';
                    
      COMMENT ON COLUMN nop_oauth_authorization.device_code_metadata IS 'DeviceCode元数据';
                    
      COMMENT ON TABLE nop_oauth_authorization_consent IS 'Oauth许可';
                
      COMMENT ON COLUMN nop_oauth_authorization_consent.registered_client_id IS '注册客户端ID';
                    
      COMMENT ON COLUMN nop_oauth_authorization_consent.principal_name IS '客户端名称';
                    
      COMMENT ON COLUMN nop_oauth_authorization_consent.authorities IS '扩展属性';
                    
      COMMENT ON TABLE nop_oauth_registered_client IS 'Oauth注册客户端';
                
      COMMENT ON COLUMN nop_oauth_registered_client.sid IS 'Id';
                    
      COMMENT ON COLUMN nop_oauth_registered_client.client_id IS '客户端ID';
                    
      COMMENT ON COLUMN nop_oauth_registered_client.client_id_issued_at IS '客户端ID发放时间';
                    
      COMMENT ON COLUMN nop_oauth_registered_client.client_secret IS '客户端密码';
                    
      COMMENT ON COLUMN nop_oauth_registered_client.client_secret_expires_at IS '客户端密码过期时间';
                    
      COMMENT ON COLUMN nop_oauth_registered_client.client_name IS '客户端名称';
                    
      COMMENT ON COLUMN nop_oauth_registered_client.client_authentication_methods IS '客户端授权方法';
                    
      COMMENT ON COLUMN nop_oauth_registered_client.authorization_grant_types IS '客户端认证类型';
                    
      COMMENT ON COLUMN nop_oauth_registered_client.redirect_uris IS '重定向URI';
                    
      COMMENT ON COLUMN nop_oauth_registered_client.post_logout_redirect_uris IS 'Logout重定向URI';
                    
      COMMENT ON COLUMN nop_oauth_registered_client.scopes IS '授权范围';
                    
      COMMENT ON COLUMN nop_oauth_registered_client.client_settings IS '客户端设置';
                    
      COMMENT ON COLUMN nop_oauth_registered_client.token_settings IS 'Token设置';
                    
