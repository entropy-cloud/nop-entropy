
package io.nop.record.codec._gen;

import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldBinaryEncoder;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.reader.IBinaryDataReader;
import io.nop.record.writer.IBinaryDataWriter;

import java.nio.charset.Charset;
import java.io.IOException;

public class FieldBinaryCodec_s8be implements IFieldBinaryCodec{
    public static final FieldBinaryCodec_s8be INSTANCE = new FieldBinaryCodec_s8be();

    public Object decode(IBinaryDataReader input, int length, Charset charset,
                        IFieldCodecContext context) throws IOException{
        return input.readS8be();
    }

    public void encode(IBinaryDataWriter output, Object value, int length, Charset charset,
        IFieldCodecContext context, IFieldBinaryEncoder bodyEncoder) throws IOException{
        if(value == null){
            output.writeS8be(0L);
        }else{
            output.writeS8be((Long)value);
        }
    }
}
