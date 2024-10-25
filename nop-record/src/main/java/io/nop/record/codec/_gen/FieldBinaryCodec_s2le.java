
package io.nop.record.codec._gen;

import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldBinaryEncoder;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.reader.IBinaryDataReader;
import io.nop.record.writer.IBinaryDataWriter;

import java.nio.charset.Charset;
import java.io.IOException;

public class FieldBinaryCodec_s2le implements IFieldBinaryCodec{
    public static final FieldBinaryCodec_s2le INSTANCE = new FieldBinaryCodec_s2le();

    public Object decode(IBinaryDataReader input, int length, Charset charset,
                        IFieldCodecContext context) throws IOException{
        return input.readS2le();
    }

    public void encode(IBinaryDataWriter output, Object value, int length, Charset charset,
        IFieldCodecContext context, IFieldBinaryEncoder bodyEncoder) throws IOException{
        if(value == null){
            output.writeS2le((short) 0);
        }else{
            output.writeS2le((Short)value);
        }
    }
}
