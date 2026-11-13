package io.nop.ib.scope;

import java.util.Locale;
import java.util.Set;

/**
 * Change routing per design 01 §七/§八. Every branch is explicit; the
 * data/model-driven test branch precedes the plain-resource branch (the 01 §七
 * history lesson: wrong branch order produced "compiles green, zero tests run").
 */
public final class ChangeClassifier {

    public enum Kind {
        POM, MAIN_JAVA, TEST_JAVA, GEN_OR_MODEL, TEST_DATA, CONFIG_RESOURCE,
        MAIN_RESOURCE, TEST_RESOURCE, OTHER
    }

    private static final Set<String> XLANG_MODEL_EXT = Set.of(
            "xgen", "xdef", "xmeta", "xbiz", "xwf", "xjob", "xtask", "xlib", "xdsl",
            "xplanet", "xddl", "xenum", "xdict", "xconfig", "xagent", "xmov", "xfull");

    public Kind classify(String relPath, boolean inMainSourceDir, boolean inTestSourceDir,
                         boolean inMainResourceDir, boolean inTestResourceDir) {
        String lower = relPath.toLowerCase(Locale.ROOT);
        String name = relPath.substring(relPath.lastIndexOf('/') + 1);

        if (name.equals("pom.xml"))
            return Kind.POM;
        if (lower.endsWith(".java") && !lower.contains("/_gen/"))
            return inTestSourceDir ? Kind.TEST_JAVA : Kind.MAIN_JAVA;
        if (lower.endsWith(".java") && lower.contains("/_gen/"))
            return Kind.GEN_OR_MODEL;
        if (isGenOrModel(lower, name))
            return Kind.GEN_OR_MODEL;
        if (isTestData(lower, name))
            return Kind.TEST_DATA;
        if (isConfigResource(lower, name))
            return Kind.CONFIG_RESOURCE;
        if (inTestResourceDir)
            return Kind.TEST_RESOURCE;
        if (inMainResourceDir)
            return Kind.MAIN_RESOURCE;
        return Kind.OTHER;
    }

    /** design 01 §八 rule set for generated products / XLang models / templates. */
    public static boolean isGenOrModel(String lower, String name) {
        if (lower.contains("/_gen/"))
            return true;
        if (name.startsWith("gen-") && lower.endsWith(".xgen"))
            return true;
        if (name.startsWith("_") && !name.startsWith("__"))
            return true; // reserved retention file (normative for XLang parse trees)
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            String ext = lower.substring(dot + 1);
            if (XLANG_MODEL_EXT.contains(ext))
                return true;
            if (ext.equals("xml") && (lower.endsWith(".orm.xml") || lower.endsWith(".xbiz.xml")
                    || lower.endsWith(".beans.xml") || lower.endsWith(".xdsl.xml")))
                return true;
        }
        return false;
    }

    /** design 01 §八 rule set for test data / corpora / expectation files. */
    public static boolean isTestData(String lower, String name) {
        if (lower.contains("/_cases/") || lower.contains("/_vfs/test/"))
            return true;
        return name.startsWith("_") && (lower.endsWith(".md") || lower.endsWith(".yaml")
                || lower.endsWith(".json") || lower.endsWith(".xml") || lower.endsWith(".csv")
                || lower.endsWith(".txt") || lower.endsWith(".snapshot") || lower.endsWith(".snap"));
    }

    /** configuration-class resources: they decide container assembly and codegen. */
    public static boolean isConfigResource(String lower, String name) {
        if (name.equals("bootstrap.yaml") || name.equals("bootstrap.yml") || name.equals("application.yaml"))
            return true;
        if (lower.endsWith(".beans.xml") || lower.endsWith(".service.xml"))
            return true;
        if (lower.contains("meta-inf/services/") || lower.contains("meta-inf/nop/"))
            return true;
        return name.startsWith("log4j") || name.startsWith("logback") || name.equals("logback.xml");
    }
}
