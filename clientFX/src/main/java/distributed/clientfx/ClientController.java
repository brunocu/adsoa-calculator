package distributed.clientfx;

import distributed.message.ContentCode;
import distributed.message.Message;
import distributed.message.MessageBuilder;
import distributed.util.UID;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.ToggleGroup;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

public class ClientController {
    private static final char HANDSHAKE_CHAR = 'C';
    public static final String MIN_PORT = "MIN_PORT";
    public static final String MAX_PORT = "MAX_PORT";
    private static final String MINIMUM_ACK = "MIN_ACK";
    private static final String TIMEOUT = "TIMEOUT";
    private static final UnaryOperator<TextFormatter.Change> FLOAT_FILTER = change -> {
        String newText = change.getControlNewText();
        if (newText.matches("[+-]?([0-9]+([.][0-9]*)?|[.][0-9]+)")) {
            return change;
        }
        return null;
    };
    private final ByteBuffer handshakeBuffer = ByteBuffer.wrap(new byte[]{(byte) HANDSHAKE_CHAR});
    private final ByteBuffer messageDataBuffer = ByteBuffer.allocate(2 * Float.BYTES);
    private long uid;
    private SocketChannel socketChannel = null;
    private int requestNumber = 0;
    private boolean ackResult = false;
    private final AtomicReference<Message> pendingMessageReference = new AtomicReference<>();
    private final BlockingQueue<Message> requestQueue = new LinkedBlockingQueue<>();
    private final ConcurrentSkipListSet<Long> acknowledgeSet = new ConcurrentSkipListSet<>();
    private final Semaphore requestSemaphore = new Semaphore(-1);
    //<editor-fold desc="FXML Nodes">
    @FXML
    private Label labelUID;
    @FXML
    private TextArea txtLog;
    @FXML
    private TextField txtLVal;
    @FXML
    private TextField txtRVal;
    @FXML
    private TextField txtResult;
    @FXML
    private ToggleGroup toggleGroup;
    @FXML
    private Button btnRecnn;
    @FXML
    private RadioButton selLVal;
    @FXML
    private RadioButton selRVal;
    //</editor-fold>
    private Thread requestThread;
    private Thread responseThread;

