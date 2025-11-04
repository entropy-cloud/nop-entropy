/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.i18n;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.util.IComponentModel;
import io.nop.api.core.util.SourceLocation;

import java.util.Map;

@DataBean
public class I18nMessage implements IComponentModel {
    //static final Logger LOG = LoggerFactory.getLogger(I18nMessage.class);

    private final SourceLocation location;
    private final Map<String, String> messages;

    public I18nMessage(SourceLocation location, Map<String, String> messages) {
        this.location = location;
        this.messages = messages;
    }

    @Override
    public SourceLocation getLocation() {
        return location;
    }

    public Map<String, String> getMessages() {
        return messages;
    }

    public void update(Map<String, String> messages) {
        if (messages != null) {
            this.messages.putAll(messages);
        }
    }
}