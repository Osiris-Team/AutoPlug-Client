package com.osiris.autoplug.client;

import com.osiris.autoplug.client.utils.NonBlockingPipedInputStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.concurrent.atomic.AtomicInteger;

class MainTest {

    @Test
    void nonBlockingPipedInputStreamExample() throws IOException, InterruptedException {
        NonBlockingPipedInputStream pipedInput = new NonBlockingPipedInputStream();
        OutputStream pipedOutput = new PipedOutputStream(pipedInput);
        MyTeeOutputStream teeOutput = new MyTeeOutputStream(System.out, pipedOutput);
        PrintStream out = new PrintStream(teeOutput);

        final int expectedPrintedLinesCount = 1000;
        AtomicInteger actualPrintedLinesCount = new AtomicInteger();
        AtomicInteger actualReadLinesCount = new AtomicInteger();

        Thread t1 = new Thread(() -> { // Thread for writing data to OUT
            try {
                for (int i = 1; i <= expectedPrintedLinesCount; i++) {
                    out.println("Hello! " + i);
                    actualPrintedLinesCount.incrementAndGet();
                    Thread.sleep(10);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        // NonBlockingPipedInputStream starts a new thread when it is initialised.
        // That thread reads the PipedInputStream and fires an event every time a full line was written.
        pipedInput.actionsOnWriteLineEvent.add(line -> {
            actualReadLinesCount.getAndIncrement();
        });

        t1.start();

        for (int i = 0; i < 30; i++) { // 30 seconds max waiting for threads to complete
            Thread.sleep(1000); // Do this because Junit doesn't support multithreaded stuff
        }

        Assertions.assertEquals(expectedPrintedLinesCount, actualPrintedLinesCount.get());
        Assertions.assertEquals(expectedPrintedLinesCount, actualReadLinesCount.get());
    }

    @Test
    void testInputStreamPipe() throws IOException, InterruptedException {
        PipedInputStream pipedInput = new PipedInputStream();
        OutputStream pipedOutput = new PipedOutputStream(pipedInput);
        PrintStream out = new PrintStream(pipedOutput);

        final int expectedPrintedLinesCount = 1000;
        AtomicInteger actualPrintedLinesCount = new AtomicInteger();

        Thread t1 = new Thread(() -> { // Thread for writing data to OUT
            try {
                for (int i = 0; i < expectedPrintedLinesCount; i++) {
                    out.println("Hello! " + i);
                    actualPrintedLinesCount.incrementAndGet();
                    Thread.sleep(10);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });


        Thread t2 = new Thread(() -> { // Thread for reading data from IN
            BufferedReader reader = new BufferedReader(new InputStreamReader(pipedInput));
            StringBuilder builder = new StringBuilder();
            try {
                while (true) {
                    builder.append(reader.readLine());
                }
            } catch (IOException e) {
                //e.printStackTrace(); // ignore
                System.out.println(builder);
            }
        });

        t1.start();
        //t2.start(); // If we aren't reading then PipedInputStream in Thread2, we only print 94 lines instead of 1000!?

        for (int i = 0; i < 30; i++) { // 30 seconds max waiting for threads to complete
            Thread.sleep(1000); // Do this because Junit doesn't support multithreaded stuff
        }

        Assertions.assertEquals(expectedPrintedLinesCount, actualPrintedLinesCount.get() + 1);
    }


    @Test
    void testSplittingOutput() throws IOException, InterruptedException {
        PipedInputStream pipedInput = new PipedInputStream();
        OutputStream pipedOutput = new PipedOutputStream(pipedInput);
        //TeeOutputStream teeOutput = new TeeOutputStream(System.out, pipedOutput);
        MyTeeOutputStream teeOutput = new MyTeeOutputStream(System.out, pipedOutput);
        PrintStream out = new PrintStream(teeOutput);

        final int expectedPrintedLinesCount = 1000;
        AtomicInteger actualPrintedLinesCount = new AtomicInteger();

        Thread t1 = new Thread(() -> { // Thread for writing data to OUT
            try {
                for (int i = 0; i < expectedPrintedLinesCount; i++) {
                    out.println("Hello! " + i);
                    actualPrintedLinesCount.incrementAndGet();
                    Thread.sleep(10);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });


        Thread t2 = new Thread(() -> { // Thread for reading data from IN
            BufferedReader reader = new BufferedReader(new InputStreamReader(pipedInput));
            StringBuilder builder = new StringBuilder();
            try {
                while (true) {
                    builder.append(reader.readLine());
                }
            } catch (IOException e) {
                //e.printStackTrace(); // ignore
                System.out.println(builder);
            }
        });

        t1.start();
        //t2.start(); // If we aren't reading then PipedInputStream in Thread2, we only print 94 lines instead of 1000!?

        for (int i = 0; i < 15; i++) { // 30 seconds max waiting for threads to complete
            Thread.sleep(1000); // Do this because Junit doesn't support multithreaded stuff
        }

        Assertions.assertEquals(expectedPrintedLinesCount, actualPrintedLinesCount.get() + 1);
    }

    @Test
    void testMultipleLineReaders() throws InterruptedException {
        /* // THIS TEST WONT WORK
        LineReader lineReader1 = LineReaderBuilder.builder()
                .terminal(TERMINAL)
                .build();
        LineReader lineReader2 = LineReaderBuilder.builder()
                .terminal(TERMINAL)
                .build();

        Thread t1 = new Thread(()->{
            while (true){
                System.out.println("LineReader1: "+lineReader1.readLine());
            }
        });


        Thread t2 = new Thread(()->{
            while (true){
                System.out.println("LineReader2: "+lineReader2.readLine());
            }
        });


        // Start readers
        t1.start();
        t2.start();

        TERMINAL.writer().println("Test stuff");
        TERMINAL.writer().println("More test stuff");
        TERMINAL.writer().println("Hello world!");

        for (int i = 0; i < 5; i++) { // 5 seconds max waiting for threads to complete
            Thread.sleep(1000); // Do this because Junit doesn't support multithreaded stuff
        }

         */
    }

    final class MyTeeOutputStream extends OutputStream {

        private final OutputStream out;
        private final OutputStream tee;

        public MyTeeOutputStream(OutputStream out, OutputStream tee) {
            if (out == null)
                throw new NullPointerException();
            else if (tee == null)
                throw new NullPointerException();

            this.out = out;
            this.tee = tee;
        }


        @Override
        public void write(int b) throws IOException {
            out.write(b);
            tee.write(b);
        }

        @Override
        public void write(byte[] b) throws IOException {
            out.write(b);
            tee.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            tee.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            out.flush();
            tee.flush();
        }

        @Override
        public void close() throws IOException {
            try {
                out.close();
            } finally {
                tee.close();
            }
        }
    }
}