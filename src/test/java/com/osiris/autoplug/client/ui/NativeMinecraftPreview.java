package com.osiris.autoplug.client.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.google.gson.JsonObject;
import com.osiris.autoplug.client.mc.client.*;
import com.osiris.autoplug.client.mc.launch.*;
import com.osiris.autoplug.client.mc.profiles.*;
import com.osiris.autoplug.client.mc.update.*;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.Map;

/** Disposable desktop review fixture, not a production entry point or a live-game test. */
public final class NativeMinecraftPreview {
    public static void main(String[] arguments) throws Exception {
        Path root=Files.createTempDirectory(Paths.get(arguments[0]),"autoplug-desktop-review-").toRealPath();
        ClientSettings settings=new ClientSettings(root);
        settings.set("cacheDirectory",root.resolve("cache").toString());settings.save();
        LaunchTransport transport=new LaunchTransport(){
            public JsonObject json(String method,URI uri,Map<String,String> headers,byte[] body,Cancellation cancellation)throws IOException{throw new IOException("Offline desktop fixture: no online metadata/account requests");}
            public void download(URI uri,Path target,Cancellation cancellation)throws IOException{throw new IOException("Offline desktop fixture: no downloads");}
        };
        ProfileUpdateService.Engine engine=new ProfileUpdateService.Engine(){
            public ProfileUpdateService.Decision resolve(Profile profile,Path jar,String version){return ProfileUpdateService.Decision.error("Offline review fixture has no provider response");}
            public void download(ProfileUpdateService.Decision decision,Path target)throws IOException{throw new IOException("No fixture download");}
        };
        NativeClientContext context=new NativeClientContext(root,root,transport,engine);
        context.profiles().create("review-vanilla",ProfileType.MODS,"1.20.4","VANILLA",false);
        context.profiles().create("review-paper",ProfileType.PLUGINS,"1.20.4","PAPER",false);
        context.profiles().create("review-template",ProfileType.MODS,"1.19.4","FABRIC",true);
        context.servers().add("Synthetic example (not online)","example.invalid:25565","VANILLA");
        context.worlds().create("review-world","review-paper");
        System.out.println("Disposable review data: "+root);
        SwingUtilities.invokeAndWait(()->{
            FlatDarkLaf.setup();JFrame frame=new JFrame("AutoPlug — Offline review (synthetic data)");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);frame.setLayout(new BorderLayout());
            JLabel notice=new JLabel(" OFFLINE REVIEW — synthetic data; no Minecraft account, game, server or router test ");
            notice.setBorder(BorderFactory.createEmptyBorder(10,8,10,8));frame.add(notice,BorderLayout.NORTH);
            frame.add(new NativeMinecraftPanel(context));frame.setSize(1180,760);frame.setLocationRelativeTo(null);
            frame.addWindowListener(new java.awt.event.WindowAdapter(){@Override public void windowClosed(java.awt.event.WindowEvent event){try{context.close();}catch(IOException error){System.err.println(error.getMessage());}}});
            frame.setVisible(true);
            if(arguments.length>1){
                javax.swing.Timer render=new javax.swing.Timer(750,event->{
                    try {
                        java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(frame.getContentPane().getWidth(),frame.getContentPane().getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB);
                        Graphics2D graphics=image.createGraphics();frame.getContentPane().printAll(graphics);graphics.dispose();
                        javax.imageio.ImageIO.write(image,"png",Paths.get(arguments[1]).toFile());
                    }catch(IOException error){System.err.println("Cannot render review fixture: "+error.getMessage());}
                });render.setRepeats(false);render.start();
            }
        });
    }
}
