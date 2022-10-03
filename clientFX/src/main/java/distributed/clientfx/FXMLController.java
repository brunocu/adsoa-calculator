package distributed.clientfx;

import distributed.message.ContentCode;
import distributed.message.Message;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.stream.IntStream;

public class FXMLController {
    @FXML
    private TextArea txtLog;
    @FXML
    private TextField txtInput;
    @FXML
    public Button btnDo;
    @FXML
    private Label labelResult;
    @FXML
    private Label labelHistory;

    private SocketChannel socketChannel = null;

    public void initialize() {
        txtLog.appendText("Calling server on 50000...");
        try {
            socketChannel = SocketChannel.open(new InetSocketAddress(50000));
            txtLog.appendText("\nConnected!");

            Thread listenerThread = new Thread(new ClientListener(socketChannel, txtLog));
            listenerThread.start();
        } catch (IOException e) {
            txtLog.appendText("\n" + e.getMessage());
        }
    }

    public void shutdown() {
        try {
            socketChannel.close();
        } catch (NullPointerException e) {
            // ok
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void sendMessage(ActionEvent event) {
        if (socketChannel.isConnected()) {
            try {
                String messageBody = txtInput.getText();
                txtLog.appendText("\nUser: " + messageBody);
                txtInput.clear();
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
                txtLog.appendText("\n" + e.getMessage());
            }
        } else {
            txtLog.appendText("\nNo server connection");
        }
    }

}