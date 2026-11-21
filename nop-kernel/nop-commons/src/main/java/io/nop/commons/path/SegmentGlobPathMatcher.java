/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.path;

import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Segment-oriented glob path matcher: {@code *} matches within one path
 * segment, {@code ?} one character, {@code **} across segments. Unlike
 * {@link AntPathMatcher} (Ant-style, template variables, pattern combining),
 * this matcher deliberately implements the narrower fail-loud contract used by
 * build-tool globs (nop-ib task inputs/outputs): the metacharacters
 * {@code [ ] ( ) ^ + } etc. are rejected at compile time instead of being
 * silently literalized or reinterpreted (other regex punctuation such as
 * {@code |} is escaped and matched literally), and a {@code **} followed by
 * {@code /} swallows that separator so {@code a/**&#47;b} also matches
 * {@code a&#47;b} itself at the directory boundary.
 *
 * <p>Migrated from nop-ib's GlobMatcher (analysis
 * 2026-11-22-nop-ib-nop-git-platform-reuse); nop-ib's {@code GlobMatcher}
 * now delegates here. Template-variable and pattern-combining operations are
 * unsupported: a glob has no URI-template semantics.
 */
public class SegmentGlobPathMatcher implements IPathMatcher {
    private static final String REGEX_META_CHARS = "[]{}()^+\\";

    private static final SegmentGlobPathMatcher INSTANCE = new SegmentGlobPathMatcher();

    public static SegmentGlobPathMatcher instance() {
        return INSTANCE;
    }

    /** Compiles and validates eagerly; a bad glob is a config error (fail-loud). */
    public Pattern compile(String glob) {
        // regex metacharacters have no glob meaning — reject them instead of
        // silently literalizing (fail-loud contract)
        for (int k = 0; k < glob.length(); k++) {
            char c = glob.charAt(k);
            if (REGEX_META_CHARS.indexOf(c) >= 0)
                throw new IllegalArgumentException("invalid glob '" + glob + "': character '" + c
                        + "' has no glob meaning");
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        int n = glob.length();
        while (i < n) {
            char c = glob.charAt(i);
            if (c == '*') {
                if (i + 1 < n && glob.charAt(i + 1) == '*') {
                    // '**' crosses segments; swallow a following '/' so "a/**" also
                    // matches "a" itself being a directory boundary
                    if (i + 2 < n && glob.charAt(i + 2) == '/')
                        i += 3;
                    else
                        i += 2;
                    sb.append(".*");
                } else {
                    i++;
                    sb.append("[^/]*");
                }
            } else if (c == '?') {
                i++;
                sb.append("[^/]");
            } else {
                if (!Character.isLetterOrDigit(c) && "_./-".indexOf(c) < 0)
                    sb.append('\\');
                sb.append(c);
                i++;
            }
        }
        try {
            return Pattern.compile(sb.toString());
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("invalid glob '" + glob + "': " + e.getMessage());
        }
    }

    public boolean matches(Pattern compiled, String relPath) {
        String normalized = relPath.replace('\\', '/');
        return compiled.matcher(normalized).matches();
    }

    @Override
    public boolean isPattern(String path) {
        return path.indexOf('*') >= 0 || path.indexOf('?') >= 0;
    }

    @Override
    public boolean match(String pattern, String path) {
        return matches(compile(pattern), path);
    }

    @Override
    public boolean matchStart(String pattern, String path) {
        // a prefix "starts" the match when some full segment expansion of the
        // pattern can still complete: approximate by appending a recursive tail
        return matches(compile(pattern.endsWith("/**") ? pattern : pattern + "/**"), path);
    }

    @Override
    public String extractPathWithinPattern(String pattern, String path) {
        // globs carry no static-prefix machinery worth extracting; return the
        // full path when it is a pattern (AntPathMatcher-compatible fallback)
        return isPattern(pattern) ? path : "";
    }

    @Override
    public Map<String, String> extractUriTemplateVariables(String pattern, String path) {
        // globs have no URI-template semantics
        return Collections.emptyMap();
    }

    @Override
    public Comparator<String> getPatternComparator(String path) {
        // longest literal prefix wins; keep a stable, cheap approximation
        return (p1, p2) -> Integer.compare(countLiteralChars(p2), countLiteralChars(p1));
    }

    private int countLiteralChars(String pattern) {
        int n = 0;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c != '*' && c != '?' && c != '/')
                n++;
        }
        return n;
    }

    @Override
    public String combine(String pattern1, String pattern2) {
        if (pattern1.isEmpty())
            return pattern2;
        if (pattern1.endsWith("/**"))
            return pattern2.startsWith("/") ? pattern1 + pattern2 : pattern1 + "/" + pattern2;
        throw new IllegalArgumentException("cannot combine globs: '" + pattern1 + "' + '" + pattern2 + "'");
    }
}
