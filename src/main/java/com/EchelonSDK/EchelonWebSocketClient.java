package com.EchelonSDK;

import com.EchelonSDK.Responses.Responses;
import com.EchelonSDK.Responses.TwitchResponses;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import javax.websocket.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;

import static com.EchelonSDK.Echelon.*;

@ClientEndpoint
public class EchelonWebSocketClient extends Endpoint {


    @FunctionalInterface
    public interface onConnectSocket{
        void run();
    }

    private String domain;

    Gson gson;

    static String currentSocketID;
    static Session session;

    public static boolean connected;
    public static boolean connecting;
    public EchelonWebSocketClient(String domain)
    {
        this.domain = domain;




    }


    public void init(onConnectSocket onConnectSocket)
    {
        if (connecting || connected)
        {
            logger.warn("Socket is already connecting or reconnecting, Ignoring Init call...");
            return;
        }
        connecting = true;
        URI uri = URI.create("wss://" + getDomain() + "/");
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        container.setDefaultMaxSessionIdleTimeout(0);
        container.setAsyncSendTimeout(10000000);
        try{
            session = container.connectToServer(this, ClientEndpointConfig.Builder.create().build(),uri);
            sendSocketInitMessage();
            onConnectSocket.run();
            connecting = false;
            connected = true;
        } catch (DeploymentException | IOException e) {
            throw new RuntimeException(e);
        }


    }


    public void sendSocketInitMessage()
    {
        if (healthCheck()){
            TwitchResponses.ClientToken token = EchelonTwitchController.getClientTokenData();
            currentSocketID = token == null ? deviceID: token.uid;
            HashMap<String,Object> init = new HashMap<>();
            init.put("type","core");
            init.put("method","init");
            init.put("clientType","SDK");
            init.put("id",currentSocketID);
            init.put("apiVersion", 1.9);
            String data = Utils.getJsonString(init);
            Echelon.logger.info("Sending Init Message");
            sendMessage(data);
        }
    }

    public static void sendOverridingInitMessage(String newID)
    {
        HashMap<String,Object> override = new HashMap<>();
        override.put("type", "core");
        override.put("method", "overrideId");
        override.put("clientType", "SDK");
        override.put("id", currentSocketID);
        override.put("newId", newID);
        String data = Utils.getJsonString(override);
        sendMessage(data);
    }

    public static void sendMessage(String message)
    {
        session.getAsyncRemote().sendText(message, handle ->{
           if (handle.isOK())
           {
               Echelon.logger.info("Successfully sent Message");
           }else
           {
               handle.getException().printStackTrace();
           }

        });


    }

    public boolean healthCheck() {

        HashMap<String,Object> health = new HashMap<>();
        health.put("type","global");
        health.put("method","healthCheck");
        health.put("apiVersion", 1.9);
        String data = Utils.getJsonString(health);
        HttpClient client = HttpClient.newHttpClient();


        data = Utils.encodeURL(data);

        String string = "https://" + domain +"/?data=" + data;
        URI uri = URI.create(string);
        HttpRequest request = HttpRequest.newBuilder(uri).build();

        try {
            HttpResponse<String> response =  client.send(request, HttpResponse.BodyHandlers.ofString());
            Echelon.logger.info(response.body());
            HashMap<String, Object> jsonBody = new Gson().fromJson(
                    response.body(), new TypeToken<HashMap<String, Object>>() {}.getType()
            );
            return (boolean) jsonBody.get("success");

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public void onOpen(Session session, EndpointConfig config) {
        this.session = session;


    }


    @Override
    public void onClose(Session session, CloseReason closeReason) {
        System.err.println("Socket Connection Closes");
        logger.warn("Close Reason " + closeReason.toString());
        super.onClose(session, closeReason);
    }

    @Override
    public void onError(Session session, Throwable thr) {
        logger.error("Error Occurred");
        logger.error(thr);
        thr.printStackTrace();

        super.onError(session, thr);
    }




}
