package com.EchelonSDK;

import com.EchelonSDK.EchelonTwitchController.onAuthComplete;
import com.EchelonSDK.Responses.Responses;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jdk.jshell.execution.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;

import static com.EchelonSDK.Responses.TwitchResponses.*;

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

        public final int index;

        Environment(int index)
        {
            this.index = index;
        }
    }
    @FunctionalInterface
    public interface onSDKInitialisation {
        void run(boolean success, String error);
    }
    @FunctionalInterface
    public interface onStatLeaderboard {
        void run(Responses.StatLeaderboard leaderboard);
    }

    @FunctionalInterface
    public interface onDashboardStatusChange {
        void run(boolean connected);
    }

    @FunctionalInterface
    public interface onDashboardUrl {
        void run(String url);
    }


    @FunctionalInterface
    public interface onPlayersRewardsCheck {

        void run(Responses.PlayersRewards rewards);
    }

    @FunctionalInterface
    public interface  onMetaResponse {
        void run(Utils.APIResponse response);
    }

    @FunctionalInterface
    public interface onPlayerRewardClaimed {
        void run(String playerUID,String rewardId,String rewardUID,String value);
    }


    public interface onPlayerStat{

        void run(Responses.PlayerStat statData);

    }

    public interface onRedeemGiveAwayPoints{

        void run(Responses.GiveWayPoints points);
    }


    public ArrayList<onAuthComplete> onAuthCompletedEvents = new ArrayList<>();
    public onDashboardStatusChange onDashboardStatusChange;

    public onPlayerRewardClaimed onPlayerRewardClaimed;
    private boolean isDashboardConnected;
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

    TimerTask dashboardStatusCheckRoutine;

    TimerTask playerRewardsCheckRoutine;
    private final float version = 1.9f;

    public static Boolean initialised = false;
    private ArrayList<String> players;
    private final HashMap<String,ArrayList<String>> rewardsBeingUnlocked = new HashMap<>();
    public Echelon(Environment environment,String devToken ,boolean devModes,onSDKInitialisation initialisation)
    {
        currentEnvironment = environment;
        token = devToken;
        devMode = devModes;
        deviceID = Utils.getRandomString();
        client = new EchelonWebSocketClient(getDomain());
        getSDKData();
        Timer timer = new Timer();
        client.init(() ->{
            Echelon.logger.info("Successfully initialized Echelon");
            initialisation.run(true,"");
            dashboardStatusCheckRoutine = new TimerTask() {
                @Override
                public void run() {
                    dashboardStatusCheckRoutine().join();
                }
            };
            timer.schedule(dashboardStatusCheckRoutine,3000,3000);
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
        CompletableFuture<Responses.SdkData> red = Utils.apiRequest(Echelon.getUrl(),formData, Responses.SdkData.class);
        red.thenAccept(resp->{
            enabled = !resp.bypassMode;
            gameId  = resp.gameId;
            rewardsDirectory = resp.dir;
            interactiveControlsEnabled = resp.interactiveControlsEnabled;
            interactiveControls = resp.interactiveControls;
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


    public void getStatLeaderboard(String statName, int amount, onStatLeaderboard onStatLeaderboard)
    {
        HashMap<String,Object> formData = formDataWithToken();
        formData.put("type","player");
        formData.put("method","getStatLeaderboard");
        formData.put("stat",statName);
        formData.put("amount",amount);
        formData.put("environment",currentEnvironment.index);
        CompletableFuture<Responses.StatLeaderboard> leaderboard = Utils.apiRequest(getUrl(),formData, Responses.StatLeaderboard.class);
        leaderboard.thenAccept(onStatLeaderboard::run).join();
    }


    public boolean startPlayerRewardsListener(ArrayList<String> playersToListen)
    {
        if (!initialised) return false;
        Echelon.logger.info("Starting Rewards Listener");

        if (playerRewardsCheckRoutine != null) playerRewardsCheckRoutine.cancel();
        players = playersToListen;
        rewardsBeingUnlocked.clear();
        Timer timer = new Timer();
        playerRewardsCheckRoutine = new TimerTask() {
            @Override
            public void run() {
                PlayerRewardsCheckRoutine().join();
            }
        };
        timer.schedule(playerRewardsCheckRoutine, 5000, 5000);

        return true;
    }


    private CompletableFuture<Boolean> PlayerRewardsCheckRoutine()
    {
        return CompletableFuture.supplyAsync(()->{
            if (!EchelonWebSocketClient.connected)return true;
            if(players.isEmpty()) return true;
            getPlayersRewards(players,rewards->{
            if (!rewards.success)
            {
                Echelon.logger.error("Error getting players reward unlocks");
                return;
            }

            boolean weHaveLocalPLayerData;
            for (int i = 0; i< rewards.players.length; i++)
            {
                if(rewards.players[i].rewards.length == 0)continue;
                weHaveLocalPLayerData = rewardsBeingUnlocked.containsKey(rewards.players[i].uid);
                if(!weHaveLocalPLayerData)rewardsBeingUnlocked.put(rewards.players[i].uid,new ArrayList<>());

                for (int j = 0; j< rewards.players[i].rewards.length;j++)
                {
                    if(!rewards.players[i].rewards[j].claimed)continue;

                    if (!rewardsBeingUnlocked.get(rewards.players[i].uid).contains(rewards.players[i].rewards[j].id))
                    {
                        rewardsBeingUnlocked.get(rewards.players[i].uid).add(rewards.players[i].rewards[j].id);
                        PlayerClaimedReward(rewards.players[i].uid,rewards.players[i].rewards[j].id,rewards.players[i].rewards[j].uid,rewards.players[i].rewards[j].value);
                    }
                }
            }
        }).join();

        return true;
        });
    }



    private void PlayerClaimedReward(String playerUID, String rewardId,String rewardUID,String value)
    {
        CompletableFuture<Utils.APIResponse> clearReward = ClearPlayerReward(playerUID,rewardId,comp ->{

            if(!comp.success)
            {
                Echelon.logger.error("Error Clearing player " + playerUID + " reward " + rewardId);
            }
            rewardsBeingUnlocked.get(playerUID).remove(rewardId);

            if(onPlayerRewardClaimed != null)onPlayerRewardClaimed.run(playerUID,rewardId,rewardUID,value);
            Echelon.logger.info("Player " + playerUID + " unlocked reward: " + rewardId + " with rewardUID " + rewardUID + "with value "+ value);
        });
        clearReward.join();
    }


    public CompletableFuture<Utils.APIResponse> ClearPlayerReward(String playerUID, String rewardId, onMetaResponse onComplete)
    {
        return CompletableFuture.supplyAsync(()->{

            if (!initialised) return null;
            HashMap<String,Object> formData = formDataWithToken();
            formData.put("type","rewards");
            formData.put("method","clearPlayerReward");
            formData.put("playerUID",playerUID);
            formData.put("rewardId",rewardId);
            formData.put("environment",currentEnvironment.index);

            CompletableFuture<Utils.APIResponse> response = Utils.apiRequest(getUrl(),formData, Utils.APIResponse.class);
            Utils.APIResponse res = response.join();
            onComplete.run(res);
            return  res;
        });

    }

    private CompletableFuture<Responses.PlayersRewards> getPlayersRewards(ArrayList<String> playerUIDs,onPlayersRewardsCheck onComplete)
    {
        return CompletableFuture.supplyAsync(()->{


            if (!initialised)return null;
            HashMap<String,Object> formData = formDataWithToken();
            formData.put("type","rewards");
            formData.put("method","getRewardsForPlayers");
            formData.put("playerUIDs",playerUIDs);
            formData.put("environment",currentEnvironment.index);
            CompletableFuture<Responses.PlayersRewards> resp = Utils.apiRequest(getUrl(),formData, Responses.PlayersRewards.class);
            resp.thenAccept(onComplete::run);
            return resp.join();
        });
    }


    public CompletableFuture<Responses.GiveWayPoints> redeemGiveAwayPoints(String redeemerId,String rewardUID,onRedeemGiveAwayPoints onComplete){
        return CompletableFuture.supplyAsync(()->{
            if (!initialised)return null;
            HashMap<String,Object> formData = formDataWithToken();
            formData.put("type","giveaway");
            formData.put("method","redeemPoints");
            formData.put("redeemerId",redeemerId);
            formData.put("rewardUID",rewardUID);
            CompletableFuture<Responses.GiveWayPoints> resp = Utils.apiRequest(getUrl(),formData, Responses.GiveWayPoints.class);
            resp.thenAccept(onComplete::run);
            return resp.join();

        });

    }

    public static void getDashboardURL(onDashboardUrl onComplete,String template)
    {
        if(!initialised){
            onComplete.run("");
            return;
        }

        ClientToken clientToken = EchelonTwitchController.getClientTokenData();

        if (clientToken == null)
        {
            Echelon.logger.warn("Cannot open dashboard, no Authenticated user found!");
            onComplete.run("");
            return;
        }

        //todo ignore dashboard settings
        HashMap<String,Object> formData = new HashMap<>();
        formData.put("type","rewards");
        formData.put("method","generateDashboardToken");
        formData.put("token",token);
        formData.put("playerUID",clientToken.uid);
        formData.put("environment",currentEnvironment.index);
        CompletableFuture<Responses.GenerateDashboardToken> genToken =  Utils.apiRequest(getUrl(),formData, Responses.GenerateDashboardToken.class);
        genToken.thenAccept(tokenData->{
            if(!tokenData.success)
            {
                Echelon.logger.warn("Failed too communicate to Echelon servers");
                onComplete.run("");
                return;
            }
            onComplete.run(getUrl() + rewardsDirectory + "/?token=" + tokenData.token + "&template="+template);

    })
            .join();


    }



    public CompletableFuture<Boolean> dashboardStatusCheckRoutine() {
        return CompletableFuture.supplyAsync(() -> {
            if (!initialised) return true;

            ClientToken token = EchelonTwitchController.getClientTokenData();
            if (token == null || (token.id.isEmpty())) return true;

            HashMap<String,Object> formData = new HashMap<>();
            formData.put("type","rewards");
            formData.put("method","requestPlayerDashboardStatus");
            formData.put("uid", token.uid);

            CompletableFuture<Utils.APIResponse> response =  Utils.apiRequest(getUrl(),formData, Utils.APIResponse.class);
            response.thenAccept(resp->{
                Echelon.logger.info("DashboardStatus Routine Request sent " + resp.toString());
                if (isDashboardConnected != resp.success)dashboardStatusUpdated(resp.success);
                isDashboardConnected = resp.success;
            }).join();

            return true;
        }
    );
    }

    public void dashboardStatusUpdated(boolean isDashboardConnected)
    {
        if (onDashboardStatusChange != null)
        {
        onDashboardStatusChange.run(isDashboardConnected);
        }
    }


    public CompletableFuture<Responses.PlayerStat> addPlayerStat(String playerUID,String statName,float value,onPlayerStat onComplete){
        return addPlayerStat(playerUID,statName,(double)value,onComplete);

    }
    CompletableFuture<Responses.PlayerStat> addPlayerStat(String playerUID,String statName,double value,onPlayerStat onComplete){

        return CompletableFuture.supplyAsync(()->{
        if(!initialised)return null;
        HashMap<String,Object> formData = formDataWithToken();
        formData.put("type","player");
        formData.put("method","addPlayerStat");
        formData.put("playerUID",playerUID);
        formData.put("stat",statName);
        formData.put("value",value);
        formData.put("environment",currentEnvironment.index);

        CompletableFuture<Responses.PlayerStat> resp = Utils.apiRequest(getUrl(),formData, Responses.PlayerStat.class);
        resp.thenAccept(onComplete::run);
        return resp.join();
        });
    }



    public CompletableFuture<Utils.APIResponse> ResetLocalPlayerStats(String playerUID, String statName, onMetaResponse onComplete)
    {

        return CompletableFuture.supplyAsync(()->{
            if(!initialised) return null;

            HashMap<String,Object> formData = formDataWithToken();
            formData.put("type","player");
            formData.put("method","resetPlayerStat");
            formData.put("playerUID",playerUID);
            formData.put("stat",statName);
            formData.put("environment",currentEnvironment.toString());
            CompletableFuture<Utils.APIResponse> resp =  Utils.apiRequest(getUrl(),formData, Utils.APIResponse.class);

            resp.thenAccept(onComplete::run);
            return resp.join();
        });

    }


    public CompletableFuture<Utils.APIResponse> triggerMagicMoment(int id,String playerUID, onMetaResponse onComplete)
    {
        return CompletableFuture.supplyAsync(()->{
        HashMap<String,Object> formData = formDataWithToken();
        formData.put("type","player");
        formData.put("method","trigger");
        formData.put("id",id);
        formData.put("playerUID",playerUID);
        CompletableFuture<Utils.APIResponse> resp = Utils.apiRequest(getUrl(),formData, Utils.APIResponse.class);
        resp.thenAccept(onComplete::run);
        return resp.join();
    });
    }


    public CompletableFuture<Utils.APIResponse> cancelMagicMoment(int id,String playerUID, onMetaResponse onComplete)
    {
        return CompletableFuture.supplyAsync(()->{
        HashMap<String,Object> formData = formDataWithToken();
        formData.put("type","player");
        formData.put("method","cancel");
        formData.put("id",id);
        formData.put("playerUID",playerUID);
        CompletableFuture<Utils.APIResponse> resp = Utils.apiRequest(getUrl(),formData, Utils.APIResponse.class);
        resp.thenAccept(onComplete::run);
            return resp.join();
        });
    }


    public void getNearByStatLeaderboard(String playerUID, String statName,int amount,onStatLeaderboard onComplete)
    {
        HashMap<String,Object> formData = formDataWithToken();
        formData.put("type","player");
        formData.put("method","getStatLeaderboardNearBy");
        formData.put("playerUID",playerUID);
        formData.put("stat",statName);
        formData.put("amount",amount);
        formData.put("environment",currentEnvironment.index);
        CompletableFuture<Responses.StatLeaderboard> leaderboard = Utils.apiRequest(getUrl(),formData, Responses.StatLeaderboard.class);
        leaderboard.thenAccept(onComplete::run).join();
    }


    public void getPlayerStat(String playerUID,String statName,onPlayerStat onComplete)
    {
        HashMap<String,Object> formData = formDataWithToken();
        formData.put("type","player");
        formData.put("method","getPlayerStat");
        formData.put("playerUID",playerUID);
        formData.put("stat",statName);
        formData.put("environment",currentEnvironment.index);
        CompletableFuture<Responses.PlayerStat> response = Utils.apiRequest(getUrl(),formData, Responses.PlayerStat.class);
        response.thenAccept(onComplete::run);
        response.join();
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
                ClientToken token = new ClientToken();
                token.success = true;
                token.apiVersion = userData.get("apiVersion").getAsFloat();
                token.id = userData.get("id").toString();
                token.uid = userData.get("uid").getAsString();
                token.name = userData.get("name").toString();
                token.tokenVersion = userData.get("tokenVersion").getAsFloat();
                EchelonTwitchController.setClientTokenData(token,false);
            }
        }
    }
}
}
