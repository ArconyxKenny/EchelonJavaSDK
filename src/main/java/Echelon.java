import javax.websocket.ClientEndpointConfig;
import javax.websocket.ContainerProvider;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import java.net.URI;

public class Echelon {


    EchelonWebSocketClient client;

    public Echelon()
    {

        client = new EchelonWebSocketClient("echelon-internal.novembergames.com");
        URI uri = URI.create("wss://echelon-internal.novembergames.com/");
        System.out.println(uri);
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        container.setDefaultMaxSessionIdleTimeout(60000);
        container.setAsyncSendTimeout(10000000);
        try (Session session = container.connectToServer(client, ClientEndpointConfig.Builder.create().build(),uri);){

        client.session = session;
        while(session.isOpen()){
            continue;
        }
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }



    public static void main(String[] args) {
       new Echelon();
    }
}
