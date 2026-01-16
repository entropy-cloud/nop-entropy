/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.config;

import io.nop.commons.CommonConstants;
import io.nop.core.CoreConstants;

public interface ConfigConstants {
    String FILE_POSTFIX_PROPERTIES = ".properties";

    String FILE_POSTFIX_YAML = ".yaml";
    String FILE_POSTFIX_YML = ".yml";

    //String FILE_POSTFIX_JSON = ".json";

    String CFG_PRODUCT_NAME = "nop.product.name";

    String CFG_APPLICATION_NAME = "nop.application.name";

    String CFG_CONFIG_TRACE = "nop.config.trace";

    String CFG_CONFIG_ENABLED = "nop.config.enabled";

    /**
     * 配置灰度发布对应的配置项。不同版本的应用程序从配置中心获取配置后可以根据版本号等过滤得到专用于本版本的配置
     */
    String CFG_CONFIG_APP_ROUTER = "nop.config.app.router";

    /**
     * 产品级别的配置路由
     */
    String CFG_CONFIG_PRODUCT_ROUTER = "nop.config.product.router";

    /**
     * 是否启用配置中心服务
     */
    String CFG_CONFIG_SERVICE_ENABLED = CoreConstants.CFG_CONFIG_SERVICE_ENABLED;

    /**
     * 指定配置中心服务的名称。当系统中存在多个配置中心服务时，这个参数用于指定使用某个特定实现
     */
    String CFG_CONFIG_SERVICE_IMPL = "nop.config.service.impl";

    String CFG_CONFIG_EXECUTOR_IMPL = "nop.config.executor.impl";

    /**
     * 指定配置中心服务启动时所需附加参数文件所在路径。缺省为{@link ConfigConstants#CFG_PATH_CONFIG_SERVICE_PROPERTIES}
     */
    String CFG_CONFIG_SERVICE_PROPERTIES_LOCATION = "nop.config.service.properties-location";

    /**
     * 指定系统初始化所需的最基本的启动参数文件。这是整个系统读取的第一个参数文件。缺省为{@link ConfigConstants#CFG_PATH_BOOTSTRAP_YAML}
     */
    String CFG_CONFIG_BOOTSTRAP_LOCATION = "nop.config.bootstrap-location";

    /**
     * 指定应用级别附加的配置文件，其中的配置项会覆盖nop.config.location中的配置项。
     */
    String CFG_CONFIG_ADDITIONAL_LOCATION = "nop.config.additional-location";

    /**
     * 指定应用级别配置文件，缺省为{@link ConfigConstants#CFG_PATH_APPLICATION_YAML}
     */
    String CFG_CONFIG_LOCATION = "nop.config.location";

    String CFG_CONFIG_VALUE_ENHANCER_CLASS_NAME = "nop.config.value-enhancer-class-name";

    String CFG_CONFIG_ENCRYPT_KEY = "nop.config.encrypt-key";

    String CFG_CONFIG_ENCRYPT_SALT_KEY = "nop.config.encrypt-salt-key";

    String CFG_CONFIG_ENCRYPT_CONCAT_IV = "nop.config.encrypt-concat-iv";

    String CFG_KEY_FILE_CONFIG_SOURCE_PATHS = "nop.config.key-config-source.paths";

    String CFG_PROPS_FILE_CONFIG_SOURCE_PATHS = "nop.config.props-config-source.paths";

    String CFG_KEY_FILE_CONFIG_SOURCE_REFRESH_INTERVAL = "nop.config.key-config-source.refresh-interval";

    String CFG_PROPS_FILE_CONFIG_SOURCE_REFRESH_INTERVAL = "nop.config.key-config-source.refresh-interval";

    String CFG_PATH_BOOTSTRAP_YAML = "classpath:bootstrap.yaml";

    String CFG_PATH_CONFIG_SERVICE_PROPERTIES = "classpath:_conf/config-service.properties";

    long DEFAULT_CONFIG_REFRESH_INTERVAL = 15000;

    String CFG_PATH_APPLICATION_YAML = "classpath:application.yaml";

    String CFG_PATH_APPLICATION_YML = "classpath:application.yml";

    String CFG_NOP_CONFIG_YAML = "classpath:nop.config.yaml";


    String CFG_PATH_PREFIX = "classpath:_conf/";

    String CFG_SWITCH_PREFIX = "@switch:";

    String CFG_SEC_PREFIX = CommonConstants.SEC_VALUE_PREFIX;
}
