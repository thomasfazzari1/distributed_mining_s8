import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.math.BigInteger;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Interface pour la gestion des commandes.
 */
interface Command {
    void execute(String message, PrintWriter out);
}

/**
 * Classe ConnectionManager, gère la connexion au serveur.
 */
class ConnectionManager {
    private Socket server;
    private static final Logger logger = Logger.getLogger(ConnectionManager.class.getName());

    /**
     * Gère la connexion du worker au serveur.
     *
     * @param address Adresse du serveur.
     * @param port    Port du serveur.
     * @return Socket connecté au serveur.
     * @throws IOException Si une erreur d'E/S survient lors de la connexion.
     */
    public Socket connect(String address, int port) throws IOException {
        InetAddress remoteAddress = InetAddress.getByName(address);
        server = new Socket(remoteAddress, port);
        logger.log(Level.INFO, "Serveur trouvé - {0}:{1}", new Object[]{address, port});
        return server;
    }
}

/**
 * Classe Miner, gère le minage.
 */
class Miner {
    public String api_response;
    boolean keepSolving = false;
    int nonceStart;
    int nonceStep;
    BigInteger nonce;
    private static final String HASH_ALGO = "SHA-256";
    private static final Logger logger = Logger.getLogger(Miner.class.getName());

    /**
     * Résout une tâche de minage.
     *
     * @param dataJson   Les données à miner.
     * @param difficulty La difficulté du minage.
     * @param out        PrintWriter pour communiquer avec le serveur.
     */
    public void solveTask(String dataJson, int difficulty, PrintWriter out) {
        logger.log(Level.INFO, "Minage en cours (difficulté {0})", difficulty);
        String data = cleanJsonResponse(dataJson);
        String target = "0".repeat(difficulty);
        BigInteger step = BigInteger.valueOf(nonceStep);
        nonce = BigInteger.valueOf(nonceStart);
        String hash = "";

        try {
            // MessageDigest pour calculer les hashs
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGO);
            // Boucle du minage
            while (keepSolving) {
                byte[] hashBytes = mine(data, nonce, digest);
                hash = toHex(hashBytes);
                System.out.println("Hash calculé : " + hash);
                if (hash.startsWith(target)) {
                    handleMiningSuccess(out, hash, nonce);
                    break;
                }
                nonce = nonce.add(step);
            }
            handleMiningEnded();
            out.println("READY");
        } catch (NoSuchAlgorithmException e) {
            handleMiningError(e);
        }
    }

    /**
     * Effectue le minage des données un nonce spécifié.
     *
     * @param dataToMine Les données à miner.
     * @param nonce      Valeur du nonce.
     * @param digest     MessageDigest pour calculer le hash.
     * @return Le hash calculé.
     */
    private byte[] mine(String dataToMine, BigInteger nonce, MessageDigest digest) {
        byte[] dataBytes = dataToMine.getBytes(StandardCharsets.UTF_8);
        byte[] nonceToBytes = nonce.toByteArray();
        byte[] payload = Arrays.copyOf(dataBytes, dataBytes.length + nonceToBytes.length);
        System.arraycopy(nonceToBytes, 0, payload, dataBytes.length, nonceToBytes.length);
        return digest.digest(payload);
    }

    /**
     * Traite le succès du minage en envoyant le hash et le nonce au serveur.
     *
     * @param out   PrintWriter pour communiquer avec le serveur.
     * @param hash  Le hash.
     * @param nonce Le nonce trouvé pour réussir le hash.
     */
    private void handleMiningSuccess(PrintWriter out, String hash, BigInteger nonce) {
        String nonceBytesToHex = toHex(nonce.toByteArray());
        logger.log(Level.INFO, "Nonce miné : {0}\nHash : {1}", new Object[]{nonceBytesToHex, hash});
        out.println("FOUND " + hash + " " + nonceBytesToHex);
    }

    /**
     * Traite la fin du processus de minage (annulation ou accomplissement).
     */
    private void handleMiningEnded() {
        if (!keepSolving) {
            logger.log(Level.INFO, "Minage interrompu");
        } else {
            logger.log(Level.INFO, "Tâche terminée");
        }
    }

    /**
     * Gère les erreurs survenues lors du minage.
     *
     * @param e L'exception générée.
     */
    private void handleMiningError(Exception e) {
        logger.log(Level.SEVERE, "Une erreur est survenue lors du minage : {0}", e.getMessage());
        e.printStackTrace();
    }

    /**
     * Convertit un tableau de bytes en une chaîne hexadécimale.
     *
     * @param bytes Le tableau de bytes à convertir.
     * @return La représentation hexadécimale de bytes.
     */
    private String toHex(byte[] bytes) {
        StringBuilder stringBuilder = new StringBuilder();
        for (byte b : bytes) {
            stringBuilder.append(String.format("%02x", b));
        }
        return stringBuilder.toString();
    }

    /**
     * Nettoie la réponse JSON pour ne garder que les données à miner.
     *
     * @param dataJson La réponse JSON à nettoyer.
     * @return Les données à miner.
     */
    private String cleanJsonResponse(String dataJson) {
        int startIndex = dataJson.indexOf("\"data\":\"") + 8;
        int endIndex = dataJson.indexOf("\"", startIndex);
        return dataJson.substring(startIndex, endIndex);
    }

    //region setters
    public void setNonceStart(int nonceStart) {
        this.nonceStart = nonceStart;
    }

    public void setNonceStep(int nonceStep) {
        this.nonceStep = nonceStep;
    }

    public void setKeepSolving(boolean keepSolving) {
        this.keepSolving = keepSolving;
    }
    //endregion
}

