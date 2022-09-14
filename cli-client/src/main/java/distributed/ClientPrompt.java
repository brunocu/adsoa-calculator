package distributed;

import distributed.message.ContentCode;
import distributed.message.Message;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.stream.IntStream;

public class ClientPrompt implements Runnable {
    private final SocketChannel socketChannel;
    private final CountDownLatch interruptLatch;

    public ClientPrompt(SocketChannel socketChannel, CountDownLatch interruptLatch) {
        this.socketChannel = socketChannel;
        this.interruptLatch = interruptLatch;
    }

    @Override
    public void run() {
        BufferedReader stdIn = new BufferedReader(
                new InputStreamReader(System.in)
        );
        String userLine;

        while (socketChannel.isOpen()) {
            try {
                System.out.print("$ ");
                userLine = stdIn.readLine();

                if (userLine.equals("exit")) {
                    break;
                }

                // Build message
                Message message = new Message(ContentCode.OPERATION, userLine);
                // Prepare array buffer
                ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();
                // leave space for size Int at beginning of array
                IntStream.range(0, Integer.BYTES).forEach(i ->
                        arrayOutputStream.write(0)
                );
                ObjectOutputStream outputStream = new ObjectOutputStream(arrayOutputStream);
                outputStream.writeObject(message);
                outputStream.close();
                final ByteBuffer writeByteBuffer = ByteBuffer.wrap(arrayOutputStream.toByteArray());
                writeByteBuffer.putInt(0, arrayOutputStream.size() - 4);
                socketChannel.write(writeByteBuffer);
            } catch (ClosedByInterruptException e) {
                // end gracefully;
                break;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        interruptLatch.countDown();
    }
}
