/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.config.source;

public interface IDynamicTextConfigLoader {

    /**
     * 获取配置信息，并注册监听器。当配置发生变化时会触发监听器
     */
    ConfigInfo getConfigInfo(IConfigSource currentConfig, String dataId, IConfigUpdateListener listener);

    class ConfigInfo {
        private final String content;

        private final Runnable unsubscribe;

        public ConfigInfo(String content, Runnable unsubscribe) {
            this.content = content;
            this.unsubscribe = unsubscribe;
        }

        public String getContent() {
            return content;
        }

        /**
         * 取消listener监听
         */
        public Runnable getUnsubscribe() {
            return unsubscribe;
        }
    }

    interface IConfigUpdateListener {
        void onUpdateConfig(String configInfo);
    }
}
