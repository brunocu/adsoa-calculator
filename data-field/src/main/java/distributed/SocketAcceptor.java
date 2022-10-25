package distributed;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class SocketAcceptor implements Runnable {
    private final ByteBuffer handshakeReceiveBuffer = ByteBuffer.allocate(1);
    private final ServerSocketChannel serverSocket;
    private final Selector cellSelector;
    private final Selector nodeSelector;

    public SocketAcceptor(ServerSocketChannel serverSocket, Selector cellSelector, Selector nodeSelector) {
        this.serverSocket = serverSocket;
        this.cellSelector = cellSelector;
        this.nodeSelector = nodeSelector;
    }

    @Override
    public void run() {
        try (
                Selector handshakeSelector = Selector.open()
        ) {
            System.out.println("Listening for new sessions");
            while (true) {
                SocketChannel socketChannel = serverSocket.accept();
                System.out.println("Accepting connection");
                int remotePort = ((InetSocketAddress) socketChannel.getRemoteAddress()).getPort();
                socketChannel.configureBlocking(false);
                // Handshake
                SelectionKey selectionKey = socketChannel.register(handshakeSelector, SelectionKey.OP_READ);
                int handshake = handshakeSelector.select(1000);
                selectionKey.cancel();
                if (handshake == 0) {
                    System.out.println("Handshake timeout");
                    socketChannel.close();
                    continue;
                }
                handshakeReceiveBuffer.clear();
                socketChannel.read(handshakeReceiveBuffer);
                handshakeReceiveBuffer.flip();
                char handshakeChar = (char) handshakeReceiveBuffer.get();
                switch (handshakeChar) {
                    case 'C' -> {
                        socketChannel.register(cellSelector, SelectionKey.OP_READ, remotePort);
                        System.out.println("[" + remotePort + "] Cell channel created");
                        // force MessageProcessor to requeue
                        cellSelector.wakeup();
                    }
                    case 'N' -> {
                        socketChannel.register(nodeSelector, SelectionKey.OP_READ, remotePort);
                        System.out.println("[" + remotePort + "] New communication channel node");
                        nodeSelector.wakeup();
                    }
                    default -> {
                        System.out.println("Invalid connection identifier");
                        continue;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
