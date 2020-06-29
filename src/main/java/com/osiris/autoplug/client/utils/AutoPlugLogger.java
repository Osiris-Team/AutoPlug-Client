/*
 * Copyright (c) 2020 [Osiris Team](https://github.com/Osiris-Team)
 *  All rights reserved.
 *
 *  This software is copyrighted work licensed under the terms of the
 *  AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.utils;

import com.osiris.autoplug.client.configs.ServerConfig;
import com.osiris.autoplug.client.server.ClientCommands;
import org.fusesource.jansi.AnsiConsole;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.fusesource.jansi.Ansi.Color.*;
import static org.fusesource.jansi.Ansi.ansi;

/* Colors Codes
        BLACK(Color.BLACK),
        RED(Color.RED),
        GREEN(Color.GREEN),
        YELLOW(Color.YELLOW),
        BLUE(Color.BLUE),
        MAGENTA(Color.MAGENTA),
        CYAN(Color.CYAN),
        WHITE(Color.WHITE),

        // Attributes
        RESET(Attribute.RESET),
        INTENSITY_BOLD(Attribute.INTENSITY_BOLD),
        INTENSITY_FAINT(Attribute.INTENSITY_FAINT),
        ITALIC(Attribute.ITALIC),
        UNDERLINE(Attribute.UNDERLINE),
        BLINK_SLOW(Attribute.BLINK_SLOW),
        BLINK_FAST(Attribute.BLINK_FAST),
        BLINK_OFF(Attribute.BLINK_OFF),
        NEGATIVE_ON(Attribute.NEGATIVE_ON),
        NEGATIVE_OFF(Attribute.NEGATIVE_OFF),
        CONCEAL_ON(Attribute.CONCEAL_ON),
        CONCEAL_OFF(Attribute.CONCEAL_OFF),
        UNDERLINE_DOUBLE(Attribute.UNDERLINE_DOUBLE),
        UNDERLINE_OFF(Attribute.UNDERLINE_OFF),

        // Aliases
        BOLD(Attribute.INTENSITY_BOLD),
        FAINT(Attribute.INTENSITY_FAINT),;
         */

public class AutoPlugLogger {

    public AutoPlugLogger(){
        AutoPlugLogger.newClassDebug("AutoPlugLogger");
    }

    /**
     * Initialises the AutoPlugLogger.
     */
    public static void start(){
        AnsiConsole.systemInstall();
        debug("start","Started AutoPlugLogger");

        //If old exists, delete and replace with new blank file
        try {
        if (GD.LATEST_LOG.exists()) {
                GD.LATEST_LOG.delete();
        }
        GD.LATEST_LOG.createNewFile();

        } catch (Exception e) {
            e.printStackTrace();
            error(e.getMessage(), "Failed to create latest_log.txt");
        }

        /*
        //Start new Thread
        PrintStream out = new PrintStream(
                new FileOutputStream(GD.LATEST_LOG, true), true);
        System.setOut(out);
        */

    }

    /**
     * Stops the AutoPlugLogger and saves the log to file.
     */
    public static void stop(){
        AnsiConsole.systemUninstall();
        debug("stop","Stopped AutoPlugLogger");

        if (GD.LATEST_LOG.exists()) {
            try {

                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MM-yyyy HH-mm");
                LocalDateTime now = LocalDateTime.now();

                File savedLog = new File(GD.WORKING_DIR+"/autoplug-logs/"+dtf.format(now)+".txt");

                Files.copy(GD.LATEST_LOG.toPath(), savedLog.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);

            } catch (Exception e) {
                e.printStackTrace();
                error(e.getMessage(), "Failed to save log file.");
            }
        }

    }


    /**
     * Formats a beautiful new class message.
     * Use this as first line of every constructor.
     * Helps understanding program flow.
     */
    public static void newClassDebug (String className) {

        if (ServerConfig.debug) {

            //Get current date
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
            LocalDateTime now = LocalDateTime.now();

            //Format message
            System.out.println( ansi().bg(WHITE)
                    .fg(BLACK).a("["+dtf.format(now)+"]")
                    .fg(CYAN).a("[AutoPlug-Client]")
                    .fg(GREEN).a("[NEW]")
                    .reset()
                    .fg(GREEN).a("[" + className + "]")
                    .reset()
            );
        }
    }

