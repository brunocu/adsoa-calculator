package distributed;

import distributed.message.Message;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.net.Socket;
public class Client {
    public static void main(String[] args) {
        int port = 50000; // default port
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Bad port name, using default: " + port);
            }
        }
        System.out.println("Calling server on " + port + "...");
        try (
                Socket socket = new Socket("localhost", port);
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream())
                )
        ) {
            System.out.println("Connected!");
            BufferedReader stdIn = new BufferedReader(
                    new InputStreamReader(System.in)
            );
            String userLine, serverLine;

                do {
                    System.out.print("$ ");
                    userLine = stdIn.readLine();
                    // check for user exit
                    if (userLine.equals("exit"))
                        break;
                    // send to server
                    if (userLine != null) {
                        Message message = new Message(body);
//                        out.writeObject(message);
                    } else {
                        System.err.println("Bad user message!");
                        socket.close();
                        System.exit(1);
                    }
                    serverLine = in.readLine();
                    System.out.println("Server: " + serverLine);
                } while (serverLine != null);
        } catch (IOException e) {
            System.err.println("No host on " + port);
            System.exit(-1);
        }
    }
}
