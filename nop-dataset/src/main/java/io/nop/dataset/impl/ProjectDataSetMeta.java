package io.nop.dataset.impl;

import io.nop.commons.type.StdDataType;
import io.nop.commons.util.ArrayHelper;
import io.nop.dataset.IDataFieldMeta;
import io.nop.dataset.IDataSetMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ProjectDataSetMeta implements IDataSetMeta {
    private final IDataSetMeta source;

    private final List<String> fields;

    private final int[] fieldIndexes;

    private List<IDataFieldMeta> fieldMetas;

    public ProjectDataSetMeta(IDataSetMeta source, List<String> fields) {
        this.source = source;
        this.fields = fields;
        this.fieldIndexes = buildFieldIndexes(source, fields);
    }

    static int[] buildFieldIndexes(IDataSetMeta source, List<String> fields) {
        int[] indexes = new int[fields.size()];
        for (int i = 0; i < fields.size(); i++) {
            indexes[i] = source.getFieldIndex(fields.get(i));
        }
        return indexes;
    }

    @Override
    public boolean isCaseSensitive() {
        return source.isCaseSensitive();
    }

    @Override
    public int getFieldCount() {
        return fields.size();
    }

    @Override
    public String getFieldName(int index) {
        return source.getFieldName(fieldIndexes[index]);
    }

    @Override
    public String getFieldOwnerEntityName(int index) {
        return source.getFieldOwnerEntityName(fieldIndexes[index]);
    }

    @Override
    public String getSourceFieldName(int index) {
        return source.getSourceFieldName(fieldIndexes[index]);
    }

    @Override
    public int getFieldIndex(String colName) {
        return ArrayHelper.indexOf(fieldIndexes, source.getFieldIndex(colName));
    }

    @Override
    public boolean hasField(String name) {
        return source.hasField(name);
    }

    @Override
    public StdDataType getFieldStdType(int index) {
        return source.getFieldStdType(fieldIndexes[index]);
    }

    @Override
    public List<? extends IDataFieldMeta> getFieldMetas() {
        if (fieldMetas != null) {
            return fieldMetas;
        }

        List<? extends IDataFieldMeta> sourceMeta = source.getFieldMetas();
        List<IDataFieldMeta> list = new ArrayList<>(fields.size());
        for (int i = 0, n = fieldIndexes.length; i < n; i++) {
            list.add(sourceMeta.get(fieldIndexes[i]));
        }
        this.fieldMetas = list;
        return list;
    }

    @Override
    public IDataSetMeta project(List<String> fields) {
        return new ProjectDataSetMeta(source, fields);
    }

    @Override
    public List<String> getHeaders() {
        return fields;
    }

    @Override
    public Map<String, Object> getHeaderMeta() {
        return source.getHeaderMeta();
    }

    @Override
    public Object getHeaderMeta(String name) {
        return source.getHeaderMeta(name);
    }

    @Override
    public Map<String, Object> getTrailerMeta() {
        return source.getTrailerMeta();
    }

    @Override
    public BaseDataSetMeta toBaseDataSetMeta() {
        return new BaseDataSetMeta(this);
    }

    @Override
    public IDataSetMeta projectWithRename(Map<String, String> new2old) {
        List<String> fields = new ArrayList<>(new2old.keySet());
        return new ProjectDataSetMeta(source.projectWithRename(new2old), fields);
    }
}
