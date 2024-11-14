package io.nop.record.codec.impl;

import io.netty.buffer.ByteBuf;
import io.nop.api.core.exceptions.NopException;
import io.nop.codec.IPacketCodec;
import io.nop.commons.bytes.EndianKind;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.ReflectionManager;
import io.nop.record.codec.FieldCodecRegistry;
import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.model.PacketCodecModel;
import io.nop.record.model.RecordTypeMeta;
import io.nop.record.netty.ByteBufBinaryDataReader;
import io.nop.record.netty.ByteBufBinaryDataWriter;
import io.nop.record.serialization.ModelBasedBinaryRecordDeserializer;
import io.nop.record.serialization.ModelBasedBinaryRecordSerializer;

import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.nop.record.RecordConstants.TYPE_DEFAULT;
import static io.nop.record.RecordErrors.ARG_FIELD_NAME;
import static io.nop.record.RecordErrors.ARG_LENGTH;
import static io.nop.record.RecordErrors.ARG_LENGTH_FIELD_LENGTH;
import static io.nop.record.RecordErrors.ARG_MAX_LENGTH;
import static io.nop.record.RecordErrors.ARG_MIN_LENGTH;
import static io.nop.record.RecordErrors.ARG_TYPE_NAME;
import static io.nop.record.RecordErrors.ERR_RECORD_FIELD_LENGTH_IS_NEGATIVE;
import static io.nop.record.RecordErrors.ERR_RECORD_FIELD_LENGTH_IS_TOO_LARGE;
import static io.nop.record.RecordErrors.ERR_RECORD_FIELD_LENGTH_IS_TOO_SMALL;
import static io.nop.record.RecordErrors.ERR_RECORD_UNKNOWN_OBJ_TYPE;
import static io.nop.record.RecordErrors.ERR_RECORD_UNSUPPORTED_PACKET_LENGTH_FIELD_LENGTH;

public class ModelBasedPacketCodec implements IPacketCodec<Object> {
    static final String PACKET_LENGTH = "packetLength";

    private final ByteOrder byteOrder;
    private final int maxFrameLength;
    private final int lengthFieldOffset;
    private final int lengthFieldLength;
    private final int lengthFieldEndOffset;
    private final int lengthAdjustment;
    private final int initialBytesToStrip;
    private final IEvalFunction decodeTypeDecider;
    private final IEvalFunction encodeTypeDecider;

    private final IFieldBinaryCodec lengthCodec;

    private final FieldCodecRegistry registry;

    private final ModelBasedBinaryRecordDeserializer deserializer;
    private final ModelBasedBinaryRecordSerializer serializer;

    private final PacketCodecModel codecModel;

    public ModelBasedPacketCodec(PacketCodecModel codecModel, FieldCodecRegistry registry) {
        this.registry = registry;
        this.codecModel = codecModel;
        this.maxFrameLength = codecModel.getMaxFrameLength();
        this.lengthFieldOffset = codecModel.getLengthFieldOffset();
        this.initialBytesToStrip = codecModel.getInitialBytesToStrip();
        this.lengthFieldLength = codecModel.getLengthFieldLength();
        this.lengthAdjustment = codecModel.getLengthAdjustment();
        this.lengthFieldEndOffset = lengthFieldOffset + lengthFieldLength;
        this.decodeTypeDecider = codecModel.getDecodeTypeDecider();
        this.encodeTypeDecider = codecModel.getEncodeTypeDecider();
        EndianKind endian = codecModel.getLengthEndian();
        if (endian == null)
            endian = codecModel.getDefaultEndian();
        this.byteOrder = endian == EndianKind.little ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
        if (codecModel.getLengthFieldCodec() != null) {
            this.lengthCodec = registry.requireBinaryCodec(PACKET_LENGTH, codecModel.getLengthFieldCodec());
        } else {
            this.lengthCodec = null;
        }
        this.deserializer = new ModelBasedBinaryRecordDeserializer(registry);
        this.serializer = new ModelBasedBinaryRecordSerializer(registry);
    }

