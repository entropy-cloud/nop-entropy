package io.nop.code.flow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestChangeAnalyzerPathMatching {

    @Test
    void testClassLevelQualifiedNameMatchesPath() {
        String filePath = "src/main/java/com/example/UserService.java";
        String qualifiedName = "com.example.UserService";

        ChangeAnalyzer analyzer = new ChangeAnalyzer();
        boolean matches = invokePathMatchesQualifiedName(analyzer, filePath, qualifiedName);
        assertTrue(matches, "Class-level QN should match file path");
    }

    @Test
    void testMethodLevelQualifiedNameMatchesContainingClass() {
        String filePath = "src/main/java/com/example/UserService.java";
        String qualifiedName = "com.example.UserService.getUser";

        ChangeAnalyzer analyzer = new ChangeAnalyzer();
        boolean matches = invokePathMatchesQualifiedName(analyzer, filePath, qualifiedName);
        assertTrue(matches, "Method-level QN should match containing class file path");
    }

    @Test
    void testNoFalsePositiveOnUnrelatedPath() {
        String filePath = "src/main/java/com/example/other/UserHandler.java";
        String qualifiedName = "com.example.UserService.getUser";

        ChangeAnalyzer analyzer = new ChangeAnalyzer();
        boolean matches = invokePathMatchesQualifiedName(analyzer, filePath, qualifiedName);
        assertFalse(matches, "Unrelated path should not match");
    }

    @Test
    void testNoFalsePositiveOnSimilarClassName() {
        String filePath = "src/main/java/com/example/UserServiceHelper.java";
        String qualifiedName = "com.example.UserService";

        ChangeAnalyzer analyzer = new ChangeAnalyzer();
        boolean matches = invokePathMatchesQualifiedName(analyzer, filePath, qualifiedName);
        assertFalse(matches, "Should not match based on substring prefix of class name");
    }

    // WP-7 AR-10/40: substring indexOf must not match mid-token at the START boundary either —
    // "mycom" is a different package than "com", so a path under mycom/ must not match a com.* QN.
    @Test
    void testNoFalsePositiveOnPrefixedPackageSegment() {
        String filePath = "src/main/java/mycom/example/User.java";
        String qualifiedName = "com.example.User";

        ChangeAnalyzer analyzer = new ChangeAnalyzer();
        boolean matches = invokePathMatchesQualifiedName(analyzer, filePath, qualifiedName);
        assertFalse(matches, "mycom/example/User must not match QN com.example.User");
    }

    @Test
    void testLegitimateClassMatchStillHolds() {
        String filePath = "src/main/java/com/example/User.java";
        String qualifiedName = "com.example.User";

        ChangeAnalyzer analyzer = new ChangeAnalyzer();
        boolean matches = invokePathMatchesQualifiedName(analyzer, filePath, qualifiedName);
        assertTrue(matches, "Exact package/class path must still match");
    }

    private boolean invokePathMatchesQualifiedName(ChangeAnalyzer analyzer, String filePath, String qn) {
        try {
            java.lang.reflect.Method method = ChangeAnalyzer.class.getDeclaredMethod(
                    "pathMatchesQualifiedName", String.class, String.class);
            method.setAccessible(true);
            return (Boolean) method.invoke(analyzer, filePath, qn);
        } catch (Exception e) {
            throw new AssertionError("Reflection failed", e);
        }
    }
}
