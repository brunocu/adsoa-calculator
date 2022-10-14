package distributed;

import java.io.IOException;
import java.net.BindException;
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
                int remotePort = ((InetSocketAddress) socketChannel.getRemoteAddress()).getPort();
                socketChannel.configureBlocking(false);
                socketChannel.register(readSelector, SelectionKey.OP_READ, remotePort);
                System.out.println("Client channel [" + remotePort + "] created");
                // force MessageProcessor to requeue
                readSelector.wakeup();
            }
        } catch (BindException e) {
            e.printStackTrace();
            System.exit(-1);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
