
package io.nop.record.codec._gen;

import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldBinaryEncoder;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.reader.IBinaryDataReader;
import io.nop.record.writer.IBinaryDataWriter;

import java.nio.charset.Charset;
import java.io.IOException;

public class FieldBinaryCodec_s2be implements IFieldBinaryCodec{
    public static final FieldBinaryCodec_s2be INSTANCE = new FieldBinaryCodec_s2be();

    public Object decode(IBinaryDataReader input, Object record, int length, Charset charset,
                        IFieldCodecContext context) throws IOException{
        return input.readS2be();
    }

    public void encode(IBinaryDataWriter output, Object value, int length, Charset charset,
        IFieldCodecContext context, IFieldBinaryEncoder bodyEncoder) throws IOException{
        if(value == null){
            output.writeS2be((short) 0);
        }else{
            output.writeS2be((Short)value);
        }
    }
}
