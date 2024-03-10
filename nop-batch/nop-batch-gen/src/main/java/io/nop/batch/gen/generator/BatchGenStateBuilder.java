/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.gen.generator;

import io.nop.batch.gen.model.BatchGenCaseModel;
import io.nop.commons.util.CollectionHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BatchGenStateBuilder {

    public static List<BatchGenState> buildSeqStates(List<BatchGenCaseModel> subCases, long totalCount) {
        if (subCases == null || subCases.isEmpty())
            return Collections.emptyList();

        List<BatchGenState> ret = new ArrayList<>(subCases.size());

        for (int i = 0, n = subCases.size(); i < n; i++) {
            BatchGenCaseModel subCase = subCases.get(i);
            BatchGenState subState = new BatchGenState(subCase, totalCount);
            ret.add(subState);
        }

        return ret;
    }

    public static List<BatchGenState> buildSubStates(List<BatchGenCaseModel> subCases, long totalCount) {
        if (subCases == null || subCases.isEmpty())
            return Collections.emptyList();

        List<BatchGenState> ret = new ArrayList<>(subCases.size());

        double totalWeight = CollectionHelper.sumDouble(subCases, BatchGenCaseModel::getWeight);

        long count = 0;
        for (int i = 0, n = subCases.size() - 1; i < n; i++) {
            BatchGenCaseModel subCase = subCases.get(i);
            long subCount = (long) (totalCount * subCase.getWeight() / totalWeight);
            count += subCount;
            BatchGenState subState = new BatchGenState(subCase, subCount);
            ret.add(subState);
        }
        long subCount = totalCount - count;
        ret.add(new BatchGenState(subCases.get(subCases.size() - 1), subCount));
        return ret;
    }
}