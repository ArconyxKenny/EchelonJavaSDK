package com.Test;

import com.EchelonSDK.Echelon;
import com.EchelonSDK.EchelonTwitchController;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Scanner;

public class TestConsoleApp {

    
    Echelon echelon;
    public static void main(String[] args) {
        TestConsoleApp app = new TestConsoleApp();
    }

    TestConsoleApp()
    {
        Scanner scanner = new Scanner(System.in);
        String input;
        Echelon.logger.info("NEED INPUT");
        do
        {
            input = scanner.nextLine();

            switch (input)
            {

                case "1":
                    Echelon.logger.info("Creating Echelon System");
                    initialiseEchelon();
                    break;
                case "2":
                    Echelon.logger.info("Set Logging in");
                    setLoginTest();
                    break;


            }
        }
        while (!input.equals("stop"));
    }


    public void initialiseEchelon()
    {
        echelon = new Echelon(Echelon.Environment.DEVELOPMENT,"eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpZCI6NTcsImdhbWUiOjE2LCJ0eXBlIjoiZGV2LXByaXYifQ.0530QV3YsiIxD08J7JsBXN8TyT4Vd84Ny1H648_YpK0",true,
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



    public void setLoginTest()
    {
        EchelonTwitchController.ClientAuthGenerateAuthCode(required->{
            try {
                Echelon.logger.info(required.webUrl);
                java.awt.Desktop.getDesktop().browse(new URI(required.webUrl));
            } catch (IOException | URISyntaxException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
