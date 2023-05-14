/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dataset.impl;

import io.nop.dataset.IDataRow;
import io.nop.dataset.IDataSet;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DataSetCacheHelper {
    /**
     * 读取DataSet的数据，保存为CacheData类型
     *
     * @param dataSet 数据集
     * @return 可以缓存的数据对象
     */
    public static DataSetCacheData toCacheData(IDataSet dataSet) {
        DataSetCacheData cacheData = new DataSetCacheData();
        cacheData.setMeta(BaseDataSetMeta.fromMeta(dataSet.getMeta()));

        List<Object[]> objects = dataSet.stream().map(IDataRow::getFieldValues).collect(Collectors.toList());
        cacheData.setRecords(objects);
        return cacheData;
    }

    /**
     * 根据缓存数据构造DataSet对象
     *
     * @param cacheData 缓存的数据
     */
    public static IDataSet toDataSet(DataSetCacheData cacheData, boolean readOnly) {
        BaseDataSetMeta meta = cacheData.getMeta();
        List<IDataRow> rows = new ArrayList<>(cacheData.getRecords().size());
        for (Object[] record : cacheData.getRecords()) {
            rows.add(new BaseDataRow(meta, readOnly, record));
        }
        BaseDataSet ds = new BaseDataSet(rows, meta);
        return ds;
    }
}
