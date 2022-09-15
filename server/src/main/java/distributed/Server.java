package distributed;

import distributed.message.ContentCode;
import distributed.message.Message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.util.stream.IntStream;

public class Server {
    private final int port;

    public Server(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        System.out.println("Calling server on " + port + "...");
        SocketChannel socketChannel = SocketChannel.open(new InetSocketAddress(port));
        System.out.println("Connected!");


        while (socketChannel.isConnected()) {
            try {
                // discard header
                socketChannel.socket().getInputStream().skip(Integer.BYTES);
//                socketChannel.read(lengthByteBuffer);
                ObjectInputStream objectInputStream = new ObjectInputStream(Channels.newInputStream(socketChannel));
                final Message message = (Message) objectInputStream.readObject();
                // process only ContentCode.OPERATION
                if (message.getContentCode() != ContentCode.OPERATION)
                    continue;
                System.out.println("Message: " + message.getBody());
                // TODO do stuff
                // return message
                Message response = new Message(ContentCode.OPERATION, message.getBody());
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
