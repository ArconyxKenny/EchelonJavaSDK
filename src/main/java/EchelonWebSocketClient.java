import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import javax.websocket.*;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.concurrent.Future;

@ClientEndpoint
public class EchelonWebSocketClient extends Endpoint {



    private String domain;

    Gson gson;

    Session session;
    public EchelonWebSocketClient(String domain)
    {
        this.domain = domain;
        healthCheck();



    }

    public void InitMessage()
    {

        String message = " {\"type\":\"core\",\"method\":\"init\",\"clientType\":\"SDK\",\"id\":\"143ced777ade49a2\",\"apiVersion\":1.9}";
        HashMap<String,Object> init = new HashMap<>();
        init.put("type","core");
        init.put("method","init");
        init.put("clientType","SDK");
        init.put("id","143ced777ade49a2");
        init.put("apiVersion", 1.9);
        String data = getJsonString(init);
        System.out.println("Sending Message " + data);
        Future<Void> future = session.getAsyncRemote().sendText(data);
        System.out.println("Send Message Done " + future.isDone());

    }

    private void healthCheck() {

        HashMap<String,Object> health = new HashMap<>();
        health.put("type","global");
        health.put("method","healthCheck");
        health.put("apiVersion", 1.9);
        String data = getJsonString(health);
        HttpClient client = HttpClient.newHttpClient();


        data = data.replace("{",URLEncoder.encode("{"));
        data = data.replace("}",URLEncoder.encode("}"));
        data = data.replace("\"",URLEncoder.encode(String.valueOf('"')));

        String string = "https://" + domain +"/?data=" + data;
        System.out.println(string.charAt(52));
        System.out.println(string);
        URI uri = URI.create(string);
        System.out.println(uri);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .build();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept(event ->{
            System.out.println(event.body());
        }).join();

    }

    @Override
    public void onOpen(Session session, EndpointConfig config) {
        this.session = session;
        session.addMessageHandler(new WholeTextMsgHandler());
        System.out.println("sent Initialisation");

        HashMap<String,Object> ping = new HashMap<>();
        ping.put("type","ping");

        String gsonData = getJsonString(ping);
        //Future<Void> future = session.getAsyncRemote().sendText(gsonData);
        //System.out.println("Send Message Done " + future.isDone());
        InitMessage();
    }

    public String getJsonString(HashMap<String,?> map)
    {
        gson = new Gson();
        Type typeObject = new TypeToken<HashMap>() {}.getType();
        return gson.toJson(map, typeObject);
    }



    private class WholeTextMsgHandler implements MessageHandler.Whole<String> {
        @Override
        public void onMessage(String message) {
            JsonElement jsonMessage = JsonParser.parseString(message);
            System.out.println("Server Message " + jsonMessage);
            String type = jsonMessage.getAsJsonObject().get("type").getAsString();
            if (type.equals("ping")) {
                HashMap<String,Object> pong = new HashMap<>();
                pong.put("type","core");
                pong.put("method","pong");
                pong.put("apiVersion",1.9);

                String gsonData = getJsonString(pong);
                Future<Void> future = session.getAsyncRemote().sendText(gsonData);
                System.out.println("Send Message Done " + future.isDone());

            }else if (type.equals("ready")){

            }
        }
    }
}
