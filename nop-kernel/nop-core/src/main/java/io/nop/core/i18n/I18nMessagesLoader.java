/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.i18n;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.CoreConstants;
import io.nop.core.lang.json.DeltaJsonOptions;
import io.nop.core.lang.json.JObject;
import io.nop.core.lang.json.JsonSaveOptions;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static io.nop.core.CoreErrors.ARG_KEY;
import static io.nop.core.CoreErrors.ARG_LOC_A;
import static io.nop.core.CoreErrors.ARG_LOC_B;
import static io.nop.core.CoreErrors.ARG_VALUE_A;
import static io.nop.core.CoreErrors.ARG_VALUE_B;
import static io.nop.core.CoreErrors.ERR_I18N_DUPLICATED_MESSAGE_KEY;

public class I18nMessagesLoader {
    static final Logger LOG = LoggerFactory.getLogger(I18nMessagesLoader.class);

    public I18nMessage loadI18nMessages(String locale) {
        Map<String, ValueWithLocation> merged = new HashMap<>();

        List<? extends IResource> files = VirtualFileSystem.instance().getChildren("/i18n/" + locale);
        if (files != null) {
            for (IResource file : files) {
                String fileName = file.getName();
                if (fileName.startsWith("_"))
                    continue;

                if (fileName.endsWith(CoreConstants.FILE_TYPE_I18N_YAML)) {
                    Map<String, ValueWithLocation> messages = loadMessages(file);
                    if (messages != null) {
                        merge(merged, messages, false);
                    }
                }
            }
        }

        IResource mainResource = VirtualFileSystem.instance()
                .getResource("/main/i18n/" + locale + "." + CoreConstants.FILE_TYPE_I18N_YAML);

        if (mainResource.exists()) {
            Map<String, ValueWithLocation> messages = loadMessages(mainResource);
            if (messages != null) {
                merge(merged, messages, true);
            }
        }

        Map<String, String> ret = new HashMap<>();
        merged.forEach((k, vl) -> {
            // intern将减少内存消耗，重复的字符串只会返回同一个实例
            ret.put(k, vl.asString().intern());
        });

        dumpMerged(locale, ret);

        return new I18nMessage(SourceLocation.fromPath(mainResource.getPath()), ret);
    }

    private void dumpMerged(String locale, Map<String, String> merged) {
        if (AppConfig.isDebugMode()) {
            String dumpPath = ResourceHelper
                    .getDumpPath("/main/" + locale + "/merged." + CoreConstants.FILE_TYPE_I18N_JSON);
            IResource resource = VirtualFileSystem.instance().getResource(dumpPath);
            JsonSaveOptions options = new JsonSaveOptions();
            options.setPretty(true);
            JsonTool.instance().saveToResource(resource, new TreeMap<>(merged), options);
        }
    }

    Map<String, ValueWithLocation> loadMessages(IResource resource) {
        JObject obj = JsonTool.loadDeltaBeanFromResource(resource, JObject.class, new DeltaJsonOptions());
        if (obj == null)
            return null;
        return obj.flatten();
    }

    private void merge(Map<String, ValueWithLocation> merged, Map<String, ValueWithLocation> map,
                       boolean allowReplace) {
        map.forEach((k, vl) -> {
            merge(merged, k, vl, allowReplace);
        });
    }

    void merge(Map<String, ValueWithLocation> merged, String key, ValueWithLocation vl, boolean allowReplace) {
        ValueWithLocation old;
        String value = vl.asString();
        if (StringHelper.isEmpty(value)) {
            old = merged.remove(key);
        } else {
            old = merged.put(key, vl);
        }
        if (old != null && !old.equals(vl)) {
            if (!allowReplace)
                throw new NopException(ERR_I18N_DUPLICATED_MESSAGE_KEY).param(ARG_KEY,key).param(ARG_LOC_A, old.getLocation())
                        .param(ARG_VALUE_A, old.getValue()).param(ARG_LOC_B, vl.getLocation())
                        .param(ARG_VALUE_B, vl.getValue());
            LOG.info("nop.core.i18n.override-i18n-message:key={},value={},old={},loc={},oldLoc={}", key, vl.getValue(),
                    old, vl.getLocation(), old.getLocation());
        }
    }
}