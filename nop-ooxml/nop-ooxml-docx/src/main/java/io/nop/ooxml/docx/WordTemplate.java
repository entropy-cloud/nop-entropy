/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.docx;

import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.resource.tpl.ITextTemplateOutput;
import io.nop.ooxml.common.OfficeConstants;
import io.nop.ooxml.common.gen.XplGenConfig;
import io.nop.ooxml.common.output.AbstractOfficeTemplate;
import io.nop.ooxml.docx.model.WordOfficePackage;

import java.io.File;
import java.util.Map;

/*
<w:document mc:Ignorable="w14 wp14">
<w:body>
<w:p></w:p>
<w:tbl></w:tbl>
<w:sectPr>
  <w:type w:val="nextPage"/>
  <w:pgSz w:w="11906" w:h="16838"/>
  <w:pgMar w:left="1134" w:right="1134" w:header="0"
 w:top="1134" w:footer="0" w:bottom="1134" w:gutter="0"/>
 <w:pgNumType w:fmt="decimal"/>
 <w:formProt w:val="false"/>
 <w:textDirection w:val="lrTb"/>
 <w:docGrid w:type="default" w:linePitch="240" w:charSpace="4294903807"/>
</w:sectPr>
</w:body>
</w:document>
 */
public class WordTemplate extends AbstractOfficeTemplate {

    private final WordOfficePackage pkg;
    private final IEvalAction beforeGen;
    private final Map<String, ITextTemplateOutput> outputs;
    private final IEvalAction afterGen;
    private final XplGenConfig genConfig;

    private boolean removeComments;

    public WordTemplate(WordOfficePackage pkg,
                        IEvalAction beforeGen,
                        Map<String, ITextTemplateOutput> outputs,
                        IEvalAction afterGen,
                        XplGenConfig genConfig) {
        this.pkg = pkg;
        this.outputs = outputs;
        this.genConfig = genConfig;
        this.beforeGen = beforeGen;
        this.afterGen = afterGen;

        // 将模板中所有内容都读入到内存中，从而不再持有外部zip文件的引用，可以被安全的缓存并重复使用。
        pkg.loadInMemory();
    }

    public void setRemoveComments(boolean removeComments) {
        this.removeComments = removeComments;
    }

    @Override
    public void generateToDir(File tempDir, IEvalContext context) {
        IEvalScope scope = context.getEvalScope();
        WordOfficePackage copy = pkg.copy();
        scope.setLocalValue(null, OfficeConstants.VAR_OFC_PKG, copy);
        scope.setLocalValue(null, OfficeConstants.VAR_XPL_GEN_CONFIG, genConfig);

        if (beforeGen != null)
            beforeGen.invoke(context);

        outputs.forEach((path, output) -> {
            output.generateToFile(new File(tempDir, path), context);
        });

        if (afterGen != null)
            afterGen.invoke(context);

        if (removeComments)
            copy.removeCommentsFile();

        copy.generateToDir(tempDir, scope);
    }
}