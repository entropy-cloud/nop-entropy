package io.nop.record.serialization;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Symbol;
import io.nop.commons.text.SimpleTextTemplate;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.model.FieldRepeatKind;
import io.nop.record.model.RecordFieldMeta;
import io.nop.record.model.RecordObjectMeta;
import io.nop.record.model.RecordTypeMeta;
import io.nop.record.reader.IDataReaderBase;

import java.io.IOException;

public class StreamingRecordDeserializer<Input extends IDataReaderBase> {
    private final AbstractModelBasedRecordDeserializer<Input> deserializer;

    public StreamingRecordDeserializer(AbstractModelBasedRecordDeserializer<Input> deserializer) {
        this.deserializer = deserializer;
    }

    public StreamingReadResult readObjectStreaming(Input in, RecordObjectMeta recordMeta, Object record,
                                                   IFieldCodecContext context) throws IOException {
        if (!recordMeta.isAnyFieldSupportStreaming()) {
            if (!deserializer.readObject(in, recordMeta, record, context))
                return null;
            return StreamingReadResult.ofValue(record);
        }

        StreamingStackFrame frame = new StreamingStackFrame();
        frame.setRecordMeta(recordMeta);
        frame.setCurrentRecord(record);
        frame.setOriginalIn(in);
        frame.setCurrentStage(StreamingStackFrame.STAGE_INIT);

        try {
            return processObjectStreaming(frame, in, recordMeta, context);
        } catch (NopException e) {
            if (recordMeta.getRawVarName() != null && frame.getRawDataString() != null)
                e.param(recordMeta.getRawVarName(), frame.getRawDataString());
            throw e;
        }
    }

    private StreamingReadResult processObjectStreaming(StreamingStackFrame frame, Input in, RecordObjectMeta recordMeta,
                                                       IFieldCodecContext context) throws IOException {
        Object record = frame.getCurrentRecord();

        while (!frame.isCompleted()) {
            switch (frame.getCurrentStage()) {
                case StreamingStackFrame.STAGE_INIT:
                    // 初始化阶段
                    if (recordMeta.getBeforeRead() != null) {
                        recordMeta.getBeforeRead().call3(null, in, record, context, context.getEvalScope());
                    }

                    int length = deserializer.getObjectLength(in, recordMeta, record, context);
                    if (length > 0) {
                        in = (Input) in.subInput(length);

                        // 处理原始数据保存
                        if (recordMeta.getRawVarName() != null) {
                            String rawString = deserializer.getRawDataString(in, length);
                            frame.setRawDataString(rawString);
                            context.setValue(recordMeta.getRawVarName(), rawString);
                        }
                    }

                    frame.moveToNextStage();
                    break;

                case StreamingStackFrame.STAGE_BEFORE_READ:
                    // 读取前处理 - 处理基类
                    if (recordMeta.getResolvedBaseType() != null) {
                        // 递归处理基类
                        StreamingReadResult baseResult = processObjectStreaming(
                                frame, in,
                                recordMeta.getResolvedBaseType(),
                                context
                        );
                        // 需要继续处理基类
                        if (baseResult != null) {
                            Input paramIn = in;
                            return baseResult.then(() -> {
                                try {
                                    frame.moveToNextStage();
                                    return processObjectStreaming(frame, paramIn, recordMeta, context);
                                } catch (IOException e) {
                                    throw NopException.adapt(e);
                                }
                            });
                        }
                    }
                    frame.moveToNextStage();
                    break;

                case StreamingStackFrame.STAGE_READ_TAGS:
                    // 读取标签
                    frame.setTags(deserializer.readTags(in, recordMeta, context));
                    frame.moveToNextStage();
                    break;

                case StreamingStackFrame.STAGE_READ_FIELDS:
                    // 读取字段
                    StreamingReadResult fieldResult = processFieldsStreaming(frame, in, recordMeta, context);
                    if (fieldResult != null) {
                        // 返回字段处理结果
                        Input paramIn = in;
                        return fieldResult.then(() -> {
                            frame.moveToNextStage();
                            try {
                                return processObjectStreaming(frame, paramIn, recordMeta, context);
                            } catch (IOException e) {
                                throw NopException.adapt(e);
                            }
                        });
                    }
                    frame.moveToNextStage();
                    break;

                case StreamingStackFrame.STAGE_AFTER_READ:
                    // 读取后处理
                    if (recordMeta.getAfterRead() != null) {
                        recordMeta.getAfterRead().call3(null, in, frame.makeNonStreamingFields(), context, context.getEvalScope());
                    }
                    frame.moveToNextStage();
                    break;
                default:
                    frame.moveToNextStage();
                    break;
            }
        }

        return frame.newEndOfObjectResult();
    }

