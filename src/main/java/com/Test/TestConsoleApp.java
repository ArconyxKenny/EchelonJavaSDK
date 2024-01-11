package com.Test;

import com.EchelonSDK.Echelon;
import com.EchelonSDK.EchelonTwitchController;
import com.EchelonSDK.Responses.Responses;
import com.EchelonSDK.Responses.TwitchResponses;
import com.EchelonSDK.Responses.TwitchResponses.ClientToken;
import com.google.gson.Gson;

import java.awt.image.AreaAveragingScaleFilter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Scanner;

public class TestConsoleApp {

    
    Echelon echelon;

    private ClientToken playerData;
    public static void main(String[] args) {
        TestConsoleApp app = new TestConsoleApp();
    }
    TestConsoleApp()
    {
        startInput();


    }


    private void startInput() {
        Scanner scanner = new Scanner(System.in);

        String input;
        do
        {
            Echelon.logger.info("NEED INPUT");
            input = scanner.nextLine();
            switch (input) {
                case "1" -> {
                    Echelon.logger.info("Creating Echelon System");
                    initialiseEchelon();
                    initialiseEventHooks();
                }
                case "2" -> {
                    Echelon.logger.info("Logging in");
                    echelonLogin();
                }
                case "3" -> {
                    Echelon.logger.info("Logging out");
                    echelonLogout();
                }
                case "4" -> {
                    Echelon.logger.info("Getting Top 10 Players");
                    getTop10LeaderBoard();
                }
                case "5" ->{
                    Echelon.logger.info("Opening Dashboard");
                    openDashboard();
                }
            }
        }
        while (!input.equals("stop"));
    }

    private void initialiseEchelon() {
        echelon = new Echelon(Echelon.Environment.DEVELOPMENT,"eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpZCI6NzcsImdhbWUiOjQ2LCJ0eXBlIjoiZGV2LXB1YiJ9.EowLCJRkDrAbPbeM3UDo2sZPDgFVw3CcqSgnEKJX5I8",true,
                (success,error) ->{
                    if (!success)
                    {

                        Echelon.logger.info("ERROR" + error);
                    }  else
                    {
                        Echelon.logger.info("Success");

                    }
                });

    }

    private void initialiseEventHooks() {

        echelon.onPlayerRewardClaimed = this::onPlayerRewardClaimed;
        echelon.onAuthCompletedEvents.add(this::onAuthCompleted);
        EchelonTwitchController.onTwitchAuthCompleted.add(this::onAuthCompletedTriggered);
    }

    private void echelonLogin()
    {
        EchelonTwitchController.AuthWithTwitch(required->{
            try {
                Echelon.logger.info(required.webUrl);
                java.awt.Desktop.getDesktop().browse(new URI(required.webUrl));
            } catch (IOException | URISyntaxException e) {
                throw new RuntimeException(e);
            }
        });
    }
    private void echelonLogout()
    {
        EchelonTwitchController.clearClientTokenData();
    }

    private void getTop10LeaderBoard()
    {
        Echelon.getStatLeaderboard("points",10,response ->{

            for (Responses.StatLeaderboardPlayer player: response.players)
            {
                System.out.println(player);
            }
        });
    }

    private void openDashboard()
    {
        Echelon.getDashboardURL(url->{
                    try {
                        if (!url.isEmpty())
                        {
                        java.awt.Desktop.getDesktop().browse(new URI(url));
                        }
                    } catch (IOException | URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                },
                "");
    }


    public void startRewardsListener(){
        ArrayList<String> testPlayers = new ArrayList<>();
        testPlayers.add(playerData.uid);

        boolean success = echelon.startPlayerRewardsListener(testPlayers);
        Echelon.logger.info("Checking for unlocked rewards (" + success + ")");
    }

    public void onAuthCompleted(ClientToken tokenData, boolean fromStoredCredentials)
    {
        Echelon.logger.info("Twitch user Authorized: " + tokenData.name + " id " + tokenData.id + " data " + new Gson().toJson(tokenData));
        playerData =tokenData;

        startRewardsListener();

        //TODO Interactive Controls and Chat invitor needs to be added here
    }


    public void onAuthCompletedTriggered(ClientToken tokenData, boolean fromStoredCredentials)
    {
        //TODO C# API has analytics API calls and save credentials here

        for (EchelonTwitchController.onAuthComplete complete:   echelon.onAuthCompletedEvents)
        {
          complete.run(tokenData,fromStoredCredentials);
        }

    }
    private void onPlayerRewardClaimed(String playerUID, String rewardId,String value, String rewardToken)
    {
        Echelon.logger.info("Player "+playerUID+" claimed reward "+rewardId+" with data "+value);
    }


}