    /**
     * Formats standard info message.
     * Output is at System.out.
     */
    public static void info(String text) {

        //Get current date
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();

        //Format message
        System.out.println( ansi().bg(WHITE)
                .fg(BLACK).a("["+dtf.format(now)+"]")
                .fg(CYAN).a("[AutoPlug-Client]")
                .fg(BLACK).a("[INFO]")
                .reset()
                .a(" | "+text)
                .reset()
                );

        try {

            OutputStreamWriter writer = new OutputStreamWriter(AnsiConsole.out());
            Files.write(GD.LATEST_LOG.toPath(), writer.toString().getBytes());
        } catch (IOException e) {
            e.printStackTrace();
            error(e.getMessage(), "Failed to update latest log file.");
        }

    }

    /**
     * Formats standard info message.
     * Output is at System.out.
     */
    public static void barrier() {

        //Get current date
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();

        //Format message
        System.out.println( ansi().bg(WHITE)
                .fg(BLACK).a("["+dtf.format(now)+"]")
                .fg(CYAN).a("[AutoPlug-Client]")
                .fg(BLACK).a("[INFO]")
                .reset()
                .a(" | ------------------------------------------- | ")
                .reset()
        );

    }

    /**
     * Formats standard debug message.
     * Output is at System.out.
     */
    public static void debug(String method, String text) {

        if (ServerConfig.debug) {

            //Get current date
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
            LocalDateTime now = LocalDateTime.now();

            //Format message
            System.out.println( ansi().bg(WHITE)
                    .fg(BLACK).a("["+dtf.format(now)+"]")
                    .fg(CYAN).a("[AutoPlug-Client]")
                    .fg(MAGENTA).a("[DEBUG]")
                    .reset()
                    .fg(CYAN).a("[" + method + "]")
                    .a(" "+text)
                    .reset()
            );
        }
    }

    /**
     * Formats standard warn message.
     * Output is at System.out.
     */
    public static void warn (String text){

        //Get current date
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();

        //Format message
        System.out.println( ansi().bg(WHITE)
                .fg(BLACK).a("["+dtf.format(now)+"]")
                .fg(CYAN).a("[AutoPlug-Client]")
                .fg(YELLOW).a("[WARN]")
                .reset()
                .fg(YELLOW).a(" "+text)
                .reset()
        );
    }

    /**
     * Formats critical error message and closes program after that.
     * This only should be used if program isn't able to continue after this error.
     */
    public static void error(String error, String errorMessage) {

        //Get current date
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();

        //Format error title
        System.out.println( ansi().bg(RED)
                        .fg(WHITE).a("["+dtf.format(now)+"]")
                        .fg(WHITE).a("[AutoPlug-Client]")
                        .fg(YELLOW).a("[ERROR][!]")
                        .fg(YELLOW).a(" ["+error+"] [!]")
                        .reset());

        //Format error message
        System.out.println( ansi().bg(RED)
                .fg(WHITE).a("["+dtf.format(now)+"]")
                .fg(WHITE).a("[AutoPlug-Client]")
                .fg(YELLOW).a("[ERROR][!]")
                .fg(YELLOW).a(" ["+errorMessage+"] [!]")
                .reset());

        //Format shutdown notice
        System.out.println( ansi().bg(RED)
                .fg(WHITE).a("["+dtf.format(now)+"]")
                .fg(WHITE).a("[AutoPlug-Client]")
                .fg(YELLOW).a("[ERROR][!]")
                .fg(YELLOW).a(" [AutoPlug is shutting down in 10 seconds. Log saved to /autoplug-logs.] [!]")
                .reset());

        try{
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        //Kills everything
        ClientCommands.isCommand(".kill");
    }

}
