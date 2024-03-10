/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.spl.execute;

import com.scudata.cellset.datamodel.PgmCellSet;
import com.scudata.dm.Context;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.commons.util.MavenDirHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.json.JsonTool;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import java.io.File;

import static io.nop.report.spl.execute.SplHelper.spl2CellSet;

public class TestSplExecutor extends JunitBaseTestCase {

    @Inject
    SplExecutor splExecutor;

    public TestSplExecutor() {
        File dir = MavenDirHelper.getClassesDir(TestSplExecutor.class);
        System.setProperty("start.home", dir.getAbsolutePath());
    }

    @Test
    public void testSpl() {
        PgmCellSet pgmCellSet = spl2CellSet("=100.new(~:baseNum,~*~:square2)");  // dfx, sqlx 二进制文件
        Context context = new Context(); //上下文,参数..设置
        pgmCellSet.setContext(context);
        Object result = SplHelper.normalizeResult(pgmCellSet.execute());
        System.out.println(result);
    }

    @Disabled
    @Test
    public void testSplFile() {
        IEvalScope scope = XLang.newEvalScope();
        for (int i = 1; i < 10; i++) {
            String path = "/test/p0" + i + ".splx";
            Object result = splExecutor.executeForPath(scope, path, null);
            System.out.println("result" + i + "=" + JsonTool.serialize(result, true));
        }
    }
}