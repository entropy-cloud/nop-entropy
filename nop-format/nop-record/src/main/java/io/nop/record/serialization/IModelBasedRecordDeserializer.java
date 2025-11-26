package io.nop.record.serialization;

import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.model.RecordFieldMeta;
import io.nop.record.model.RecordObjectMeta;
import io.nop.record.model.RecordSimpleFieldMeta;
import io.nop.record.reader.IDataReaderBase;

import java.io.IOException;

public interface IModelBasedRecordDeserializer<Input extends IDataReaderBase> {
    boolean readObject(Input in, RecordObjectMeta recordMeta, Object record,
                       IFieldCodecContext context) throws IOException;

    boolean readField(Input in, RecordFieldMeta field, Object record, IFieldCodecContext context) throws IOException;

    Object readSimpleField(Input in, RecordSimpleFieldMeta field, Object record, IFieldCodecContext context) throws IOException;

    /**
     * 流式读取对象
     * 当 recordMeta.isSupportStreaming() 为 true 时使用
     *
     * @param in         输入数据读取器
     * @param recordMeta 记录对象元数据
     * @param record     目标记录对象
     * @param context    字段编解码上下文
     * @return 流式读取结果，可迭代遍历字段值
     * @throws IOException 读取异常
     */
    StreamingReadResult readObjectStreaming(Input in, RecordObjectMeta recordMeta, Object record,
                                            IFieldCodecContext context) throws IOException;

}
