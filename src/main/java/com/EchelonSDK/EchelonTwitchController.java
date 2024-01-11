package com.EchelonSDK;

import com.EchelonSDK.Responses.TwitchResponses;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

public class EchelonTwitchController {
    public static class LoginAuthData
    {
        public boolean success;
        public String authCode;
        public String webUrl;
        //public Sprite QRCode;//TODO re implement QR Code
        public String qRCodeUrl;
    }

    @FunctionalInterface
    interface onGenerateAuthCode {
        void run(TwitchResponses.AuthCode code);
    }

    @FunctionalInterface
    public interface onAuthRequired {
        void run(LoginAuthData data);
    }

    @FunctionalInterface
    public interface onAuthComplete{

        void run(TwitchResponses.ClientToken tokenData, boolean fromStoredCredentials);
    };


    public static ArrayList<onAuthComplete> onTwitchAuthCompleted = new ArrayList<>();
    static TwitchResponses.ClientToken clientToken;

    private static void GenerateAuthCode(onGenerateAuthCode code)
    {
        HashMap<String, Object> formData = Echelon.formDataWithToken();
        formData.put("type","twitch");
        formData.put("method","generateAuthCode");
        formData.put("environment", Echelon.currentEnvironment.index);
        formData.put("devToken",Echelon.token);
        formData.put("requestorId",Echelon.deviceID);

        CompletableFuture<TwitchResponses.AuthCode> red = Utils.apiRequest(Echelon.getUrl(),formData, TwitchResponses.AuthCode.class);
        red.thenAccept(code::run).join();

    }


    public static void ClientAuthGenerateAuthCode(onAuthRequired required)
    {
        GenerateAuthCode(code->{

        if(!code.success)
        {
         Echelon.logger.info("Error generating AuthCode");
         required.run(new LoginAuthData());
        }

        Echelon.logger.info("Got AuthCode: " + code.code);
        LoginAuthData authData = new LoginAuthData();
        authData.success = true;
        authData.authCode = code.code;
        authData.webUrl = "https://" + Echelon.getDomain() +"/" + Echelon.rewardsDirectory + "/?code="+code.code;
        authData.qRCodeUrl = "https://" + Echelon.getDomain() +"/" +  Echelon.rewardsDirectory + "/?fromQR=true&code="+code.code;
        required.run(authData);





        });
    }

    public static void AuthWithTwitch(onAuthRequired onAuthRequired)
    {
        if (!Echelon.initialised) return;

        TwitchResponses.ClientToken tokenData = getClientTokenData();

        if (tokenData == null)
        {
            ClientAuthGenerateAuthCode(onAuthRequired);
            return;
        }

        TriggerAuthCompleted(tokenData,true);



    }


    public static void TriggerAuthCompleted(TwitchResponses.ClientToken tokenData,boolean fromStoredCredentials)
    {
        for (onAuthComplete complete: onTwitchAuthCompleted)
        {
            complete.run(tokenData,fromStoredCredentials);
        }
        //also override our socket connection device id in server for our twitch id
        EchelonWebSocketClient.sendOverridingInitMessage(tokenData.uid);
    }

    public static void setClientTokenData(TwitchResponses.ClientToken token,boolean fromStoredCredentials)
    {
       setClientTokenData(token,fromStoredCredentials,true);
    }

    public static void setClientTokenData(TwitchResponses.ClientToken token,boolean fromStoredCredentials,boolean triggerAuthEvents)
    {
        //TODO TriggerAuthEvents is only false if Credentials are saved and do not want to call Auth Events as they are not needed
        clientToken = token;
        if (triggerAuthEvents) TriggerAuthCompleted(clientToken, fromStoredCredentials);
    }

    public static boolean clearClientTokenData()
    {
        if(!Echelon.initialised)return false;

        clientToken = null;
        EchelonWebSocketClient.sendOverridingInitMessage("");
        //TODO on Credentials Cleared;
        return true;
    }

    public static TwitchResponses.ClientToken getClientTokenData()
    {
        return clientToken;
    }
}
