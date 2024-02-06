package com.EchelonSDK;

import com.EchelonSDK.Responses.APIResponse;
import com.google.gson.Gson;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;


import java.io.*;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URLEncoder;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;


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
        try {
            string = URLEncoder.encode(string, String.valueOf(StandardCharsets.UTF_8));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        return string;
    }


    public static String getJsonString(Object object)
    {
        Gson gson = new Gson();
        return gson.toJson(object);
    }


    public static <T extends APIResponse> CompletableFuture<T> apiRequest(String apiUrl, HashMap<String,Object> formData, onApiRequestComplete<T> onComplete, Type typeToken)
    {

        return CompletableFuture.supplyAsync(() ->{
            Echelon.logger.info(Thread.currentThread().getContextClassLoader().toString());
            T response;
            if (apiVersion != 0f)
            {
                formData.put("apiVersion", apiVersion);
            }
            String request = getJsonString(formData);
            String fullUrl = apiUrl + "?data=" + Utils.encodeURL(request);
            URI uri = URI.create(fullUrl);
            try(CloseableHttpClient client = HttpClients.createDefault()){
                HttpGet htttpRequest = new HttpGet(uri);
            try{
                HttpResponse httpResponse =  client.execute(htttpRequest);
                StringBuilder builder = new StringBuilder();
                InputStream stream = httpResponse.getEntity().getContent();
                try (Reader reader = new BufferedReader(new InputStreamReader
                        (stream, StandardCharsets.UTF_8))) {
                    int c;
                    while ((c = reader.read()) != -1) {
                        builder.append((char) c);
                    }
                }
                String stringResponse = builder.toString();

                response = new Gson().fromJson(
                        stringResponse,typeToken
                );

            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            if (onComplete != null) onComplete.run();
            return response;
        } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static <T extends  APIResponse> CompletableFuture<T> apiRequest(String apiUrl, HashMap<String,Object> formData, Type typeToken)
    {
        return apiRequest(apiUrl,formData,null,typeToken);
    }







}
