/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc;

import io.nop.api.core.ApiConstants;

public interface IocConstants {
    String XDEF_BEANS = "/nop/schema/beans.xdef";

    String SCOPE_PROTOTYPE = "prototype";

    String SCOPE_SINGLETON = "singleton";

    String PREFIX_INJECT_REF = "@inject-ref:";

    String PREFIX_INJECT_TYPE = "@inject-type:";

    String PREFIX_BEAN = "@bean:";

    String PREFIX_CFG = "@cfg:";

    String PREFIX_R_CFG = "@r-cfg:";

    String CONFIG_BEAN_ID = ApiConstants.CONFIG_BEAN_ID;
    String CONFIG_BEAN_CONTAINER = ApiConstants.CONFIG_BEAN_CONTAINER;
    String CONFIG_BEAN_TYPE = ApiConstants.CONFIG_BEAN_TYPE;

    String GEN_ID_PREFIX = "$GEN$";

    String DEFAULT_ID_PREFIX = "$DEFAULT$";

    String PRODUCER_BEAN_PREFIX = "&";

    String SYS_VAR_BEAN = "bean";
    String SYS_VAR_BEAN_DEF = "beanDef";
    String SYS_VAR_BEAN_CONTAINER = "beanContainer";

    String FILE_POSTFIX_BEANS = ".beans";

    String VFS_PATH_AUTOCONFIG = "/nop/autoconfig";

    int DEFAULT_INIT_ORDER = 100;

    String BEAN_TAG_PROXY = "proxy";
}