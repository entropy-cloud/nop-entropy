
package io.nop.record.codec._gen;

import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldBinaryEncoder;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.reader.IBinaryDataReader;
import io.nop.record.writer.IBinaryDataWriter;

import java.nio.charset.Charset;
import java.io.IOException;

public class FieldBinaryCodec_s4be implements IFieldBinaryCodec{
    public static final FieldBinaryCodec_s4be INSTANCE = new FieldBinaryCodec_s4be();

    public Object decode(IBinaryDataReader input, int length, Charset charset,
                        IFieldCodecContext context) throws IOException{
        return input.readS4be();
    }

    public void encode(IBinaryDataWriter output, Object value, int length, Charset charset,
        IFieldCodecContext context, IFieldBinaryEncoder bodyEncoder) throws IOException{
        if(value == null){
            output.writeS4be(0);
        }else{
            output.writeS4be((Integer)value);
        }
    }
}
