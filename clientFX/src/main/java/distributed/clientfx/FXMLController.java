package distributed.clientfx;

import distributed.message.ContentCode;
import distributed.message.Message;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.stream.IntStream;

public class FXMLController {
    private static final char HANDSHAKE_CHAR = 'C';
    private final ByteBuffer handshakeBuffer = ByteBuffer.wrap(new byte[]{(byte) HANDSHAKE_CHAR});
    @FXML
    private TextArea txtLog;
    @FXML
    private Label labelResult;
    @FXML
    private Label labelHistory;
    private SocketChannel socketChannel = null;

    public void initialize() {
        txtLog.appendText("Min port: 50000");
        txtLog.appendText("\nPort range: 100");
        for (int porti = 50000; porti < (50000 + 100); porti++) {
            txtLog.appendText("\nCalling server on " + porti);
            try {
                socketChannel = SocketChannel.open(new InetSocketAddress(porti));
                socketChannel.write(handshakeBuffer);
                int localPort = ((InetSocketAddress) socketChannel.getLocalAddress()).getPort();
                txtLog.appendText("\n[" + localPort + "] Connected to " + porti);

                Thread listenerThread = new Thread(new ClientListener(socketChannel, txtLog, labelResult));
                listenerThread.start();
                break;
            } catch (ConnectException e) {
                continue;
            } catch (IOException e) {
                txtLog.appendText("\n" + e.getMessage());
                break;
            }
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

    private void sendMessage(String operationMessage) {
        logAppend("User: " + operationMessage);
        if (socketChannel != null && socketChannel.isConnected()) {
            try {
                // Parse message
                String messageBody = operationMessage.replace('\u00F7', '/').replace('\u00D7', '*');
                // Build message
                Message message = new Message(ContentCode.OPERATION, messageBody);
                // Prepare array buffer
                ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();
                // leave space for size Int at beginning of array
                IntStream.range(0, Integer.BYTES).forEach(i ->
                        arrayOutputStream.write(0)
                );
                ObjectOutputStream outputStream = new ObjectOutputStream(arrayOutputStream);
                outputStream.writeObject(message);
                outputStream.close();
                final ByteBuffer writeByteBuffer = ByteBuffer.wrap(arrayOutputStream.toByteArray());
                writeByteBuffer.putInt(0, arrayOutputStream.size() - 4);
                socketChannel.write(writeByteBuffer);
            } catch (IOException e) {
                logAppend(e.getMessage());
            }
        } else {
            logAppend("No server connection");
        }
    }

    private void logAppend(String message) {
        Platform.runLater(() -> {
            txtLog.appendText("\n" + message);
        });
    }

    @FXML
    private void processNumber(ActionEvent event) {
        String buttonText = ((Button) event.getSource()).getText();
        String resultText = labelResult.getText();
        if (resultText.equals("0"))
            labelResult.setText(buttonText);
        else
            labelResult.setText(resultText + buttonText);
    }

    @FXML
    private void processDot(ActionEvent event) {
        String resultText = labelResult.getText();
        if (!resultText.contains(".")) {
            labelResult.setText(resultText + ".");
        }
    }

    @FXML
    private void processOperator(ActionEvent event) {
        String operator = ((Button) event.getSource()).getText();
        String resultText = labelResult.getText();
        String historyText = labelHistory.getText();
        String operationText;
        if (resultText.startsWith("-"))
            operationText = "(" + resultText + ")" + operator;
        else
            operationText = resultText + operator;

        if (historyText.matches("([0-9]+[^0-9])+$"))
            labelHistory.setText(historyText + operationText);
        else
            labelHistory.setText(operationText);

        labelResult.setText("0");
    }

    @FXML
    private void processNegate(ActionEvent event) {
        String resultText = labelResult.getText();
        if (!resultText.equals("0")) {
            if (resultText.startsWith("-"))
                labelResult.setText(resultText.substring(1));
            else
                labelResult.setText("-" + resultText);
        }
    }

    @FXML
    private void clearFunction(ActionEvent event) {
        labelResult.setText("0");
    }

    @FXML
    private void clearEverythingFunction(ActionEvent event) {
        labelResult.setText("0");
        labelHistory.setText("");
    }

    @FXML
    private void eraseFunction(ActionEvent event) {
        String resultText = labelResult.getText();
        int lenLimit = !resultText.startsWith("-") ? 1 : 2;
        if (resultText.length() > lenLimit)
            labelResult.setText(resultText.substring(0, resultText.length() - 1));
        else
            labelResult.setText("0");
    }

    @FXML
    private void processCalculate(ActionEvent event) {
        String resultText = labelResult.getText();
        String historyText = labelHistory.getText();

        String operationText;
        if (resultText.startsWith("-"))
            operationText = "(" + resultText + ")";
        else
            operationText = resultText;

        String operationMessage;
        if (historyText.matches("([0-9]+[^0-9])+$"))
            operationMessage = historyText + operationText;
        else if (historyText.isEmpty())
            operationMessage = operationText;
        else
            operationMessage = historyText;

        labelHistory.setText(operationMessage);
        labelResult.setText("0");
        // send message asynchronously
        new Thread(() -> {
            sendMessage(operationMessage);
        }).start();
    }
}