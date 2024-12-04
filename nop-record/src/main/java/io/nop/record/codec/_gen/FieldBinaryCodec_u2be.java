
package io.nop.record.codec._gen;

import io.nop.record.codec.IFieldBinaryEncoder;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.reader.IBinaryDataReader;
import io.nop.record.writer.IBinaryDataWriter;
import io.nop.record.codec.impl.StaticFieldBinaryCodecFactory;

import java.io.IOException;

public class FieldBinaryCodec_u2be extends StaticFieldBinaryCodecFactory{
    public static final FieldBinaryCodec_u2be INSTANCE = new FieldBinaryCodec_u2be();

    @Override
    public Object decode(IBinaryDataReader input, Object record, int length,
                        IFieldCodecContext context) throws IOException{
        return input.readU2be();
    }

    @Override
    public void encode(IBinaryDataWriter output, Object value, int length,
        IFieldCodecContext context, IFieldBinaryEncoder bodyEncoder) throws IOException{
        if(value == null){
            output.writeU2be(0);
        }else{
            
                    output.writeU2be((Integer)value);
                

        }
    }
}
