package com.EchelonSDK;

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



    public static class TwitchResponses {

        public static class AuthCode extends Utils.APIResponse {
            public String code;
        }


        public static class ClientToken extends Utils.APIResponse {
            public String id;
            public String uid;
            public String name;
            public float tokenVersion;

        }
    }
}

