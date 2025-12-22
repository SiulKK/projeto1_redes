// ChatServer.java
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.*;

public class ChatServer {

    private static final int PORT = 9999;
    private static final int MAX_CLIENTS = 100;

    // nickname -> sessão do cliente
    private final ConcurrentMap<String, ClientSession> clients = new ConcurrentHashMap<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(MAX_CLIENTS);

    public static void main(String[] args) {
        new ChatServer().start();
    }

    public void start() {
        try (ServerSocket serverSocket =
                     new ServerSocket(PORT, 50, InetAddress.getByName("0.0.0.0"))) {

            System.out.println("ChatServer iniciado na porta " + PORT);

            while (true) {
                Socket socket = serverSocket.accept();
                pool.execute(new ClientHandler(socket));
            }
        } catch (IOException e) {
            System.err.println("Erro no servidor: " + e.getMessage());
        }
    }

    // ===================== BROADCAST =====================
    private void broadcast(String message) {
        clients.values().forEach(session -> session.enqueue(message));
    }

    private void broadcast(String from, String message) {
        broadcast(from + ": " + message);
    }

    private void sendPrivate(String from, String to, String message) {
        ClientSession target = clients.get(to);
        if (target != null) {
            target.enqueue("(PM) " + from + ": " + message);
        }
    }

    // ===================== CLIENT HANDLER =====================
    private class ClientHandler implements Runnable {
        private final Socket socket;
        private String nickname;
        private ClientSession session;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            String remote = socket.getRemoteSocketAddress().toString();
            System.out.println("Conectado: " + remote);

            try (BufferedReader in =
                         new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                session = new ClientSession(out);
                session.startWriter();

                session.enqueue("Bem-vindo ao Chat!");
                session.enqueue("Use /nick <nome>, /pm <nick> <msg>, /list, /quit");

                String line;
                while ((line = in.readLine()) != null) {
                    handleCommand(line.trim());
                }

            } catch (IOException e) {
                System.err.println("Erro com cliente " + remote);
            } finally {
                disconnect();
            }
        }

        private void handleCommand(String line) {
            if (line.isEmpty()) return;

            if (line.startsWith("/nick ")) {
                String desired = line.substring(6).trim();
                if (desired.isEmpty()) {
                    session.enqueue("Uso: /nick <nome>");
                    return;
                }

                synchronized (clients) {
                    if (clients.containsKey(desired)) {
                        session.enqueue("Nick já em uso.");
                    } else {
                        if (nickname != null) {
                            clients.remove(nickname);
                        }
                        nickname = desired;
                        clients.put(nickname, session);
                        session.enqueue("Nick definido: " + nickname);
                        broadcast(nickname + " entrou no chat.");
                    }
                }

            } else if (line.equalsIgnoreCase("/list")) {
                session.enqueue("Usuários conectados:");
                clients.keySet().forEach(n -> session.enqueue("- " + n));
                session.enqueue("END");

            } else if (line.startsWith("/pm ")) {
                String[] parts = line.split(" ", 3);
                if (parts.length < 3) {
                    session.enqueue("Uso: /pm <nick> <mensagem>");
                } else if (nickname == null) {
                    session.enqueue("Defina um nick antes.");
                } else if (!clients.containsKey(parts[1])) {
                    session.enqueue("Usuário não encontrado.");
                } else {
                    sendPrivate(nickname, parts[1], parts[2]);
                }

            } else if (line.equalsIgnoreCase("/quit")) {
                session.enqueue("Saindo...");
                disconnect();

            } else {
                if (nickname == null) {
                    session.enqueue("Defina um nick antes.");
                } else {
                    broadcast(nickname, line);
                }
            }
        }

        private void disconnect() {
            if (nickname != null) {
                clients.remove(nickname);
                broadcast(nickname + " saiu do chat.");
            }
            if (session != null) session.stop();
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    // ===================== CLIENT SESSION =====================
    private static class ClientSession {
        private final PrintWriter out;
        private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        private volatile boolean running = true;

        ClientSession(PrintWriter out) {
            this.out = out;
        }

        void enqueue(String msg) {
            try {
                queue.put(msg);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        void startWriter() {
            Thread writer = new Thread(() -> {
                try {
                    while (running) {
                        String msg = queue.take();
                        out.println(msg);
                    }
                } catch (InterruptedException ignored) {
                }
            });
            writer.setDaemon(true);
            writer.start();
        }

        void stop() {
            running = false;
        }
    }
}
