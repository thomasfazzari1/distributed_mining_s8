import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Interface pour l'authentification des workers.
 */
interface Authenticator {
    /**
     * Authentifie un worker.
     *
     * @param in  BufferedReader pour lire les données du worker.
     * @param out PrintWriter pour envoyer des données au worker.
     * @return true si l'authentification est réussie, sinon false.
     * @throws IOException En cas d'erreur d'entrée/sortie.
     */
    boolean authenticate(BufferedReader in, PrintWriter out) throws IOException;
}

/**
 * Interface pour la gestion des workers.
 */
interface WorkerHandler {
    /**
     * Gère un nouveau worker.
     *
     * @param workerSocket Le socket du worker à gérer.
     */
    void handleWorker(Socket workerSocket);
}

/**
 * Interface pour la gestion des commandes.
 */
interface CommandProcessor {
    /**
     * Traite une commande.
     *
     * @param command La commande à traiter.
     * @return true si la commande a été traitée avec succès, sinon false.
     */
    boolean processCommand(String command);
}

/**
 * Implémentation de l'authentification des workers.
 */
class WorkerAuthenticator implements Authenticator {
    private final String password;

    /**
     * Constructeur de l'authentificateur du worker.
     *
     * @param password Le mot de passe envoyé par le worker.
     */
    public WorkerAuthenticator(String password) {
        this.password = password;
    }

    /**
     * Authentifie un worker à partir du mot de passe qu'il fournit.
     *
     * @param in  BufferedReader pour lire les données du worker.
     * @param out PrintWriter pour envoyer des données au worker.
     * @return true si l'authentification est réussie, sinon false.
     * @throws IOException En cas d'erreur d'entrée/sortie.
     */
    @Override
    public boolean authenticate(BufferedReader in, PrintWriter out) throws IOException {
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
}

/**
 * Implémentation de la gestion des workers côté serveur.
 */
class ServerWorkerHandler implements WorkerHandler {
    private final List<Socket> workers;
    private final Authenticator authenticator;
    private final WebService webService;
    private int difficulty;
    private static final Logger logger = Logger.getLogger(ServerWorkerHandler.class.getName());

    /**
     * Constructeur du gestionnaire de workers.
     *
     * @param workers       La liste des workers connectés.
     * @param authenticator L'authentificateur utilisé pour l'authentification des workers.
     * @param webService    Le WebService utilisé pour communiquer avec la webapp.
     */
    public ServerWorkerHandler(List<Socket> workers, Authenticator authenticator, WebService webService) {
        this.workers = workers;
        this.authenticator = authenticator;
        this.webService = webService;
    }

