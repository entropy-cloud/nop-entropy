package io.nop.ib.junit;

import io.nop.ib.model.IbModule;

import java.util.ArrayList;
import java.util.List;

/**
 * Surefire include-pattern parity for the dispatch surface (defect 14, Syncope
 * observation): surefire only executes classes matching its <includes> patterns
 * (default: Test*, *Test, *Tests, *TestCase). The unit index dispatches every
 * JUnit-marked class — which ALSO runs base classes like *ITCase that carry
 * @Test methods but rely on a @BeforeAll in a concrete subclass to start their
 * environment (ZK TestingServer). Running them standalone fails and poisons
 * Spring's context-failure threshold for the whole module.
 *
 * <p>Dispatch therefore filters by the module's recorded surefire includes,
 * falling back to surefire's default naming rules when the pom declares none.
 * This aligns the executed corpus EXACTLY with Maven's (defect 14: "more is
 * safe" is disproven — extra classes changed test outcomes).
 */
public final class SurefireClassFilter {

    private SurefireClassFilter() {
    }

    /** classes of the module that surefire would execute. */
    public static List<String> filter(List<String> classNames, IbModule module) {
        List<String> includes = module.getSurefireIncludes();
        List<String> excludes = module.getSurefireExcludes();
        List<String> out = new ArrayList<>();
        for (String cls : classNames) {
            String simple = simpleName(cls);
            // surefire <excludes> parity (defect 30, spring-batch observation):
            // mvn's test phase drops excluded classes (**/*IntegrationTests.java
            // runs via failsafe at the integration-test phase instead); dispatching
            // them over-ran mvn by 75 classes / 249 tests
            if (excludes != null && !excludes.isEmpty() && matchesAnyPattern(simple, excludes))
                continue;
            if (matchesDefault(simple) && (includes == null || includes.isEmpty()))
                out.add(cls);
            else if (includes != null && !includes.isEmpty() && matchesAnyPattern(simple, includes))
                out.add(cls);
        }
        return out;
    }

    /** surefire default naming rules: Test*, *Test, *Tests, *TestCase. */
    private static boolean matchesDefault(String simple) {
        return simple.startsWith("Test")
                || simple.endsWith("Test")
                || simple.endsWith("Tests")
                || simple.endsWith("TestCase");
    }

    /** include patterns like **&#47;SomeName.java, SomeName.java, **&#47;*Test.java —
     *  reduced to their basename stem, then matched with the same prefix/suffix
     *  /exact rules surefire applies to class names. */
    private static boolean matchesAnyPattern(String simple, List<String> includes) {
        for (String include : includes) {
            String base = include;
            int slash = base.lastIndexOf('/');
            if (slash >= 0)
                base = base.substring(slash + 1);
            if (base.endsWith(".java") || base.endsWith(".class"))
                base = base.substring(0, base.length() - 5);
            if (base.isEmpty())
                continue;
            if (base.equals(simple))
                return true;
            if (base.startsWith("*") && simple.endsWith(base.substring(1)))
                return true;
            if (base.endsWith("*") && simple.startsWith(base.substring(0, base.length() - 1)))
                return true;
        }
        return false;
    }

    private static String simpleName(String cls) {
        int dot = cls.lastIndexOf('.');
        return dot >= 0 ? cls.substring(dot + 1) : cls;
    }
}
