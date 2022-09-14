package distributed;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;

public class Client {
    private final int port;
    private final CountDownLatch interruptLatch = new CountDownLatch(1);

    public Client(int port) {
        this.port = port;
    }

    public void start() throws IOException, InterruptedException {
        System.out.println("Calling server on " + port + "...");
        SocketChannel socketChannel = SocketChannel.open(new InetSocketAddress(port));
        System.out.println("Connected!");

        Thread listenerThread = new Thread(new ClientListener(socketChannel, interruptLatch));
        Thread promptThread = new Thread(new ClientPrompt(socketChannel, interruptLatch));

        listenerThread.start();
        promptThread.start();

        // wait for signal to end program
        interruptLatch.await();
        System.out.println("Goodbye :)");
        System.exit(0);  // ugly exit
//        listenerThread.interrupt();
//        promptThread.interrupt();
//        socketChannel.close();
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        int port = 50000; // default port
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Bad port name, using default: " + port);
            }
        }

        Client client = new Client(port);
        client.start();
    }
}
