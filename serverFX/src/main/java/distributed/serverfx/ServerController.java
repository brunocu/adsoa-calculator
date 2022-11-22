package distributed.serverfx;

import distributed.message.ContentCode;
import distributed.message.Message;
import distributed.message.MessageBuilder;
import distributed.util.UID;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

import java.awt.Desktop;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class ServerController {
    private static final char HANDSHAKE_CHAR = 'C';
    private static final int MIN_PORT = 50000;
    private final ByteBuffer handshakeBuffer = ByteBuffer.wrap(new byte[]{(byte) HANDSHAKE_CHAR});
    private long uid;
    private Path rootPath;
    private Path servicesPath;
    private int cloneCount;
    private SocketChannel socketChannel = null;
    //<editor-fold desc="FXML Nodes">
    @FXML
    private Label labelUID;
    @FXML
    private Hyperlink linkPath;
    @FXML
    private TextArea txtLog;
    @FXML
    private Button btnRecnn;
    //</editor-fold>
    private Thread requestThread;

    public void initialize() {
        uid = UID.generateUID();
        labelUID.setText("UID: " + Long.toHexString(uid).toUpperCase());

        rootPath = Paths.get("").toAbsolutePath().getParent();
        servicesPath = rootPath.resolve("services");
        //noinspection ResultOfMethodCallIgnored
        servicesPath.toFile().mkdir();
        linkPath.setText(servicesPath.toString());

        txtLog.appendText(String.format("Connecting to Data Field on range: %d\u2013%d%n", MIN_PORT, (MIN_PORT + 100)));
        bindSocket();
        if (socketChannel == null) {
            txtLog.appendText("No available Data Fields\n");
            btnRecnn.setDisable(false);
        } else {
            requestThread = new Thread(new RequestServer());
            requestThread.start();
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
    private void hyperlinkAction(ActionEvent ignoredEvent) throws IOException {
        Desktop.getDesktop().browse(servicesPath.toUri());
    }

    @FXML
    private void reconnectAction(ActionEvent ignoredEvent) {
        if (requestThread != null && requestThread.isAlive())
            throw new RuntimeException("Active connection!");

        logAppend(String.format("Connecting to Data Field on range: %d\u2013%d", MIN_PORT, (MIN_PORT + 100)));
        socketChannel = null;
        bindSocket();
        if (socketChannel == null) {
            logAppend("No available Data Fields\n");
        } else {
            btnRecnn.setDisable(true);
            requestThread = new Thread(new RequestServer());
            requestThread.start();
        }
    }

    private class RequestServer implements Runnable {
        private final ByteBuffer responseDataBuffer = ByteBuffer.allocate(Float.BYTES);

        @Override
        public void run() {
            while (socketChannel.isConnected()) {
                ByteBuffer requestDataBuffer;
                String requestContentCodeName = null;
                try {
                    // discard header
                    int read = socketChannel.read(ByteBuffer.allocate(Integer.BYTES));
                    if (read == -1) {
                        // end-of-stream
                        logAppend("Server closed connection!");
                        break;
                    }
                    ObjectInputStream messageObjectInputStream = new ObjectInputStream(Channels.newInputStream(socketChannel));
                    final Message requestMessage = (Message) messageObjectInputStream.readObject();
                    // process only operations
                    ContentCode requestContentCode = requestMessage.contentCode();
                    switch (requestContentCode) {
                        case INY -> {
                            // service injection
                            ByteArrayInputStream bodyArrayInputStream = new ByteArrayInputStream(requestMessage.body());
                            ObjectInputStream bodyObjectInputStream = new ObjectInputStream(bodyArrayInputStream);
                            ContentCode opCode = (ContentCode) bodyObjectInputStream.readObject();
                            logAppend("Received service: " + opCode);
                            OutputStream fileOutputStream = Files.newOutputStream(
                                    servicesPath.resolve(opCode.name().toLowerCase() + ".jar")
                            );
                            fileOutputStream.write(bodyArrayInputStream.readAllBytes());
                            fileOutputStream.close();
                        }
                        case CPY -> {
                            if (requestMessage.serviceUID() != uid) {
                                continue;
                            }
                            // clone protocol
                            // send clone acknowledge to client
                            Message ackMessage = MessageBuilder.from(requestMessage)
                                                               .contentCode(ContentCode.CPACK)
                                                               .serviceUID(uid)
                                                               .body(null)
                                                               .digestFingerprint(cloneCount++)
                                                               .build();
                            sendMessage(ackMessage);
                            logAppend("Cloning...");

                            // get next server directory name
                            Path parentPath = rootPath.getParent();
                            String rootPathName = parentPath.relativize(rootPath).toString();
                            Pattern postfixPattern = Pattern.compile("[0-9]+$");
                            Matcher rootMatcher = postfixPattern.matcher(rootPathName);
                            String dirBaseName;
                            if (rootMatcher.find()) {
                                dirBaseName = rootPathName.substring(0, rootMatcher.start());
                            } else {
                                dirBaseName = rootPathName;
                            }
                            PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher(String.format("regex:%s[0-9]*", dirBaseName));

                            OptionalInt maxInt;
                            try (Stream<Path> pathWalk = Files.walk(parentPath, 1)) {
                                maxInt = pathWalk
                                        .filter(path -> pathMatcher.matches(path.getFileName()))
                                        .mapMultiToInt((path, consumer) -> {
                                            Matcher matcher = postfixPattern.matcher(path.toString());
                                            if (matcher.find()) {
                                                consumer.accept(Integer.parseInt(matcher.group()));
                                            }
                                        })
                                        .max();
                            }
                            int dirCount = maxInt.orElse(0) + 1;
                            Path target = parentPath.resolve(String.format("%s%02d", dirBaseName, dirCount));
                            MessageBuilder copyMessageBuilder = MessageBuilder.from(ackMessage);
                            try {
                                Files.walkFileTree(rootPath, new TreeCopier(rootPath, target));
                                ProcessBuilder pb = new ProcessBuilder(target.resolve(Path.of("bin", "serverFX.bat"))
                                                                             .toString());
                                pb.directory(target.resolve("bin").toFile());
                                pb.start();
                                logAppend("Created: " + target);
                                copyMessageBuilder.contentCode(ContentCode.CPC);
                            } catch (IOException e) {
                                logAppend(String.format("Unable to create: %s: %s", target, e));
                                copyMessageBuilder.contentCode(ContentCode.CPERR);
                            }
                            sendMessage(copyMessageBuilder.build());
                        }
                        case ADD, SUB, MUL, DIV -> {
                            // service request
                            requestContentCodeName = requestContentCode.name();
                            requestDataBuffer = ByteBuffer.wrap(requestMessage.body());
                            float aVal = requestDataBuffer.getFloat();
                            float bVal = requestDataBuffer.getFloat();
                            logAppend(String.format("Request: %s,%.2f,%.2f", requestContentCodeName, aVal, bVal));
                            Method evalMethod = loadEvalMethod(requestContentCodeName);
                            ContentCode ackCode = ContentCode.valueOf("ACK_" + requestContentCodeName);
                            Message ackMessage = MessageBuilder.from(requestMessage)
                                                               .contentCode(ackCode)
                                                               .serviceUID(uid)
                                                               .body(null)
                                                               .build();
                            sendMessage(ackMessage);
                            float responseVal = (float) evalMethod.invoke(null, aVal, bVal);
                            logAppend("Result: " + responseVal);

                            // response message
                            responseDataBuffer.putFloat(responseVal);
                            Message responseMessage = MessageBuilder.from(requestMessage)
                                                                    .contentCode(ContentCode.RES)
                                                                    .serviceUID(uid)
                                                                    .body(responseDataBuffer.array())
                                                                    .build();
                            sendMessage(responseMessage);
                        }
                    }
                } catch (SocketException e) {
                    // connection closed from server
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
                } catch (IOException | IllegalAccessException | InvocationTargetException e) {
                    logAppend(e.getMessage());
                } catch (ClassNotFoundException e) {
                    logAppend("Unknown service: " + requestContentCodeName);
                } finally {
                    responseDataBuffer.clear();
                }
            }
        }

        private Method loadEvalMethod(String service) throws ClassNotFoundException, IOException {
            URLClassLoader classLoader = null;
            Method evalMethod;
            try {
                URL url = servicesPath.resolve(service.toLowerCase() + ".jar").toUri().toURL();
                classLoader = URLClassLoader.newInstance(new URL[]{url});
                String serviceClassName = "distributed.services." + service.substring(0, 1)
                                                                           .toUpperCase() + service.substring(1)
                                                                                                   .toLowerCase();
                Class<?> clazz = classLoader.loadClass(serviceClassName);
                Class<?>[] partypes = new Class[]{float.class, float.class};
                evalMethod = clazz.getMethod("eval", partypes);
            } catch (NoSuchMethodException | MalformedURLException e) {
                throw new RuntimeException(e);
            } finally {
                if (classLoader != null)
                    classLoader.close();
            }
            return evalMethod;
        }

        private void sendMessage(Message message) throws IOException {
            // Prepare array buffer
            ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();
            // leave space for size Int at beginning of array
            IntStream.range(0, Integer.BYTES).forEach(i ->
                    arrayOutputStream.write(0)
            );
            ObjectOutputStream outputStream = new ObjectOutputStream(arrayOutputStream);
            outputStream.writeObject(message);
            outputStream.close();
            ByteBuffer writeByteBuffer = ByteBuffer.wrap(arrayOutputStream.toByteArray());
            writeByteBuffer.putInt(0, arrayOutputStream.size() - 4);
            socketChannel.write(writeByteBuffer);
        }
    }
}