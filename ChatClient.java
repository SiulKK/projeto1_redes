import java.io.*;      // Importa classes para entrada e saída de dados
import java.net.*;    // Importa classes para comunicação via rede (sockets)

/**
 * Classe ChatClient
 * 
 * Implementa um cliente de chat simples utilizando sockets TCP.
 * O cliente se conecta a um servidor, envia mensagens digitadas pelo usuário e recebe mensagens do servidor em tempo real.
 */
public class ChatClient {

    public static void main(String[] args) {

        // Define o endereço do servidor.
        // Caso não seja passado como argumento, usa localhost (127.0.0.1)
        String host = (args.length >= 1) ? args[0] : "127.0.0.1";

        // Define a porta do servidor.
        // Caso não seja passada como argumento, utiliza a porta 12345
        int port = (args.length >= 2) ? Integer.parseInt(args[1]) : 12345;

        // Estrutura try-with-resources garante o fechamento automático dos recursos
        try (
            // Cria o socket que estabelece a conexão com o servidor
            Socket socket = new Socket(host, port);

            // Fluxo para receber mensagens do servidor
            BufferedReader serverIn = new BufferedReader(
                new InputStreamReader(socket.getInputStream())
            );

            // Fluxo para enviar mensagens ao servidor
            PrintWriter serverOut = new PrintWriter(
                socket.getOutputStream(), true
            );

            // Fluxo para leitura das mensagens digitadas pelo usuário
            BufferedReader stdin = new BufferedReader(
                new InputStreamReader(System.in)
            )
        ) {
            // Exibe mensagem confirmando a conexão com o servidor
            System.out.println("Conectado a " + host + ":" + port);

            // Cria uma thread responsável por receber mensagens do servidor
            Thread reader = new Thread(() -> {
                try {
                    String s;
                    while ((s = serverIn.readLine()) != null) {
                        System.out.println(s);
                    }
                } catch (IOException e) {
                    // Mensagem exibida caso a conexão seja encerrada
                    System.out.println("Conexão encerrada pelo servidor.");
                }
            });

            // encerra junto com o programa principal)
            reader.setDaemon(true);
            reader.start();

            // Loop principal para leitura das mensagens do usuário
            String line;
            while ((line = stdin.readLine()) != null) {
                serverOut.println(line);

                // Caso o usuário digite "/quit", encerra o cliente
                if (line.trim().equalsIgnoreCase("/quit")) {
                    break;
                }
            }

            // Mensagem de encerramento do cliente
            System.out.println("Encerrando cliente.");

        } catch (IOException e) {
            // Tratamento de erros relacionados à comunicação de rede
            System.err.println("Erro de rede: " + e.getMessage());
        }
    }
}
