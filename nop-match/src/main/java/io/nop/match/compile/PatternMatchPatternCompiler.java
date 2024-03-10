/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.match.compile;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.json.JsonParseOptions;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.functional.IEqualsChecker;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.lang.json.JObject;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.json.utils.SourceLocationHelper;
import io.nop.match.IMatchPattern;
import io.nop.match.IMatchPatternCompiler;
import io.nop.match.MatchConstants;
import io.nop.match.MatchPatternCompileConfig;
import io.nop.match.pattern.AlwaysTrueMatchPattern;
import io.nop.match.pattern.EqMatchPattern;
import io.nop.match.pattern.IsNullMatchPattern;
import io.nop.match.pattern.ListMatchPattern;
import io.nop.match.pattern.MapMatchPattern;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static io.nop.match.MatchConstants.MATCH_ALL_PATTERN;
import static io.nop.match.MatchConstants.NAME_PATTERN;
import static io.nop.match.MatchErrors.ARG_PATTERN;
import static io.nop.match.MatchErrors.ERR_MATCH_UNKNOWN_PATTERN;

public class PatternMatchPatternCompiler implements IMatchPatternCompiler {
    public static PatternMatchPatternCompiler INSTANCE = new PatternMatchPatternCompiler();

    @Override
    public IMatchPattern parseFromValue(SourceLocation loc, Object value, MatchPatternCompileConfig config) {
        return parseValuePattern(loc, value, config);
    }

    private IMatchPattern parseFromText(SourceLocation loc, String text, MatchPatternCompileConfig config) {
        JsonParseOptions options = new JsonParseOptions();
        options.setStrictMode(false);
        options.setTargetType(JObject.class);

        Map<String, Object> json = (Map<String, Object>) JsonTool.instance().parseFromText(loc, text, options);

        return parseFromJson(json, config);
    }

    private IMatchPattern parseFromJson(Map<String, Object> json, MatchPatternCompileConfig config) {
        String pattern = ConvertHelper.toString(json.get(MatchConstants.KEY_PREFIX));
        if (pattern != null && !pattern.equals(NAME_PATTERN)) {
            SourceLocation loc = SourceLocationHelper.getBeanLocation(json);
            return requireCompiler(loc, pattern, config).parseFromValue(loc, json, config);
        } else {
            return parseMapPattern(json, config);
        }
    }

    IMatchPattern parseMapPattern(Map<String, Object> json, MatchPatternCompileConfig config) {
        IMatchPattern extPropsPattern = null;
        Map<String, IMatchPattern> patterns = CollectionHelper.newLinkedHashMap(json.size());

        for (Map.Entry<String, Object> entry : json.entrySet()) {
            String name = entry.getKey();
            if (MatchConstants.KEY_PREFIX.equals(name))
                continue;

            Object value = entry.getValue();
            SourceLocation loc = SourceLocationHelper.getPropLocation(json, name);

            IMatchPattern pattern = parseValuePattern(loc, value, config);
            if (MatchConstants.MATCH_ALL_PATTERN.equals(name)) {
                extPropsPattern = pattern;
            } else {
                patterns.put(name, pattern);
            }
        }
        return new MapMatchPattern(patterns, extPropsPattern);
    }

    protected IMatchPattern parseValuePattern(SourceLocation loc, Object value, MatchPatternCompileConfig config) {
        if (value == null) {
            return IsNullMatchPattern.INSTANCE;
        }

        if (value instanceof Map) {
            return parseFromJson((Map<String, Object>) value, config);
        } else if (value instanceof Collection) {
            return parseFromCollection((Collection<?>) value, config);
        } else if (value instanceof String) {
            return parseFromStringValue(loc, (String) value, config);
        } else {
            return new EqMatchPattern(config.getEqualsChecker(), value);
        }
    }

    protected IMatchPattern parseFromCollection(Collection<?> list, MatchPatternCompileConfig config) {
        List<IMatchPattern> patterns = new ArrayList<>(list.size());
        Iterator<?> it = list.iterator();
        for (int i = 0, n = list.size(); i < n; i++) {
            SourceLocation loc = SourceLocationHelper.getElementLocation(list, i);
            Object value = it.next();
            IMatchPattern pattern = parseValuePattern(loc, value, config);
            patterns.add(pattern);
        }
        return new ListMatchPattern(patterns);
    }

    protected IMatchPattern parseFromStringValue(SourceLocation loc, String text, MatchPatternCompileConfig config) {
        if (MATCH_ALL_PATTERN.equals(text))
            return AlwaysTrueMatchPattern.INSTANCE;

        IEqualsChecker<Object> equalsChecker = config.getEqualsChecker();
        if (text.length() <= 1) {
            return new EqMatchPattern(equalsChecker, text);
        }

        if (text.startsWith("@@"))
            return new EqMatchPattern(equalsChecker, text.substring(1));

        if (text.charAt(0) == '@') {
            int pos = text.indexOf(':');
            if (pos > 0) {
                String patternName = text.substring(1, pos);
                String options = text.substring(pos + 1);
                SourceLocation patternLoc = SourceLocationHelper.offset(loc, pos);
                if (patternName.equals(NAME_PATTERN)) {
                    return parseFromText(patternLoc, options, config);
                }
                return requireCompiler(patternLoc, patternName, config).parseFromValue(patternLoc, options, config);
            }
        }
        return new EqMatchPattern(equalsChecker, text);
    }

    IMatchPatternCompiler requireCompiler(SourceLocation loc, String pattern, MatchPatternCompileConfig config) {
        IMatchPatternCompiler compiler = config.getRegistry().getCompiler(pattern);
        if (compiler == null)
            throw new NopException(ERR_MATCH_UNKNOWN_PATTERN).loc(loc).param(ARG_PATTERN, pattern);
        return compiler;
    }
}