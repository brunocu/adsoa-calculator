package distributed;

import java.io.IOException;
import java.nio.channels.Selector;

public class Node {
    private final int port;
    private final Selector readSelector;

    public Node(int port) throws IOException {
        this.port = port;

        this.readSelector = Selector.open();
    }

    public void start() {
        Thread socketThread = new Thread(new SocketAcceptor(port, readSelector));
        Thread processorThread = new Thread(new MessageProcessor(readSelector));

        socketThread.start();
        processorThread.start();
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

        Node node = new Node(port);
        node.start();
    }
}
