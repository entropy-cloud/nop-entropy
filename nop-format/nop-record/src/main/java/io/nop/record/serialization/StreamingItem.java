package io.nop.record.serialization;

import io.nop.api.core.annotations.data.DataBean;

import java.util.Map;

/**
 * 流式解析返回的结果对象。attributes中保存非集合类型的普通字段。index记录集合中的下标。endOfField记录是否字段是否已经结束。
 */
@DataBean
public class StreamingItem {
    private String recordTypeName;
    private Map<String, Object> nonStreamingFields;
    private int collectionSize = -1;
    private int collectionIndex = -1;

    private String streamingField;
    private Object streamingData;

    // 表示streamingData是否包含数据。有时streamingData为null，难以区分是数据本身是null，还是已经endOfField，只是返回一个结束标识
    private boolean streamingDataAssigned;

    // 对于集合字段, endOfField为true时，collectionSize会返回集合总长度。
    private boolean endOfField;

    // 标记整个对象的数据都已经返回
    private boolean endOfObject;

    public StreamingItem endOfField() {
        this.setEndOfField(true);
        return this;
    }

    public StreamingItem endOfObject() {
        this.setEndOfObject(true);
        return this;
    }

    public String getRecordTypeName() {
        return recordTypeName;
    }

    public void setRecordTypeName(String recordTypeName) {
        this.recordTypeName = recordTypeName;
    }

    public Object getNonStreamingField(String name) {
        if (nonStreamingFields == null)
            return null;
        return nonStreamingFields.get(name);
    }

    public String getStreamingField() {
        return streamingField;
    }

    public void setStreamingField(String streamingField) {
        this.streamingField = streamingField;
    }

    public Object getStreamingData() {
        return streamingData;
    }

    public void setStreamingData(Object streamingData) {
        this.streamingData = streamingData;
    }

    public Map<String, Object> getNonStreamingFields() {
        return nonStreamingFields;
    }

    public void setNonStreamingFields(Map<String, Object> nonStreamingFields) {
        this.nonStreamingFields = nonStreamingFields;
    }

    public int getCollectionSize() {
        return collectionSize;
    }

    public void setCollectionSize(int collectionSize) {
        this.collectionSize = collectionSize;
    }

    public int getCollectionIndex() {
        return collectionIndex;
    }

    public void setCollectionIndex(int collectionIndex) {
        this.collectionIndex = collectionIndex;
    }

    public boolean isStreamingDataAssigned() {
        return streamingDataAssigned;
    }

    public void setStreamingDataAssigned(boolean streamingDataAssigned) {
        this.streamingDataAssigned = streamingDataAssigned;
    }

    public boolean isEndOfField() {
        return endOfField;
    }

    public void setEndOfField(boolean endOfField) {
        this.endOfField = endOfField;
    }

    public boolean isEndOfObject() {
        return endOfObject;
    }

    public void setEndOfObject(boolean endOfObject) {
        this.endOfObject = endOfObject;
    }
}