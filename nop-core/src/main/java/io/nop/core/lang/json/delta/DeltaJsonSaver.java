/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.json.delta;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.objects.Pair;
import io.nop.core.lang.json.DeltaJsonOptions;
import io.nop.core.lang.json.JsonSaveOptions;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

import static io.nop.core.lang.json.delta.DeltaMergeHelper.buildUniqueKey;

public class DeltaJsonSaver {
    public static final DeltaJsonSaver INSTANCE = new DeltaJsonSaver();
    static final Logger LOG = LoggerFactory.getLogger(DeltaJsonSaver.class);

    public boolean saveDelta(IResource resource, Map<String, Object> json, DeltaJsonOptions options,
                             boolean checkSameContent) {
        if (!resource.exists() || resource.length() <= 0) {
            return JsonTool.instance().saveToResource(resource, json, null);
        } else {
            Map<String, Object> base = DeltaJsonLoader.instance().loadFromResource(resource, options);
            SourceLocation loc = SourceLocation.fromPath(resource.getPath());
            Map<String, Object> diff = removeInherited(loc, json, base, options);
            if (options.getCleaner() != null) {
                options.getCleaner().accept(diff);
            }
            JsonSaveOptions saveOptions = new JsonSaveOptions();
            saveOptions.setCheckSameContent(checkSameContent);
            return JsonTool.instance().saveToResource(resource, diff, saveOptions);
        }
    }

    public Map<String, Object> getJsonDelta(IResource resource, Map<String, Object> json, DeltaJsonOptions options) {
        Map<String, Object> base = DeltaJsonLoader.instance().loadFromResource(resource, options);
        SourceLocation loc = SourceLocation.fromPath(resource.getPath());
        Map<String, Object> diff = DeltaJsonSaver.INSTANCE.removeInherited(loc, json, base, options);
        if (options.getCleaner() != null) {
            options.getCleaner().accept(diff);
        }
        return diff;
    }

    public Map<String, Object> removeInherited(SourceLocation loc, Map<String, Object> json, Map<String, Object> base,
                                               DeltaJsonOptions options) {
        Map<String, Object> genExtends = DeltaJsonLoader.instance().getGenExtends(loc, base, true);
        Map<String, Object> currentExtends = DeltaJsonLoader.instance().getGenExtends(loc, json, true);
        genExtends.putAll(currentExtends);

        Map<String, Object> inherited = (Map<String, Object>) DeltaJsonLoader.instance().resolveExtends(genExtends,
                options);

        if (options.getCleaner() != null)
            options.getCleaner().accept(inherited);

        if (LOG.isDebugEnabled())
            LOG.debug("inherited:\n{}", JsonTool.serialize(inherited, true));

        // 从json中删除根据x:gen-extends生成的部分
        Map<String, Object> diff = JsonDiffer.instance().diffMap(json, inherited);
        // 文件中的x:gen-extends可以被json中的x:gen-extends覆盖，同时会保持位置在源码中靠前的位置
        Map<String, Object> merged = DeltaJsonLoader.instance().getGenExtends(loc, genExtends, false);
        merged.putAll(diff);

        Pair<String, String> key = buildUniqueKey(merged);
        if (key != null) {
            // 根节点的id如果是继承的，则没有必要保留
            if (Objects.equals(key.getValue(), inherited.get(key.getKey()))) {
                merged.remove(key.getKey());
            }
        }

        return merged;
    }
}