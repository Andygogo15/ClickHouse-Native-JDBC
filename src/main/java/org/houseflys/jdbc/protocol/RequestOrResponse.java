package org.houseflys.jdbc.protocol;

import java.io.IOException;
import java.sql.SQLException;

import org.houseflys.jdbc.serializer.BinarySerializer;
import org.houseflys.jdbc.serializer.BinaryDeserializer;

public abstract class RequestOrResponse {

    private final ProtocolType type;

    public ProtocolType type() {
        return type;
    }

    RequestOrResponse(ProtocolType type) {
        this.type = type;
    }

    public void writeTo(BinarySerializer serializer) throws IOException, SQLException {
        serializer.writeVarInt(type.id());

        this.writeImpl(serializer);
    }

    public abstract void writeImpl(BinarySerializer serializer) throws IOException, SQLException;

    public static RequestOrResponse readFrom(BinaryDeserializer deserializer) throws IOException, SQLException {
        switch ((int) deserializer.readVarInt()) {
            case 0:
                return HelloResponse.readFrom(deserializer);
            case 1:
                return DataResponse.readFrom(deserializer);
            case 2:
                throw ExceptionResponse.readExceptionFrom(deserializer);
            case 3:
                return ProgressResponse.readFrom(deserializer);
            case 4:
                return PongResponse.readFrom(deserializer);
            case 5:
                return EOFStreamResponse.readFrom(deserializer);
            case 6:
                return ProfileInfoResponse.readFrom(deserializer);
            case 7:
                return TotalsResponse.readFrom(deserializer);
            case 8:
                return ExtremesResponse.readFrom(deserializer);
            default:
                throw new IllegalStateException("Accept the id of response that is not recognized by Server.");
        }
    }

    private static boolean isPingResult(ProtocolType type, BinaryDeserializer deserializer) {
        return ProtocolType.REQUEST_PING.equals(type);
    }

    private static boolean isResultPacket(ProtocolType type, BinaryDeserializer deserializer) {
        return ProtocolType.REQUEST_QUERY.equals(type);
    }
}
