package distributed;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;

public class MessageProcessor implements Runnable {
    private final Selector readSelector;
    private final ByteBuffer readByteBuffer = ByteBuffer.allocate(1024 * 1024);

    public MessageProcessor(Selector readSelector) {
        this.readSelector = readSelector;
    }

    @Override
    public void run() {
        System.out.println("Starting MessageProcessor task");
        while (true) {
            try {
                int readReady = this.readSelector.selectNow();

                if (readReady > 0) {
                    Set<SelectionKey> selectedKeys = this.readSelector.selectedKeys();
                    Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

                    while (keyIterator.hasNext()) {
                        SelectionKey key = keyIterator.next();
                        SocketChannel socketChannel = (SocketChannel) key.channel();
                        int bytesRead = socketChannel.read(readByteBuffer);
                        readByteBuffer.flip();

                        if(readByteBuffer.remaining() == 0){
                            // empty message
                            readByteBuffer.clear();
                            return;
                        }
                        Charset utf8Cset = StandardCharsets.UTF_8;
                        System.out.println(utf8Cset.decode(readByteBuffer));
                        System.out.printf("Remaining: %d", readByteBuffer.remaining());

                        keyIterator.remove();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
