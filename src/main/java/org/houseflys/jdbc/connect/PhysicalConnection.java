package org.houseflys.jdbc.connect;

import org.houseflys.jdbc.data.Block;
import org.houseflys.jdbc.misc.Validate;
import org.houseflys.jdbc.protocol.*;
import org.houseflys.jdbc.serializer.BinaryDeserializer;
import org.houseflys.jdbc.serializer.BinarySerializer;
import org.houseflys.jdbc.settings.ClickHouseConfig;
import org.houseflys.jdbc.settings.ClickHouseDefines;
import org.houseflys.jdbc.protocol.QueryRequest.ClientInfo;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.sql.SQLException;
import java.util.UUID;

import static org.houseflys.jdbc.protocol.QueryRequest.COMPLETE_STAGE;

public class PhysicalConnection {
    private final Socket socket;
    private final SocketAddress address;
    private final BinarySerializer serializer;
    private final BinaryDeserializer deserializer;

    public PhysicalConnection(Socket socket, BinarySerializer serializer, BinaryDeserializer deserializer) {
        this.socket = socket;
        this.serializer = serializer;
        this.deserializer = deserializer;
        this.address = socket.getLocalSocketAddress();
    }

    public boolean ping(int soTimeout) {
        try {
            sendRequest(new PingRequest());
            for (; ; ) {
                RequestOrResponse response = receiveResponse(soTimeout);
                Validate.isTrue(response instanceof ProgressResponse || response instanceof PongResponse,
                    "Expect Pong Response.");

                if (response instanceof PongResponse)
                    return true;
            }
        } catch (SQLException e) {
            //TODO log
            return false;
        }
    }

    public void sendData(Block data) throws SQLException {
        sendRequest(new DataRequest("", data));
    }

    public void sendQuery(String query, ClientInfo info) throws SQLException {
        sendQuery(UUID.randomUUID().toString(), COMPLETE_STAGE, info, query);
    }

    public void sendHello(String client, long reversion, String db, String user, String password) throws SQLException {
        sendRequest(new HelloRequest(client, reversion, db, user, password));
    }

    public Block receiveSampleBlock(int soTimeout) throws SQLException {
        while (true) {
            RequestOrResponse response = receiveResponse(soTimeout);
            if (response instanceof DataResponse) {
                return ((DataResponse) response).block();
            }
        }
    }

    public HelloResponse receiveHello(int soTimeout) throws SQLException {
        RequestOrResponse response = receiveResponse(soTimeout);
        Validate.isTrue(response instanceof HelloResponse, "Expect Hello Response.");
        return (HelloResponse) response;
    }

    public EOFStreamResponse receiveEndOfStream(int soTimeout) throws SQLException {
        RequestOrResponse response = receiveResponse(soTimeout);
        Validate.isTrue(response instanceof EOFStreamResponse, "Expect EOFStream Response.");
        return (EOFStreamResponse) response;
    }

    public RequestOrResponse receiveResponse(int soTimeout) throws SQLException {
        try {
            socket.setSoTimeout(soTimeout);
            return RequestOrResponse.readFrom(deserializer);
        } catch (IOException ex) {
            throw new SQLException(ex.getMessage(), ex);
        }
    }

    public SocketAddress address() {
        return address;
    }

    public void disPhysicalConnection() throws SQLException {
        try {
            if (!socket.isClosed()) {
                serializer.flushToTarget(true);
                socket.close();
            }
        } catch (IOException ex) {
            throw new SQLException(ex.getMessage(), ex);
        }
    }

    private void sendQuery(String id, int stage, ClientInfo info, String query) throws SQLException {
        sendRequest(new QueryRequest(id, info, stage, true, query));
    }

    private void sendRequest(RequestOrResponse request) throws SQLException {
        try {
            request.writeTo(serializer);
            serializer.flushToTarget(true);
        } catch (IOException ex) {
            throw new SQLException(ex.getMessage(), ex);
        }
    }

    public static PhysicalConnection openPhysicalConnection(ClickHouseConfig configure) throws SQLException {
        try {
            SocketAddress endpoint = new InetSocketAddress(configure.address(), configure.port());

            Socket socket = new Socket();
            socket.setTcpNoDelay(true);
            socket.setSendBufferSize(ClickHouseDefines.DBMS_DEFAULT_BUFFER_SIZE.intValue());
            socket.setReceiveBufferSize(ClickHouseDefines.DBMS_DEFAULT_BUFFER_SIZE.intValue());
            socket.connect(endpoint, configure.connectTimeout());

            return new PhysicalConnection(socket, new BinarySerializer(socket), new BinaryDeserializer(socket));
        } catch (IOException ex) {
            throw new SQLException(ex.getMessage(), ex);
        }
    }
}
