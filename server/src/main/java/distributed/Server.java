package distributed;

import distributed.message.ContentCode;
import distributed.message.Message;
import distributed.message.MessageBuilder;
import distributed.util.UID;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.util.EnumSet;
import java.util.stream.IntStream;

public class Server {
    private static final char HANDSHAKE_CHAR = 'C';
    private static final EnumSet<ContentCode> SERVER_CONTENT_CODES = EnumSet.of(
            ContentCode.ADD,
            ContentCode.SUB,
            ContentCode.MUL,
            ContentCode.DIV
    );
    private final ByteBuffer handshakeBuffer = ByteBuffer.wrap(new byte[]{(byte) HANDSHAKE_CHAR});
    private final ByteBuffer responseDataBuffer = ByteBuffer.allocate(Float.BYTES);
    private final int minPort;
    private final long uid;
    private SocketChannel socketChannel = null;

    public Server(int minPort) {
        this.minPort = minPort;
        uid = UID.generateUID();
    }

    public void start() throws IOException {
        System.out.printf("UID: %016X%n", uid);
        for (int porti = minPort; porti < (minPort + 100); porti++) {
            System.out.println("Calling server on " + porti);
            try {
                socketChannel = SocketChannel.open(new InetSocketAddress(porti));
                socketChannel.write(handshakeBuffer);
                int localPort = ((InetSocketAddress) socketChannel.getLocalAddress()).getPort();
                System.out.println("Connected: " + localPort);
                break;
            } catch (ConnectException e) {
                continue;
            }
        }
        if (socketChannel == null) {
            // no available ports
            System.out.println("No available Data Fields");
            System.exit(-1);
        }


        while (socketChannel.isConnected()) {
            ByteBuffer requestDataBuffer;
            try {
                // discard header
                int read = socketChannel.read(ByteBuffer.allocate(Integer.BYTES));
                if (read == -1) {
                    // end-of-stream
                    System.out.println("\nServer closed connection!");
                    break;
                }
                ObjectInputStream objectInputStream = new ObjectInputStream(Channels.newInputStream(socketChannel));
                final Message requestMessage = (Message) objectInputStream.readObject();
                // process only operations
                ContentCode requestContentCode = requestMessage.contentCode();
                if (!SERVER_CONTENT_CODES.contains(requestContentCode))
                    continue;
                // send ACK
                ContentCode ackCode = ContentCode.valueOf("ACK_" + requestMessage.contentCode().name());
                Message ackMessage = MessageBuilder.from(requestMessage)
                                                   .contentCode(ackCode)
                                                   .serviceUID(uid)
                                                   .body(null)
                                                   .build();
                sendMessage(ackMessage);

                requestDataBuffer = ByteBuffer.wrap(requestMessage.body());
                float aVal = requestDataBuffer.getFloat();
                float bVal = requestDataBuffer.getFloat();

                float responseVal;
                // do stuff
                switch (requestContentCode) {
                    case ADD -> {
                        System.out.printf("Request: %f+%f%n", aVal, bVal);
                        responseVal = aVal + bVal;
                    }
                    case SUB -> {
                        System.out.printf("Request: %f-%f%n", aVal, bVal);
                        responseVal = aVal - bVal;
                    }
                    case MUL -> {
                        System.out.printf("Request: %f*%f%n", aVal, bVal);
                        responseVal = aVal * bVal;
                    }
                    case DIV -> {
                        System.out.printf("Request: %f/%f%n", aVal, bVal);
                        responseVal = aVal / bVal;
                    }
                    default -> throw new IllegalStateException("Unexpected value: " + requestContentCode);
                }
                System.out.println("Response: " + responseVal);

                // return message
                responseDataBuffer.putFloat(responseVal);
                Message responseMessage = MessageBuilder.from(requestMessage)
                                                        .contentCode(ContentCode.RES)
                                                        .serviceUID(uid)
                                                        .body(responseDataBuffer.array())
                                                        .build();
                sendMessage(responseMessage);
            } catch (SocketException e) {
                // connection closed from server
                System.out.println("\nServer closed connection!");
                break;
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            } finally {
                responseDataBuffer.clear();
            }
        }
        socketChannel.close();
    }

    private void sendMessage(Message message) throws IOException {
        // Prepare array buffer
        ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();
        // leave space for size Int at beginning of array
        IntStream.range(0, Integer.BYTES).forEach(i ->
                arrayOutputStream.write(0)
        );
        ObjectOutputStream outputStream = new ObjectOutputStream(arrayOutputStream);
        outputStream.writeObject(message);
        outputStream.close();
        ByteBuffer writeByteBuffer = ByteBuffer.wrap(arrayOutputStream.toByteArray());
        writeByteBuffer.putInt(0, arrayOutputStream.size() - 4);
        socketChannel.write(writeByteBuffer);
    }

    public static void main(String[] args) throws IOException {
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");

        int port = 50000; // default port
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Bad port name, using default: " + port);
            }
        }

        Server server = new Server(port);
        server.start();
    }
}
