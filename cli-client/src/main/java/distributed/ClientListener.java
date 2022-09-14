package distributed;

import distributed.message.Message;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.Channels;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;

public class ClientListener implements Runnable {
    private final SocketChannel socketChannel;
    private final CountDownLatch interruptLatch;
    private final ByteBuffer lengthByteBuffer = ByteBuffer.allocate(Integer.BYTES);

    public ClientListener(SocketChannel socketChannel, CountDownLatch interruptLatch) {
        this.socketChannel = socketChannel;
        this.interruptLatch = interruptLatch;
    }

    @Override
    public void run() {
        while (socketChannel.isConnected()) {
            try {
                // get incoming object size
                socketChannel.read(lengthByteBuffer);
                lengthByteBuffer.flip();
                // discard header
                lengthByteBuffer.getInt();
                // read
                ObjectInputStream objectInputStream = new ObjectInputStream(Channels.newInputStream(socketChannel));
                final Message message = (Message) objectInputStream.readObject();
                // TODO accept only ContentCode.RESPONSE messages

                System.out.println("\nServer: " + message.getBody());
                System.out.print("$ ");
            } catch (ClosedByInterruptException e){
                // end gracefully
                break;
            } catch (SocketException e) {
                // connection closed from server
                System.out.println("\nServer closed connection!");
                break;
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            } finally {
                //cleanup
                lengthByteBuffer.clear();
            }
        }
        interruptLatch.countDown();
    }
}
