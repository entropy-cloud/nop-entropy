/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xui.model;

import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.reflect.hook.IExtensibleObject;

import java.util.Set;

/**
 * grid的col以及form的cell配置对应的公共接口
 */
public interface IUiDisplayMeta extends IExtensibleObject {
    String getId();

    String getProp();

    String getDomain();

    String getStdDomain();

    String getControl();

    String getEditMode();

    Set<String> getDepends();

    String getWidth();

    String getLabel();

    String getBizObjName();

    String getIdProp();

    String getDisplayProp();

    String getVisibleOn();

    String getDisabledOn();

    String getReadonlyOn();

    String getRequiredOn();

    String getIf();

    String getClassName();

    String getPlaceholder();

    String getHint();

    IEvalAction getGenControl();

    String getMatchRegexp();
}