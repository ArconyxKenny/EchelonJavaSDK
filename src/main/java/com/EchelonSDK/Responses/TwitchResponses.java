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



}