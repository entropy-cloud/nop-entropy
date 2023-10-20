/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.file.core;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;

import jakarta.ws.rs.core.MediaType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MediaTypeHelper {
    private static Map<String, String> fileExtToMediaType = new ConcurrentHashMap<>();

    static {
        fileExtToMediaType.put("json", MediaType.APPLICATION_JSON);
        fileExtToMediaType.put("json5", MediaType.APPLICATION_JSON);
        fileExtToMediaType.put("xml", MediaType.APPLICATION_XML);
        fileExtToMediaType.put("html", MediaType.TEXT_HTML);
        fileExtToMediaType.put("xhtml", MediaType.APPLICATION_XHTML_XML);
    }

    private static boolean configLoaded;

    public static String getMediaTypeFromFileExt(String fileExt) {
        if (StringHelper.isEmpty(fileExt))
            return null;

        if (!configLoaded) {
            loadConfig();
            configLoaded = true;
        }
        return fileExtToMediaType.get(fileExt);
    }

    public static String getMimeType(String contentType) {
        if (contentType == null)
            return null;
        return StringHelper.firstPart(contentType, '?');
    }

    public static String getMimeType(String contentType, String fileExt) {
        String mimeType = getMimeType(contentType);
        if (StringHelper.isEmpty(mimeType))
            mimeType = getMediaTypeFromFileExt(fileExt);
        return mimeType;
    }

    private static void loadConfig() {
        IResource resource = VirtualFileSystem.instance().getResource(FileConstants.MEDIA_TYPE_CONFIG_PATH);
        if (resource.exists()) {
            Map<String, String> map = (Map<String, String>) JsonTool.parseBeanFromResource(resource);
            registerMediaTypes(map);
        }
    }

    public static void registerMediaType(String fileExt, String mediaType) {
        fileExtToMediaType.put(fileExt, mediaType);
    }

    public static void registerMediaTypes(Map<String, String> mediaTypes) {
        if (mediaTypes != null) {
            mediaTypes.forEach((fileExt, mediaType) -> {
                registerMediaType(fileExt, mediaType);
            });
        }
    }
}