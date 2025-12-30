import java.io.*;
import java.net.*;

public class ChatClient {
    public static void main(String[] args) {
        String host = (args.length >= 1) ? args[0] : "127.0.0.1";
        int port = (args.length >= 2) ? Integer.parseInt(args[1]) : 12345;

        try (Socket socket = new Socket(host, port);
             BufferedReader serverIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter serverOut = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in))
        ) {
            System.out.println("Conectado a " + host + ":" + port);

            Thread reader = new Thread(() -> {
                try {
                    String s;
                    while ((s = serverIn.readLine()) != null) {
                        System.out.println(s);
                    }
                } catch (IOException e) {
                    System.out.println("Conexão encerrada pelo servidor.");
                }
            });
            reader.setDaemon(true);
            reader.start();

            String line;
            while ((line = stdin.readLine()) != null) {
                serverOut.println(line);
                if (line.trim().equalsIgnoreCase("/quit")) break;
            }

            System.out.println("Encerrando cliente.");
        } catch (IOException e) {
            System.err.println("Erro de rede: " + e.getMessage());
        }
    }
}
