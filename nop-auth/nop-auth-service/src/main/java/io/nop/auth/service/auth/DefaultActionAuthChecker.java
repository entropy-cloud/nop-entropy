package io.nop.auth.service.auth;

import io.nop.api.core.auth.IActionAuthChecker;
import io.nop.api.core.auth.ISecurityContext;
import io.nop.auth.service.sitemap.SiteMapProviderImpl;

import javax.inject.Inject;

public class DefaultActionAuthChecker implements IActionAuthChecker {
    private SiteMapProviderImpl siteMapProvider;

    @Inject
    public void setSiteMapProvider(SiteMapProviderImpl provider) {
        siteMapProvider = provider;
    }

    @Override
    public boolean isPermitted(String permission, ISecurityContext context) {
        return siteMapProvider.isPermitted(permission, context);
    }
}