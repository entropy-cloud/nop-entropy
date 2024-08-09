/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons;

public interface CommonConstants {
    String ENCODING_UTF8 = "UTF-8";

    String BEAN_DEFAULT_SCHEDULED_EXECUTOR = "defaultScheduledExecutor";

    String BEAN_DEFAULT_TASK_EXECUTOR = "defaultTaskExecutor";

    String BEAN_DEFAULT_TIMER = "defaultTimer";

    long DEFAULT_THREAD_RENEWAL_DELAY = 1000L;

    /**
     * 避免出现名为class的属性，它对应的getClass()方法与Java类上自带的getClass()方法冲突
     */
    String PROP_CLASS_NAME = "className";

    String PROP_CLASS = "class";
    String PROP_ID = "id";
    String PROP_ID_ = "id_";

    String ENC_VALUE_PREFIX = "@enc:";

    // 加密密钥等
    String SEC_VALUE_PREFIX = "@sec:";

    String I18N_PREFIX = "@i18n:";


    String BASE_64_PREFIX = "@base64:";
    String HEX_PREFIX = "@hex:";
    String UTF8_PREFIX = "@utf8:";


    String PREFIX_INTERNAL_TAG = "~";
}