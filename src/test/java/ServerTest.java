import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.io.*;
import java.net.Socket;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


public class ServerTest {

    @Mock
    private WebService webService;

    @Mock
    private Authenticator authenticator;

    @Mock
    private WorkerHandler workerHandler;

    @InjectMocks
    private Server server;

    private List<Socket> workers;

    @Test
    public void testServerRun() {
        Server server = new Server();
        Thread serverThread = new Thread(server::run);
        serverThread.start();

        assertDoesNotThrow(() -> Thread.sleep(1000));
        assertTrue(server.isKeepGoing());

        serverThread.interrupt();
    }

    @Test
    void testAuthenticateSuccess() throws IOException {
        BufferedReader in = mock(BufferedReader.class);
        PrintWriter out = mock(PrintWriter.class);

        when(in.readLine()).thenReturn("ITS_ME", "PASSWD test", "READY");
        WorkerAuthenticator authenticator = new WorkerAuthenticator("test");

        boolean result = authenticator.authenticate(in, out);

        assertTrue(result);
        verify(out).println("WHO_ARE_YOU_?");
        verify(out).println("GIMME_PASSWORD");
        verify(out).println("HELLO_YOU");
        verify(out).println("OK");
    }

    @Test
    void testAuthenticateFailure() throws IOException {
        BufferedReader in = mock(BufferedReader.class);
        PrintWriter out = mock(PrintWriter.class);

        when(in.readLine()).thenReturn("ITS_ME", "PASSWD mauvais", "READY");
        WorkerAuthenticator authenticator = new WorkerAuthenticator("test");

        boolean result = authenticator.authenticate(in, out);

        assertFalse(result);
        verify(out).println("WHO_ARE_YOU_?");
        verify(out).println("GIMME_PASSWORD");
        verify(out).println("YOU_DONT_FOOL_ME");
    }

    @Test
    void testHandleHelp() {
        Server server = new Server();
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));

        server.handleHelp();

        assertTrue(outContent.toString().contains("status - Afficher des informations sur les workers connectés"));
        assertTrue(outContent.toString().contains("solve <d> - Lancer un minage avec une difficulté <d> spécifiée"));
        assertTrue(outContent.toString().contains("cancel - Annuler un minage"));
        assertTrue(outContent.toString().contains("help - Consulter les commandes disponibles"));
        assertTrue(outContent.toString().contains("quit - Arreter le minage en cours et fermer le serveur"));
    }

    @Test
    void testHandleStatusNoWorkers() {
        Server server = new Server();
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));

        server.handleStatus();

        assertTrue(outContent.toString().contains("Aucun worker connecté."));
    }
}
