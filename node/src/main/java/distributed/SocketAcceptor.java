package distributed;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class SocketAcceptor implements Runnable {
    private final int port;
    private final Selector readSelector;

    public SocketAcceptor(int port, Selector readSelector) {
        this.port = port;
        this.readSelector = readSelector;
    }

    @Override
    public void run() {
        System.out.println("Starting SocketAcceptor task");
        try (
                ServerSocketChannel serverSocket = ServerSocketChannel.open()
        ) {
            serverSocket.bind(new InetSocketAddress(port));
            System.out.println("Listening on port " + port + "...");
            while (true) {
                SocketChannel socketChannel = serverSocket.accept();
                System.out.println("Accepting connection");
                socketChannel.configureBlocking(false);
                socketChannel.register(readSelector, SelectionKey.OP_READ);
                System.out.println("Client Channel connected");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
