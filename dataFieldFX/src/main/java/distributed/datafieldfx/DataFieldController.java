package distributed.datafieldfx;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

public class DataFieldController {
    private static final char HANDSHAKE_CHAR = 'N';
    public static final int MIN_PORT = 50000;
    private final ByteBuffer handshakeBuffer = ByteBuffer.wrap(new byte[]{(byte) HANDSHAKE_CHAR});
    private final Selector cellSelector = Selector.open();
    private final Selector nodeSelector = Selector.open();
    private final StringProperty peerCountProperty = new SimpleStringProperty("0");
    private final StringProperty cellCountProperty = new SimpleStringProperty("0");
    private ServerSocketChannel serverSocket = null;
    private int port;
    //<editor-fold desc="FXML Nodes">
    @FXML
    private Label labelPort;
    @FXML
    private Label labelPeers;
    @FXML
    private Label labelCells;
    @FXML
    private TextArea txtLog;
    //</editor-fold>

    public DataFieldController() throws IOException {
    }

    public void initialize() throws IOException {
        labelPeers.textProperty().bind(peerCountProperty);
        labelCells.textProperty().bind(cellCountProperty);

        bindSocket();
        if (serverSocket.getLocalAddress() == null) {
            txtLog.appendText(String.format("Couldn't bind on port range:%d\u2013%d%n", MIN_PORT, (MIN_PORT + 100)));
        } else {
            labelPort.setText(String.format("Puerto: %d", port));
            openCommunicationChannel();

            Thread socketThread = new Thread(new SocketAcceptor());
            Thread cellProcessorThread = new Thread(new CellMessageProcessor());
            Thread nodeProcessorThread = new Thread(new NodeMessageProcessor());

            socketThread.start();
            cellProcessorThread.start();
            nodeProcessorThread.start();
        }
    }

