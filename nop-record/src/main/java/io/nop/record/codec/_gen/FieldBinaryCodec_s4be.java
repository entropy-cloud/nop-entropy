
package io.nop.record.codec._gen;

import io.nop.record.codec.IFieldBinaryEncoder;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.reader.IBinaryDataReader;
import io.nop.record.writer.IBinaryDataWriter;
import io.nop.record.codec.impl.StaticFieldBinaryCodecFactory;

import java.io.IOException;

public class FieldBinaryCodec_s4be extends StaticFieldBinaryCodecFactory{
    public static final FieldBinaryCodec_s4be INSTANCE = new FieldBinaryCodec_s4be();

    @Override
    public Object decode(IBinaryDataReader input, Object record, int length,
                        IFieldCodecContext context) throws IOException{
        return input.readS4be();
    }

    @Override
    public void encode(IBinaryDataWriter output, Object value, int length,
        IFieldCodecContext context, IFieldBinaryEncoder bodyEncoder) throws IOException{
        if(value == null){
            output.writeS4be(0);
        }else{
            
                    output.writeS4be((Integer)value);
                

        }
    }
}
