package io.nop.dyn.service.codegen;

import io.nop.codegen.CodeGenConstants;
import io.nop.codegen.XCodeGenerator;
import io.nop.commons.util.FileHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.resource.ResourceHelper;
import io.nop.orm.model.OrmModel;
import io.nop.xlang.api.XLang;

import java.io.File;

public class DynCodeGenHelper {
    public static void genModelFiles(OrmModel ormModel) {
        File vfsDir = ResourceHelper.getOverrideVFsDir();
        if (vfsDir == null)
            throw new IllegalStateException("nop.err.vfs.no-override-fs-dir");

        String url = FileHelper.getFileUrl(vfsDir);
        XCodeGenerator gen = new XCodeGenerator(url, url);
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue(CodeGenConstants.VAR_CODE_GEN_MODEL, ormModel);
        gen.execute("/", scope);
    }
}