    /**
     * Gère un nouveau worker.
     *
     * @param workerSocket Le socket du worker à gérer.
     */
    @Override
    public void handleWorker(Socket workerSocket) {
        new Thread(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(workerSocket.getInputStream()));
                 PrintWriter out = new PrintWriter(workerSocket.getOutputStream(), true)) {

                if (authenticator.authenticate(in, out)) {
                    synchronized (workers) {
                        workers.add(workerSocket);
                    }
                    handleWorkerCommunication(workerSocket, in);
                } else {
                    logger.info("Worker " + workerSocket.getRemoteSocketAddress() + " a échoué l'authentification");
                    workerSocket.close();
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Erreur lors du traitement du worker : {0}", e.getMessage());
            }
        }).start();
    }

    /**
     * Gère la communication avec un worker.
     *
     * @param workerSocket Le socket du worker.
     * @param in           BufferedReader pour lire les données du worker.
     * @throws IOException En cas d'erreur d'entrée/sortie.
     */
    private void handleWorkerCommunication(Socket workerSocket, BufferedReader in) throws IOException {
        boolean connected = true;
        try {
            logger.info("Worker " + workerSocket.getRemoteSocketAddress() + " connecté avec succès");
            // Boucle de communication avec le worker
            while (connected) {
                String response;
                try {
                    response = in.readLine();
                } catch (IOException e) {
                    connected = false;
                    handleWorkerDisconnection(workerSocket, "La connexion a été interrompue");
                    break;
                }
                if (response != null) {
                    handleWorkerResponse(response);
                } else {
                    connected = false;
                    handleWorkerDisconnection(workerSocket, "Le worker a fermé la connexion");
                }
            }
        } finally {
            cleanWorker(workerSocket);
        }
    }

    /**
     * Gère la réponse d'un worker.
     *
     * @param response La réponse du worker.
     * @throws IOException En cas d'erreur d'entrée/sortie.
     */
    private void handleWorkerResponse(String response) throws IOException {
        if (response.startsWith("FOUND")) {
            processFoundResponse(response);
        } else if (response.startsWith("TESTING")) {
            logger.info(response);
        } else if (response.equalsIgnoreCase("NOPE")) {
            logger.info(response);
        }
    }

    /**
     * Traite la réponse du worker lorsqu'il trouve une solution.
     *
     * @param response La réponse du worker.
     * @throws IOException En cas d'erreur d'entrée/sortie.
     */
    private void processFoundResponse(String response) throws IOException {
        // Extraction des informations de la réponse
        String[] splittedResponse = response.split(" ");
        String nonce = splittedResponse[2];
        String hash = splittedResponse[1];
        String body = String.format("{\"d\":%d,\"n\":\"%s\",\"h\":\"%s\"}", difficulty, nonce, hash);
        // Validation de la solution auprès du WebService
        boolean validateResponse = webService.validateWork(body);
        logger.info("Résultat de la vérification : " + validateResponse);

        if (validateResponse) {
            endMining();
        }
    }

    /**
     * Envoie la commande "CANCELLED" à tous les workers connectés.
     * Permet d'ordonner l'arrêt le minage.
     */
    private void endMining() {
        for (Socket worker : workers) {
            try {
                PrintWriter out = new PrintWriter(worker.getOutputStream(), true);
                out.println("CANCELLED");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Gère la déconnexion d'un worker.
     *
     * @param workerSocket Le socket du worker.
     * @param message      Le message de déconnexion.
     */
    private void handleWorkerDisconnection(Socket workerSocket, String message) {
        logger.info(message);
        removeWorker(workerSocket);
    }

    /**
     * Nettoie les ressources d'un worker.
     *
     * @param workerSocket Le socket du worker.
     */
    private void cleanWorker(Socket workerSocket) {
        try {
            workerSocket.close();
            removeWorker(workerSocket);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Erreur lors du nettoyage du worker : {0}", e.getMessage());
        }
    }

    /**
     * Retire un worker de la liste des workers connectés.
     *
     * @param workerSocket Le socket du worker.
     */
    private void removeWorker(Socket workerSocket) {
        synchronized (workers) {
            workers.remove(workerSocket);
        }
        logger.info("Worker " + workerSocket.getRemoteSocketAddress() + " supprimé");
    }

    /**
     * Définit la difficulté pour le minage.
     *
     * @param difficulty La difficulté du minage.
     */
    public void setDifficulty(int difficulty) {
        this.difficulty = difficulty;
    }
}


/**
 * Implémentation du gestionnaire de commandes.
 */
class ServerCommandProcessor implements CommandProcessor {
    private final Server server;
    private final WorkerHandler workerHandler;

    /**
     * Constructeur du processeur de commandes côté serveur.
     *
     * @param server        L'instance du serveur.
     * @param workerHandler Le gestionnaire de workers.
     */
    public ServerCommandProcessor(Server server, WorkerHandler workerHandler) {
        this.server = server;
        this.workerHandler = workerHandler;
    }

    /**
     * Traite une commande utilisateur.
     *
     * @param command La commande à traiter.
     * @return true si la commande a été traitée avec succès, sinon false.
     */
    @Override
    public boolean processCommand(String command) {
        switch (command.toLowerCase()) {
            case "quit":
                return server.handleQuitCommand();
            case "cancel":
                server.handleCancel();
                break;
            case "status":
                server.handleStatus();
                break;
            case "progress":
                server.handleProgressCommand();
                break;
            case "help":
                server.handleHelp();
                break;
            default:
                if (command.toLowerCase().startsWith("solve")) {
                    handleSolveCommand(command);
                } else {
                    System.out.println("Commande non reconnue : " + command);
                }
                break;
        }
        return true;
    }

    /**
     * Traite la commande de résolution du minage avec une difficulté spécifiée.
     *
     * @param command La commande à traiter.
     */
    private void handleSolveCommand(String command) {
        String[] parts = command.split(" ");
        if (parts.length == 2) {
            try {
                int difficulty = Integer.parseInt(parts[1]);
                ((ServerWorkerHandler) workerHandler).setDifficulty(difficulty);
                // Génération du travail à effectuer à partir de la webapp
                String data = server.getWebService().generateWork(difficulty);

                if (data != null) {
                    // Envoi du nonce aux workers
                    server.sendNonceToWorkers();
                    // Envoi du payload aux workers
                    server.sendDataToWorkers("PAYLOAD " + data);
                    // Envoi de la commande de résolution aux workers
                    server.sendDataToWorkers("SOLVE " + difficulty);
                }
            } catch (NumberFormatException | IOException e) {
                Logger.getLogger(ServerCommandProcessor.class.getName()).log(Level.SEVERE, "Cette difficulté n'existe pas ou erreur lors de la génération du travail : {0}", e.getMessage());
            }
        }
    }
}

/**
 * Classe principale du serveur.
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
    // Authentificateur utilisé pour l'authentification des workers
    private final Authenticator authenticator;
    // Gestionnaire de workers
    private final WorkerHandler workerHandler;
    // Gestionnaire de commandes
    private final CommandProcessor commandProcessor;
    // Logger
    private final Logger logger = Logger.getLogger(Server.class.getName());

    /**
     * Méthode principale pour lancer le serveur.
     *
     * @param args Les arguments de la ligne de commande.
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
     * Constructeur du serveur.
     */
    public Server() {
        String apiKey = System.getenv("API_KEY");
        String password = System.getenv("PASSWORD");
        this.webService = new WebService(apiKey);
        this.workers = new ArrayList<>();
        this.authenticator = new WorkerAuthenticator(password);
        this.workerHandler = new ServerWorkerHandler(workers, authenticator, webService);
        this.commandProcessor = new ServerCommandProcessor(this, workerHandler);
    }

    /**
     * Lance le serveur.
     */
    public void run() {
        new Thread(() -> {
            try (ServerSocket server = new ServerSocket(port)) {
                System.out.printf("Serveur démarré\nPORT : %s\nMOT DE PASSE : %s\n", port, System.getenv("PASSWORD"));
                while (keepGoing) {
                    try {
                        Socket serverAccept = server.accept();
                        workerHandler.handleWorker(serverAccept);
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
     * Lance l'écoute des commandes utilisateur.
     */
    public void runArgs() {
        // Utilisation d'un Scanner pour eviter l'erreur NullPointerException sur la console
        Scanner scanner = new Scanner(System.in);
        new Thread(() -> {
            while (keepGoing) {
                System.out.print("$ ");
                String command = scanner.nextLine();
                if (command == null || command.isEmpty()) break;
                try {
                    keepGoing = commandProcessor.processCommand(command.trim());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /**
     * Termine l'exécution du serveur. Ferme toutes les connexions avec les workers
     * et arrête le programme.
     *
     * @return toujours false
     */
    public boolean handleQuitCommand() {
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
     * Envoie une la commande "CANCELLED" à tous les workers connectés.
     * Permet d'ordonner l'arrêt le minage.
     */
    public void handleCancel() {
        for (Socket worker : workers) {
            try {
                PrintWriter out = new PrintWriter(worker.getOutputStream(), true);
                out.println("CANCELLED");
                logger.info("CANCELLED envoyé au worker " + worker.getRemoteSocketAddress());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Affiche des informations sur les workers connectés.
     */
    public void handleStatus() {
        synchronized (workers) {
            if (workers.isEmpty()) {
                System.out.println("Aucun worker connecté.");
            } else {
                // Liste des workers
                System.out.println("Workers connectés :");
                for (Socket worker : workers) {
                    // Adresse du worker
                    System.out.println(" - " + worker.getRemoteSocketAddress());
                }
            }
        }
    }

    /**
     * Envoie la commande "PROGRESS" à tous les workers connectés pour obtenir
     * des informations sur leur progression.
     */
    public void handleProgressCommand() {
        for (Socket worker : workers) {
            try {
                PrintWriter out = new PrintWriter(worker.getOutputStream(), true);
                out.println("PROGRESS");
                logger.info("PROGRESS envoyé au worker " + worker.getRemoteSocketAddress());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Affiche les différentes commandes disponibles pour l'utilisateur.
     */
    public void handleHelp() {
        System.out.println(" • status - Afficher des informations sur les workers connectés");
        System.out.println(" • solve <d> - Lancer un minage avec une difficulté <d> spécifiée");
        System.out.println(" • cancel - Annuler un minage");
        System.out.println(" • help - Consulter les commandes disponibles");
        System.out.println(" • quit - Arreter le minage en cours et fermer le serveur");
    }

    /**
     * Renvoie le WebService associé au serveur.
     *
     * @return le WebService associé au serveur
     */
    public WebService getWebService() {
        return webService;
    }

    /**
     * Envoie le nonce aux workers pour démarrer le processus de minage.
     */
    public void sendNonceToWorkers() {
        int step = workers.size();
        for (int i = 0; i < step; i++) {
            sendDataToWorker(workers.get(i), "NONCE " + i + " " + step);
        }
    }

    /**
     * Envoie un message à tous les workers connectés.
     *
     * @param message le message à envoyer
     */
    public void sendDataToWorkers(String message) {
        for (Socket worker : workers) {
            sendDataToWorker(worker, message);
        }
    }

    /**
     * Envoie un message à un worker spécifique.
     *
     * @param worker  le worker auquel envoyer le message
     * @param message le message à envoyer
     */
    private void sendDataToWorker(Socket worker, String message) {
        try {
            PrintWriter out = new PrintWriter(worker.getOutputStream(), true);
            out.println(message);
            logger.info(message + " envoyé au worker " + worker.getRemoteSocketAddress());
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Erreur lors de l'envoi au worker {0} : {1}", new Object[]{worker.getRemoteSocketAddress(), e.getMessage()});
        }
    }
}
