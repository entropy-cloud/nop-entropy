/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.stream.cep.model.builder;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.ClassHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.stream.cep.model.*;
import io.nop.stream.cep.nfa.aftermatch.AfterMatchSkipStrategy;
import io.nop.stream.cep.pattern.Pattern;
import io.nop.stream.cep.pattern.WithinType;
import io.nop.stream.cep.pattern.conditions.IterativeCondition;

import java.util.HashSet;
import java.util.Set;

import static io.nop.stream.cep.NopCepErrors.*;

public class CepPatternBuilder {

    public Pattern buildFromModel(CepPatternModel patternModel) {
        Pattern pattern = buildGroupPattern(patternModel);
        if (patternModel.getWithin() != null)
            pattern = pattern.within(patternModel.getWithin());
        if (patternModel.getGapWithin() != null)
            pattern = pattern.within(patternModel.getGapWithin(), WithinType.PREVIOUS_AND_CURRENT);
        return pattern;
    }

    private Pattern buildGroupPattern(ICepPatternGroupModel groupModel) {
        String start = groupModel.getStart();
        CepPatternPartModel partModel = groupModel.requirePart(start);

        Pattern pattern;
        if (partModel instanceof CepPatternSingleModel) {
            pattern = Pattern.begin(start, getAfterMatchSkipStrategy(groupModel));
            pattern = buildSinglePattern(pattern, (CepPatternSingleModel) partModel);
        } else {
            pattern = buildGroupPattern((ICepPatternGroupModel) partModel);
            pattern = Pattern.begin(pattern, getAfterMatchSkipStrategy(groupModel));
        }
        Set<String> previous = new HashSet<>();
        previous.add(start);

        do {
            pattern = addQualifier(pattern, partModel);

            String next = partModel.getNext();
            if (StringHelper.isEmpty(next))
                break;

            if (!previous.add(next))
                throw new NopException(ERR_CEP_PATTERN_PART_NOT_ALLOW_LOOP)
                        .source(partModel).param(ARG_PART_NAME, next)
                        .param(ARG_NEXT, next);

            CepPatternPartModel nextModel = groupModel.requirePart(next);
            FollowKind followKind = partModel.getFollowKind();

            if (nextModel instanceof CepPatternPartModel) {
                pattern = buildFollow(pattern, followKind, nextModel.getName());
            } else {
                pattern = buildFollowGroup(pattern, followKind, (CepPatternGroupModel) nextModel);
            }

            partModel = nextModel;
        } while (true);

        return pattern;
    }

    private Pattern buildFollowGroup(Pattern pattern, FollowKind followKind, CepPatternGroupModel groupModel) {
        if (followKind == null)
            followKind = FollowKind.next;
        Pattern groupPattern = buildGroupPattern(groupModel);
        switch (followKind) {
            case next:
                pattern = pattern.next(groupPattern);
                break;
            case followedBy:
                pattern = pattern.followedBy(groupPattern);
                break;
            case followedByAny:
                pattern = pattern.followedByAny(groupPattern);
                break;
            default:
                throw new NopException(ERR_CEP_NOT_CONDITION_DOES_NOT_SUPPORT_GROUP)
                        .source(groupModel).param(ARG_PART_NAME, groupModel.getName())
                        .param(ARG_FOLLOW_KIND, followKind);
        }
        return pattern;
    }

    private Pattern buildFollow(Pattern pattern, FollowKind followKind, String nextName) {
        if (followKind == null)
            followKind = FollowKind.next;
        switch (followKind) {
            case next:
                pattern = pattern.next(nextName);
                break;
            case followedBy:
                pattern = pattern.followedBy(nextName);
                break;
            case followedByAny:
                pattern = pattern.followedByAny(nextName);
                break;
            case notNext:
                pattern = pattern.notNext(nextName);
                break;
            case notFollowedBy:
                pattern = pattern.notFollowedBy(nextName);
                break;
        }
        return pattern;
    }

    private Pattern buildSinglePattern(Pattern pattern, CepPatternSingleModel singleModel) {
        if (singleModel.getWhere() != null) {
            pattern = pattern.where(buildCondition(singleModel.getWhere()));
        }
        if (singleModel.getUntil() != null) {
            pattern = pattern.until(buildCondition(singleModel.getUntil()));
        }
        return pattern;
    }

    private IterativeCondition buildCondition(IEvalFunction action) {
        return new IterativeCondition() {
            @Override
            public boolean filter(Object value, Context ctx) {
                return ConvertHelper.toTruthy(action.call2(null, value, ctx, null));
            }
        };
    }

    private Pattern addQualifier(Pattern pattern, CepPatternPartModel partModel) {
        if (partModel.getSubType() != null) {
            try {
                pattern.subtype(ClassHelper.safeLoadClass(partModel.getSubType()));
            } catch (Exception e) {
                throw NopException.adapt(e);
            }
        }

        if (partModel.isOneOrMore()) {
            pattern = pattern.oneOrMore(partModel.getWindowTime());
        }

        if (partModel.getTimes() != null) {
            pattern = pattern.times(partModel.getTimes().getBegin(), partModel.getTimes().getLast(), partModel.getWindowTime());
        }

        if (partModel.getTimesOrMore() != null) {
            pattern = pattern.timesOrMore(partModel.getTimesOrMore(), partModel.getWindowTime());
        }

        if (partModel.isConsecutive()) {
            pattern = pattern.consecutive();
        }

        if (partModel.isAllowCombinations()) {
            pattern = pattern.allowCombinations();
        }

        if (partModel.isGreedy()) {
            pattern = pattern.greedy();
        }

        if (partModel.isOptional()) {
            pattern = pattern.optional();
        }

        return pattern;
    }

    private AfterMatchSkipStrategy getAfterMatchSkipStrategy(ICepPatternGroupModel partModel) {
        if (partModel.getAfterMatchSkipStrategy() == null)
            return AfterMatchSkipStrategy.noSkip();

        AfterMatchSkipStrategy strategy = AfterMatchSkipStrategy.noSkip();
        switch (partModel.getAfterMatchSkipStrategy()) {
            case NO_SKIP: {
                strategy = AfterMatchSkipStrategy.noSkip();
                break;
            }
            case SKIP_TO_NEXT: {
                strategy = AfterMatchSkipStrategy.skipToNext();
                break;
            }
            case SKIP_PAST_LAST_EVENT: {
                strategy = AfterMatchSkipStrategy.skipPastLastEvent();
                break;
            }
            case SKIP_TO_FIRST: {
                strategy = AfterMatchSkipStrategy.skipToFirst(partModel.getAfterMatchSkipTo());
                break;
            }
            case SKIP_TO_LAST: {
                strategy = AfterMatchSkipStrategy.skipToLast(partModel.getAfterMatchSkipTo());
                break;
            }
        }
        return strategy;
    }
}