    public void shutdown() {
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void bindSocket() throws IOException {
        logAppend("Binding Socket");
        serverSocket = ServerSocketChannel.open();
        for (int porti = MIN_PORT; porti < (MIN_PORT + 100); porti++) {
            try {
                serverSocket.bind(new InetSocketAddress(porti));
                logAppend("Server bound at port " + porti);
                port = porti;
                break;
            } catch (BindException e) {
                logAppend("Port " + porti + " already bound");
            }
        }
    }

    private void openCommunicationChannel() {
        // connect to existing
        for (int otherPort = MIN_PORT; otherPort < (MIN_PORT + 100); otherPort++) {
            if (otherPort == port)
                continue;
            try {
                SocketChannel nodeSocketChannel = SocketChannel.open(new InetSocketAddress(otherPort));
                nodeSocketChannel.write(handshakeBuffer);
                int localPort = ((InetSocketAddress) nodeSocketChannel.getLocalAddress()).getPort();
                nodeSocketChannel.configureBlocking(false);
                nodeSocketChannel.register(nodeSelector, SelectionKey.OP_READ, localPort);
                int lambdaOtherPort = otherPort;
                Platform.runLater(() -> {
                    txtLog.appendText("[" + localPort + "] Communication channel node on " + lambdaOtherPort + "\n");
                    peerCountProperty.setValue(String.valueOf(nodeSelector.keys().size()));
                });
            } catch (IOException e) {
                // continue
            }

        }
    }

    private void logAppend(String message) {
        Platform.runLater(() -> {
            txtLog.appendText(message + "\n");
        });
    }

    private class SocketAcceptor implements Runnable {
        private final ByteBuffer handshakeReceiveBuffer = ByteBuffer.allocate(1);

        @Override
        public void run() {
            try (
                    Selector handshakeSelector = Selector.open()
            ) {
                logAppend("Listening for new sessions");
                while (serverSocket.isOpen()) {
                    try {
                        SocketChannel socketChannel = serverSocket.accept();
                        logAppend("Accepting connection");
                        int remotePort = ((InetSocketAddress) socketChannel.getRemoteAddress()).getPort();
                        socketChannel.configureBlocking(false);
                        // Handshake
                        SelectionKey selectionKey = socketChannel.register(handshakeSelector, SelectionKey.OP_READ);
                        int handshake = handshakeSelector.select(1000);
                        selectionKey.cancel();
                        if (handshake == 0) {
                            logAppend("Handshake timeout");
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
                                // force MessageProcessor to requeue
                                cellSelector.wakeup();
                                Platform.runLater(() -> {
                                    txtLog.appendText("[" + remotePort + "] Cell channel created\n");
                                    cellCountProperty.setValue(String.valueOf(cellSelector.keys().size()));
                                });
                            }
                            case 'N' -> {
                                socketChannel.register(nodeSelector, SelectionKey.OP_READ, remotePort);
                                nodeSelector.wakeup();
                                Platform.runLater(() -> {
                                    txtLog.appendText("[" + remotePort + "] New communication channel node\n");
                                    peerCountProperty.setValue(String.valueOf(nodeSelector.keys().size()));
                                });
                            }
                            default -> {
                                logAppend("Invalid connection identifier");
                                continue;
                            }
                        }
                    } catch (AsynchronousCloseException e) {
                        // process ended by user
                        cellSelector.close();
                        nodeSelector.close();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class CellMessageProcessor implements Runnable {
        private final ByteBuffer lengthByteBuffer = ByteBuffer.allocate(Integer.BYTES);

        @SuppressWarnings("resource")
        @Override
        public void run() {
            logAppend("Starting CellMessageProcessor task");
            while (cellSelector.isOpen()) {
                try {
                    int readReady = cellSelector.select();

                    if (readReady > 0) {
                        Set<SelectionKey> selectedKeys = cellSelector.selectedKeys();
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
                                logAppend("[" + remotePort + "] Cell connection closed");
                                continue;
                            }
                            lengthByteBuffer.flip();
                            ByteBuffer readByteBuffer = ByteBuffer.allocate(Integer.BYTES + lengthByteBuffer.getInt());
                            // read bytes into buffer
                            readByteBuffer.put(lengthByteBuffer.array());
                            socketChannel.read(readByteBuffer);
                            //noinspection UnnecessaryToStringCall
                            logAppend("[" + remotePort + "] Cell message: " + readByteBuffer.toString());
                            // broadcast data to all open channels
                            readByteBuffer.flip();
                            // Cells
                            Set<SelectionKey> cellKeySet = cellSelector.keys();
                            for (SelectionKey cellKey : cellKeySet) {
                                // do not resend to same client
                                if (key != cellKey) {
                                    SocketChannel cellSocketChannel = (SocketChannel) cellKey.channel();
                                    cellSocketChannel.write(readByteBuffer);
                                    readByteBuffer.rewind();
                                }
                            }
                            // Communication Channel
                            Set<SelectionKey> nodeKeySet = nodeSelector.keys();
                            for (SelectionKey nodeKey : nodeKeySet) {
                                SocketChannel nodeSocketChannel = (SocketChannel) nodeKey.channel();
                                nodeSocketChannel.write(readByteBuffer);
                                readByteBuffer.rewind();
                            }
                            // cleanup
                            lengthByteBuffer.clear();
                            keyIterator.remove();
                        }
                    }
                } catch (ClosedSelectorException e) {
                    break;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private class NodeMessageProcessor implements Runnable {
        private final ByteBuffer lengthByteBuffer = ByteBuffer.allocate(Integer.BYTES);

        @SuppressWarnings("resource")
        @Override
        public void run() {
            logAppend("Starting NodeMessageProcessor task");
            while (nodeSelector.isOpen()) {
                try {
                    int readReady = nodeSelector.select();

                    if (readReady > 0) {
                        Set<SelectionKey> selectedKeys = nodeSelector.selectedKeys();
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
                                logAppend("[" + localPort + "] Node connection closed");
                                continue;
                            }
                            lengthByteBuffer.flip();
                            ByteBuffer readByteBuffer = ByteBuffer.allocate(Integer.BYTES + lengthByteBuffer.getInt());
                            // read bytes into buffer
                            readByteBuffer.put(lengthByteBuffer.array());
                            socketChannel.read(readByteBuffer);
                            //noinspection UnnecessaryToStringCall
                            logAppend("[" + localPort + "] Node message: " + readByteBuffer.toString());
                            // broadcast data only to cells
                            readByteBuffer.flip();
                            // Cells
                            Set<SelectionKey> cellKeySet = cellSelector.keys();
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
                } catch (ClosedSelectorException e) {
                    break;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}