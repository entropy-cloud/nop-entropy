/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.sso;

public interface SsoConstants {

    String WELL_KNOWN_CONFIGURATION = "/.well-known/openid-configuration";

    String CLIENT_ASSERTION = "client_assertion";
    String CLIENT_ASSERTION_TYPE = "client_assertion_type";
    String JWT_BEARER_CLIENT_ASSERTION_TYPE = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";

    String CLIENT_CREDENTIALS_GRANT = "client_credentials";
    String PASSWORD_GRANT = "password"; //NOSONAR
    String REFRESH_TOKEN_GRANT = "refresh_token";
    String REFRESH_TOKEN_VALUE = "refresh_token";

    String ACCESS_TOKEN_VALUE = "access_token";
    String ID_TOKEN_VALUE = "id_token";

    String LOGOUT_ID_TOKEN_HINT = "id_token_hint";
    String LOGOUT_STATE = "state";
    String POST_LOGOUT_REDIRECT_URI = "post_logout_redirect_uri";

    String INTROSPECTION_TOKEN_TYPE_HINT = "token_type_hint";
    String INTROSPECTION_TOKEN = "token";
    String INTROSPECTION_TOKEN_ACTIVE = "active";
    String INTROSPECTION_TOKEN_EXP = "exp";
    String INTROSPECTION_TOKEN_USERNAME = "username";
    String INTROSPECTION_TOKEN_SUB = "sub";

    String PASSWORD_GRANT_USERNAME = "username";  //NOSONAR
    String PASSWORD_GRANT_PASSWORD = "password";  //NOSONAR

    String TOKEN_SCOPE = "scope";
    String GRANT_TYPE = "grant_type";

    String CLIENT_ID = "client_id";
    String CLIENT_SECRET = "client_secret";

    String BEARER_SCHEME = "Bearer";
    String BASIC_SCHEME = "Basic";

    String AUTHORIZATION_CODE = "authorization_code";
    String CODE_FLOW_RESPONSE_TYPE = "response_type";
    String CODE_FLOW_RESPONSE_MODE = "response_mode";
    String CODE_FLOW_CODE = "code";
    String CODE_FLOW_ERROR = "error";
    String CODE_FLOW_ERROR_DESCRIPTION = "error_description";
    String CODE_FLOW_STATE = "state";
    String CODE_FLOW_REDIRECT_URI = "redirect_uri";

    String EXCHANGE_GRANT = "urn:ietf:params:oauth:grant-type:token-exchange";

    String EXPIRES_IN = "expires_in";
    String REFRESH_EXPIRES_IN = "refresh_expires_in";

    String PKCE_CODE_VERIFIER = "code_verifier";
    String PKCE_CODE_CHALLENGE = "code_challenge";

    String PKCE_CODE_CHALLENGE_METHOD = "code_challenge_method";
    String PKCE_CODE_CHALLENGE_S256 = "S256";

    String BACK_CHANNEL_LOGOUT_TOKEN = "logout_token";
    String BACK_CHANNEL_EVENTS_CLAIM = "events";
    String BACK_CHANNEL_EVENT_NAME = "http://schemas.openid.net/event/backchannel-logout";
    String BACK_CHANNEL_LOGOUT_SID_CLAIM = "sid";

    String PLACE_HOLDER_REALM = "{realm}";
}
