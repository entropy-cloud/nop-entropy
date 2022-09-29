package io.nop.match.pattern;

import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.type.StdDataType;
import io.nop.core.lang.json.utils.SourceLocationHelper;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.core.reflect.bean.IBeanPropertyModel;
import io.nop.match.IMatchPattern;
import io.nop.match.MatchConstants;
import io.nop.match.MatchState;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static io.nop.match.MatchErrors.ARG_ALLOWED_NAMES;
import static io.nop.match.MatchErrors.ERR_MATCH_FIELD_NOT_EXISTS;
import static io.nop.match.MatchErrors.ERR_MATCH_FIELD_NOT_OBJECT;
import static io.nop.match.MatchErrors.ERR_MATCH_OBJECT_IS_NULL;

public class MapMatchPattern implements IMatchPattern {
    private final Map<String, IMatchPattern> patterns;
    private final IMatchPattern extPropPattern;

    public MapMatchPattern(Map<String, IMatchPattern> patterns, IMatchPattern extPropPattern) {
        this.patterns = Guard.notNull(patterns, "patterns");
        this.extPropPattern = extPropPattern;
    }

    @Override
    public Object toJson() {
        Map<String, Object> ret = new LinkedHashMap<>();
        for (Map.Entry<String, IMatchPattern> entry : patterns.entrySet()) {
            ret.put(entry.getKey(), entry.getValue().toJson());
        }
        if (extPropPattern != null) {
            ret.put(MatchConstants.MATCH_ALL_PATTERN, extPropPattern.toJson());
        }
        return ret;
    }

    @Override
    public boolean matchValue(MatchState state, boolean collectError) {
        Object value = state.getValue();
        if (value == null) {
            if (collectError) {
                state.buildError(ERR_MATCH_OBJECT_IS_NULL)
                        .addToCollector(state.getErrorCollector());
            }
            return false;
        }

        if (StdDataType.fromJavaClass(value.getClass()).isSimpleType()
                || value instanceof Collection<?>) {
            if (collectError) {
                state.buildError(ERR_MATCH_FIELD_NOT_OBJECT)
                        .addToCollector(state.getErrorCollector());
            }
            return false;
        }

        Object parent = state.getParent();
        try {
            if (value instanceof Map) {
                return matchMap((Map<String, Object>) value, state, collectError);
            } else {
                return matchBean(state.getValue(), state, collectError);
            }
        } finally {
            state.setParent(parent);
        }
    }

    boolean matchMap(Map<String, Object> map, MatchState state, boolean collectError) {
        boolean matched = true;
        for (Map.Entry<String, IMatchPattern> entry : patterns.entrySet()) {
            String name = entry.getKey();
            IMatchPattern pattern = entry.getValue();

            Object value = map.get(name);

            state.enter(name);
            state.setParent(map);
            state.setValue(value);
            state.setLocation(getLocation(map, name));

            try {
                if (!map.containsKey(name)) {
                    state.buildError(ERR_MATCH_FIELD_NOT_EXISTS)
                            .addToCollector(state.getErrorCollector());
                    continue;
                }

                if (!pattern.matchValue(state, collectError)) {
                    matched = false;
                }
            } finally {
                state.leave();
            }
        }

        if (extPropPattern == AlwaysTrueMatchPattern.INSTANCE)
            return matched;

        if (!state.isIgnoreUnknown() || extPropPattern != null) {
            if (map.size() > patterns.size()) {
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    String name = entry.getKey();
                    if (patterns.containsKey(name))
                        continue;

                    Object value = entry.getValue();
                    state.enter(name);
                    state.setParent(map);
                    state.setValue(value);
                    state.setLocation(getLocation(map, name));

                    try {
                        if (extPropPattern != null) {
                            if (!extPropPattern.matchValue(state, collectError))
                                matched = false;
                        } else {
                            matched = false;
                            if (collectError) {
                                state.buildError(ERR_MATCH_FIELD_NOT_EXISTS)
                                        .param(ARG_ALLOWED_NAMES, map.keySet())
                                        .addToCollector(state.getErrorCollector());
                            }
                        }
                    } finally {
                        state.leave();
                    }
                }
            }
        }
        return matched;
    }

    SourceLocation getLocation(Map<String, Object> map, String name) {
        return SourceLocationHelper.getPropLocation(map, name);
    }


    boolean matchBean(Object bean, MatchState state, boolean collectError) {
        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(bean.getClass());

        SourceLocation loc = getLocation(bean);

        boolean matched = true;
        for (Map.Entry<String, IMatchPattern> entry : patterns.entrySet()) {
            String name = entry.getKey();
            IMatchPattern pattern = entry.getValue();
            IBeanPropertyModel propModel = beanModel.getPropertyModel(name);

            state.enter(name);
            state.setParent(bean);
            state.setLocation(loc);

            try {
                Object value = null;
                if (propModel != null) {
                    value = propModel.getPropertyValue(bean);
                    state.setValue(value);
                } else if (beanModel.isAllowExtProperty(bean, name)) {
                    value = beanModel.getExtProperty(bean, name);
                    state.setValue(value);
                } else {
                    if (collectError) {
                        state.buildError(ERR_MATCH_FIELD_NOT_EXISTS)
                                .param(ARG_ALLOWED_NAMES, beanModel.getPropertyModels().keySet())
                                .addToCollector(state.getErrorCollector());
                    }
                    continue;
                }

                if (!pattern.matchValue(state, collectError))
                    matched = false;
            } finally {
                state.leave();
            }
        }

        if (extPropPattern == AlwaysTrueMatchPattern.INSTANCE)
            return matched;

        if (!state.isIgnoreUnknown() || extPropPattern != null) {
            Set<String> propNames = beanModel.getExtPropertyNames(bean);
            if (propNames != null) {
                for (String name : propNames) {
                    if (patterns.containsKey(name))
                        continue;

                    Object value = beanModel.getExtProperty(bean, name);
                    state.enter(name);
                    state.setParent(bean);
                    state.setValue(value);
                    state.setLocation(loc);

                    try {
                        if (extPropPattern != null) {
                            if (!extPropPattern.matchValue(state, collectError))
                                matched = false;
                        } else {
                            matched = false;
                            if (collectError) {
                                state.buildError(ERR_MATCH_FIELD_NOT_EXISTS)
                                        .param(ARG_ALLOWED_NAMES, beanModel.getPropertyModels().keySet())
                                        .addToCollector(state.getErrorCollector());
                            }
                        }
                    } finally {
                        state.leave();
                    }
                }
            }
        }
        return matched;
    }

    SourceLocation getLocation(Object bean) {
        return SourceLocationHelper.getBeanLocation(bean);
    }
}
