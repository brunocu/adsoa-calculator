package distributed.serviceinjector;

import distributed.message.ContentCode;
import distributed.message.Message;
import distributed.message.MessageBuilder;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.stream.IntStream;

public class InjectorController {
    private static final char HANDSHAKE_CHAR = 'C';
    private static final int MIN_PORT = 50000;
    private final ByteBuffer handshakeBuffer = ByteBuffer.wrap(new byte[]{(byte) HANDSHAKE_CHAR});
    private static final EnumSet<ContentCode> SERVICE_CONTENT_CODES = EnumSet.of(
            ContentCode.ADD,
            ContentCode.SUB,
            ContentCode.MUL,
            ContentCode.DIV
    );
    private SocketChannel socketChannel = null;
    @FXML
    private TextArea txtLog;
    @FXML
    private ChoiceBox<ContentCode> opCodeChoiceBox;
    @FXML
    private TextField textFilePath;
    @FXML
    private Button btnInject;
    @FXML
    private Button btnRecnn;
    private Thread keepAliveThread;

    public void initialize() {
        opCodeChoiceBox.getItems().addAll(SERVICE_CONTENT_CODES);
        opCodeChoiceBox.setValue(ContentCode.ADD);

        bindSocket();
        if (socketChannel == null) {
            logAppend("No available Data Fields");
            btnInject.setDisable(true);
            btnRecnn.setDisable(false);
        } else {
            keepAliveThread = new Thread(new KeepAlive());
            keepAliveThread.start();
        }
    }

    public void shutdown() {
        if (socketChannel != null) {
            try {
                socketChannel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void bindSocket() {
        logAppend(String.format("Connecting to Data Field on range: %d\u2013%d", MIN_PORT, (MIN_PORT + 100)));
        for (int port = MIN_PORT; port < (MIN_PORT + 100); port++) {
            try {
                socketChannel = SocketChannel.open(new InetSocketAddress(port));
                socketChannel.write(handshakeBuffer);
                int localPort = ((InetSocketAddress) socketChannel.getLocalAddress()).getPort();
                logAppend("[" + localPort + "] Connection to " + port);
                handshakeBuffer.clear();
                break;
            } catch (ConnectException e) {
                continue;
            } catch (IOException e) {
                logAppend(e.getMessage() + "\n");
                break;
            }
        }
    }

    private void logAppend(String message) {
        Platform.runLater(() -> {
            txtLog.appendText(message + "\n");
        });
    }

    @FXML
    private void handleFileChooser(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialDirectory(new File("").getAbsoluteFile());
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Jar Files", "*.jar")
        );
        Stage stage = new Stage();
        File file = fileChooser.showOpenDialog(stage);
        if (file != null) {
            textFilePath.setText(file.getAbsolutePath());
        }
    }

    @FXML
    private void handleReconnect(ActionEvent event) {
        if (socketChannel != null)
            throw new RuntimeException("Active connection!");

        bindSocket();
        if (socketChannel == null) {
            logAppend("No available Data Fields");
        } else {
            btnInject.setDisable(false);
            btnRecnn.setDisable(true);
            keepAliveThread = new Thread(new KeepAlive());
            keepAliveThread.start();
        }
    }

    @FXML
    private void handleInject(ActionEvent event) {
        Path filePath = Path.of(textFilePath.getText());
        if (Files.exists(filePath)) {
            try (
                    InputStream fileInputStream = Files.newInputStream(filePath)
            ) {
                ContentCode opCodeValue = opCodeChoiceBox.getValue();
                logAppend(String.format("Injecting operation [%s] from: %s", opCodeValue, filePath.getFileName()));
                ByteArrayOutputStream fileArrayOutputStream = new ByteArrayOutputStream();
                ObjectOutputStream fileObjectOutputStream = new ObjectOutputStream(fileArrayOutputStream);
                fileObjectOutputStream.writeObject(opCodeValue);
                fileInputStream.transferTo(fileArrayOutputStream);
                Message injectionMessage = new MessageBuilder().contentCode(ContentCode.INY)
                                                               .body(fileArrayOutputStream.toByteArray())
                                                               .build();
                ByteArrayOutputStream messageArrayOutputStream = new ByteArrayOutputStream();
                IntStream.range(0, Integer.BYTES).forEach(
                        i -> messageArrayOutputStream.write(0)
                );
                ObjectOutputStream messageObjectOutputStream = new ObjectOutputStream(messageArrayOutputStream);
                messageObjectOutputStream.writeObject(injectionMessage);
                ByteBuffer writeByteBuffer = ByteBuffer.wrap(messageArrayOutputStream.toByteArray());
                writeByteBuffer.putInt(0, messageArrayOutputStream.size() - Integer.BYTES);
                socketChannel.write(writeByteBuffer);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            logAppend("File does not exist: " + filePath);
        }
    }

    private class KeepAlive implements Runnable {

        private final ByteBuffer lengthByteBuffer = ByteBuffer.allocate(Integer.BYTES);

        @Override
        public void run() {
            while (socketChannel.isConnected()) {
                try {
                    int read = socketChannel.read(lengthByteBuffer);
                    if (read == -1) {
                        // end-of-stream
                        break;
                    }
                    lengthByteBuffer.flip();
                    ByteBuffer dataByteBuffer = ByteBuffer.allocate(lengthByteBuffer.getInt());
                    read = socketChannel.read(dataByteBuffer);
                    System.out.println(read);
                } catch (SocketException e) {
                    // connection closed from server
                    logAppend("Remote connection closed!\nRetrying connection...");
                    socketChannel = null;
                    bindSocket();
                    if (socketChannel == null) {
                        Platform.runLater(() -> {
                            txtLog.appendText("No available Data Fields\n");
                            btnInject.setDisable(true);
                            btnRecnn.setDisable(false);
                        });
                        break;
                    }
                } catch (IOException e) {
                    logAppend(e.getMessage());
                } finally {
                    lengthByteBuffer.clear();
                }
            }
        }
    }
}