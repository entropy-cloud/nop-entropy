/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.api.actor;


import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.util.ApiStringHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class WfActorHelper {


    public static final String PROP_ID = "id";
    public static final String PROP_TYPE = "type";
    public static final String PROP_DEPT_ID = "deptId";

    public static List<IWfActor> resolveActorsFromJson(IWfActorResolver actorResolver, List<Map<String, Object>> json) {
        if (json == null || json.isEmpty())
            return Collections.emptyList();

        List<IWfActor> ret = new ArrayList<>(json.size());
        for (Map<String, Object> actorInfo : json) {
            IWfActor actor = resolveActorFromJson(actorResolver, actorInfo);
            if (actor != null)
                ret.add(actor);
        }
        return ret;
    }

    public static IWfActor resolveActorFromJson(IWfActorResolver actorResolver, Map<String, Object> actorInfo) {
        if (actorInfo == null)
            return null;

        String id = ConvertHelper.toString(actorInfo.get(PROP_ID), (String) null);
        if (ApiStringHelper.isEmpty(id))
            return null;

        String type = (String) actorInfo.get(PROP_TYPE);
        if (type == null)
            type = IWfActor.ACTOR_TYPE_USER;

        String deptId = ConvertHelper.toString(actorInfo.get(PROP_DEPT_ID), (String) null);

        IWfActor actor = actorResolver.resolveActor(type, id, deptId);
        return actor;
    }
}