package distributed.clientfx;

import distributed.message.ContentCode;
import distributed.message.Message;
import javafx.application.Platform;
import javafx.scene.control.TextArea;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.SocketChannel;

public class ClientListener implements Runnable {
    private final SocketChannel socketChannel;
    private final ByteBuffer lengthByteBuffer = ByteBuffer.allocate(Integer.BYTES);
    private final TextArea txtLog;

    public ClientListener(SocketChannel socketChannel, TextArea txtLog) {
        this.socketChannel = socketChannel;
        this.txtLog = txtLog;
    }

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
                if (message.getContentCode() != ContentCode.RESPONSE)
                    continue;

                logln("Server: " + message.getBody());
            } catch (ClosedByInterruptException | SocketException e) {
                // end gracefully
                break;
            } catch (IOException | ClassNotFoundException e) {
                logln(e.getMessage());
            } finally {
                //cleanup
                lengthByteBuffer.clear();
            }
        }
        logln("Server disconnected!");
    }

    private void logln(String message) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                txtLog.setText(txtLog.getText() + "\n" + message);
            }
        });
    }
}
