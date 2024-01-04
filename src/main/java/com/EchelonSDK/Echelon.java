package com.EchelonSDK;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.websocket.MessageHandler;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

public class Echelon {


    public static final Logger logger = LogManager.getLogger();
    private enum DOMAIN
    {
        DEV("echelon-internal.novembergames.com"),
        PRODUCTION("echelon.novembergames.com");
        final String url;
        DOMAIN(String string)
        {
            url = string;
        }
    }
    public enum Environment
    {
        DEVELOPMENT(0),
        STAGING(1),
        PRODUCTION(2);

        public int index;

        Environment(int index)
        {
            this.index = index;
        }
    }


    @FunctionalInterface
    public interface onSDKInitialisation {
        void run(boolean success, String error);
    }





    public static boolean enabled;
    public static int gameId;
    public static String rewardsDirectory;
    public static boolean interactiveControlsEnabled;
    public static Responses.InteractiveControl[] interactiveControls;


    public static Environment currentEnvironment;

    EchelonWebSocketClient client;
    static String token;
    public static String deviceID;


    private static boolean devMode = false;



    private final float version = 1.9f;

    public static Boolean initialised = false;
    public Echelon(Environment environment,String devToken ,boolean devModes,onSDKInitialisation initialisation)
    {
        currentEnvironment = environment;
        token = devToken;
        devMode = devModes;
        deviceID = Utils.getRandomString();
        client = new EchelonWebSocketClient(getDomain());
        getSDKData();
        client.init(() ->{
            Echelon.logger.info("Successfully initialized Echelon");
            initialisation.run(true,"");
            initialised = true;
        });
        Utils.apiVersion = version;

        EchelonWebSocketClient.session.addMessageHandler(new EchelonMessageHandler());

    }


    public void getSDKData()
    {
        HashMap<String,Object> formData = new HashMap<>();
        formData.put("type","developer");
        formData.put("method","initSDK");
        formData.put("token",token);
        CompletableFuture<Responses.SdkData> red = Utils.ApiRequest(Echelon.getUrl(),formData, Responses.SdkData.class);
        red.thenAccept(test->{
            enabled = !test.bypassMode;
            gameId  = test.gameId;
            rewardsDirectory = test.dir;
            interactiveControlsEnabled = test.interactiveControlsEnabled;
            interactiveControls = test.interactiveControls;
        });

    }

    public static  String getDomain()
    {
        return devMode ? DOMAIN.DEV.url: DOMAIN.PRODUCTION.url;
    }
    public static void main(String[] args) {

    }
    public static String getUrl()
    {
        return "https://"+getDomain()+"/";
    }


    public static HashMap<String,Object> formDataWithToken()
    {
        HashMap<String,Object> formData = new HashMap<>();
        formData.put("token",token);
        return formData;
    }



    public static class EchelonMessageHandler implements javax.websocket.MessageHandler.Whole<String> {

    @Override
    public void onMessage(String message) {
        JsonElement jsonElement = JsonParser.parseString(message);
        Echelon.logger.info("Server Message " + jsonElement);
        JsonObject jsonMessage = jsonElement.getAsJsonObject();
        String type = jsonMessage.get("type").getAsString();

        switch (type) {
            case "ping" -> {
                HashMap<String, Object> pong = new HashMap<>();
                pong.put("type", "core");
                pong.put("method", "pong");
                pong.put("apiVersion", 1.9);
                String gsonData = Utils.getJsonString(pong);
                EchelonWebSocketClient.sendMessage(gsonData);
            }
            case "ready" -> {

            }
            case "twitchAuthCompleted" -> {
                JsonObject userData = jsonMessage.getAsJsonObject("userData");
                Responses.TwitchResponses.ClientToken token = new Responses.TwitchResponses.ClientToken();
                token.success = true;
                token.apiVersion = userData.get("apiVersion").getAsFloat();
                token.id = userData.get("id").toString();
                token.uid = userData.get("uid").toString();
                token.name = userData.get("name").toString();
                token.tokenVersion = userData.get("tokenVersion").getAsFloat();
                EchelonTwitchController.setClientTokenData(token,false);
            }
        }
    }
}
}
