package io.nop.report.spl.execute;

import com.scudata.ide.spl.SPL;
import io.nop.commons.util.MavenDirHelper;

import java.io.File;

public class SplIDE {
    public static void main(String[] args) {
        File classesFile = MavenDirHelper.getClassesDir(SplIDE.class);
        System.setProperty("start.home", classesFile.getAbsolutePath());
        SPL.main(args);
    }
}
