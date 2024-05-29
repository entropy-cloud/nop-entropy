package io.nop.demo.biz;

import io.nop.api.core.annotations.biz.BizLoader;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.ContextSource;
import io.nop.api.core.annotations.biz.RequestBean;
import io.nop.api.core.annotations.core.LazyLoad;
import io.nop.api.core.annotations.directive.Auth;
import io.nop.auth.api.messages.LoginResult;
import io.nop.auth.service.biz.LoginApiBizModel;
import io.nop.core.context.IServiceContext;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;

import java.util.concurrent.CompletionStage;

import static io.nop.api.core.util.IOrdered.NORMAL_PRIORITY;

@BizModel("LoginApi")
public class LoginApiBizModelDelta {
    @Inject
    LoginApiBizModel loginApiBizModel;

    /**
     * 通过{@link BizLoader}注解未LoginResult引入扩展字段。这里设置了autoCreateField=true，表示如果LoginResult对象上没有location字段，
     * 就自动创建一个。缺省情况下autoCreateField=false，只会为已有的字段补充loader逻辑，并不会创建字段本身。
     * <p>
     * 通过{@link LazyLoad}注解来表示本字段为延迟加载字段，除非前台明确请求返回该字段，否则REST请求时会忽略此字段
     *
     * @param result
     * @param context GraphQL执行的上下文对象，可以用于在多个函数之间共享变量。也可以通过它读写http的header信息
     * @return location字段的值
     */
    @BizLoader(autoCreateField = true, forType = LoginResult.class)
    @LazyLoad
    public String location(@ContextSource LoginResult result, IServiceContext context) {
        return "loc:" + result.getUserInfo().getUserId();
    }

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
        request.setAttr("a", "123");
        return loginApiBizModel.loginAsync(request, context);
    }

}
