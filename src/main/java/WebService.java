import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

interface HttpService {
    HttpURLConnection createConnection(URL url, String method) throws IOException;

    String readResponse(BufferedReader in) throws IOException;

    void createRequestBody(HttpURLConnection connection, String body) throws IOException;
}

/**
 * La classe RaizoHttpService est une implémentation de l'interface HttpService
 */
class RaizoHttpService implements HttpService {
    private String header;

    /**
     * Constructeur de la classe HttpServiceImpl.
     *
     * @param header L'en-tête d'autorisation pour les requêtes HTTP.
     */
    public RaizoHttpService(String header) {
        this.header = header;
    }

    /**
     * Crée une connexion HTTP avec les paramètres spécifiés.
     *
     * @param url    L'URL de la requête.
     * @param method La méthode HTTP (GET ou POST).
     * @return La connexion HTTP configurée.
     * @throws IOException En cas d'erreur lors de l'ouverture de la connexion.
     */
    @Override
    public HttpURLConnection createConnection(URL url, String method) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setRequestProperty("Authorization", header);
        return connection;
    }

    /**
     * Lit la réponse du serveur.
     *
     * @param in Le BufferedReader pour lire la réponse du serveur.
     * @return La réponse du serveur sous forme de chaîne.
     * @throws IOException En cas d'erreur lors de la lecture de la réponse.
     */
    @Override
    public String readResponse(BufferedReader in) throws IOException {
        StringBuilder response = new StringBuilder();
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        return response.toString();
    }

    /**
     * Génère le corps de la requête HTTP.
     *
     * @param connection La connexion HTTP.
     * @param body       Le corps de la requête en format JSON.
     * @throws IOException En cas d'erreur lors de l'écriture du corps de la requête.
     */
    @Override
    public void createRequestBody(HttpURLConnection connection, String body) throws IOException {
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = body.getBytes("utf-8");
            os.write(input, 0, input.length);
        }
    }
}

/**
 * Classe permettant la communication avec l'API Raizo afin de
 * générer une tâche ou de valider le résultat d'un minage.
 */
public class WebService {
    // URL de base de l'API
    private final String raizo_url = "https://projet-raizo-idmc.netlify.app/.netlify/functions";
    private HttpService httpService;

    /**
     * Constructeur de la classe WebService.
     *
     * @param key La clé d'API pour l'autorisation.
     */
    public WebService(String key) {
        this.httpService = new RaizoHttpService("Bearer " + key);
    }

    /**
     * Génère une tâche récupérée auprès de l'API en fonction de la difficulté spécifiée.
     *
     * @param difficulty La difficulté du travail à générer.
     * @return La réponse du serveur sous forme de chaîne.
     * @throws IOException En cas d'erreur de communication avec le serveur.
     */
    public String generateWork(int difficulty) throws IOException {
        URL url = new URL(raizo_url + "/generate_work?d=" + difficulty);
        HttpURLConnection connection = httpService.createConnection(url, "GET");

        try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            return httpService.readResponse(in);
        }
    }

    /**
     * Valide le résultat d'un minage auprès de l'API.
     *
     * @param body Le corps de la requête en format JSON.
     * @return True si la validation est réussie, sinon une exception est levée.
     * @throws IOException En cas d'erreur de communication avec le serveur.
     */
    public boolean validateWork(String body) throws IOException {
        URL url = new URL(raizo_url + "/validate_work");

        HttpURLConnection connection = httpService.createConnection(url, "POST");
        connection.setRequestProperty("Content-Type", "application/json;utf-8");
        connection.setDoOutput(true);

        httpService.createRequestBody(connection, body);

        int responseCode = connection.getResponseCode();
        if (responseCode >= 400) {
            throw new IOException();
        }

        try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            httpService.readResponse(in);
        }
        return true;
    }
}
