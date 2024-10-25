
package io.nop.record.codec._gen;

import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldBinaryEncoder;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.reader.IBinaryDataReader;
import io.nop.record.writer.IBinaryDataWriter;

import java.nio.charset.Charset;
import java.io.IOException;

public class FieldBinaryCodec_u1 implements IFieldBinaryCodec{
    public static final FieldBinaryCodec_u1 INSTANCE = new FieldBinaryCodec_u1();

    public Object decode(IBinaryDataReader input, int length, Charset charset,
                        IFieldCodecContext context) throws IOException{
        return input.readU1();
    }

    public void encode(IBinaryDataWriter output, Object value, int length, Charset charset,
        IFieldCodecContext context, IFieldBinaryEncoder bodyEncoder) throws IOException{
        if(value == null){
            output.writeU1((short) 0);
        }else{
            output.writeU1((Short)value);
        }
    }
}
