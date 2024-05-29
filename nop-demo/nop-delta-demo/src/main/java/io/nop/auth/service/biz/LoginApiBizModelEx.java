package io.nop.auth.service.biz;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.RequestBean;
import io.nop.api.core.annotations.directive.Auth;
import io.nop.auth.api.messages.LoginResult;
import io.nop.core.context.IServiceContext;
import io.nop.demo.biz.LoginRequestEx;
import jakarta.annotation.Priority;

import java.util.concurrent.CompletionStage;

import static io.nop.api.core.util.IOrdered.NORMAL_PRIORITY;

@BizModel("LoginApi")
public class LoginApiBizModelEx extends LoginApiBizModel {

    /**
     * 因为修改了函数参数类型，导致loginAsync不会覆盖基类的实现。此时可以通过 {@code @Priority}来设置函数优先级，从而覆盖基类中的定义
     *
     * @param request 扩展后的输入对象
     * @param context GraphQL执行上下文
     */
    @BizMutation("login")
    @Auth(publicAccess = true)
    @Priority(NORMAL_PRIORITY - 100)
    public CompletionStage<LoginResult> loginAsync(@RequestBean LoginRequestEx request, IServiceContext context) {
        return loginService.loginAsync(request, context.getRequestHeaders()).thenApply(this::buildLoginResult);
    }
}
