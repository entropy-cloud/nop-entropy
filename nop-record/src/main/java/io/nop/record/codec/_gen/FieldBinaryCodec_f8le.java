
package io.nop.record.codec._gen;

import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldBinaryEncoder;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.reader.IBinaryDataReader;
import io.nop.record.writer.IBinaryDataWriter;

import java.nio.charset.Charset;
import java.io.IOException;

public class FieldBinaryCodec_f8le implements IFieldBinaryCodec{
    public static final FieldBinaryCodec_f8le INSTANCE = new FieldBinaryCodec_f8le();

    public Object decode(IBinaryDataReader input, Object record, int length, Charset charset,
                        IFieldCodecContext context) throws IOException{
        return input.readF8le();
    }

    public void encode(IBinaryDataWriter output, Object value, int length, Charset charset,
        IFieldCodecContext context, IFieldBinaryEncoder bodyEncoder) throws IOException{
        if(value == null){
            output.writeF8le(0.0);
        }else{
            output.writeF8le((Double)value);
        }
    }
}
