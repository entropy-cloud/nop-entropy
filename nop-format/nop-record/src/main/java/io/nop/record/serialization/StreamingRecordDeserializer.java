package io.nop.record.serialization;

import io.nop.api.core.convert.ConvertHelper;
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
import java.util.Map;

import static io.nop.record.RecordErrors.ARG_FIELD_NAME;
import static io.nop.record.RecordErrors.ARG_LENGTH;
import static io.nop.record.RecordErrors.ERR_RECORD_COLLECTION_SIZE_EXCEED_LIMIT;

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
            closeSubIn(frame);
            if (recordMeta.getRawVarName() != null && frame.getRawDataString() != null)
                e.param(recordMeta.getRawVarName(), frame.getRawDataString());
            throw e;
        } catch (IOException e) {
            closeSubIn(frame);
            throw e;
        }
    }

    private static void closeSubIn(StreamingStackFrame frame) {
        if (frame.getSubIn() != null) {
            try {
                frame.getSubIn().close();
            } catch (IOException e) {
                // ignore
            }
            frame.setSubIn(null);
        }
        if (frame.getCollSubIn() != null) {
            try {
                frame.getCollSubIn().close();
            } catch (IOException e) {
                // ignore
            }
            frame.setCollSubIn(null);
        }
    }

    private StreamingReadResult processObjectStreaming(StreamingStackFrame frame, Input in, RecordObjectMeta recordMeta,
                                                       IFieldCodecContext context) throws IOException {
        Object record = frame.getCurrentRecord();

        while (!frame.isCompleted()) {
            switch (frame.getCurrentStage()) {
                case StreamingStackFrame.STAGE_INIT:
                    // 初始化阶段
                    if (recordMeta.getReadWhen() != null) {
                        if (!ConvertHelper.toPrimitiveBoolean(recordMeta.getReadWhen().call3(null, in, record, context, context.getEvalScope())))
                            return null;
                    }

                    if (recordMeta.getBeforeRead() != null) {
                        recordMeta.getBeforeRead().call3(null, in, record, context, context.getEvalScope());
                    }

                    int length = deserializer.getObjectLength(in, recordMeta, record, context);
                    if (length > 0) {
                        frame.setSubBaseIn(in);
                        frame.setSubStartPos(in.pos());
                        frame.setSubLength(length);
                        in = (Input) in.subInput(length);
                        frame.setSubIn(in);

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
                        // 基类必须使用独立 frame：共享 frame 会被基类一路推进到 COMPLETED，
                        // 返回后派生类型的 READ_TAGS/READ_FIELDS 循环立即退出，派生字段全部丢失
                        RecordObjectMeta baseMeta = recordMeta.getResolvedBaseType();
                        StreamingStackFrame baseFrame = new StreamingStackFrame();
                        baseFrame.setRecordMeta(baseMeta);
                        baseFrame.setCurrentRecord(record);
                        baseFrame.setOriginalIn(in);
                        baseFrame.setCurrentStage(StreamingStackFrame.STAGE_BEFORE_READ);
                        baseFrame.setSuppressEndOfObject(true);
                        StreamingReadResult baseResult = processObjectStreaming(baseFrame, in, baseMeta, context);
                        if (baseResult != null) {
                            Input paramIn = in;
                            return baseResult.then(() -> {
                                try {
                                    copyNonStreamingFields(baseFrame, frame);
                                    frame.moveToNextStage();
                                    return processObjectStreaming(frame, paramIn, recordMeta, context);
                                } catch (IOException e) {
                                    throw NopException.adapt(e);
                                }
                            });
                        }
                        copyNonStreamingFields(baseFrame, frame);
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

        // 帧完成：区域残留对齐（R2-1），与 readObject 同公式
        if (frame.getSubBaseIn() != null) {
            long remaining = (frame.getSubStartPos() + frame.getSubLength()) - frame.getSubBaseIn().pos();
            if (remaining > 0)
                deserializer.readOffset((Input) frame.getSubBaseIn(), (int) remaining, context);
            frame.setSubBaseIn(null);
        }
        closeSubIn(frame);

        if (frame.isSuppressEndOfObject())
            return null;
        return frame.newEndOfObjectResult();
    }

    private static void copyNonStreamingFields(StreamingStackFrame from, StreamingStackFrame to) {
        Map<String, Object> fields = from.makeNonStreamingFields();
        if (!fields.isEmpty())
            fields.forEach(to::setNonStreamingFields);
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
                    if (field.getReadWhen() != null) {
                        if (!ConvertHelper.toPrimitiveBoolean(field.getReadWhen().call3(null, in, frame.makeNonStreamingFields(), context, context.getEvalScope()))) {
                            // readWhen=false：跳过整个字段（不消费字节、不触发 before/after 回调），与写侧 shouldIgnoreWrite 对称
                            frame.setFieldStage(StreamingStackFrame.FIELD_STAGE_COMPLETED);
                            break;
                        }
                    }
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
                if (length > 0) {
                    frame.setCollectionSize(-2); // -2 表示固定长度区域
                    frame.setCollSubBaseIn(in);
                    frame.setCollSubStartPos(in.pos());
                    frame.setCollSubLength(length);
                    in = (Input) in.subInput(length);
                    frame.setCollSubIn(in);
                } else {
                    // 与非流式 readCollection 一致：length<=0 时仅读取一个元素，否则会吞掉后续输入
                    frame.setCollectionSize(1);
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
                throw new NopException(ERR_RECORD_COLLECTION_SIZE_EXCEED_LIMIT)
                        .param(ARG_FIELD_NAME, field.getName()).param(ARG_LENGTH, frame.getCollectionIndex());

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

        // 关闭集合级subInput并对齐区域残留，与非流式readCollection的finally+对齐公式一致
        closeCollSubIn(frame, context);

        return null;
    }

    private void closeCollSubIn(StreamingStackFrame frame, IFieldCodecContext context) throws IOException {
        IDataReaderBase collSubIn = frame.getCollSubIn();
        if (collSubIn == null)
            return;

        IDataReaderBase baseIn = frame.getCollSubBaseIn();
        long subStartPos = frame.getCollSubStartPos();
        int subLength = frame.getCollSubLength();
        frame.setCollSubIn(null);
        frame.setCollSubBaseIn(null);

        try {
            collSubIn.close();
        } catch (IOException e) {
            // ignore
        }

        if (baseIn != null) {
            long remaining = (subStartPos + subLength) - baseIn.pos();
            if (remaining > 0)
                deserializer.readOffset((Input) baseIn, (int) remaining, context);
        }
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