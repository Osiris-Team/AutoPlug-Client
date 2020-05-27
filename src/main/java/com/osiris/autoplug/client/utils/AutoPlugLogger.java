/*
 * Copyright (c) 2020 [Osiris Team](https://github.com/Osiris-Team)
 *  All rights reserved.
 *
 *  This software is copyrighted work licensed under the terms of the
 *  AutoPlug License.  Please consult the file "LICENSE" for details.
 */

package com.osiris.autoplug.client.utils;


import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@SuppressWarnings("ALL")
public class AutoPlugLogger {

    private String ANSI_RESET = "\u001B[0m";
    private String ANSI_BLACK = "\u001B[30m";
    private String ANSI_RED = "\u001B[31m";
    private String ANSI_GREEN = "\u001B[32m";
    private String ANSI_YELLOW = "\u001B[33m";
    private String ANSI_BLUE = "\u001B[34m";
    private String ANSI_PURPLE = "\u001B[35m";
    private String ANSI_CYAN = "\u001B[36m";
    private String ANSI_WHITE = "\u001B[37m";

    private String ANSI_BLACK_BACKGROUND = "\u001B[40m";
    private String ANSI_RED_BACKGROUND = "\u001B[41m";
    private String ANSI_GREEN_BACKGROUND = "\u001B[42m";
    private String ANSI_YELLOW_BACKGROUND = "\u001B[43m";
    private String ANSI_BLUE_BACKGROUND = "\u001B[44m";
    private String ANSI_PURPLE_BACKGROUND = "\u001B[45m";
    private String ANSI_CYAN_BACKGROUND = "\u001B[46m";
    private String ANSI_WHITE_BACKGROUND = "\u001B[47m";

    private String tag(){
        String tag = "";
        if (GD.WINDOWS_OS){
            ANSI_WHITE_BACKGROUND = "";
            ANSI_BLUE = "";
            ANSI_RESET = "";
        }
        tag = ANSI_WHITE_BACKGROUND + ANSI_BLUE + "[AutoPlug-Client]" + ANSI_RESET;
        return tag;
    }
    private String info (){
        String info = "";
        if (GD.WINDOWS_OS){
            ANSI_WHITE_BACKGROUND = "";
            ANSI_CYAN = "";
            ANSI_RESET = "";
        }
        info = ANSI_WHITE_BACKGROUND + ANSI_CYAN + " [INFO] " + ANSI_RESET;
        return info;
    }

    public void class_startup (String text) {

        if (GD.DEBUG){
            if (GD.WINDOWS_OS){
                ANSI_WHITE_BACKGROUND = "";
                ANSI_BLACK = "";
                ANSI_RESET = "";
                ANSI_GREEN_BACKGROUND = "";
                ANSI_WHITE = "";
            }
        //Get actual date
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();

        //Format date
        String date = ANSI_WHITE_BACKGROUND + ANSI_BLACK +"["+dtf.format(now)+"]"+ ANSI_RESET;

        //Format class_startup_tag
        String class_startup_tag = ANSI_GREEN_BACKGROUND + ANSI_WHITE +"[*NEW CLASS*] "+ ANSI_RESET;

        //Special text
        String special_text = ANSI_GREEN_BACKGROUND + ANSI_BLACK +text + ANSI_RESET;

        //RESULT:
        String result = date + tag() + info() + class_startup_tag + special_text;


        System.out.println(result);
        }


    }

    public void global_info(String text) {

        if (GD.WINDOWS_OS){
            ANSI_WHITE_BACKGROUND = "";
            ANSI_BLACK = "";
            ANSI_RESET = "";
        }

        //Get actual date
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();

        //Format date
        String date = ANSI_WHITE_BACKGROUND + ANSI_BLACK +"["+dtf.format(now)+"]"+ ANSI_RESET;

        //RESULT:
        String result = date + tag() + info() + text;

        System.out.println(result);
    }

    public void global_debugger(String class_name, String method_name, String text) {

        if (GD.DEBUG) {

            if (GD.WINDOWS_OS){
                ANSI_WHITE_BACKGROUND = "";
                ANSI_BLACK = "";
                ANSI_RESET = "";
                ANSI_GREEN_BACKGROUND = "";
                ANSI_WHITE = "";
                ANSI_BLUE_BACKGROUND = "";
                ANSI_YELLOW = "";
                ANSI_PURPLE = "";
            }

            //Get actual date
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
            LocalDateTime now = LocalDateTime.now();

            //Format date
            String date = ANSI_WHITE_BACKGROUND + ANSI_BLACK + "[" + dtf.format(now) + "]" + ANSI_RESET;

            //Format debugger_tag
            String debugger_tag = ANSI_BLUE_BACKGROUND + ANSI_YELLOW +" [DEBUG] "+ ANSI_RESET;

            //Format class_tag
            String class_tag = ANSI_BLUE_BACKGROUND + ANSI_YELLOW +" ["+class_name+"] "+ ANSI_RESET;

            //Format method_tag
            String method_tag = ANSI_BLUE_BACKGROUND + ANSI_PURPLE +" ["+method_name+"] "+ ANSI_RESET;

            //RESULT:
            String result = date + tag() + debugger_tag + class_tag + method_tag + text;

            System.out.println(result);
        }
    }

    public void spiget_info(String text) {

        if (GD.DEBUG){

            if (GD.WINDOWS_OS){
                ANSI_WHITE_BACKGROUND = "";
                ANSI_BLACK = "";
                ANSI_RESET = "";
                ANSI_GREEN_BACKGROUND = "";
                ANSI_WHITE = "";
                ANSI_BLUE_BACKGROUND = "";
                ANSI_YELLOW = "";
                ANSI_PURPLE = "";
            }

        //Get actual date
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();

        //Format date
        String date = ANSI_WHITE_BACKGROUND + ANSI_BLACK +"["+dtf.format(now)+"]"+ ANSI_RESET;

        //Format spiget_tag
        String spiget_tag = ANSI_YELLOW + "[Spiget] " + ANSI_RESET;

        //RESULT:
        String result = date + tag() + info()+ spiget_tag + text;


        System.out.println(result);
        }

    }

    public void listener_info(String text, int port) {

        if (GD.WINDOWS_OS){
            ANSI_WHITE_BACKGROUND = "";
            ANSI_BLACK = "";
            ANSI_RESET = "";
            ANSI_GREEN_BACKGROUND = "";
            ANSI_GREEN = "";
        }

        //Get actual date
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();

        //Format date
        String date = ANSI_WHITE_BACKGROUND + ANSI_BLACK +"["+dtf.format(now)+"]"+ ANSI_RESET;

        //Format listener_tag
        String listener_tag = ANSI_GREEN + "[Listener/"+port+"] " + ANSI_RESET;

        //RESULT
        String result = date + tag() + info()+ listener_tag + text;

        System.out.println(result);
    }

    public void global_warn(String text) {

        if (GD.WINDOWS_OS){
            ANSI_RED_BACKGROUND = "";
            ANSI_YELLOW = "";
            ANSI_RESET = "";
            ANSI_RED = "";
        }

        //Get actual date
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();

        //Format date
        String date = ANSI_RED_BACKGROUND + ANSI_YELLOW +"["+dtf.format(now)+"]"+ ANSI_RESET;

        //Format warn
        String warn = ANSI_RED_BACKGROUND + ANSI_YELLOW +" [WARN] "+ ANSI_RESET;

        String warn_text = ANSI_RED +text+ ANSI_RESET;

        String result = date + tag() + warn + warn_text;

        System.out.println(result);
    }

}
