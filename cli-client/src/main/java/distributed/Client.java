package distributed;

import distributed.message.ContentCode;
import distributed.message.Message;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.stream.IntStream;

public class Client {
    public static void main(String[] args) throws IOException {
        int port = 50000; // default port
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Bad port name, using default: " + port);
            }
        }
        System.out.println("Calling server on " + port + "...");
        try (
                SocketChannel socketChannel = SocketChannel.open()
        ) {
            socketChannel.connect(new InetSocketAddress(port));
            System.out.println("Connected!");
            BufferedReader stdIn = new BufferedReader(
                    new InputStreamReader(System.in)
            );
            String userLine;

            while (true) {
                System.out.print("$ ");
                userLine = stdIn.readLine();
                // check for user exit
                if (userLine.equals("exit"))
                    break;

                // Build message
                Message message = new Message(ContentCode.OPERATION, userLine);
                // Prepare array buffer
                ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();
                // make space for size Int at beginning of array
                IntStream.range(0, 4).forEach(i ->
                        arrayOutputStream.write(0)
                );
                ObjectOutputStream outputStream = new ObjectOutputStream(arrayOutputStream);
                outputStream.writeObject(message);
                outputStream.close();
                final ByteBuffer writeByteBuffer = ByteBuffer.wrap(arrayOutputStream.toByteArray());
                writeByteBuffer.putInt(0, arrayOutputStream.size() - 4);
                socketChannel.write(writeByteBuffer);
            }
        }
    }
}
