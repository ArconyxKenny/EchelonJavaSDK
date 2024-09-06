package com.EchelonSDK;

import com.EchelonSDK.EchelonTwitchController.onAuthComplete;
import com.EchelonSDK.Responses.APIResponse;
import com.EchelonSDK.Responses.Responses;
import com.EchelonSDK.Responses.TwitchResponses;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.EchelonSDK.Responses.TwitchResponses.*;

public class Echelon {

    public static Echelon INSTANCE;
    public final Logger logger = LoggerFactory.getLogger("Echelon");



    public static ExecutorService threadPool = Executors.newFixedThreadPool(16);
    private enum DOMAIN {
        DEV("echelon-internal.novembergames.com"),
        PRODUCTION("echelon.novembergames.com");
        final String url;

        DOMAIN(String string) {
            url = string;
        }
    }

    public enum Environment {
        DEVELOPMENT(0),
        STAGING(1),
        PRODUCTION(2);

        public final int index;

        Environment(int index) {
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
    public interface onMetaResponse {
        void run(APIResponse response);
    }

    @FunctionalInterface
    public interface onPlayerRewardClaimed {
        void run(String playerUID, String rewardId, String rewardUID, String value);
    }


    public interface onPlayerStat {

        void run(Responses.PlayerStat statData);

    }

    public interface onRedeemGiveAwayPoints {

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
    private final HashMap<String, ArrayList<String>> rewardsBeingUnlocked = new HashMap<>();

    public Echelon(Environment environment, String devToken, boolean devModes, onSDKInitialisation initialisation) {
        INSTANCE = this;
        currentEnvironment = environment;
        token = devToken;
        devMode = devModes;
        deviceID = Utils.getRandomString();
        client = new EchelonWebSocketClient(getDomain());

        getSDKData();
        Timer timer = new Timer();
        client.init(() -> {
            INSTANCE.logger.info("Successfully initialized Echelon");
            initialisation.run(true, "");
            dashboardStatusCheckRoutine = new TimerTask() {
                @Override
                public void run() {
                    dashboardStatusCheckRoutine();
                }
            };
            timer.schedule(dashboardStatusCheckRoutine, 3000, 3000);
            initialised = true;
        });

        Utils.apiVersion = version;

    }


    public void getSDKData() {
        HashMap<String, Object> formData = new HashMap<>();
        formData.put("type", "developer");
        formData.put("method", "initSDK");
        formData.put("token", token);
        CompletableFuture<Responses.SdkData> red = Utils.apiRequest(Echelon.getUrl(), formData, new TypeToken<Responses.SdkData>() {
        }.getType());
        red.thenAccept(resp -> {
            enabled = !resp.bypassMode;
            gameId = resp.gameId;
            rewardsDirectory = resp.dir;
            interactiveControlsEnabled = resp.interactiveControlsEnabled;
            interactiveControls = resp.interactiveControls;
        });

    }

    public static String getDomain() {
        return devMode ? DOMAIN.DEV.url : DOMAIN.PRODUCTION.url;
    }

    public static void main(String[] args) {

    }

    public static String getUrl() {
        return "https://" + getDomain() + "/";
    }


    public static HashMap<String, Object> formDataWithToken() {
        HashMap<String, Object> formData = new HashMap<>();
        formData.put("token", token);
        return formData;
    }


    public void getStatLeaderboard(String statName, int amount, onStatLeaderboard onStatLeaderboard) {
        HashMap<String, Object> formData = formDataWithToken();
        formData.put("type", "player");
        formData.put("method", "getStatLeaderboard");
        formData.put("stat", statName);
        formData.put("amount", amount);
        formData.put("environment", currentEnvironment.index);
        CompletableFuture<Responses.StatLeaderboard> leaderboard = Utils.apiRequest(getUrl(), formData, new TypeToken<Responses.StatLeaderboard>() {
        }.getType());
        leaderboard.thenAccept(onStatLeaderboard::run);
    }


    public boolean startPlayerRewardsListener(ArrayList<String> playersToListen) {
        if (!initialised) return false;
        INSTANCE.logger.info("Starting Rewards Listener");

        if (playerRewardsCheckRoutine != null) playerRewardsCheckRoutine.cancel();
        players = playersToListen;
        rewardsBeingUnlocked.clear();
        Timer timer = new Timer();
        playerRewardsCheckRoutine = new TimerTask() {
            @Override
            public void run() {
                PlayerRewardsCheckRoutine();
            }
        };
        timer.schedule(playerRewardsCheckRoutine, 5000, 5000);

        return true;
    }


    private void PlayerRewardsCheckRoutine() {
        CompletableFuture.supplyAsync(() -> {
            if (!EchelonWebSocketClient.connected) return true;
            if (players.isEmpty()) return true;
            getPlayersRewards(players, rewards -> {
                if (!rewards.success) {
                    INSTANCE.logger.error("Error getting players reward unlocks");
                    return;
                }

                boolean weHaveLocalPLayerData;
                for (int i = 0; i < rewards.players.length; i++) {
                    if (rewards.players[i].rewards.length == 0) continue;
                    weHaveLocalPLayerData = rewardsBeingUnlocked.containsKey(rewards.players[i].uid);
                    if (!weHaveLocalPLayerData) rewardsBeingUnlocked.put(rewards.players[i].uid, new ArrayList<>());

                    for (int j = 0; j < rewards.players[i].rewards.length; j++) {
                        if (!rewards.players[i].rewards[j].claimed) continue;

                        if (!rewardsBeingUnlocked.get(rewards.players[i].uid).contains(rewards.players[i].rewards[j].id)) {
                            rewardsBeingUnlocked.get(rewards.players[i].uid).add(rewards.players[i].rewards[j].id);
                            PlayerClaimedReward(rewards.players[i].uid, rewards.players[i].rewards[j].id, rewards.players[i].rewards[j].uid, rewards.players[i].rewards[j].value);
                        }
                    }
                }
            });

            return true;
        }, Echelon.threadPool);
    }


    private void PlayerClaimedReward(String playerUID, String rewardId, String rewardUID, String value) {
            ClearPlayerReward(playerUID, rewardId, comp -> {

                if (!comp.success) {
                    INSTANCE.logger.error("Error Clearing player " + playerUID + " reward " + rewardId);
                    return;
                }
                rewardsBeingUnlocked.get(playerUID).remove(rewardId);

                if (onPlayerRewardClaimed != null) {
                    onPlayerRewardClaimed.run(playerUID, rewardId, rewardUID, value);
                    INSTANCE.logger.info("Player " + playerUID + " unlocked reward: " + rewardId + " with rewardUID " + rewardUID + "with value " + value);
                }
        });
    }


    public void ClearPlayerReward(String playerUID, String rewardId, onMetaResponse onComplete) {
        CompletableFuture.runAsync(() -> {

            if (!initialised) return;
            HashMap<String, Object> formData = formDataWithToken();
            formData.put("type", "rewards");
            formData.put("method", "clearPlayerReward");
            formData.put("playerUID", playerUID);
            formData.put("rewardId", rewardId);
            formData.put("environment", currentEnvironment.index);

            CompletableFuture<APIResponse> response = Utils.apiRequest(getUrl(), formData, new TypeToken<APIResponse>() {
            }.getType());
            response.thenAccept(onComplete::run);

        },Echelon.threadPool);

    }

    private void getPlayersRewards(ArrayList<String> playerUIDs, onPlayersRewardsCheck onComplete) {
        CompletableFuture.runAsync(() -> {
            if (!initialised) return;
            HashMap<String, Object> formData = formDataWithToken();
            formData.put("type", "rewards");
            formData.put("method", "getRewardsForPlayers");
            formData.put("playerUIDs", playerUIDs);
            formData.put("environment", currentEnvironment.index);
            CompletableFuture<Responses.PlayersRewards> resp = Utils.apiRequest(getUrl(), formData, new TypeToken<Responses.PlayersRewards>() {
            }.getType());
            resp.thenAccept(onComplete::run);

        }, Echelon.threadPool);
    }


    public void redeemGiveAwayPoints(String redeemerId, String rewardUID, onRedeemGiveAwayPoints onComplete) {
        CompletableFuture.runAsync(() -> {
            if (!initialised) return;
            HashMap<String, Object> formData = formDataWithToken();
            formData.put("type", "giveaway");
            formData.put("method", "redeemPoints");
            formData.put("redeemerId", redeemerId);
            formData.put("rewardUID", rewardUID);
            CompletableFuture<Responses.GiveWayPoints> resp = Utils.apiRequest(getUrl(), formData, new TypeToken<Responses.GiveWayPoints>() {
            }.getType());
            resp.thenAccept(onComplete::run);

        },Echelon.threadPool);

    }

    public static void getDashboardURL(onDashboardUrl onComplete, String template) {
        if (!initialised) {
            onComplete.run("");
            return;
        }

        ClientToken clientToken = EchelonTwitchController.getClientTokenData();

        if (clientToken == null) {
            INSTANCE.logger.warn("Cannot open dashboard, no Authenticated user found!");
            onComplete.run("");
            return;
        }

        //todo ignore dashboard settings
        HashMap<String, Object> formData = new HashMap<>();
        formData.put("type", "rewards");
        formData.put("method", "generateDashboardToken");
        formData.put("token", token);
        formData.put("playerUID", clientToken.uid);
        formData.put("environment", currentEnvironment.index);
        CompletableFuture<Responses.GenerateDashboardToken> genToken = Utils.apiRequest(getUrl(), formData, new TypeToken<Responses.GenerateDashboardToken>() {
        }.getType());
        genToken.thenAccept(tokenData -> {
                    if (!tokenData.success) {
                        INSTANCE.logger.warn("Failed too communicate to Echelon servers");
                        onComplete.run("");
                        return;
                    }
                    onComplete.run(getUrl() + rewardsDirectory + "/?token=" + tokenData.token + "&template=" + template);

                });


    }


    public void dashboardStatusCheckRoutine() {
        CompletableFuture.runAsync(() -> {
                    if (!initialised) return;

                    ClientToken token = EchelonTwitchController.getClientTokenData();
                    if (token == null || (token.id.isEmpty())) return;

                    HashMap<String, Object> formData = new HashMap<>();
                    formData.put("type", "rewards");
                    formData.put("method", "requestPlayerDashboardStatus");
                    formData.put("uid", token.uid);

                    CompletableFuture<APIResponse> response = Utils.apiRequest(getUrl(), formData, new TypeToken<APIResponse>() {
                    }.getType());
                    response.thenAccept(resp -> {
                        INSTANCE.logger.info("DashboardStatus Routine Request sent " + resp.toString());
                        if (isDashboardConnected != resp.success) dashboardStatusUpdated(resp.success);
                        isDashboardConnected = resp.success;
                    });


                },
                Echelon.threadPool
        );
    }

    public void dashboardStatusUpdated(boolean isDashboardConnected) {
        if (onDashboardStatusChange != null) {
            onDashboardStatusChange.run(isDashboardConnected);
        }
    }


    public void addPlayerStats(String playerUID, String statName, float value, onPlayerStat onComplete) {
        addPlayerStat(playerUID, statName, (double) value, onComplete);

    }

   private void addPlayerStat(String playerUID, String statName, double value, onPlayerStat onComplete) {

        CompletableFuture.runAsync(() -> {
            if (!initialised) return;
            HashMap<String, Object> formData = formDataWithToken();
            formData.put("type", "player");
            formData.put("method", "addPlayerStat");
            formData.put("playerUID", playerUID);
            formData.put("stat", statName);
            formData.put("value", value);
            formData.put("environment", currentEnvironment.index);

            CompletableFuture<Responses.PlayerStat> resp = Utils.apiRequest(getUrl(), formData, new TypeToken<Responses.PlayerStat>() {
            }.getType());
            resp.thenAccept(onComplete::run);
        },Echelon.threadPool);
    }


    public void ResetLocalPlayerStats(String playerUID, String statName, onMetaResponse onComplete) {

        CompletableFuture.runAsync(() -> {
            if (!initialised) return;

            HashMap<String, Object> formData = formDataWithToken();
            formData.put("type", "player");
            formData.put("method", "resetPlayerStat");
            formData.put("playerUID", playerUID);
            formData.put("stat", statName);
            formData.put("environment", currentEnvironment.index);
            CompletableFuture<APIResponse> resp = Utils.apiRequest(getUrl(), formData, new TypeToken<APIResponse>() {
            }.getType());

            resp.thenAccept(onComplete::run);
        },Echelon.threadPool);

    }


    public void triggerMagicMoment(int id, String playerUID, onMetaResponse onComplete) {
         CompletableFuture.runAsync(() -> {
            HashMap<String, Object> formData = formDataWithToken();
            formData.put("type", "player");
            formData.put("method", "trigger");
            formData.put("id", id);
            formData.put("playerUID", playerUID);
            CompletableFuture<APIResponse> resp = Utils.apiRequest(getUrl(), formData, new TypeToken<APIResponse>() {
            }.getType());
            resp.thenAccept(onComplete::run);
        },Echelon.threadPool);
    }


    public void cancelMagicMoment(int id, String playerUID, onMetaResponse onComplete) {
        CompletableFuture.runAsync(() -> {
            HashMap<String, Object> formData = formDataWithToken();
            formData.put("type", "player");
            formData.put("method", "cancel");
            formData.put("id", id);
            formData.put("playerUID", playerUID);
            CompletableFuture<APIResponse> resp = Utils.apiRequest(getUrl(), formData, new TypeToken<APIResponse>() {
            }.getType());
            resp.thenAccept(onComplete::run);
        },Echelon.threadPool);
    }


    public void getNearByStatLeaderboard(String playerUID, String statName, int amount, onStatLeaderboard onComplete) {
        HashMap<String, Object> formData = formDataWithToken();
        formData.put("type", "player");
        formData.put("method", "getStatLeaderboardNearBy");
        formData.put("playerUID", playerUID);
        formData.put("stat", statName);
        formData.put("amount", amount);
        formData.put("environment", currentEnvironment.index);
        CompletableFuture<Responses.StatLeaderboard> leaderboard = Utils.apiRequest(getUrl(), formData, new TypeToken<Responses.StatLeaderboard>() {
        }.getType());
        leaderboard.thenAccept(onComplete::run);
    }


    public void getPlayerStat(String playerUID, String statName, onPlayerStat onComplete) {
        HashMap<String, Object> formData = formDataWithToken();
        formData.put("type", "player");
        formData.put("method", "getPlayerStat");
        formData.put("playerUID", playerUID);
        formData.put("stat", statName);
        formData.put("environment", currentEnvironment.index);
        CompletableFuture<Responses.PlayerStat> response = Utils.apiRequest(getUrl(), formData, new TypeToken<Responses.PlayerStat>() {
        }.getType());
        response.thenAccept(onComplete::run);
    }



    public Logger getLogger()
    {
        return logger;
    }
    public static EchelonWebSocketClient getClient() {
        return INSTANCE.client;
    }


    public static class MessageHandler implements javax.websocket.MessageHandler.Whole<String> {


        @Override
        public void onMessage(String message) {
            JsonParser parser = new JsonParser();
            JsonElement jsonElement = parser.parse(message);
            INSTANCE.logger.info("Server Message " + jsonElement);
            JsonObject jsonMessage = jsonElement.getAsJsonObject();
            String type = jsonMessage.get("type").getAsString();

            switch (type) {
                case "ping":
                    HashMap<String, Object> pong = new HashMap<>();
                    pong.put("type", "core");
                    pong.put("method", "pong");
                    pong.put("apiVersion", 1.9);
                    String gsonData = Utils.getJsonString(pong);
                    INSTANCE.client.sendMessage(gsonData);
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
                    EchelonTwitchController.setClientTokenData(token, false);
                    break;
            }
        }

    }
}