    private StreamingReadResult processFieldsStreaming(StreamingStackFrame frame, Input in,
                                                       RecordObjectMeta recordMeta, IFieldCodecContext context) throws IOException {
        SimpleTextTemplate template = recordMeta.getNormalizedTemplate();

        if (template != null) {
            // 处理模板字段
            while (frame.hasMoreTemplateParts(template.getParts().size())) {
                Object part = template.getParts().get(frame.getTemplatePartIndex());

                if (part instanceof Symbol) {
                    String name = ((Symbol) part).getText();
                    RecordFieldMeta field = recordMeta.requireField(name);
                    frame.setCurrentField(field);

                    if (field.isMatchTag(frame.getTags())) {
                        // 处理字段 - 如果是流式模式，直接返回字段结果
                        if (field.isSupportStreaming()) {
                            StreamingReadResult fieldResult = processFieldStreaming(frame, in, context);
                            if (fieldResult != null) {
                                Input paramIn = in;
                                return fieldResult.then(() -> {
                                    try {
                                        frame.incrementTemplatePartIndex();
                                        return processFieldsStreaming(frame, paramIn, recordMeta, context);
                                    } catch (IOException e) {
                                        throw NopException.adapt(e);
                                    }
                                });
                            }
                        } else {
                            // 非流式模式，正常处理。字段数据读取到nonStreamingFields集合中
                            deserializer.readField(in, field, frame.makeNonStreamingFields(), context);
                        }
                    }
                } else {
                    // 处理静态文本
                    deserializer.readString(in, part.toString(), recordMeta.getCharsetObj(), context);
                }

                frame.incrementTemplatePartIndex();
            }
        } else {
            while (frame.hasMoreFields()) {
                // 处理普通字段列表
                RecordFieldMeta field = recordMeta.getFields().get(frame.getFieldIndex());
                frame.setCurrentField(field);

                if (field.isMatchTag(frame.getTags())) {
                    // 处理字段 - 如果是流式模式，直接返回字段结果
                    if (field.isSupportStreaming()) {
                        StreamingReadResult fieldResult = processFieldStreaming(frame, in, context);
                        if (fieldResult != null) {
                            Input paramIn = in;
                            return fieldResult.then(() -> {
                                try {
                                    frame.incrementFieldIndex();
                                    return processFieldsStreaming(frame, paramIn, recordMeta, context);
                                } catch (IOException e) {
                                    throw NopException.adapt(e);
                                }
                            });
                        }
                    } else {
                        // 非流式模式，正常处理
                        deserializer.readField(in, field, frame.makeNonStreamingFields(), context);
                    }
                }

                frame.incrementFieldIndex();
            }
        }

        // 所有字段处理完成
        return null;
    }

    private StreamingReadResult processFieldStreaming(StreamingStackFrame frame, Input in, IFieldCodecContext context) throws IOException {
        RecordFieldMeta field = frame.getCurrentField();

        while (!frame.isFieldCompleted()) {
            switch (frame.getFieldStage()) {
                case StreamingStackFrame.FIELD_STAGE_BEFORE_READ:
                    // 字段前处理
                    if (field.getOffset() > 0) {
                        deserializer.readOffset(in, field.getOffset(), context);
                    }
                    context.enterField(field);
                    if (field.getBeforeRead() != null) {
                        field.getBeforeRead().call3(null, in, frame.makeNonStreamingFields(), context, context.getEvalScope());
                    }
                    frame.moveToNextFieldStage();
                    break;

                case StreamingStackFrame.FIELD_STAGE_READ_CONTENT:
                    // 字段内容读取
                    StreamingReadResult contentResult;
                    if (field.getRepeatKind() != null) {
                        if (field.getCodec() != null) {
                            contentResult = processCollectionWithCodecStreaming(frame, in, context);
                        } else {
                            contentResult = processCollectionStreaming(frame, in, context);
                        }
                    } else {
                        contentResult = processSwitchFieldStreaming(frame, in, context);
                    }

                    if (contentResult != null) {
                        return contentResult.then(() -> {
                            try {
                                frame.moveToNextFieldStage();
                                return processFieldStreaming(frame, in, context);
                            } catch (IOException e) {
                                throw NopException.adapt(e);
                            }
                        });
                    }
                    // 如果返回null，继续下一个field stage
                    frame.moveToNextFieldStage();
                    break;

                case StreamingStackFrame.FIELD_STAGE_AFTER_READ:
                    // 字段后处理
                    if (field.getAfterRead() != null) {
                        field.getAfterRead().call3(null, in, frame.makeNonStreamingFields(), context, context.getEvalScope());
                    }
                    context.exitField(field);
                    frame.moveToNextFieldStage();
                    break;

                default:
                    break;
            }
        }

        StreamingReadResult result = frame.newEndOfFieldResult();
        frame.resetFieldState();
        return result;
    }

