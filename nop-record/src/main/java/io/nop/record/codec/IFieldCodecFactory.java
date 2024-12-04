package io.nop.record.codec;

public interface IFieldCodecFactory {
    default IFieldBinaryCodec newBinaryCodec(IFieldConfig config) {
        throw new UnsupportedOperationException();
    }

    default IFieldTextCodec newTextCodec(IFieldConfig config) {
        throw new UnsupportedOperationException();
    }
}
