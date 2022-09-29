package io.nop.auth.service;

import io.nop.api.core.annotations.autotest.EnableSnapshot;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.util.FutureHelper;
import io.nop.auth.api.LoginApi;
import io.nop.auth.api.messages.AccessTokenRequest;
import io.nop.auth.api.messages.LoginRequest;
import io.nop.auth.api.messages.LoginResult;
import io.nop.auth.api.messages.LoginUserInfo;
import io.nop.auth.api.messages.LogoutRequest;
import io.nop.auth.api.messages.RefreshTokenRequest;
import io.nop.auth.service.audit.AuditServiceImpl;
import io.nop.autotest.junit.EnableVariants;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.core.type.utils.TypeReference;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.graphql.core.rpc.RpcServiceOnGraphQL;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import javax.inject.Inject;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertTrue;

@NopTestConfig
public class TestLoginApi extends JunitAutoTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    AuditServiceImpl auditService;

    LoginApi buildLoginApi() {
        RpcServiceOnGraphQL service = new RpcServiceOnGraphQL(graphQLEngine, "LoginApi", Collections.emptyList());
        return service.asProxy(LoginApi.class);
    }

    @EnableSnapshot
    @Test
    public void testCreateUser() {
        ApiRequest<?> request = input("request.json5", ApiRequest.class);
        IGraphQLExecutionContext context = graphQLEngine.newRpcContext("NopAuthUser", "save", request);
        Object result = FutureHelper.syncGet(graphQLEngine.executeRpcAsync(context));
        output("response.json5", result);
        assertTrue(FutureHelper.waitUntil(() -> auditService.isAllProcessed(), 1000));
    }

    @EnableSnapshot
    @Test
    public void testLogin() {
        forceStackTrace();
        LoginApi loginApi = buildLoginApi();

        //ApiRequest<LoginRequest> request = request("request.json5", LoginRequest.class);
        ApiRequest<LoginRequest> request = input("request.json5", new TypeReference<ApiRequest<LoginRequest>>(){}.getType());

        ApiResponse<LoginResult> result = loginApi.login(request);

        output("response.json5", result);

        assertTrue(FutureHelper.waitUntil(() -> auditService.isAllProcessed(), 1000));
    }

    @EnableSnapshot
    @Test
    public void testLoginLogout() {
        LoginApi loginApi = buildLoginApi();

        ApiRequest<LoginRequest> request = request("1_request.json5", LoginRequest.class);

        ApiResponse<LoginResult> result = loginApi.login(request);

        output("1_response.json5", result);

        ApiRequest<AccessTokenRequest> userRequest = request("2_userRequest.json5", AccessTokenRequest.class);

        ApiResponse<LoginUserInfo> userResponse = loginApi.getLoginUserInfo(userRequest);
        output("2_userResponse.json5", userResponse);

        ApiRequest<RefreshTokenRequest> refreshTokenRequest = request("3_refreshTokenRequest.json5", RefreshTokenRequest.class);
        ApiResponse<LoginResult> refreshTokenResponse = loginApi.refreshToken(refreshTokenRequest);
        output("3_refreshTokenResponse.json5", refreshTokenResponse);

        ApiRequest<LogoutRequest> logoutRequest = request("4_logoutRequest.json5", LogoutRequest.class);
        ApiResponse<Void> logoutResponse = loginApi.logout(logoutRequest);
        output("4_logoutResponse.json5", logoutResponse);
    }

    @ParameterizedTest
    @EnableVariants
    @EnableSnapshot
    public void testVariants(String variant) {
        output("displayName.json5",testInfo.getDisplayName());
    }

}
