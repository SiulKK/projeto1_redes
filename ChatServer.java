import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.concurrent.*;

public class ChatServer {

    // Porta onde o servidor ficará escutando
    private static final int PORT = 12345;

    // Conjunto thread-safe para armazenar os clientes conectados
    private static Set<ClientHandler> clients =
            ConcurrentHashMap.newKeySet();

    // Pool de threads para atender vários clientes ao mesmo tempo
    private static ExecutorService pool =
            Executors.newFixedThreadPool(20);

    public static void main(String[] args) {
        System.out.println("Servidor iniciado na porta " + PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {

            // Aceita conexões continuamente
            while (true) {
                Socket socket = serverSocket.accept();
                ClientHandler client = new ClientHandler(socket);
                clients.add(client);
                pool.execute(client);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Envia mensagem para todos os clientes conectados
    public static void broadcast(String message) {
        for (ClientHandler c : clients) {
            c.send(message);
        }
    }

    // Remove cliente do conjunto
    public static void remove(ClientHandler c) {
        clients.remove(c);
    }

    static class ClientHandler implements Runnable {

        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String nick = "Anônimo";

        // Fila de mensagens para evitar bloqueios no envio
        private BlockingQueue<String> queue =
                new LinkedBlockingQueue<>();

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                // Streams de entrada e saída
                in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Thread separada apenas para envio de mensagens
                new Thread(this::sendLoop).start();

                send(ts() + " Servidor: Bem-vindo ao chat!");
                sendHelp();
                broadcast(ts() + " Servidor: Um usuário entrou no chat");

                String msg;
                while ((msg = in.readLine()) != null) {
                    handleMessage(msg);
                }

            } catch (IOException e) {
                // conexão encerrada
            } finally {
                disconnect();
            }
        }

        // Trata comandos e mensagens do cliente
        private void handleMessage(String msg) {

            if (msg.startsWith("/help")) {
                sendHelp();
                return;
            }

            if (msg.startsWith("/nick")) {
                String[] p = msg.split(" ", 2);
                if (p.length < 2) {
                    send(ts() + " Servidor: uso correto /nick <nome>");
                    return;
                }
                nick = p[1];
                send(ts() + " Servidor: nick alterado para " + nick);
                return;
            }

            if (msg.startsWith("/list")) {
                send(ts() + " Servidor: usuários conectados:");
                for (ClientHandler c : clients) {
                    send("- " + c.nick);
                }
                return;
            }

            if (msg.startsWith("/pm")) {
                String[] p = msg.split(" ", 3);
                if (p.length < 3) {
                    send(ts() + " Servidor: uso correto /pm <nick> <msg>");
                    return;
                }

                for (ClientHandler c : clients) {
                    if (c.nick.equals(p[1])) {
                        c.send(ts() + " (PM) " + nick + ": " + p[2]);
                        return;
                    }
                }
                send(ts() + " Servidor: usuário não encontrado");
                return;
            }

            if (msg.startsWith("/quit")) {
                disconnect();
                return;
            }

            // Mensagem enviada para todos
            broadcast(ts() + " " + nick + ": " + msg);
        }

        // Loop responsável apenas por enviar mensagens da fila
        private void sendLoop() {
            try {
                while (true) {
                    out.println(queue.take());
                }
            } catch (InterruptedException ignored) {}
        }

        // Adiciona mensagem na fila de envio
        void send(String msg) {
            queue.offer(msg);
        }

        // Gera timestamp no formato HH:mm:ss
        private String ts() {
            return "[" + LocalTime.now()
                    .format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "]";
        }

        // Envia lista de comandos disponíveis
        private void sendHelp() {
            send("Comandos disponíveis:");
            send("/nick <nome> - Define o nome");
            send("/list        - Lista usuários");
            send("/pm <nick> <msg> - Mensagem privada");
            send("/help        - Ajuda");
            send("/quit        - Sair");
        }

        // Finaliza conexão do cliente
        private void disconnect() {
            try {
                clients.remove(this);
                broadcast(ts() + " Servidor: " + nick + " saiu do chat");
                socket.close();
            } catch (IOException ignored) {}
        }
    }
}
