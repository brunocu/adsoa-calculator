package io.brunocu;

import java.io.IOException;
import java.nio.channels.Selector;

public class Node {
    private final int port;
    private final Selector readSelector;

    public Node(int port) throws IOException {
        this.port = port;

        this.readSelector = Selector.open();
    }

    public void start() throws InterruptedException {
        Thread socketThread = new Thread(new SocketAcceptor(port, readSelector));
        Thread processorThread = new Thread(new MessageProcessor(readSelector));

        socketThread.start();
        processorThread.start();
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        Node node = new Node(50000);

        node.start();
    }
}
