package com.Test;

import com.EchelonSDK.Echelon;
import com.EchelonSDK.EchelonTwitchController;
import com.EchelonSDK.Responses.Responses;
import com.EchelonSDK.Responses.TwitchResponses.ClientToken;
import com.google.gson.Gson;


import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.logging.Level;

public class TestConsoleApp {

    
    Echelon echelon;

    private ClientToken playerData;
    public static void main(String[] args) {
        TestConsoleApp app = new TestConsoleApp();
    }
    TestConsoleApp()
    {
        startInput();
        Echelon.logger.isEnabledForLevel(org.slf4j.event.Level.DEBUG);

    }


    private void startInput() {
        Scanner scanner = new Scanner(System.in);

        String input;
        do
        {
            Echelon.logger.info("NEED INPUT");
            input = scanner.nextLine();
            if (!input.isEmpty()){
            String mode = input.split(" ")[0];
            String option ="";
            if(input.split(" ").length > 1) {
                option = input.split(" ")[1];
            }
            switch (mode) {
                case "1":
                    Echelon.logger.info("Creating Echelon System");
                    initialiseEchelon();
                    initialiseEventHooks();
                    break;

                case "2":
                    Echelon.logger.info("Logging in");
                    echelonLogin();
                    break;
                case "3" :
                    Echelon.logger.info("Logging out");
                    echelonLogout();
                    break;
                case "4" :
                    Echelon.logger.info("Getting Top 10 Players");
                    getTop10LeaderBoard();
                    break;
                case "5" :
                    Echelon.logger.info("Opening Dashboard");
                    openDashboard();
                    break;
                case "6":
                    Echelon.logger.info("Adding Points");
                    addPoints(option);
                    break;

                case "7":
                    Echelon.logger.info("Getting Player Stat");
                    getPlayerStat();
                    break;
                case "8":
                    Echelon.logger.info("Getting nearby leader board players");
                    getNearByPlayerStats();
                    break;
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
        echelon.getStatLeaderboard("points",10,response ->{
            Echelon.logger.info("Leader board player count " + response.players.length);
            for (Responses.StatLeaderboardPlayer player: response.players)
            {
                System.out.println(player.value);
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



    private void addPoints(String points)
    {
        float value = Float.parseFloat(points);
        echelon.addPlayerStat(playerData.uid,"points",value,(add)->{

            if (add.success){
                Echelon.logger.info("Added Points to local player " + value);
            }else
            {
                Echelon.logger.info("Not sure what happened but it didn't work");
            }
        }).join();
    }

    private void getPlayerStat()
    {
        echelon.getPlayerStat(playerData.uid,"points",(points)->{
            System.out.println("Player Points: " + points.value);
        });
    }


    private void getNearByPlayerStats()
    {
        echelon.getNearByStatLeaderboard(playerData.uid,"points",5,(nearPoints)->{
            System.out.println("Near By Players");

            for (Responses.StatLeaderboardPlayer player: nearPoints.players){
                System.out.println("Player " + player.uid + "Score: " + player.value);
            }
        });
    }

}
