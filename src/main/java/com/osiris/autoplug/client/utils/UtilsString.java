/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class UtilsString {

    /**
     * Input: "Hello there" my "friend" <br>
     * Output list: ["Hello there", my, "friend"] <br>
     * Spaces inside quotes are ignored. <br>
     */
    public List<String> splitBySpacesAndQuotes(String s) throws Exception {
        List<String> list = new ArrayList<>();
        int countQuotes = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = (char) s.codePointAt(i);
            if (c == '\"') countQuotes++;
        }
        if (countQuotes == 0) {
            list.addAll(Arrays.asList(s.split(" ")));
            return list;
        }
        if (countQuotes % 2 == 1) throw new Exception("Open quote found! Please close the quote in: " + s);
        // "bla""bla"
        // "bla" "bla"
        // bla "bla"
        // "bla" bla
        // "bla" bla "bla"
        boolean isInQuote = false;
        int iLastOpenQuote = 0;
        int iLastSpace = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = (char) s.codePointAt(i);
            if (c == ' ') {
                if (!isInQuote) {
                    if (iLastSpace != -1)
                        list.add(s.substring(iLastSpace, i).trim());
                }
                iLastSpace = i;
            }
            if (c == '\"')
                if (!isInQuote) {
                    iLastOpenQuote = i;
                    isInQuote = true;
                } else {
                    list.add(s.substring(iLastOpenQuote, i + 1).trim());
                    isInQuote = false;
                    iLastSpace = -1;
                }
        }
        if (iLastSpace != -1) list.add(s.substring(iLastSpace).trim()); // Add last one if there is
        return list;
    }

}
