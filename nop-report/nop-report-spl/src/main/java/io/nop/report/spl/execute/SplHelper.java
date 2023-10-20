/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.report.spl.execute;

import com.scudata.array.IArray;
import com.scudata.cellset.datamodel.PgmCellSet;
import com.scudata.common.StringUtils;
import com.scudata.dm.DataStruct;
import com.scudata.dm.IComputeItem;
import com.scudata.dm.IResource;
import com.scudata.dm.Param;
import com.scudata.dm.ParamList;
import com.scudata.dm.Sequence;
import com.scudata.dm.cursor.ICursor;
import com.scudata.util.CellSetUtil;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdDataType;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.nop.report.spl.SplErrors.ARG_RESULT_TYPE;
import static io.nop.report.spl.SplErrors.ERR_XPT_SPL_RESULT_NOT_CURSOR;

public class SplHelper {
    public static void closeResource(Object o) {
        if (o instanceof IResource)
            ((IResource) o).close();
    }

    public static PgmCellSet spl2CellSet(String spl) {
        PgmCellSet cellSet;
        if (!StringUtils.isValidString(spl)) {
            return null;
        } else {
            cellSet = CellSetUtil.toPgmCellSet(spl);
        }
        if (cellSet != null) {
            ParamList pl = cellSet.getParamList();
            if (pl != null) {
                for (int i = 0; i < pl.count(); i++) {
                    Param p = pl.get(i);
                    if (p != null) {
                        if (p.getValue() != null && p.getEditValue() == null) {
                            p.setEditValue(p.getValue());
                        }
                    }
                }
            }
        }
        return cellSet;
    }

    public static String cellSetToSpl(PgmCellSet cellSet) {
        return CellSetUtil.toString(cellSet);
    }

    public static Object normalizeResult(Object result) {
        return normalizeResult(result, 100);
    }

    public static Object normalizeResult(Object result, int fetchSize) {
        if (StringHelper.isEmptyObject(result))
            return Collections.emptyList();

        if (result instanceof Sequence) {
            return getSequenceResult((Sequence) result);
        } else if (result instanceof ICursor) {
            return getCursorResults((ICursor) result, fetchSize);
        } else if (StdDataType.isSimpleType(result.getClass().getName())) {
            return result;
        } else {
            throw new NopException(ERR_XPT_SPL_RESULT_NOT_CURSOR)
                    .param(ARG_RESULT_TYPE, result.getClass().getName());
        }
    }

    public static List<Object> getSequenceResult(Sequence seq) {
        int n = seq.length();
        List<Object> ret = new ArrayList<>(n);
        collectSequenceResult(seq, ret);
        return ret;
    }

    public static void collectSequenceResult(Sequence seq, List<Object> ret) {
        DataStruct ds = seq.getFirstRecordDataStruct();
        int n = seq.length();
        IArray data = seq.getMems();

        if (ds == null) {
            for (int i = 1; i <= n; i++) {
                Object value = data.get(i);
                ret.add(value);
            }
        } else {
            for (int i = 1; i <= n; i++) {
                Map<String, Object> record = buildRecord(data.get(i), ds);
                ret.add(record);
            }
        }
    }

    static Map<String, Object> buildRecord(Object value, DataStruct ds) {
        if (value instanceof IComputeItem) {
            IComputeItem item = (IComputeItem) value;
            String[] fieldNames = ds.getFieldNames();
            int n = fieldNames.length;
            Map<String, Object> map = CollectionHelper.newLinkedHashMap(n);
            for (int i = 0; i < n; i++) {
                String name = fieldNames[i];
                Object v = item.getFieldValue(i);
                map.put(name, v);
            }
            return map;
        } else {
            return null;
        }
    }

    public static List<Object> getCursorResults(ICursor cursor, int fetchSize) {
        List<Object> ret = new ArrayList<>();
        while (true) {
            Sequence seq = cursor.fetch(fetchSize);
            if (seq == null || seq.length() == 0)
                break;
            collectSequenceResult(seq, ret);
        }
        return ret;
    }
}