    private StreamingReadResult processCollectionStreaming(StreamingStackFrame frame, Input in,
                                                           IFieldCodecContext context) throws IOException {
        RecordFieldMeta field = frame.getCurrentField();

        // 初始化集合状态
        if (frame.getCollectionIndex() == -1) {
            frame.setCollectionIndex(0);

            // 确定集合大小
            IEvalFunction repeatUntil = field.getRepeatUntil();
            if (repeatUntil != null) {
                frame.setCollectionSize(-1); // -1 表示直到条件满足
            } else if (field.getRepeatKind() == FieldRepeatKind.fixed) {
                int length = deserializer.getFieldLength(in, field, frame.makeNonStreamingFields(), context);
                frame.setCollectionSize(-2); // -2 表示固定长度
                if (length > 0) {
                    in = (Input) in.subInput(length);
                }
            } else {
                int count = deserializer.readRepeatCount(in, field, frame.makeNonStreamingFields(), context);
                if (count <= 0) {
                    return null;
                }
                frame.setCollectionSize(count);
            }
        }

        // 检查是否还有更多集合项
        do {
            boolean hasMoreItems = false;
            if (frame.getCollectionSize() > 0) {
                hasMoreItems = frame.getCollectionIndex() < frame.getCollectionSize();
            } else if (frame.getCollectionSize() == -1) {
                hasMoreItems = !deserializer.checkUntil(field.getRepeatUntil(), in, frame.makeNonStreamingFields(), context);
            } else if (frame.getCollectionSize() == -2) {
                hasMoreItems = !in.isEof();
            }

            if (!hasMoreItems)
                break;

            if (frame.getCollectionIndex() >= field.getMaxCollectionSize())
                throw new IllegalStateException("collection size exceed limit:field=" + field.getName() + ",count=" + frame.getCollectionIndex());

            // 直接处理集合项的内容，不改变field stage
            StreamingReadResult itemResult = processSwitchFieldStreaming(frame, in, context);

            if (itemResult != null) {
                Input paramIn = in;
                return itemResult.then(() -> {
                    try {
                        frame.incrementCollectionIndex();
                        // 继续处理下一个集合项
                        return processCollectionStreaming(frame, paramIn, context);
                    } catch (IOException e) {
                        throw NopException.adapt(e);
                    }
                });
            } else {
                frame.incrementCollectionIndex();
            }
        } while (true);

        frame.setCollectionSize(frame.getCollectionIndex());
        frame.setCollectionIndex(-1);

        return null;
    }

    private StreamingReadResult processCollectionWithCodecStreaming(StreamingStackFrame frame, Input in,
                                                                    IFieldCodecContext context) throws IOException {
        // 暂时不支持collection codec的情况下的流式处理
        deserializer.readCollectionWithCodec(in, frame.getCurrentField(), frame.makeNonStreamingFields(), context);
        return null;
    }

    private StreamingReadResult processSwitchFieldStreaming(StreamingStackFrame frame, Input in,
                                                            IFieldCodecContext context) throws IOException {
        RecordFieldMeta field = frame.getCurrentField();
        Object record = frame.getCurrentRecord();

        // 确定对象类型
        RecordTypeMeta typeMeta = deserializer.determineObjectType(in, field, record, context);
        if (typeMeta != null) {
            // 对于流式模式，如果确定是对象类型，递归处理
            Object obj = deserializer.makeObject(field, typeMeta, record, context);
            StreamingReadResult objResult = readObjectStreaming(in, typeMeta, obj, context);
            if (objResult != null)
                return objResult.map(value -> {
                    StreamingItem item = frame.newStreamingItem();
                    item.setStreamingField(field.getName());
                    item.setStreamingData(value);
                    item.setStreamingDataAssigned(true);
                    item.setCollectionIndex(frame.getCollectionIndex());
                    item.setCollectionSize(frame.getCollectionSize());
                    return item;
                });
            return objResult;
        }

        // 处理简单字段
        Object value = deserializer.readField0(in, field, record, context);

        // 验证和设置字段值
        deserializer.validate(value, field, in, context);
        if (field.getVarName() != null) {
            context.setValue(field.getVarName(), value);
        }

        if (!field.isVirtual()) {
            deserializer.setPropByName(record, field.getPropOrFieldName(), value);
        }

        StreamingItem item = frame.newStreamingItem();
        item.setStreamingField(field.getName());
        item.setStreamingData(value);
        item.setStreamingDataAssigned(true);
        item.setCollectionIndex(frame.getCollectionIndex());
        item.setCollectionSize(frame.getCollectionSize());
        return StreamingReadResult.ofValue(item);
    }
}