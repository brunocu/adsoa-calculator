package distributed;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class Node {
    private static final char HANDSHAKE_CHAR = 'N';
    private final int portMin;
    private final int portRange;
    private final ByteBuffer handshakeBuffer = ByteBuffer.wrap(new byte[]{(byte) HANDSHAKE_CHAR});
    private final Selector cellSelector = Selector.open();
    private final Selector nodeSelector = Selector.open();
    private ServerSocketChannel serverSocket = null;
    private int port;

    public Node(int portMin, int portRange) throws IOException {
        this.portMin = portMin;
        this.portRange = portRange;
    }

    public Node(int portMin) throws IOException {
        this(portMin, 100);
    }

    public Node() throws IOException {
        this(50000, 100);
    }

    public void start() throws IOException {
        bindSocket();
        openCommunicationChannel();

        Thread socketThread = new Thread(new SocketAcceptor(serverSocket, cellSelector, nodeSelector));
        Thread cellProcessorThread = new Thread(new CellMessageProcessor(cellSelector, nodeSelector));
        Thread nodeProcessorThread = new Thread(new NodeMessageProcessor(cellSelector, nodeSelector));

        socketThread.start();
        cellProcessorThread.start();
        nodeProcessorThread.start();
    }

    private void bindSocket() throws IOException {
        // Bind Socket
        System.out.println("Binding Socket");
        serverSocket = ServerSocketChannel.open();
        for (int porti = portMin; porti < (portMin + portRange); porti++) {
            try {
                serverSocket.bind(new InetSocketAddress(porti));
                System.out.println("Server bound at port " + porti);
                port = porti;
                break;
            } catch (BindException e) {
                System.out.println("Port " + porti + " already bound");
            }
        }
        if (serverSocket.getLocalAddress() == null) {
            System.out.println("Couldn't bind on port range");
            System.exit(-1);
        }
    }

    @SuppressWarnings("resource")
    private void openCommunicationChannel() {
        // connect to existing
        for (int otherPort = portMin; otherPort < (portMin + portRange); otherPort++) {
            if (otherPort == port)
                continue;
            try {
                SocketChannel nodeSocketChannel = SocketChannel.open(new InetSocketAddress(otherPort));
                nodeSocketChannel.write(handshakeBuffer);
                int localPort = ((InetSocketAddress) nodeSocketChannel.getLocalAddress()).getPort();
                System.out.println("[" + localPort + "] Communication channel node on " + otherPort);
                nodeSocketChannel.configureBlocking(false);
                nodeSocketChannel.register(nodeSelector, SelectionKey.OP_READ, localPort);
            } catch (IOException e) {
                // continue
            }
        }
    }

    public static void main(String[] args) throws IOException {
        int port = 50000; // default port
        int portRange = 100; // default range
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Bad port name, using default: " + port);
            }
        }
        if (args.length > 1) {
            try {
                portRange = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.out.println("Bad port range, using default: " + portRange);
            }
        }

        Node node = new Node(port, portRange);
        node.start();
    }

}