    public void initialize() {
        // FX setup
        txtLVal.setTextFormatter(
                new TextFormatter<Float>(FLOAT_FILTER));

        uid = UID.generateUID();
        labelUID.setText("UID: " + Long.toHexString(uid).toUpperCase());

        // connect to DataField
        bindSocket();
        if (socketChannel == null) {
            txtLog.appendText("No available Data Fields\n");
            btnRecnn.setDisable(false);
        } else {
            requestThread = new Thread(new RequestSender());
            responseThread = new Thread(new ResponseListener());

            requestThread.start();
            responseThread.start();
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
        int minPort = Integer.parseInt(ClientApplication.getProperty(MIN_PORT));
        int maxPort = Integer.parseInt(ClientApplication.getProperty(MAX_PORT));
        logAppend(String.format("Connecting to Data Field on range: %d\u2013%d", minPort, maxPort));
        for (int port = minPort; port < maxPort; port++) {
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
                logAppend(e.getMessage());
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
    private void processOperator(ActionEvent event) {
        if ((socketChannel == null) || !(socketChannel.isConnected())) {
            logAppend("No server connection");
            return;
        }

        String buttonText = ((Button) event.getSource()).getText();
        ContentCode contentCode;
        switch (buttonText) {
            case "+" -> contentCode = ContentCode.ADD;
            case "-" -> contentCode = ContentCode.SUB;
            case "\u00D7" -> contentCode = ContentCode.MUL;
            case "\u00F7" -> contentCode = ContentCode.DIV;
            default -> throw new IllegalStateException("Unexpected value: " + buttonText);
        }
        // Build message buffer
        messageDataBuffer.putFloat(Float.parseFloat(txtLVal.getText()));
        messageDataBuffer.putFloat(Float.parseFloat(txtRVal.getText()));

        Message requestMessage = new MessageBuilder().contentCode(contentCode)
                                                     .requestUID(uid)
                                                     .body(messageDataBuffer.array())
                                                     .digestFingerprint(requestNumber++)
                                                     .build();
        requestQueue.add(requestMessage);
        logAppend(String.format("[%016X] Request: %s%s%s",
                requestMessage.fingerprint(),
                txtLVal.getText(),
                buttonText,
                txtRVal.getText()));
        messageDataBuffer.clear();
    }

    @FXML
    private void processNumber(ActionEvent event) {
        String buttonText = ((Button) event.getSource()).getText();
        TextField valTextField = (TextField) toggleGroup.getSelectedToggle().getUserData();
        String resultText = valTextField.getText();
        if (resultText.equals("0")) valTextField.setText(buttonText);
        else valTextField.setText(resultText + buttonText);
    }

    @FXML
    private void processDot(ActionEvent event) {
        TextField valTextField = (TextField) toggleGroup.getSelectedToggle().getUserData();
        String resultText = valTextField.getText();
        if (!resultText.contains(".")) {
            valTextField.setText(resultText + ".");
        }
    }

    @FXML
    private void processNegate(ActionEvent event) {
        TextField valTextField = (TextField) toggleGroup.getSelectedToggle().getUserData();
        String resultText = valTextField.getText();
        if (!resultText.equals("0")) {
            if (resultText.startsWith("-")) valTextField.setText(resultText.substring(1));
            else valTextField.setText("-" + resultText);
        }
    }

    @FXML
    private void clearFunction(ActionEvent event) {
        TextField valTextField = (TextField) toggleGroup.getSelectedToggle().getUserData();
        valTextField.setText("0");
    }

    @FXML
    private void clearEverythingFunction(ActionEvent event) {
        txtLVal.setText("0");
        txtRVal.setText("0");
    }

    @FXML
    private void eraseFunction(ActionEvent event) {
        TextField valTextField = (TextField) toggleGroup.getSelectedToggle().getUserData();
        String resultText = valTextField.getText();
        int lenLimit = !resultText.startsWith("-") ? 1 : 2;
        if (resultText.length() > lenLimit) valTextField.setText(resultText.substring(0, resultText.length() - 1));
        else valTextField.setText("0");
    }

    @FXML
    private void openConfig(ActionEvent event) throws IOException {
        Node node = (Node) event.getSource();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("config-scene.fxml"));
        Scene scene = new Scene(loader.load());

        Stage stage = new Stage();
        stage.initOwner(node.getScene().getWindow());
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Config");
        stage.setScene(scene);
        stage.showAndWait();
    }

    @FXML
    private void reconnectAction(ActionEvent ignoredEvent) {
        if (responseThread != null && responseThread.isAlive())
            throw new RuntimeException("Active connection!");

        socketChannel = null;
        bindSocket();
        if (socketChannel == null) {
            logAppend("No available Data Fields");
        } else {
            btnRecnn.setDisable(true);

            requestThread = new Thread(new RequestSender());
            responseThread = new Thread(new ResponseListener());

            requestThread.start();
            responseThread.start();
        }
    }

    private class ResponseListener implements Runnable {
        private final ByteBuffer lengthByteBuffer = ByteBuffer.allocate(Integer.BYTES);

        @Override
        public void run() {
            while (socketChannel.isConnected()) {
                try {
                    // get incoming object size
                    socketChannel.read(lengthByteBuffer);
                    lengthByteBuffer.flip();
                    // discard header
                    lengthByteBuffer.getInt();
                    // read
                    ObjectInputStream objectInputStream = new ObjectInputStream(Channels.newInputStream(socketChannel));
                    final Message message = (Message) objectInputStream.readObject();
                    // accept only ContentCode.RESPONSE messages
                    // accept only messages directed for this cell
                    if (message.requestUID() != uid)
                        continue;

                    switch (message.contentCode()) {
                        case ACK_ADD:
                        case ACK_SUB:
                        case ACK_MUL:
                        case ACK_DIV:
                            processAcknowledge(message.fingerprint(), message.serviceUID());
                            break;
                        case RES:
                            processResult(message.body(), message.serviceUID());
                            break;
                    }


                } catch (SocketException e) {
                    logAppend("Remote connection closed!\nRetrying connection...");
                    socketChannel = null;
                    bindSocket();
                    if (socketChannel == null) {
                        Platform.runLater(() -> {
                            txtLog.appendText("No available Data Fields\n");
                            btnRecnn.setDisable(false);
                        });
                        break;
                    }
                } catch (IOException | ClassNotFoundException e) {
                    logAppend(e.getMessage());
                } finally {
                    //cleanup
                    lengthByteBuffer.clear();
                }
            }
            requestThread.interrupt();
        }

        private void processResult(byte[] messageBody, long serviceUID) {
            float messageResult = ByteBuffer.wrap(messageBody).getFloat();
            logAppend(String.format("[%016X] Result: %f", serviceUID, messageResult));
            if (ackResult)
                Platform.runLater(() -> {
                    txtResult.setText(Float.toString(messageResult));
                });
        }

        private void processAcknowledge(long messageHash, long serviceUID) {
            if (messageHash == pendingMessageReference.get().fingerprint()) {
                if (acknowledgeSet.add(serviceUID)) {
                    requestSemaphore.release();
                    int availablePermits = requestSemaphore.availablePermits();
                    logAppend(String.format("[%016X] ACK %d", serviceUID, availablePermits));
                }
            }
        }
    }

    private class RequestSender implements Runnable {
        @Override
        public void run() {
            while (socketChannel.isConnected()) {
                Message requestMessage;
                try {
                    // wait for new request
                    requestMessage = requestQueue.take();
                } catch (InterruptedException e) {
                    break;
                }
                pendingMessageReference.set(requestMessage);
                try {
                    int minimumAck;
                    int timeout;
                    do {
                        ackResult = false;
                        acknowledgeSet.clear();
                        requestSemaphore.drainPermits();
                        try {
                            ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();
                            IntStream.range(0, Integer.BYTES).forEach(
                                    i -> arrayOutputStream.write(0)
                            );
                            ObjectOutputStream outputStream = new ObjectOutputStream(arrayOutputStream);
                            outputStream.writeObject(requestMessage);
                            outputStream.close();
                            ByteBuffer writeByteBuffer = ByteBuffer.wrap(arrayOutputStream.toByteArray());
                            writeByteBuffer.putInt(0, arrayOutputStream.size() - 4);
                            socketChannel.write(writeByteBuffer);
                        } catch (IOException e) {
                            logAppend(e.getMessage());
                        }
                        minimumAck = Integer.parseInt(ClientApplication.getProperty(MINIMUM_ACK));
                        timeout = Integer.parseInt(ClientApplication.getProperty(TIMEOUT));
                    } while (socketChannel.isConnected() && !requestSemaphore.tryAcquire(minimumAck, timeout, TimeUnit.SECONDS));
                    ackResult = true;
                } catch (InterruptedException ignored) {
                    break;
                }
            }
        }
    }
}