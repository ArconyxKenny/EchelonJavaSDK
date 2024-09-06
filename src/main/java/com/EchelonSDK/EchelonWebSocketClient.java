package com.EchelonSDK;
import com.EchelonSDK.Responses.TwitchResponses;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

;
import javax.websocket.*;
import java.io.*;
import java.net.URI;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;



import static com.EchelonSDK.Echelon.*;

@ClientEndpoint
public class EchelonWebSocketClient extends Endpoint {


    Session session;
    static EchelonWebSocketClient client;
    public EchelonWebSocketClient(String domain)
    {
        client = this;
        this.domain = domain;
    }


    @Override
    public void onClose(Session session, CloseReason closeReason) {
        INSTANCE.logger.error("Socket Connection Closes");
        INSTANCE.logger.error("Close Reason " + closeReason.toString());
        connected = false;
        if (healthCheck())
        {
            init(()-> INSTANCE.logger.info("Reconnected"));
        }
        super.onClose(session, closeReason);
    }


    @Override
    public void onError(Session session, Throwable thr) {
        INSTANCE.logger.warn("Error Occurred");
        INSTANCE.logger.warn(thr.toString());
        thr.printStackTrace();

        super.onError(session, thr);
    }


    @Override
    public void onOpen(Session session, EndpointConfig config) {
            System.out.println("Connection Opened");
    }

    @FunctionalInterface
    public interface onConnectSocket{
        void run();
    }

    private String domain;

    Gson gson;

    static String currentSocketID;

    public static boolean connected;
    public static boolean connecting;



    public void init(onConnectSocket onConnectSocket)
    {
        if (connecting || connected)
        {
            INSTANCE.logger.warn("Socket is already connecting or reconnecting, Ignoring Init call...");
            return;
        }
        connecting = true;
        URI uri = URI.create("wss://" + getDomain() + "/");
        INSTANCE.logger.info("Creating WebSocket Container");

        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        INSTANCE.logger.info("Created WebSocket Container Successfully");
        INSTANCE.logger.info("Connecting to server");
        try {
            session = container.connectToServer(this,ClientEndpointConfig.Builder.create().build(),uri );
        } catch (DeploymentException | IOException e) {
            throw new RuntimeException(e);
        }

        INSTANCE.logger.info("Connected to server, sending init message");
        client.session.addMessageHandler(new Echelon.MessageHandler());
        sendSocketInitMessage();
        onConnectSocket.run();
        connecting = false;
        connected = true;



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

            sendMessage(data);
            INSTANCE.logger.info("Sent Init Message");
        }
    }

    public  void sendOverridingInitMessage(String newID)
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

    public void sendMessage(String message)
    {
        session.getAsyncRemote().sendText(message, handle ->
        {
            if (handle.isOK())
            {
                INSTANCE.logger.info("Sent Message Successfully");
            }
        });
    }

    public boolean healthCheck() {

        HashMap<String,Object> health = new HashMap<>();
        health.put("type","global");
        health.put("method","healthCheck");
        health.put("apiVersion", 1.9);
        String data = Utils.getJsonString(health);

        try(CloseableHttpClient client = HttpClients.createDefault())
        {
            data = Utils.encodeURL(data);

            String string = "https://" + domain +"/?data=" + data;
            URI uri = URI.create(string);
            HttpGet request = new HttpGet(uri);

            HttpResponse response =  client.execute(request);
            INSTANCE.logger.warn(response.getEntity().getContent().toString());

            StringBuilder builder = new StringBuilder();
            InputStream stream = response.getEntity().getContent();
            try (Reader reader = new BufferedReader(new InputStreamReader
                    (stream, StandardCharsets.UTF_8))) {
                int c;
                while ((c = reader.read()) != -1) {
                    builder.append((char) c);
                }
            }
            String stringResponse = builder.toString();
            HashMap<String, Object> jsonBody = new Gson().fromJson(
                    stringResponse, new TypeToken<HashMap<String, Object>>() {}.getType()
            );
            return (boolean) jsonBody.get("success");


        } catch (IOException e) {
            throw new RuntimeException(e);
        }




    }







}
