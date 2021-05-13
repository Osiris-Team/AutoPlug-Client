package com.osiris.autoplug.client;

import org.junit.jupiter.api.Test;

import java.io.*;

class MainTest {


    @Test
    void testSplittingOutput() throws IOException, InterruptedException {
        // Set default SysOut to TeeOutput, for the OnlineConsole
        PipedInputStream inputStream = new PipedInputStream();
        OutputStream outputStream = new PipedOutputStream(inputStream);
        //TeeOutputStream newOut = new TeeOutputStream(System.out, outputStream);

        Thread t1 = new Thread(() -> { // Thread for writing data to OUT
            new PrintStream(outputStream).println("Hello!");
        });


        Thread t2 = new Thread(() -> { // Thread for reading data from IN
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            try {
                System.out.println("Received from pipe: " + reader.readLine());
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        t1.start();
        t2.start();

        while (!t1.isInterrupted() || !t2.isInterrupted()) // Do this because Junit doesn't support multithreaded stuff
            Thread.sleep(1000);
    }
}