/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.service.entity;

import io.nop.api.core.annotations.biz.BizLoader;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.ContextSource;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.graphql.GraphQLReturn;
import io.nop.auth.dao.entity.NopAuthRole;
import io.nop.auth.dao.entity.NopAuthRoleResource;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.auth.dao.entity.NopAuthUserRole;
import io.nop.auth.service.NopAuthConstants;
import io.nop.biz.crud.CrudBizModel;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.utils.Underscore;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;

@BizModel("NopAuthRole")
public class NopAuthRoleBizModel extends CrudBizModel<NopAuthRole> {
    public NopAuthRoleBizModel() {
        setEntityName(NopAuthRole.class.getName());
    }

    @BizLoader
    @GraphQLReturn(bizObjName = "NopAuthUser")
    public List<NopAuthUser> roleUsers(@ContextSource NopAuthRole role) {
        return role.getUserMappings().stream().map(NopAuthUserRole::getUser)
                .sorted(comparing(NopAuthUser::getUserName)).collect(Collectors.toList());
    }

    @BizMutation
    public void removeRoleUsers(@Name("roleId") String roleId,
                                @Name("userIds") Collection<String> userIds) {
        removeRelations(NopAuthUserRole.class,
                "roleId", "userId",
                roleId, userIds);
    }

    @BizMutation
    public void addRoleUsers(@Name("roleId") String roleId, @Name("userIds") Collection<String> userIds) {
        addRelations(NopAuthUserRole.class, "roleId", "userId",
                roleId, userIds);
    }

    @BizLoader
    public List<String> roleResourceIds(@ContextSource NopAuthRole role,
                                        IServiceContext context) {
        return (List) Underscore.pluck(role.getResourceMappings(), "resourceId");
    }

    @BizMutation
    public void updateRoleResources(@Name("roleId") String roleId,
                                    @Name("siteId") String siteId,
                                    @Name("resourceIds") Collection<String> resourceIds) {
        if (StringHelper.isEmpty(siteId))
            siteId = NopAuthConstants.SITE_ID_MAIN;

        Map<String, Object> fixedProps = new HashMap<>();
        fixedProps.put("roleId", roleId);

        String fixedSiteId = siteId;
        super.updateRelations(NopAuthRoleResource.class, fixedProps,
                relation -> relation.getResource().getSiteId().equals(fixedSiteId),
                true, "resourceId", resourceIds);
    }
}