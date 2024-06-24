package com.osiris.autoplug.client.network.online.connections;

import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.command.CommandFactory;
import org.apache.sshd.server.channel.ChannelSession;

public class SSHServerConsoleFactory implements CommandFactory {
    @Override
    public Command createCommand(ChannelSession channel, String command) {
        return new SSHServerConsoleReceive();
    }
}
