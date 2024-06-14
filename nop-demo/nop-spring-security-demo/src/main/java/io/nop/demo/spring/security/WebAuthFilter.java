package io.nop.demo.spring.security;

import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.time.CoreMetrics;
import io.nop.auth.core.login.UserContextImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * 需要从OncePerRequestFilter继承，避免Forward的时候重入多次
 */
public class WebAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        IContext context = ContextProvider.getOrCreateContext();
        try {
            onLoginSuccess(context, request);
            filterChain.doFilter(request, response);
        } finally {
            context.close();
        }
    }

    void onLoginSuccess(IContext context, HttpServletRequest request) {
        String userId = "nop";
        String userName = "123";

        // 这里应该按照具体框架要求设置token，这里仅仅是
        SecurityContext secureContext = SecurityContextHolder.getContext();
        secureContext.setAuthentication(new UserIdToken(userId));
        request.setAttribute(RequestAttributeSecurityContextRepository.DEFAULT_REQUEST_ATTR_NAME, secureContext);

        // context上保存了一些最基本的信息，IUserContext是更多的用户相关信息
        context.setUserId(userId);
        context.setUserName(userName);

        // 这里模拟登录成功后设置user上下文
        UserContextImpl userContext = new UserContextImpl();
        userContext.setAccessToken("aaa");
        userContext.setLastAccessTime(CoreMetrics.currentTimeMillis());
        userContext.setUserId(userId);
        userContext.setUserName(userName);
        userContext.setRoles(Set.of("manager", "checker"));

        IUserContext.set(userContext);
    }
}
