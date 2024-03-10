/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.unittest;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.nop.core.CoreErrors.ARG_ERROR_CODE;
import static io.nop.core.CoreErrors.ARG_EXCEPTION;
import static io.nop.core.CoreErrors.ARG_EXPECTED;
import static io.nop.core.CoreErrors.ARG_FILE_NAME;
import static io.nop.core.CoreErrors.ARG_TITLE;
import static io.nop.core.CoreErrors.ARG_VALUE;
import static io.nop.core.CoreErrors.ERR_UNITTEST_EXCEPTION_EXPECTED;
import static io.nop.core.CoreErrors.ERR_UNITTEST_EXCEPTION_WITH_ERROR_CODE_EXPECTED;
import static io.nop.core.CoreErrors.ERR_UNITTEST_RETURN_VALUE_MISMATCH;
import static io.nop.core.CoreErrors.ERR_UNITTEST_UNKNOWN_MARKDOWN_SECTION;

/**
 * 读取markdown文件中的section作为测试数据
 */
public class MarkdownTestFile {
    static final Logger LOG = LoggerFactory.getLogger(MarkdownTestFile.class);

    private String fileName;
    private List<MarkdownTestSection> sections;

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public List<MarkdownTestSection> getSections() {
        return sections;
    }

    public void setSections(List<MarkdownTestSection> sections) {
        this.sections = sections;
    }

    public MarkdownTestSection getSection(String title) {
        if (sections != null) {
            for (MarkdownTestSection section : sections) {
                if (Objects.equals(title, section.getTitle()))
                    return section;
            }
        }
        return null;
    }

    public MarkdownTestSection requireSection(String title) {
        MarkdownTestSection block = getSection(title);
        if (block == null)
            throw new NopException(ERR_UNITTEST_UNKNOWN_MARKDOWN_SECTION).param(ARG_FILE_NAME, fileName)
                    .param(ARG_TITLE, title);
        return block;
    }

    public void run(Function<MarkdownTestSection, Object> action) {
        LOG.info("sections:" + sections.stream().map(MarkdownTestSection::getTitle).collect(Collectors.toList()));

        long begin = CoreMetrics.nanoTime();
        LOG.info("nop.test.begin-run-file:file={}", fileName);
        for (MarkdownTestSection section : sections) {
            runSection(section, action);
        }
        long diff = CoreMetrics.nanoToMillis(CoreMetrics.nanoTimeDiff(begin));
        LOG.info("nop.test.end-block:file={},usedTime={}ms", fileName, diff);
    }

    public static void runSection(MarkdownTestSection section, Function<MarkdownTestSection, Object> action) {
        long begin = CoreMetrics.nanoTime();
        Object result = null;
        boolean failed = false;
        try {
            result = action.apply(section);
        } catch (NopException e) {
            //e.printStackTrace();
            failed = true;
            if (!section.matchErrorCode(e.getErrorCode())) {
                throw e;
            }
        } catch (RuntimeException e) {
           // e.printStackTrace();
            failed = true;
            String msg = e.getMessage();
            if (!section.matchMessage(msg)) {
                throw e;
            }
        }
        long diff = CoreMetrics.nanoToMillis(CoreMetrics.nanoTimeDiff(begin));
        if (!failed) {
            if (!StringHelper.isEmpty(section.getErrorCodeAttr()))
                throw new NopException(ERR_UNITTEST_EXCEPTION_WITH_ERROR_CODE_EXPECTED)
                        .param(ARG_FILE_NAME, section.getFileName()).param(ARG_TITLE, section.getTitle())
                        .param(ARG_ERROR_CODE, section.getErrorCodeAttr());

            if (!StringHelper.isEmpty(section.getExceptionAttr()))
                throw new NopException(ERR_UNITTEST_EXCEPTION_EXPECTED).param(ARG_FILE_NAME, section.getFileName())
                        .param(ARG_TITLE, section.getTitle()).param(ARG_EXCEPTION, section.getExceptionAttr());

            if (!section.matchReturn(result)) {
                throw new NopException(ERR_UNITTEST_RETURN_VALUE_MISMATCH).param(ARG_FILE_NAME, section.getFileName())
                        .param(ARG_TITLE, section.getTitle()).param(ARG_VALUE, result)
                        .param(ARG_EXPECTED, section.getReturnAttr());
            }

        }
        LOG.info("nop.test.run-section:section={},usedTime={}ms", section.getTitle(), diff);
    }
}