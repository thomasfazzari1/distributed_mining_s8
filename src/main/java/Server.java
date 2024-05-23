import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Classe représentant le serveur du programme.
 */
public class Server {
    // Booléen pour contrôler l'exécution du serveur
    private volatile boolean keepGoing = true;
    // Port utilisé par le serveur
    private final int port = 1337;
    // WebService utilisé par le serveur pour communiquer avec l'API Raizo
    private WebService webService;
    // Liste des workers connectés
    private static ArrayList<Socket> workers;
    // Mot de passe du serveur permettant l'authentification des travailleurs
    private String password;
    // Difficulté du minage
    private int difficulty;
    // Logger
    private static final Logger logger = Logger.getLogger(Server.class.getName());

    /**
     * Méthode permettant l'exécution du serveur.
     *
     * @param args Arguments de la ligne de commande (non utilisés ici).
     */
    public static void main(String[] args) {
        Server server = new Server();
        try {
            server.run();
            server.runArgs();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Constructeur de la classe Server. Initialise les attributs du serveur.
     */
    public Server() {
        // Récupèration de la clé d'API et du mot de passe du serveur depuis les variables d'environnement
        String apiKey = System.getenv("API_KEY");
        this.password = System.getenv("PASSWORD");
        webService = new WebService(apiKey);
        workers = new ArrayList<>();
    }

    /**
     * Démarre le serveur et gére les connexions entrantes.
     */
    public void run() {
        new Thread(() -> {
            try (ServerSocket server = new ServerSocket(port)) {
                System.out.printf("Serveur démarré\nPORT : %s\nMOT DE PASSE : %s\n", port, password);

                while (keepGoing) {
                    try {
                        Socket serverAccept = server.accept();
                        handleNewWorker(serverAccept);
                    } catch (IOException e) {
                        logger.log(Level.SEVERE, "Erreur lors de l'acceptation du worker : {0}", e.getMessage());
                    }
                }
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Erreur lors de la création du serveur : {0}", e.getMessage());
            }
        }).start();
    }

    /**
     * Traite un nouveau worker connecté.
     *
     * @param workerSocket Socket du nouveau worker.
     */
    private void handleNewWorker(Socket workerSocket) {
        new Thread(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(workerSocket.getInputStream()));
                 PrintWriter out = new PrintWriter(workerSocket.getOutputStream(), true)) {

                if (authenticateWorker(in, out)) {
                    synchronized (workers) {
                        workers.add(workerSocket);
                    }
                    handleWorker(workerSocket, in);
                } else {
                    System.out.println("Worker " + workerSocket.getRemoteSocketAddress() + " a échoué l'authentification");
                    System.out.print("$ ");
                    workerSocket.close();
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Erreur lors du traitement du worker : {0}", e.getMessage());
            }
        }).start();
    }

