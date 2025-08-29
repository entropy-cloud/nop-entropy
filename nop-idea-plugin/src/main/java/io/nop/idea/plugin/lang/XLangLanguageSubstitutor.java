/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang;

import java.io.InputStreamReader;
import java.io.Reader;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.intellij.lang.Language;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem;
import com.intellij.psi.LanguageSubstitutor;
import io.nop.commons.io.stream.FastBufferedReader;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XRootNodeParser;
import io.nop.xlang.xdsl.XDslKeys;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 根据 XML 中的特定结构（即包含 xmlns:x 和 x:schema 属性）动态识别其是否为 XLang
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-08-02
 */
public class XLangLanguageSubstitutor extends LanguageSubstitutor {
    private static final Logger LOG = LoggerFactory.getLogger(XLangLanguageSubstitutor.class);

    private static final Language LANG_NULL = new Language("NULL") {};

    private final Cache<Integer, Language> cached = Caffeine.newBuilder().maximumSize(500).build();

    @Override
    public @Nullable Language getLanguage(@NotNull VirtualFile file, @NotNull Project project) {
        String ext = file.getExtension();
        if ("xsd".equalsIgnoreCase(ext) //
            || file.getName().equals("pom.xml") //
        ) {
            return null;
        }

        Language lang;
        // Note: 对于只读文件，缓存结果，避免重复读取
        if (file.getFileSystem() instanceof ArchiveFileSystem) {
            lang = cached.get(file.hashCode(), (k) -> getLanguage(file));
        } else {
            lang = getLanguage(file);
        }

        return lang == LANG_NULL ? null : lang;
    }

    public Language getLanguage(@NotNull VirtualFile file) {
        LOG.debug("Try to detect XLang from file {}", file);

        try (InputStreamReader reader = new InputStreamReader(file.getInputStream(), file.getCharset())) {
            return isXLangFile(reader) //
                   ? XLangLanguage.INSTANCE
                   // Note: 由于是按内容动态确定，故而，需在非 XLang 时，返回原始语言
                   : XMLLanguage.INSTANCE;
        } catch (Exception ignore) {
            return LANG_NULL;
        }
    }

    /** 根据 xml 内容做精确判断 */
    private boolean isXLangFile(Reader reader) {
        // Note: 仅分析根节点
        XNode node = parseRootNode(reader);
        XDslKeys keys = XDslKeys.of(node);

        return node.hasAttr(keys.SCHEMA);
    }

    public static XNode parseRootNode(Reader reader) {
        return new XRootNodeParser().parseFromReader(null, new FastBufferedReader(reader));
    }
}
