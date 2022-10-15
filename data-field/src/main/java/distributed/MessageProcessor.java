package distributed;

import java.io.IOException;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

public class MessageProcessor implements Runnable {
    private final Selector cellSelector;
    private final ByteBuffer lengthByteBuffer = ByteBuffer.allocate(Integer.BYTES);

    public MessageProcessor(Selector cellSelector) {
        this.cellSelector = cellSelector;
    }

    @SuppressWarnings("resource")
    @Override
    public void run() {
        System.out.println("Starting MessageProcessor task");
        while (true) {
            try {
                int readReady = this.cellSelector.select();

                if (readReady > 0) {
                    Set<SelectionKey> selectedKeys = this.cellSelector.selectedKeys();
                    Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

                    // while instead of for-each because we (may) mutate set
                    while (keyIterator.hasNext()) {
                        SelectionKey key = keyIterator.next();
                        SocketChannel socketChannel = (SocketChannel) key.channel();
                        int remotePort = (Integer) key.attachment();
                        // get Object size
                        try {
                            socketChannel.read(lengthByteBuffer);
                        } catch (SocketException e) {
                            socketChannel.close();
                            keyIterator.remove();
                            System.out.println("[" + remotePort + "] Client connection closed");
                            continue;
                        }
                        lengthByteBuffer.flip();
                        ByteBuffer readByteBuffer = ByteBuffer.allocate(Integer.BYTES + lengthByteBuffer.getInt());
                        // read bytes into buffer
                        readByteBuffer.put(lengthByteBuffer.array());
                        socketChannel.read(readByteBuffer);
                        //noinspection UnnecessaryToStringCall
                        System.out.println("[" + remotePort + "] Client message: " + readByteBuffer.toString());
                        // broadcast data to other channels
                        readByteBuffer.flip();
                        Set<SelectionKey> keySet = this.cellSelector.keys();
                        for (SelectionKey otherKey : keySet) {
                            // do not resend to same client
                            if (key != otherKey) {
                                SocketChannel otherSocketChannel = (SocketChannel) otherKey.channel();
                                otherSocketChannel.write(readByteBuffer);
                                readByteBuffer.rewind();
                            }
                        }
                        // cleanup
                        lengthByteBuffer.clear();
                        keyIterator.remove();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
