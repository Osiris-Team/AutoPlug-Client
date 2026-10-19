package com.osiris.autoplug.client.network.online.connections;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SSHServerConsoleReceiveTest {
    private String broadcast(String... messages) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        SSHServerConsoleReceive console = new SSHServerConsoleReceive();
        console.setOutputStream(output);
        for (String message : messages) console.broadcast(message);
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    @Test
    void convertsLinuxLineEndings() {
        assertEquals("one\r\ntwo\r\n", broadcast("one\ntwo\n"));
    }

    @Test
    void preservesWindowsLineEndings() {
        assertEquals("one\r\ntwo\r\n", broadcast("one\r\ntwo\r\n"));
    }

    @Test
    void convertsMixedLineEndingsWithoutDoublingCarriageReturns() {
        assertEquals("one\r\ntwo\r\nthree\r\n", broadcast("one\r\ntwo\nthree"));
    }

    @Test
    void terminatesAnUnterminatedMessage() {
        assertEquals("one\r\n", broadcast("one"));
    }

    @Test
    void completesATrailingCarriageReturn() {
        assertEquals("one\r\n", broadcast("one\r"));
    }

    @Test
    void preservesBlankLines() {
        assertEquals("one\r\n\r\ntwo\r\n", broadcast("one\n\ntwo\n"));
    }

    @Test
    void preservesTerminalControlSequences() {
        assertEquals("\u001b[32mgreen\u001b[0m\r\n", broadcast("\u001b[32mgreen\u001b[0m\n"));
    }

    @Test
    void preservesStandaloneCarriageReturnsInsideAMessage() {
        assertEquals("10%\r20%\r\n", broadcast("10%\r20%\n"));
    }

    @Test
    void preservesUtf8Content() {
        assertEquals("नमस्ते 🌍\r\n", broadcast("नमस्ते 🌍\n"));
    }

    @Test
    void terminatesAnEmptyMessage() {
        assertEquals("\r\n", broadcast(""));
    }

    @Test
    void preservesAnAlreadyTerminatedEmptyLine() {
        assertEquals("\r\n", broadcast("\n"));
    }

    @Test
    void handlesSuccessiveBroadcasts() {
        assertEquals("one\r\ntwo\r\n", broadcast("one", "two\n"));
    }
}

