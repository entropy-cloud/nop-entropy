/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource;

import io.nop.commons.util.CollectionHelper;

import java.util.List;

public interface ResourceConstants {
    String ENCODING_UTF8 = "UTF-8";

    String DELTA_PATH_PREFIX = "/_delta/";
    String TENANT_PATH_PREFIX = "/_tenant/";
    String TEMPLATES_PATH_PREFIX = "/templates/";

    String RESOLVE_PREFIX = "resolve-";

    String CP_NS = "cp";
    String CP_PATH_SUFFIX = "c";

    String CLASSPATH_NS = "classpath";
    String CLASS_FILE_SUFFIX = ".class";

    // 模块文件，在每个模块目录下查找
    String MODULE_NS = "module";

    // 临时文件系统
    String TEMP_NS = "temp";
    // 调试目录
    String DUMP_NS = "dump";

    // 自选主题
    String THEME_NS = "theme";

    // 操作系统文件
    String FILE_NS = "file";

    // 不进行虚拟路径解析
    String RAW_NS = "raw";

    //
    String SUPER_NS = "super";

    String PLACEHOLDER_PROJECT_PATH = "{PROJECT_PATH}";

    /**
     * 内部路径都以/_为前缀，例如/_tenant/3/a.html， /_delta/app/a.html
     */
    String INTERNAL_PATH_PREFIX = "/_";

    String CLASS_PATH_VFS_DIR = "_vfs/";

    String COMPONENT_PARAM_TRANSFORM = "transform";
    String COMPONENT_PARAM_SUB = "sub";

    String RESOURCE_VFS_INDEX = "classpath:nop-vfs-index.txt";

    String FILE_POSTFIX_JSON = ".json";
    String FILE_POSTFIX_JSON5 = ".json5";
    String FILE_POSTFIX_YAML = ".yaml";
    String FILE_POSTFIX_YML = ".yml";

    List<String> JSON_FILE_EXTS = CollectionHelper.buildImmutableList("json","json5","yaml","yml");

    String FILE_POSTFIX_XML = ".xml";

    String FILE_POSTFIX_BAK = ".bak";
}