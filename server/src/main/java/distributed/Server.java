package distributed;

import distributed.message.ContentCode;
import distributed.message.Message;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
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
import java.util.stream.IntStream;

public class Server {
    private static final char HANDSHAKE_CHAR = 'C';
    private final ByteBuffer handshakeBuffer = ByteBuffer.wrap(new byte[]{(byte) HANDSHAKE_CHAR});
    private final int minPort;
    private final ScriptEngine scriptEngine;

    public Server(int minPort) {
        this.minPort = minPort;

        ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
        scriptEngine = scriptEngineManager.getEngineByName("JavaScript");
        System.out.println("Engine: " + scriptEngine);
    }

    public void start() throws IOException {
        SocketChannel socketChannel = null;
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
            try {
                // discard header
                int read = socketChannel.read(ByteBuffer.allocate(Integer.BYTES));
                if (read == -1) {
                    // end-of-stream
                    System.out.println("\nServer closed connection!");
                    break;
                }
                ObjectInputStream objectInputStream = new ObjectInputStream(Channels.newInputStream(socketChannel));
                final Message message = (Message) objectInputStream.readObject();
                // process only ContentCode.OPERATION
                if (message.getContentCode() != ContentCode.OPERATION)
                    continue;
                String messageBody = message.getBody();
                System.out.println("Message: " + messageBody);

                String responseBody;
                // do stuff
                try {
                    responseBody = scriptEngine.eval(messageBody).toString();
                } catch (ScriptException e) {
                    responseBody = "EXPR ERR";
                }

                // return message
                System.out.println("Response: " + responseBody);
                Message response = new Message(ContentCode.RESPONSE, responseBody);
                // Prepare array buffer
                ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();
                // leave space for size Int at beginning of array
                IntStream.range(0, Integer.BYTES).forEach(i ->
                        arrayOutputStream.write(0)
                );
                ObjectOutputStream outputStream = new ObjectOutputStream(arrayOutputStream);
                outputStream.writeObject(response);
                outputStream.close();
                final ByteBuffer writeByteBuffer = ByteBuffer.wrap(arrayOutputStream.toByteArray());
                writeByteBuffer.putInt(0, arrayOutputStream.size() - 4);
                socketChannel.write(writeByteBuffer);
            } catch (SocketException e) {
                // connection closed from server
                System.out.println("\nServer closed connection!");
                break;
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        socketChannel.close();
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