    /**
     * Authentifie un worker.
     *
     * @param in  BufferedReader pour recevoir les données du worker.
     * @param out PrintWriter pour envoyer des données au worker.
     * @return true si l'authentification réussit, sinon false.
     * @throws IOException En cas d'erreur de lecture/écriture.
     */
    private boolean authenticateWorker(BufferedReader in, PrintWriter out) throws IOException {
        System.out.println("\nAuthentification du worker " + in + " en cours");
        System.out.print("$ ");
        out.println("WHO_ARE_YOU_?");
        String response = in.readLine();
        if ("ITS_ME".equals(response)) {
            out.println("GIMME_PASSWORD");
            response = in.readLine();
            if (("PASSWD " + password).equals(response)) {
                out.println("HELLO_YOU");
                response = in.readLine();
                if ("READY".equals(response)) {
                    out.println("OK");
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Traite les données d'un worker connecté.
     *
     * @param client Socket du worker connecté.
     * @param in     BufferedReader pour recevoir les données du worker.
     */
    private void handleWorker(Socket client, BufferedReader in) {
        boolean connected = true;

        try {
            System.out.println("\nWorker " + client.getRemoteSocketAddress() + " connecté avec succès");
            System.out.print("$ ");

            while (connected) {
                String response;
                try {
                    response = in.readLine();
                } catch (IOException e) {
                    connected = false;
                    handleWorkerDisconnection(client, "La connexion a été interrompue");
                    break;
                }
                if (response != null) {
                    handleWorkerResponse(response);
                } else {
                    connected = false;
                    handleWorkerDisconnection(client, "Le worker a fermé la connexion");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            cleanWorker(client);
        }
    }

    /**
     * Traite la réponse d'un worker.
     *
     * @param response Réponse du worker.
     * @throws IOException En cas d'erreur de traitement.
     */
    private void handleWorkerResponse(String response) throws IOException {
        if (response.startsWith("FOUND")) {
            processFoundResponse(response);
        } else if (response.startsWith("TESTING")) {
            System.out.println("$ " + response);
        } else if (response.equalsIgnoreCase("NOPE")) {
            System.out.println(response);
        }
    }

    /**
     * Traite la réponse "FOUND" d'un worker.
     *
     * @param response Réponse de type "FOUND".
     * @throws IOException En cas d'erreur de traitement.
     */
    private void processFoundResponse(String response) throws IOException {
        String[] splittedResponse = response.split(" ");
        String nonce = splittedResponse[2];
        String hash = splittedResponse[1];
        String body = String.format("{\"d\":%d,\"n\":\"%s\",\"h\":\"%s\"}", difficulty, nonce, hash);

        try {
            boolean validateResponse = webService.validateWork(body);
            System.out.println("Résultat de la vérification : " + validateResponse);

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Erreur lors de la validation du travail : " + e.getMessage());
        }
    }

    /**
     * Gère la déconnexion d'un worker.
     *
     * @param client  Socket du worker déconnecté.
     * @param message Message de déconnexion.
     */
    private void handleWorkerDisconnection(Socket client, String message) {
        System.out.println(message);
        removeWorker(client);
    }

    /**
     * Nettoie les ressources d'un worker.
     *
     * @param client Socket du worker.
     */
    private void cleanWorker(Socket client) {
        try {
            client.close();
            removeWorker(client);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Lit les commandes saisies par l'utilisateur.
     */
    public void runArgs() {
        Scanner scanner = new Scanner(System.in);
        new Thread(() -> {
            while (keepGoing) {
                System.out.print("$ ");
                String command = scanner.nextLine();
                if (command == null || command.isEmpty()) break;
                try {
                    keepGoing = processCommand(command.trim());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /**
     * Traite les commandes saisies par l'utilisateur.
     *
     * @param cmd Commande.
     * @return true si le serveur doit poursuivre l'éxécution, false sinon.
     */
    private boolean processCommand(String cmd) {
        switch (cmd.toLowerCase()) {
            case "quit":
                return handleQuitCommand();
            case "cancel":
                handleCancel();
                break;
            case "status":
                handleStatus();
                break;
            case "progress":
                handleProgressCommand();
                break;
            case "help":
                handleHelp();
                break;
            default:
                if (cmd.toLowerCase().startsWith("solve")) {
                    handleSolveCommand(cmd);
                } else {
                    System.out.println("Commande non reconnue : " + cmd);
                }
                break;
        }
        return true;
    }

    /**
     * Traite la commande "quit" (fermeture du serveur).
     *
     * @return false pour indiquer l'arrêt du serveur.
     */
    private boolean handleQuitCommand() {
        keepGoing = false;
        for (Socket worker : workers) {
            try {
                worker.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.exit(1);
        return false;
    }

    /**
     * Envoie la commande "CANCELLED" à tous les workers connectés.
     */
    private void handleCancel() {
        for (Socket worker : workers) {
            try {
                PrintWriter out = new PrintWriter(worker.getOutputStream(), true);
                out.println("CANCELLED");
                System.out.println("CANCELLED envoyé au worker " + worker.getRemoteSocketAddress());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Traite la commande "status" (affiche les différents workers connectés).
     */
    private void handleStatus() {
        synchronized (workers) {
            if (workers.isEmpty()) {
                System.out.println("Aucun worker connecté.");
            } else {
                System.out.println("Workers connectés :");
                for (Socket worker : workers) {
                    System.out.println(" - " + worker.getRemoteSocketAddress());
                }
            }
        }
    }

    /**
     * Envoie la commande "PROGRESS" à tous les workers connectés.
     */
    private void handleProgressCommand() {
        for (Socket worker : workers) {
            try {
                PrintWriter out = new PrintWriter(worker.getOutputStream(), true);
                out.println("PROGRESS");
                System.out.println("PROGRESS envoyé au worker " + worker.getRemoteSocketAddress());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Affiche les commandes disponibles.
     */
    private void handleHelp() {
        System.out.println(" • status - Afficher des informations sur les workers connectés");
        System.out.println(" • solve <d> - Lancer un minage avec une difficulté <d> spécifiée");
        System.out.println(" • cancel - Annuler un minage");
        System.out.println(" • help - Consulter les commandes disponibles");
        System.out.println(" • quit - Arreter le minage en cours et fermer le serveur");
    }

    /**
     * Traite la commande "solve" et lance un minage avec une difficulté spécifiée.
     *
     * @param cmd Commande solve d complète, comprenant la difficulté d spécifiée.
     */
    private void handleSolveCommand(String cmd) {
        String[] parts = cmd.split(" ");
        if (parts.length == 2) {
            try {
                difficulty = Integer.parseInt(parts[1]);
                String data = fetchAPIData(difficulty);

                if (data != null) {
                    sendNonceToWorkers();
                    sendDataToWorkers("PAYLOAD " + data);
                    sendDataToWorkers("SOLVE " + difficulty);
                }
            } catch (NumberFormatException e) {
                logger.log(Level.SEVERE, "Cette difficulté n'existe pas");
            }
        }
    }

    /**
     * Récupère les données renvoyées par le WebService.
     *
     * @param difficulty La difficulté de minage spécifiée.
     * @return Les données récupérées depuis le WebService.
     */
    private String fetchAPIData(int difficulty) {
        try {
            String data = webService.generateWork(difficulty);
            System.out.println("Réponse : " + data);
            return data;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Une erreur est survenue lors de la récupération des données : " + e.getMessage());
            return null;
        }
    }

    /**
     * Envoie un nonce à tous les worker connectés.
     * La méthode calcule également le pas et l'index pour chaque worker.
     */
    private void sendNonceToWorkers() {
        int step = workers.size();
        for (int i = 0; i < step; i++) {
            sendDataToWorker(workers.get(i), "NONCE " + i + " " + step);
        }
    }

    /**
     * Envoie la data à tous les workers connectés.
     *
     * @param message La data à envoyer.
     */
    private void sendDataToWorkers(String message) {
        for (Socket worker : workers) {
            sendDataToWorker(worker, message);
        }
    }

    /**
     * Envoie la data à un worker spécifique.
     *
     * @param worker  Le socket du worker.
     * @param message La data à envoyer.
     */
    private void sendDataToWorker(Socket worker, String message) {
        try {
            PrintWriter out = new PrintWriter(worker.getOutputStream(), true);
            out.println(message);
            System.out.println(message + " envoyé au worker " + worker.getRemoteSocketAddress());
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Une erreur est survenue lors de l'envoi au worker " + worker.getRemoteSocketAddress() + " : " + e.getMessage());
        }
    }

    /**
     * Gère la déconnexion d'un worker.
     *
     * @param client Le socket du worker à déconnecter.
     */
    private void removeWorker(Socket client) {
        workers.remove(client);
        try {
            client.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Worker " + client.getRemoteSocketAddress() + " supprimé");
        System.out.print("$ ");
    }
}