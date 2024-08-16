package io.nop.xlang.janino;

import io.nop.commons.util.FileHelper;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.resource.scan.FileScanHelper;
import io.nop.core.unittest.BaseTestCase;
import io.nop.xlang.ast.CompilationUnit;
import io.nop.xlang.ast.print.XLangSourcePrinter;
import io.nop.xlang.xmeta.xjava.JaninoHelper;
import org.codehaus.janino.Java;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;

@Disabled
public class TestJavaTransformer extends BaseTestCase {

    @Test
    public void testTransform() throws Exception {
        File rootDir = getModuleDir().getCanonicalFile();

        FileScanHelper.scanDir(rootDir, file -> {
            String absPath = file.getAbsolutePath().replace('\\','/');
            if (absPath.contains("/target/") || !absPath.contains("/src/"))
                return;
            if (!file.isDirectory() && file.getName().endsWith(".java")) {
                String relativePath = FileHelper.getRelativePath(rootDir, file);
                transformJavaFile(new FileResource("/" + relativePath, file));
            }
        });
    }

    void transformJavaFile(IResource resource) {
        try {
            File targetFile = getTargetFile(resource.getPath());
            Java.CompilationUnit unit = JaninoHelper.parseFromResource(resource);
            CompilationUnit xUnit = new JavaToXLangTransformer(true).buildCompilationUnit(unit);
            String code = new XLangSourcePrinter().toSource(xUnit);
            FileHelper.writeText(targetFile, code, null);
        } catch (Exception e) {
            System.err.println("parse-failed；" + resource.toFile() + ",error=" + ErrorMessageManager.instance().getRealCause(e).toString());
        }
    }
}
