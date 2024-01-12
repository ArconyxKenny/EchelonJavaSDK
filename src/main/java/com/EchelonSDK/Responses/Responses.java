package com.EchelonSDK.Responses;

import com.EchelonSDK.Utils;

public class Responses {


    public static class SdkData extends Utils.APIResponse
    {
        public int gameId;
        public boolean bypassMode;
        public String dir;
        public boolean interactiveControlsEnabled;
        public InteractiveControl[] interactiveControls;

    }






    public static class InteractiveControl
    {
        public String id;
        public String value;
        public String action;
        public int width;
        public int height;
        public int x;
        public int y;

    }


    public static class GiveWayPoints extends Utils.APIResponse
    {
        public int points;
        public String rewardID;
        public String environment;
        public String error;
    }
    public static class GenerateDashboardToken extends Utils.APIResponse
    {
        public String token;
    }


    public static class PlayerRewards extends Utils.APIResponse
    {
        public RewardData[] rewards;
    }

    public static class PlayersRewards extends Utils.APIResponse
    {
        public PlayerWithRewards[] players;
    }

    public static class PlayerWithRewards extends Utils.APIResponse
    {
        public String uid;
        public RewardData[] rewards;

    }

    public static class RewardData extends Utils.APIResponse
    {
        public String id;
        public String uid;
        public boolean claimed;
        public String value;

    }


    public static class StatLeaderboard extends Utils.APIResponse
    {
        public StatLeaderboardPlayer[] players;
    }

    public static class StatLeaderboardPlayer extends Utils.APIResponse
    {
        public int pos;
        public String uid;
        public String value;
        public long date;
    }


    public static class PlayerStat extends Utils.APIResponse
    {

        public String value;
        public long date;
    }


}

