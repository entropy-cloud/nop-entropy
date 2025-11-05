/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz.api;

import io.nop.api.core.auth.ActionAuthMeta;
import io.nop.biz.model.BizMakerCheckerModel;
import io.nop.biz.model.BizTccModel;
import io.nop.core.context.action.IServiceAction;
import io.nop.core.reflect.hook.IExtensibleObject;
import io.nop.core.type.IGenericType;

import java.util.List;

public interface IBizActionModel extends IExtensibleObject {
    String getName();

    String getDisplayName();

    boolean isBizSequential();

    String getExecutor();

    boolean isAsync();

    List<? extends IBizActionArgModel> getArgs();

    IBizActionArgModel getArg(String argName);

    IGenericType getReturnType();

    boolean isReturnMandatory();

    IServiceAction getExecutable();

    ActionAuthMeta getAuth();

    BizTccModel getTcc();

    BizMakerCheckerModel getMakerChecker();
}