package distributed;

import java.io.IOException;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

public class NodeMessageProcessor implements Runnable {
    private final Selector cellSelector;
    private final Selector nodeSelector;

    private final ByteBuffer lengthByteBuffer = ByteBuffer.allocate(Integer.BYTES);

    public NodeMessageProcessor(Selector cellSelector, Selector nodeSelector) {
        this.cellSelector = cellSelector;
        this.nodeSelector = nodeSelector;
    }

    @SuppressWarnings("resource")
    @Override
    public void run() {
        System.out.println("Starting NodeMessageProcessor task");
        while (true) {
            try {
                int readReady = this.nodeSelector.select();

                if (readReady > 0) {
                    Set<SelectionKey> selectedKeys = this.nodeSelector.selectedKeys();
                    Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

                    // while instead of for-each because we (may) mutate set
                    while (keyIterator.hasNext()) {
                        SelectionKey key = keyIterator.next();
                        SocketChannel socketChannel = (SocketChannel) key.channel();
                        int localPort = (Integer) key.attachment();
                        // get Object size
                        try {
                            socketChannel.read(lengthByteBuffer);
                        } catch (SocketException e) {
                            socketChannel.close();
                            keyIterator.remove();
                            System.out.println("[" + localPort + "] Node connection closed");
                            continue;
                        }
                        lengthByteBuffer.flip();
                        ByteBuffer readByteBuffer = ByteBuffer.allocate(Integer.BYTES + lengthByteBuffer.getInt());
                        // read bytes into buffer
                        readByteBuffer.put(lengthByteBuffer.array());
                        socketChannel.read(readByteBuffer);
                        //noinspection UnnecessaryToStringCall
                        System.out.println("[" + localPort + "] Node message: " + readByteBuffer.toString());
                        // broadcast data only to cells
                        readByteBuffer.flip();
                        // Cells
                        Set<SelectionKey> cellKeySet = this.cellSelector.keys();
                        for (SelectionKey cellKey : cellKeySet) {
                            SocketChannel cellSocketChannel = (SocketChannel) cellKey.channel();
                            cellSocketChannel.write(readByteBuffer);
                            readByteBuffer.rewind();
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
