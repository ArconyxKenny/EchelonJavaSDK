package com.EchelonSDK;

import com.google.gson.Gson;


import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static java.net.http.HttpRequest.newBuilder;

public class Utils {



    public static float apiVersion;

    @FunctionalInterface
    public interface onApiRequestComplete<T>
    {
        void run();
    }

    public static String getRandomString()
    {
        return UUID.randomUUID().toString();
    }


    public static String encodeURL(String string)
    {
        //string = string.replace("{", URLEncoder.encode("{", StandardCharsets.UTF_8));
        //string = string.replace("}",URLEncoder.encode("}", StandardCharsets.UTF_8));
        //string = string.replace("\"",URLEncoder.encode(String.valueOf('"'), StandardCharsets.UTF_8));
        string = URLEncoder.encode(string, StandardCharsets.UTF_8);
        return string;
    }


    public static String getJsonString(Object object)
    {
        Gson gson = new Gson();
        return gson.toJson(object);
    }


    public static <T extends  APIResponse> CompletableFuture<T> apiRequest(String apiUrl, HashMap<String,Object> formData, onApiRequestComplete<T> onComplete, Class<T> tClass)
    {

        return CompletableFuture.supplyAsync(() ->{
            T response;
            if (apiVersion != 0f)
            {
                formData.put("apiVersion", apiVersion);
            }
            String request = getJsonString(formData);
            String fullUrl = apiUrl + "?data=" + Utils.encodeURL(request);
            URI uri = URI.create(fullUrl);
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest HttpRequest = newBuilder(uri).build();
            try{
                HttpResponse<String> httpResponse =  client.send(HttpRequest, HttpResponse.BodyHandlers.ofString());

                response = new Gson().fromJson(
                        httpResponse.body(),tClass
                );

            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }

            if (onComplete != null) onComplete.run();
            return response;
        });
    }

    public static <T extends  APIResponse> CompletableFuture<T> apiRequest(String apiUrl, HashMap<String,Object> formData, Class<T> tClass)
    {
        return apiRequest(apiUrl,formData,null,tClass);
    }






    public static class APIResponse
    {
        public boolean success;

        public float apiVersion;

        public boolean devMode;
    }

}
