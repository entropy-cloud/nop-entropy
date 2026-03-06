package io.nop.commons.path;

import io.nop.api.core.util.Guard;
import jakarta.annotation.Nullable;

import java.util.Map;

public class CompiledPathMatcher implements ICompiledPathMatcher {

    private final String pattern;
    private final String pathSeparator;
    private final AntPathMatcher.AntPathStringMatcher[] tokenizedMatchers;

    public CompiledPathMatcher(String pattern, String pathSeparator, boolean caseSensitive, boolean trimTokens,
                               String[] tokenizedPattern) {
        this.pattern = Guard.notNull(pattern, "pattern");
        this.pathSeparator = Guard.notNull(pathSeparator, "pathSeparator");
        this.tokenizedMatchers = new AntPathMatcher.AntPathStringMatcher[tokenizedPattern.length];
        for (int i = 0; i < tokenizedPattern.length; i++) {
            this.tokenizedMatchers[i] = new AntPathMatcher.AntPathStringMatcher(
                    tokenizedPattern[i], pathSeparator, caseSensitive
            );
        }
    }

    @Override
    public boolean match(String path) {
        return doMatch(path, true, null);
    }

    @Override
    public boolean matchStart(String path) {
        return doMatch(path, false, null);
    }

    private boolean doMatch(String path, boolean fullMatch, @Nullable Map<String, String> uriTemplateVariables) {
        if (path == null || path.startsWith(this.pathSeparator) != pattern.startsWith(this.pathSeparator)) {
            return false;
        }

        String[] pathDirs = tokenizePath(path);

        int pattIdxStart = 0;
        int pattIdxEnd = tokenizedMatchers.length - 1;
        int pathIdxStart = 0;
        int pathIdxEnd = pathDirs.length - 1;

        while (pattIdxStart <= pattIdxEnd && pathIdxStart <= pathIdxEnd) {
            AntPathMatcher.AntPathStringMatcher matcher = tokenizedMatchers[pattIdxStart];
            if ("**".equals(matcher.rawPattern())) {
                break;
            }
            if (!matcher.matchStrings(pathDirs[pathIdxStart], uriTemplateVariables)) {
                return false;
            }
            pattIdxStart++;
            pathIdxStart++;
        }

        if (pathIdxStart > pathIdxEnd) {
            if (pattIdxStart > pattIdxEnd) {
                return (pattern.endsWith(this.pathSeparator) == path.endsWith(this.pathSeparator));
            }
            if (!fullMatch) {
                return true;
            }
            if (pattIdxStart == pattIdxEnd && tokenizedMatchers[pattIdxStart].rawPattern().equals("*") && path.endsWith(this.pathSeparator)) {
                return true;
            }
            for (int i = pattIdxStart; i <= pattIdxEnd; i++) {
                if (!tokenizedMatchers[i].rawPattern().equals("**")) {
                    return false;
                }
            }
            return true;
        } else if (pattIdxStart > pattIdxEnd) {
            return false;
        } else if (!fullMatch && "**".equals(tokenizedMatchers[pattIdxStart].rawPattern())) {
            return true;
        }

        while (pattIdxStart <= pattIdxEnd && pathIdxStart <= pathIdxEnd) {
            AntPathMatcher.AntPathStringMatcher matcher = tokenizedMatchers[pattIdxEnd];
            if (matcher.rawPattern().equals("**")) {
                break;
            }
            if (!matcher.matchStrings(pathDirs[pathIdxEnd], uriTemplateVariables)) {
                return false;
            }
            if (pattIdxEnd == (tokenizedMatchers.length - 1) &&
                    pattern.endsWith(this.pathSeparator) != path.endsWith(this.pathSeparator)) {
                return false;
            }
            pattIdxEnd--;
            pathIdxEnd--;
        }

        if (pathIdxStart > pathIdxEnd) {
            for (int i = pattIdxStart; i <= pattIdxEnd; i++) {
                if (!tokenizedMatchers[i].rawPattern().equals("**")) {
                    return false;
                }
            }
            return true;
        }

        while (pattIdxStart != pattIdxEnd && pathIdxStart <= pathIdxEnd) {
            int patIdxTmp = -1;
            for (int i = pattIdxStart + 1; i <= pattIdxEnd; i++) {
                if (tokenizedMatchers[i].rawPattern().equals("**")) {
                    patIdxTmp = i;
                    break;
                }
            }
            if (patIdxTmp == pattIdxStart + 1) {
                pattIdxStart++;
                continue;
            }

            int patLength = (patIdxTmp - pattIdxStart - 1);
            int strLength = (pathIdxEnd - pathIdxStart + 1);
            int foundIdx = -1;

            strLoop:
            for (int i = 0; i <= strLength - patLength; i++) {
                for (int j = 0; j < patLength; j++) {
                    AntPathMatcher.AntPathStringMatcher matcher = tokenizedMatchers[pattIdxStart + j + 1];
                    String subStr = pathDirs[pathIdxStart + i + j];
                    if (!matcher.matchStrings(subStr, uriTemplateVariables)) {
                        continue strLoop;
                    }
                }
                foundIdx = pathIdxStart + i;
                break;
            }

            if (foundIdx == -1) {
                return false;
            }

            pattIdxStart = patIdxTmp;
            pathIdxStart = foundIdx + patLength;
        }

        for (int i = pattIdxStart; i <= pattIdxEnd; i++) {
            if (!tokenizedMatchers[i].rawPattern().equals("**")) {
                return false;
            }
        }

        return true;
    }

    @Override
    public Map<String, String> extractUriTemplateVariables(String path) {
        Map<String, String> variables = new java.util.LinkedHashMap<>();
        if (!doMatch(path, true, variables)) {
            throw new IllegalStateException("Pattern \"" + pattern + "\" is not a match for \"" + path + "\"");
        }
        return variables;
    }

    private String[] tokenizePath(String path) {
        return io.nop.commons.util.StringHelper.tokenizeToStringArray(path, this.pathSeparator, false, true);
    }
}
