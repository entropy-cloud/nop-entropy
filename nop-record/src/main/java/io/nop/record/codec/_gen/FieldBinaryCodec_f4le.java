
package io.nop.record.codec._gen;

import io.nop.record.codec.IFieldBinaryEncoder;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.reader.IBinaryDataReader;
import io.nop.record.writer.IBinaryDataWriter;
import io.nop.record.codec.impl.StaticFieldBinaryCodecFactory;
import io.nop.record.serialization.IModelBasedBinaryRecordDeserializer;
import io.nop.record.serialization.IModelBasedBinaryRecordSerializer;

import java.io.IOException;

public class FieldBinaryCodec_f4le extends StaticFieldBinaryCodecFactory{
    public static final FieldBinaryCodec_f4le INSTANCE = new FieldBinaryCodec_f4le();

    @Override
    public Object decode(IBinaryDataReader input, Object record, int length,
                        IFieldCodecContext context, IModelBasedBinaryRecordDeserializer deserializer ) throws IOException{
        return input.readF4le();
    }

    @Override
    public void encode(IBinaryDataWriter output, Object value, int length,
        IFieldCodecContext context, IModelBasedBinaryRecordSerializer serializer) throws IOException{
        if(value == null){
            output.writeF4le(0.0f);
        }else{
            
                    output.writeF4le((Float)value);
                

        }
    }
}