    @Override
    public int determinePacketLength(ByteBuf in) {
        long frameLength = 0;

        if (in.readableBytes() < lengthFieldEndOffset) {
            return UNDETERMINED_LENGTH;
        }

        int actualLengthFieldOffset = in.readerIndex() + lengthFieldOffset;
        frameLength = getUnadjustedFrameLength(in, actualLengthFieldOffset, lengthFieldLength, byteOrder);

        if (frameLength < 0) {
            failOnNegativeLengthField(in, frameLength, lengthFieldEndOffset);
        }

        frameLength += lengthAdjustment + lengthFieldEndOffset;

        if (frameLength < lengthFieldEndOffset) {
            failOnFrameLengthLessThanLengthFieldEndOffset(in, frameLength, lengthFieldEndOffset);
        }

        if (maxFrameLength > 0 && frameLength > maxFrameLength) {
            exceededFrameLength(in, frameLength);
        }

        // never overflows because it's less than maxFrameLength
        int frameLengthInt = (int) frameLength;

        if (initialBytesToStrip > frameLengthInt) {
            failOnFrameLengthLessThanInitialBytesToStrip(in, frameLength, initialBytesToStrip);
        }
        return frameLengthInt;
    }

    private static void failOnNegativeLengthField(ByteBuf in, long frameLength, int lengthFieldEndOffset) {
        in.skipBytes(lengthFieldEndOffset);
        throw new NopException(ERR_RECORD_FIELD_LENGTH_IS_NEGATIVE).param(ARG_FIELD_NAME, PACKET_LENGTH)
                .param(ARG_LENGTH, frameLength);
    }

    private static void failOnFrameLengthLessThanLengthFieldEndOffset(ByteBuf in,
                                                                      long frameLength,
                                                                      int lengthFieldEndOffset) {
        in.skipBytes(lengthFieldEndOffset);
        throw new NopException(ERR_RECORD_FIELD_LENGTH_IS_TOO_SMALL).param(ARG_FIELD_NAME, PACKET_LENGTH)
                .param(ARG_LENGTH, frameLength).param(ARG_MIN_LENGTH, lengthFieldEndOffset);
    }

    private void exceededFrameLength(ByteBuf in, long frameLength) {
        long discard = frameLength - in.readableBytes();
        if (discard < 0) {
            // buffer contains more bytes then the frameLength so we can discard all now
            in.skipBytes((int) frameLength);
        } else {
            // Enter the discard mode and discard everything received so far.
            in.skipBytes(in.readableBytes());
        }
        throw new NopException(ERR_RECORD_FIELD_LENGTH_IS_TOO_LARGE)
                .param(ARG_LENGTH, frameLength)
                .param(ARG_MAX_LENGTH, maxFrameLength);
    }

    private static void failOnFrameLengthLessThanInitialBytesToStrip(ByteBuf in,
                                                                     long frameLength,
                                                                     int initialBytesToStrip) {
        in.skipBytes((int) frameLength);
        throw new NopException(ERR_RECORD_FIELD_LENGTH_IS_TOO_SMALL)
                .param(ARG_FIELD_NAME, PACKET_LENGTH)
                .param(ARG_LENGTH, frameLength)
                .param(ARG_MIN_LENGTH, initialBytesToStrip);
    }

