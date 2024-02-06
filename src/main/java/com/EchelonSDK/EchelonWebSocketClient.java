package com.EchelonSDK;
import com.EchelonSDK.Responses.TwitchResponses;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;


import javax.websocket.WebSocketContainer;
import java.io.*;
import java.net.URI;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;



import static com.EchelonSDK.Echelon.*;

public class EchelonWebSocketClient implements WebSocketContainer {



    static EchelonWebSocketClient client;
    public EchelonWebSocketClient(String domain)
    {
        super(URI.create("wss://" + getDomain() + "/"));
        client = this;
        this.domain = domain;




    }



    @Override
    public void onOpen(ServerHandshake handshakedata) {

    }

    @Override
    public void onMessage(String message) {
        JsonElement jsonElement = JsonParser.parseString(message);
        Echelon.logger.info("Server Message " + jsonElement);
        JsonObject jsonMessage = jsonElement.getAsJsonObject();
        String type = jsonMessage.get("type").getAsString();

        switch (type) {
            case "ping":
                HashMap<String, Object> pong = new HashMap<>();
                pong.put("type", "core");
                pong.put("method", "pong");
                pong.put("apiVersion", 1.9);
                String gsonData = Utils.getJsonString(pong);
                EchelonWebSocketClient.sendMessage(gsonData);
                break;
            case "ready":
                break;
            case "twitchAuthCompleted":
                JsonObject userData = jsonMessage.getAsJsonObject("userData");
                TwitchResponses.ClientToken token = new TwitchResponses.ClientToken();
                token.success = true;
                token.apiVersion = userData.get("apiVersion").getAsFloat();
                token.id = userData.get("id").toString();
                token.uid = userData.get("uid").getAsString();
                token.name = userData.get("name").toString();
                token.tokenVersion = userData.get("tokenVersion").getAsFloat();
                EchelonTwitchController.setClientTokenData(token,false);
                break;
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.err.println("Socket Connection Closes");
        logger.warn("Close Reason " + reason);
    }

    @Override
    public void onError(Exception ex) {
        logger.warn("Error Occurred");
        logger.warn(ex.toString());
        ex.printStackTrace();

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
            logger.warn("Socket is already connecting or reconnecting, Ignoring Init call...");
            return;
        }
        connecting = true;

        logger.info("Creating WebSocket Container");
        logger.info("Created WebSocket Container Successfully");

        logger.info("Connecting to server");
        try {
            this.connectBlocking();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        logger.info("Connected to server, sending init message");
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
            Echelon.logger.info("Sent Init Message");
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
        if (!client.isClosed()) {
            client.send(message);

        }else
        {
            logger.warn("Client is not connected");
        }
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
            Echelon.logger.warn(response.getEntity().getContent().toString());

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
