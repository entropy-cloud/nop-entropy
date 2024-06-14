package io.nop.demo.spring.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;

import java.io.Serializable;

public class SpringPermissionEvaluator implements PermissionEvaluator {
    static final Logger LOG = LoggerFactory.getLogger(SpringPermissionEvaluator.class);

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        LOG.info("nop.check-permission:{}", permission);
        return true;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object permission) {
        // 仅起示例作用
        LOG.info("nop.check-permission:target={},{}", targetId, permission);
        return true;
    }
}
