package io.nop.record.serialization;

import io.nop.commons.collections.bit.IBitSet;
import io.nop.record.model.RecordFieldMeta;
import io.nop.record.model.RecordObjectMeta;
import io.nop.record.reader.IDataReaderBase;

import java.util.LinkedHashMap;
import java.util.Map;

public class StreamingStackFrame {
    // 对象级别的stage
    public static final int STAGE_INIT = 0;
    public static final int STAGE_BEFORE_READ = 1;
    public static final int STAGE_READ_TAGS = 2;
    public static final int STAGE_READ_FIELDS = 3;
    public static final int STAGE_AFTER_READ = 4;
    public static final int STAGE_COMPLETED = 5;

    // 字段级别的stage（用于当前正在处理的字段）
    public static final int FIELD_STAGE_BEFORE_READ = 10;
    public static final int FIELD_STAGE_READ_CONTENT = 11;
    public static final int FIELD_STAGE_AFTER_READ = 12;
    public static final int FIELD_STAGE_COMPLETED = 13;

    private RecordObjectMeta recordMeta;
    private Object currentRecord;

    // === 输入流管理 ===
    private IDataReaderBase originalIn;      // 原始输入流

    // 对象级别状态
    private int currentStage = STAGE_INIT;
    private IBitSet tags;
    private int fieldIndex = 0;
    private int templatePartIndex = 0;

    // 字段级别状态（当前正在处理的字段）
    private RecordFieldMeta currentField;
    private int fieldStage = FIELD_STAGE_BEFORE_READ;

    // 集合字段状态
    private int collectionSize = 0;
    private int collectionIndex = -1;

    // 其他状态
    private String rawDataString;

    private Map<String, Object> nonStreamingFields;

    public StreamingItem newStreamingItem() {
        StreamingItem item = new StreamingItem();
        item.setRecordTypeName(recordMeta.getName());
        if (this.nonStreamingFields != null)
            item.setNonStreamingFields(new LinkedHashMap<>(this.nonStreamingFields));
        item.setCollectionSize(collectionSize);
        item.setCollectionIndex(collectionIndex);
        return item;
    }

    public StreamingReadResult newEndOfObjectResult() {
        return StreamingReadResult.ofValue(newStreamingItem().endOfObject());
    }

    public StreamingReadResult newEndOfFieldResult() {
        StreamingItem item = newStreamingItem().endOfField();
        item.setStreamingField(currentField.getName());
        return StreamingReadResult.ofValue(item);
    }

    // ========== Getter/Setter ==========

    public RecordFieldMeta getCurrentField() {
        return currentField;
    }

    public void setCurrentField(RecordFieldMeta currentField) {
        this.currentField = currentField;
    }

    public boolean isFieldCompleted() {
        return fieldStage == FIELD_STAGE_COMPLETED;
    }

    public int getFieldStage() {
        return fieldStage;
    }

    public Object getNonStreamingField(String name) {
        return nonStreamingFields == null ? null : nonStreamingFields.get(name);
    }

    public void setNonStreamingFields(String name, Object value) {
        this.makeNonStreamingFields().put(name, value);
    }

    public Map<String, Object> makeNonStreamingFields() {
        if (nonStreamingFields == null)
            nonStreamingFields = new LinkedHashMap<>();
        return nonStreamingFields;
    }

    public RecordObjectMeta getRecordMeta() {
        return recordMeta;
    }

    public void setRecordMeta(RecordObjectMeta recordMeta) {
        this.recordMeta = recordMeta;
    }

    public Object getCurrentRecord() {
        return currentRecord;
    }

    public void setCurrentRecord(Object currentRecord) {
        this.currentRecord = currentRecord;
    }

    public IDataReaderBase getOriginalIn() {
        return originalIn;
    }

    public void setOriginalIn(IDataReaderBase originalIn) {
        this.originalIn = originalIn;
    }

    public String getRawDataString() {
        return rawDataString;
    }

    public void setRawDataString(String rawDataString) {
        this.rawDataString = rawDataString;
    }

    public IBitSet getTags() {
        return tags;
    }

    public void setTags(IBitSet tags) {
        this.tags = tags;
    }

    public int getCurrentStage() {
        return currentStage;
    }

    public void setCurrentStage(int currentStage) {
        this.currentStage = currentStage;
    }

    public int getFieldIndex() {
        return fieldIndex;
    }

    public void setFieldIndex(int fieldIndex) {
        this.fieldIndex = fieldIndex;
    }

    public int getTemplatePartIndex() {
        return templatePartIndex;
    }

    public void setTemplatePartIndex(int templatePartIndex) {
        this.templatePartIndex = templatePartIndex;
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

    // ========== 辅助方法 ==========

    public void incrementFieldIndex() {
        this.fieldIndex++;
    }

    public void incrementCollectionIndex() {
        this.collectionIndex++;
    }

    public void incrementTemplatePartIndex() {
        this.templatePartIndex++;
    }

    public boolean hasMoreFields() {
        return recordMeta != null && fieldIndex < recordMeta.getFields().size();
    }

    public boolean hasMoreCollectionItems() {
        return collectionSize > 0 && collectionIndex < collectionSize;
    }

    public boolean hasMoreTemplateParts(int totalParts) {
        return templatePartIndex < totalParts;
    }

    public boolean isCompleted() {
        return currentStage >= STAGE_COMPLETED;
    }

    public void moveToNextStage() {
        this.currentStage++;
    }

    public void setStage(int stage) {
        this.currentStage = stage;
    }

    public void moveToNextFieldStage() {
        if (fieldStage < FIELD_STAGE_COMPLETED) {
            fieldStage++;
        }
    }

    public void resetFieldState() {
        this.currentField = null;
        this.fieldStage = FIELD_STAGE_BEFORE_READ;
        this.collectionSize = 0;
        this.collectionIndex = -1;
    }

    /**
     * 重置字段处理状态（用于处理下一个字段）
     */
    public void resetForNextField() {
        this.currentField = null;
        this.fieldStage = FIELD_STAGE_BEFORE_READ;
        this.collectionSize = 0;
        this.collectionIndex = -1;
        this.templatePartIndex = 0;
    }

    /**
     * 完全重置frame状态
     */
    public void reset() {
        this.recordMeta = null;
        this.currentRecord = null;
        this.originalIn = null;
        this.tags = null;
        this.currentStage = STAGE_INIT;
        this.fieldIndex = 0;
        this.templatePartIndex = 0;
        resetForNextField();
    }

    @Override
    public String toString() {
        return "StreamingStackFrame{" +
                "stage=" + currentStage +
                ", recordMeta=" + (recordMeta != null ? recordMeta.getName() : "null") +
                ", fieldIndex=" + fieldIndex +
                ", collectionIndex=" + collectionIndex + "/" + collectionSize +
                '}';
    }
}