/**
 * Classe MessageHandler, gère le traitement des messages reçus du serveur.
 */
class MessageHandler {
    // Map pour associer à chaque type de message une implémentation spécifique de l'interface Command
    private final Map<String, Command> commands = new HashMap<>();
    private final Miner miner;
    private static final Logger logger = Logger.getLogger(MessageHandler.class.getName());

    /**
     * Constructeur de la classe MessageHandler.
     *
     * @param miner Instance de la classe Miner pour effectuer les opérations de minage.
     */
    public MessageHandler(Miner miner) {
        this.miner = miner;
        // Enregistrement des commandes possibles
        // Utilisation d'expressions lambda pour définir le comportement de la méthode execute
        commands.put("WHO_ARE_YOU_?", (message, out) -> out.println(new Scanner(System.in).nextLine()));
        commands.put("GIMME_PASSWORD", (message, out) -> out.println(new Scanner(System.in).nextLine()));
        commands.put("HELLO_YOU", (message, out) -> out.println(new Scanner(System.in).nextLine()));
        commands.put("QUIT", (message, out) -> miner.setKeepSolving(false));
        commands.put("STATUS", (message, out) -> out.println("STATUS_OK"));
        commands.put("PROGRESS", (message, out) -> {
            if (miner.keepSolving) {
                out.println("TESTING " + miner.nonce);
            } else {
                out.println("NOPE");
            }
        });
        commands.put("CANCELLED", (message, out) -> {
            miner.setKeepSolving(false);
            out.println("READY");
        });
        // Enregistrement des commandes nécessitant un traitement spécifique
        commands.put("NONCE", this::handleNonceCommand);
        commands.put("PAYLOAD", this::handlePayloadCommand);
        commands.put("SOLVE", this::handleSolveCommand);
    }

    /**
     * Traite les messages reçus du serveur.
     *
     * @param in  BufferedReader pour lire les messages du serveur.
     * @param out PrintWriter pour envoyer les réponses au serveur.
     */
    public void handleMessages(BufferedReader in, PrintWriter out) {
        new Thread(() -> {
            try {
                String message;
                while ((message = in.readLine()) != null) {
                    System.out.println(message);
                    Command command = commands.getOrDefault(message.split(" ")[0], this::handleDefault);
                    command.execute(message, out);
                }
                System.out.println("Le serveur a fermé la connexion.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Traite la commande "NONCE" du serveur pour définir le début et le step du nonce.
     *
     * @param message Le message contenant les paramètres pour le nonce.
     * @param out     PrintWriter pour communiquer avec le serveur.
     */
    private void handleNonceCommand(String message, PrintWriter out) {
        String[] parts = message.split(" ");
        miner.setNonceStart(Integer.parseInt(parts[1]));
        miner.setNonceStep(Integer.parseInt(parts[2]));
        System.out.println("Début : " + miner.nonceStart + " Step : " + miner.nonceStep);
    }

    /**
     * Traite la commande "PAYLOAD" du serveur pour définir la réponse de l'API.
     *
     * @param message Le message contenant la réponse de l'API.
     * @param out     PrintWriter pour communiquer avec le serveur.
     */
    private void handlePayloadCommand(String message, PrintWriter out) {
        String[] parts = message.split(" ");
        // Utilisation du setter de Miner pour définir la réponse de l'API
        miner.api_response = parts[1];
    }

    /**
     * Traite la commande "SOLVE" du serveur pour lancer le minage.
     *
     * @param message Le message contenant la commande solve d avec la difficulté d du minage.
     * @param out     PrintWriter pour communiquer avec le serveur.
     */
    private void handleSolveCommand(String message, PrintWriter out) {
        String[] parts = message.split(" ");
        int difficulty = Integer.parseInt(parts[1]);
        miner.setKeepSolving(true);
        new Thread(() -> miner.solveTask(miner.api_response, difficulty, out)).start();
    }

    /**
     * Traite les commandes non reconnues.
     *
     * @param message Le message contenant la commande non reconnue.
     * @param out     PrintWriter pour communiquer avec le serveur.
     */
    private void handleDefault(String message, PrintWriter out) {
        if (!Objects.equals(message, "OK")) {
            logger.log(Level.WARNING, "Commande non reconnue : " + message);
        }
    }
}

/**
 * Classe principale Worker, représente le client qui se connecte au serveur pour effectuer le minage.
 */
public class Worker {
    /**
     * Point d'entrée du programme.
     *
     * @param args Arguments de la ligne de commande (non utilisés).
     */
    public static void main(String[] args) {
        Worker worker = new Worker();
        worker.run();
    }

    /**
     * Lance l'exécution du client Worker.
     */
    public void run() {
        ConnectionManager connectionManager = new ConnectionManager();
        Miner miner = new Miner();
        MessageHandler messageHandler = new MessageHandler(miner);

        try {
            Socket server = connectionManager.connect("localhost", 1337);
            BufferedReader input = new BufferedReader(new InputStreamReader(server.getInputStream()));
            PrintWriter output = new PrintWriter(server.getOutputStream(), true);
            messageHandler.handleMessages(input, output);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
