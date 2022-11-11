package distributed.server;

import distributed.message.ContentCode;
import distributed.message.Message;
import distributed.message.MessageBuilder;
import distributed.util.UID;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
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
    private final String servicesDir;
    private final ByteBuffer handshakeBuffer = ByteBuffer.wrap(new byte[]{(byte) HANDSHAKE_CHAR});
    private final ByteBuffer responseDataBuffer = ByteBuffer.allocate(Float.BYTES);
    private final int minPort;
    private final long uid;
    private SocketChannel socketChannel = null;

    public Server(int minPort, String servicesDir) {
        this.minPort = minPort;
        this.servicesDir = servicesDir;
        uid = UID.generateUID();
    }

    public void start() throws IOException {
        System.out.printf("UID: %016X%n", uid);
        System.out.println("Services directory: " + Path.of(servicesDir).toAbsolutePath());

        System.out.printf("Connecting to Data Field on range: %d\u2013%d%n", minPort, (minPort + 100));
        for (int porti = minPort; porti < (minPort + 100); porti++) {
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
            String requestContentCodeName = null;
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
                requestContentCodeName = requestContentCode.name();

                requestDataBuffer = ByteBuffer.wrap(requestMessage.body());
                float aVal = requestDataBuffer.getFloat();
                float bVal = requestDataBuffer.getFloat();
                System.out.printf("Request:%s,%.2f,%.2f%n", requestContentCodeName, aVal, bVal);

                Method evalMethod = loadEvalMethod(requestContentCodeName);
                ContentCode ackCode = ContentCode.valueOf("ACK_" + requestContentCodeName);
                Message ackMessage = MessageBuilder.from(requestMessage)
                                                   .contentCode(ackCode)
                                                   .serviceUID(uid)
                                                   .body(null)
                                                   .build();
                sendMessage(ackMessage);
                float responseVal = (float) evalMethod.invoke(null, aVal, bVal);
                System.out.println("Result: " + responseVal);

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
            } catch (IOException | IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                System.out.printf("Unknown service: %s%n", requestContentCodeName);
            } finally {
                responseDataBuffer.clear();
            }
        }
        socketChannel.close();
    }

    private Method loadEvalMethod(String service) throws ClassNotFoundException, IOException {
        URLClassLoader classLoader = null;
        Method evalMethod;
        try {
            URL url = Path.of(servicesDir, (service.toLowerCase() + ".jar")).toUri().toURL();
            classLoader = URLClassLoader.newInstance(new URL[]{url});
            String serviceClassName = "distributed.services." + service.substring(0, 1).toUpperCase() + service.substring(1).toLowerCase();
            Class<?> clazz = classLoader.loadClass(serviceClassName);
            Class<?>[] partypes = new Class[]{float.class, float.class};
            evalMethod = clazz.getMethod("eval", partypes);
        } catch (NoSuchMethodException | MalformedURLException e) {
            throw new RuntimeException(e);
        } finally {
            if (classLoader != null)
                classLoader.close();
        }
        return evalMethod;
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
        int port = 50000; // default port
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Bad port name, using default: " + port);
            }
        }

        Server server = new Server(port, "server/build/libs/services");
        server.start();
    }
}
