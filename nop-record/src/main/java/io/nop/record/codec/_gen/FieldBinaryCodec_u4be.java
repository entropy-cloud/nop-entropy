
package io.nop.record.codec._gen;

import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldBinaryEncoder;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.reader.IBinaryDataReader;
import io.nop.record.writer.IBinaryDataWriter;

import java.nio.charset.Charset;
import java.io.IOException;

public class FieldBinaryCodec_u4be implements IFieldBinaryCodec{
    public static final FieldBinaryCodec_u4be INSTANCE = new FieldBinaryCodec_u4be();

    public Object decode(IBinaryDataReader input, Object record, int length, Charset charset,
                        IFieldCodecContext context) throws IOException{
        return input.readU4be();
    }

    public void encode(IBinaryDataWriter output, Object value, int length, Charset charset,
        IFieldCodecContext context, IFieldBinaryEncoder bodyEncoder) throws IOException{
        if(value == null){
            output.writeU4be(0L);
        }else{
            
                    output.writeU4be(((Number)value).longValue());
                

        }
    }
}