    @Override
    public Object decodeFromBuf(ByteBuf buf, Class<?> targetType) {
        buf.skipBytes(lengthFieldEndOffset);

        Object record = newObject(targetType);
        IFieldCodecContext context = new DefaultFieldCodecContext(codecModel::getType);
        RecordTypeMeta typeMeta = determineType(buf, context.getEvalScope());
        try {
            deserializer.readObject(new ByteBufBinaryDataReader(buf), typeMeta, null, record, context);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
        return record;
    }

    private RecordTypeMeta determineType(ByteBuf frame, IEvalScope scope) {
        String typeName;
        if (decodeTypeDecider == null) {
            typeName = TYPE_DEFAULT;
        } else {
            typeName = (String) decodeTypeDecider.call1(null, frame, scope);
        }
        RecordTypeMeta type = codecModel.getType(typeName);
        if (type == null)
            throw new NopException(ERR_RECORD_UNKNOWN_OBJ_TYPE)
                    .param(ARG_TYPE_NAME, typeName);
        return type;
    }

    private RecordTypeMeta determineEncodeType(Object message, IEvalScope scope) {
        String typeName;
        if (encodeTypeDecider == null) {
            typeName = TYPE_DEFAULT;
        } else {
            typeName = (String) encodeTypeDecider.call1(null, message, scope);
        }
        RecordTypeMeta type = codecModel.getType(typeName);
        if (type == null)
            throw new NopException(ERR_RECORD_UNKNOWN_OBJ_TYPE)
                    .param(ARG_TYPE_NAME, typeName);
        return type;
    }

    private Object newObject(Class<?> targetType) {
        if (targetType == null || targetType == Map.class)
            return new LinkedHashMap<>();
        return ReflectionManager.instance().getBeanModelForClass(targetType).newInstance();
    }

    @Override
    public void encodeToBuf(Object message, ByteBuf buf) {
        buf.writeZero(lengthFieldEndOffset);

        IFieldCodecContext context = new DefaultFieldCodecContext(codecModel::getType);
        RecordTypeMeta typeMeta = determineEncodeType(message, context.getEvalScope());
        try {
            serializer.writeObject(new ByteBufBinaryDataWriter(buf), typeMeta, null, message, context);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
        int endIndex = buf.writerIndex();
        int len = endIndex - initialBytesToStrip - lengthAdjustment;
        buf.writerIndex(lengthFieldOffset);
        writeUnadjustedFrameLength(buf, lengthFieldLength, len);
        buf.writerIndex(endIndex);
    }

    protected long getUnadjustedFrameLength(ByteBuf buf, int offset, int length, ByteOrder order) {
        if (lengthCodec != null) {
            int readerIndex = buf.readerIndex();
            buf.skipBytes(offset);
            try {
                int len = (Integer) lengthCodec.decode(new ByteBufBinaryDataReader(buf), null, length, null, null);
                return len;
            } catch (Exception e) {
                throw NopException.adapt(e);
            } finally {
                buf.readerIndex(readerIndex);
            }
        }
        buf = buf.order(order);
        long frameLength;
        switch (length) {
            case 1:
                frameLength = buf.getUnsignedByte(offset);
                break;
            case 2:
                frameLength = buf.getUnsignedShort(offset);
                break;
            case 3:
                frameLength = buf.getUnsignedMedium(offset);
                break;
            case 4:
                frameLength = buf.getUnsignedInt(offset);
                break;
            case 8:
                frameLength = buf.getLong(offset);
                break;
            default:
                throw new NopException(ERR_RECORD_UNSUPPORTED_PACKET_LENGTH_FIELD_LENGTH)
                        .param(ARG_LENGTH_FIELD_LENGTH, length);
        }
        return frameLength;
    }

    protected void writeUnadjustedFrameLength(ByteBuf buf, int length, int frameLength) {
        if (lengthCodec != null) {
            try {
                lengthCodec.encode(new ByteBufBinaryDataWriter(buf), frameLength, length, null, null, null);
            } catch (Exception e) {
                throw NopException.adapt(e);
            }
            return;
        }
        switch (length) {
            case 1:
                buf.writeByte(frameLength);
                break;
            case 2:
                buf.writeShort(frameLength);
                break;
            case 3:
                buf.writeMedium(frameLength);
                break;
            case 4:
                buf.writeInt(frameLength);
                break;
            case 8:
                buf.writeLong(frameLength);
                break;
            default:
                throw new NopException(ERR_RECORD_UNSUPPORTED_PACKET_LENGTH_FIELD_LENGTH)
                        .param(ARG_LENGTH_FIELD_LENGTH, length);
        }
    }
}