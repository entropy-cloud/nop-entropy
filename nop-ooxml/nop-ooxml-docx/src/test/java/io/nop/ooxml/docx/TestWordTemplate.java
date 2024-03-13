/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.docx;

import io.nop.api.core.config.AppConfig;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.json.jpath.JPath;
import io.nop.core.lang.xml.XNode;
import io.nop.core.model.object.DynamicObject;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.unittest.BaseTestCase;
import io.nop.ooxml.common.OfficePackage;
import io.nop.ooxml.docx.parse.WordTemplateParser;
import io.nop.xlang.api.XLang;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.nop.api.core.ApiConfigs.CFG_EXCEPTION_FILL_STACKTRACE;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestWordTemplate extends BaseTestCase {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        AppConfig.getConfigProvider().updateConfigValue(CFG_EXCEPTION_FILL_STACKTRACE, true);
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testSimple() {
        IResource resource = new ClassPathResource("classpath:docx/test-simple.docx");
        WordTemplate tpl = new WordTemplateParser().parseFromResource(resource);

        File file = getTargetFile("/gen/result-simple.docx");
        tpl.generateToFile(file, XLang.newEvalScope());

        ResourceHelper.unzip(new FileResource(file));
    }

    @Test
    public void testLoop() {
        IResource resource = new ClassPathResource("classpath:docx/test-loop.docx");
        WordTemplate tpl = new WordTemplateParser().parseFromResource(resource);

        Object model = new DslModelParser().dynamic(true)
                .parseFromResource(new ClassPathResource("classpath:docx/result.orm.xml"));

        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue(null, "model", model);
        scope.setLocalValue(null, "createDate", LocalDate.now());
        scope.setLocalValue(null, "updateDate", LocalDate.now());
        scope.setLocalValue(null, "dialect", new DynamicObject("Dialect", null));

        File file = getTargetFile("/gen/result-loop.docx");
        tpl.generateToFile(file, scope);

        ResourceHelper.unzip(new FileResource(file));
    }

    @Test
    public void testParse() {
        IResource resource = new ClassPathResource("classpath:docx/test-tpl.docx");
        WordTemplate tpl = new WordTemplateParser().parseFromResource(resource);

        Object model = new DslModelParser().dynamic(true)
                .parseFromResource(new ClassPathResource("classpath:docx/result.orm.xml"));

        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue(null, "model", model);
        scope.setLocalValue(null, "createDate", LocalDate.now());
        scope.setLocalValue(null, "updateDate", LocalDate.now());
        scope.setLocalValue(null, "dialect", new DynamicObject("Dialect", null));

        // 模拟OrmColumnModel上的方法，避免依赖nop-orm模块
        List<DynamicObject> cols = (List<DynamicObject>) JPath.jpath("$.entities[*].columns[*]").get(model);
        for (DynamicObject col : cols) {
            col.defineMethod("getSqlType", new IEvalFunction() {
                @Override
                public Object invoke(Object thisObj, Object[] args, IEvalScope scope) {
                    DynamicObject obj = (DynamicObject) thisObj;
                    Object type = obj.prop_get("stdSqlType");
                    Object precision = obj.prop_get("precision");
                    if (precision != null)
                        return type + "(" + precision + ")";
                    return type;
                }
            }, true);
        }

        File file = getTargetFile("/gen/result.docx");
        tpl.generateToFile(file, scope);
    }

    /**
     * 使用了从poi-tl项目中拷贝过来的测试样例模板
     */
    @Test
    public void testPayment() {
        IResource resource = classpathResource("docx/payment.docx");
        Object entity = classpathBean("docx/payment.json", Map.class);

        List<IResource> images = new ArrayList<>();
        images.add(classpathResource("docx/excel-orm.png"));
        images.add(classpathResource("docx/lowcode.jpg"));
        images.add(classpathResource("docx/excel-orm.png"));

        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue(null, "entity", entity);
        scope.setLocalValue(null, "images", images);

        File file = getTargetFile("/gen/result-payment.docx");
        WordTemplate tpl = new WordTemplateParser().parseFromResource(resource);
        tpl.generateToFile(file, scope);

        OfficePackage pkg = new OfficePackage();
        pkg.loadFromFile(file);
        XNode doc = pkg.getFile("word/document.xml").buildXml(XLang.newEvalScope());
        assertEquals(attachmentXml("payment.doc.xml").xml(), doc.xml());
    }

    @Test
    public void testLinkExpr() {
        IResource resource = classpathResource("docx/test-link-expr.docx");
        Object entity = classpathBean("docx/payment.json", Map.class);

        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue(null, "entity", entity);

        File file = getTargetFile("/gen/result-link-expr.docx");
        WordTemplate tpl = new WordTemplateParser().parseFromResource(resource);
        tpl.generateToFile(file, scope);

        OfficePackage pkg = new OfficePackage();
        pkg.loadFromFile(file);
        XNode doc = pkg.getFile("word/document.xml").buildXml(XLang.newEvalScope());
        doc.dump();
        assertEquals(attachmentXml("result-link-expr.doc.xml").xml(), doc.xml());
    }

    @Test
    public void testEmbeddedEL() {
        IResource resource = classpathResource("docx/test-el.docx");
        Object entity = classpathBean("docx/mei_template.json", Map.class);

        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue(null, "entity", entity);

        File file = getTargetFile("/gen/result-test-el.docx");
        WordTemplate tpl = new WordTemplateParser().parseFromResource(resource);
        tpl.generateToFile(file, scope);

        OfficePackage pkg = new OfficePackage();
        pkg.loadFromFile(file);
        XNode doc = pkg.getFile("word/document.xml").buildXml(XLang.newEvalScope());
        doc.dump();
        assertEquals(attachmentXml("result-test-el.doc.xml").xml(), doc.xml());
    }

    //
    // public static void main(String[] args) {
    // ZipFileWatcher.main(new
    // String[]{"C:\\can\\entropy-cloud\\nop-ooxml\\nop-ooxml-docx\\target\\gen\\result-loop.docx"});
    // }
}
