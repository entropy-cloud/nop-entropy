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

    private boolean invokePathMatchesQualifiedName(ChangeAnalyzer analyzer, String filePath, String qn) {
        try {
            java.lang.reflect.Method method = ChangeAnalyzer.class.getDeclaredMethod(
                    "pathMatchesQualifiedName", String.class, String.class);
            method.setAccessible(true);
            return (Boolean) method.invoke(analyzer, filePath, qn);
        } catch (Exception e) {
            throw new io.nop.api.core.exceptions.NopException(e);
        }
    }
}
