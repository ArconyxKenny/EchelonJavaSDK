package com.EchelonSDK;

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
        void run(Responses.TwitchResponses.AuthCode code);
    }

    @FunctionalInterface
    public interface onAuthRequired {
        void run(LoginAuthData data);
    }


    static Responses.TwitchResponses.ClientToken clientToken;

    private static void GenerateAuthCode(onGenerateAuthCode code)
    {
        HashMap<String, Object> formData = Echelon.formDataWithToken();
        formData.put("type","twitch");
        formData.put("method","generateAuthCode");
        formData.put("environment", Echelon.currentEnvironment.index);
        formData.put("devToken",Echelon.token);
        formData.put("requestorId",Echelon.deviceID);

        CompletableFuture<Responses.TwitchResponses.AuthCode> red = Utils.ApiRequest(Echelon.getUrl(),formData, Responses.TwitchResponses.AuthCode.class);
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

        Responses.TwitchResponses.ClientToken tokenData = getClientTokenData();

        if (tokenData == null)
        {
            ClientAuthGenerateAuthCode(onAuthRequired);
            return;
        }

        TriggerAuthCompleted(tokenData,true);



    }


    public static void TriggerAuthCompleted(Responses.TwitchResponses.ClientToken tokenData,boolean fromStoredCredentials)
    {
        EchelonWebSocketClient.sendOverridingInitMessage(tokenData.uid);
    }

    public static void setClientTokenData(Responses.TwitchResponses.ClientToken token,boolean fromStoredCredentials)
    {
        clientToken = token;

        TriggerAuthCompleted(clientToken, fromStoredCredentials);
    }

    public static Responses.TwitchResponses.ClientToken getClientTokenData()
    {
        return clientToken;
    }
}
