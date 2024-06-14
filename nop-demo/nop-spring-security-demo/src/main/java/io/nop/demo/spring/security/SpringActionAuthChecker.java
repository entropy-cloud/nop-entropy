package io.nop.demo.spring.security;

import io.nop.api.core.auth.IActionAuthChecker;
import io.nop.api.core.auth.ISecurityContext;
import io.nop.api.core.auth.IUserContext;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.PermissionEvaluator;

/**
 * 这是Nop平台内部使用的操作权限校验接口。这里将它代理到SpringSecurity框架的PermissionEvaluator接口上。
 * <p>
 * 因为Nop平台不使用类扫描机制，因此这个Bean需要在app-demo.beans.xml中注册。
 */
public class SpringActionAuthChecker implements IActionAuthChecker {
    static final Logger LOG = LoggerFactory.getLogger(SpringActionAuthChecker.class);

    @Inject
    PermissionEvaluator permissionEvaluator;

    @Override
    public boolean isPermitted(String permission, ISecurityContext context) {
        LOG.info("nop.check-action-auth:permission={},user={}", permission, context.getUserContext());

        if (context.getUserContext() == null)
            return false;
        IUserContext userContext = context.getUserContext();

        // 这里如何构造token需要根据具体授权框架的要求来
        UserIdToken token = new UserIdToken(userContext.getUserId());
        return permissionEvaluator.hasPermission(token, "Biz", permission);
    }
}
