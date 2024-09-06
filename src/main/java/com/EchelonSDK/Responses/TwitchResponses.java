package com.EchelonSDK.Responses;

public class TwitchResponses {

    public static class AuthCode extends APIResponse {
        public String code;
    }


    public static class ClientToken extends APIResponse {
        public String id;
        public String uid;
        public String name;
        public float tokenVersion;

    }


    public static class Users extends APIResponse
    {


        public String id;
        public String name;
        public String displayName;
        public String description;
        public String profileImage;
        public LatestFollowers followers;
    }


    public static class LatestFollowers {

        public int total;
        public Follower[] latest;
    }

    public static class Follower {

        public String id;
        public String name;
        public String date;
    }
}