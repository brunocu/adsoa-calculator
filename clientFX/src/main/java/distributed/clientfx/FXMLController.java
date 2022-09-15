package distributed.clientfx;

import distributed.message.ContentCode;
import distributed.message.Message;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
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
    private SocketChannel socketChannel = null;

    public void initialize() {
        txtLog.setText("Calling server on 50000...");
        try {
            socketChannel = SocketChannel.open(new InetSocketAddress(50000));
            logln("Connected!");

            Thread listenerThread = new Thread(new ClientListener(socketChannel, txtLog));
            listenerThread.start();
        } catch (IOException e) {
            logln(e.getMessage());
        }
    }

    private void logln(String string) {
        txtLog.setText(txtLog.getText() + "\n" + string);
    }

    @FXML
    private void sendMessage(ActionEvent event) {
        if (socketChannel.isConnected()) {
            try {
                String messageBody = txtInput.getText();
                logln("User: " + messageBody);
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
                logln(e.getMessage());
            }
        } else {
            logln("No server connection");
        }
    }

}