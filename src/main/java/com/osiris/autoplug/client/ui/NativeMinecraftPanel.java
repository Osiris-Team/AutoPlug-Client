package com.osiris.autoplug.client.ui;

import com.osiris.autoplug.client.mc.client.*;
import com.osiris.autoplug.client.mc.launch.Cancellation;
import com.osiris.autoplug.client.mc.launch.MicrosoftDeviceCodeAuth;
import com.osiris.autoplug.client.mc.profiles.*;
import com.osiris.autoplug.client.mc.update.ProfileUpdateService;
import com.osiris.autoplug.client.mc.worlds.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** Native MIT launcher UI. Blocking work runs on one cancellable worker, never the EDT. */
public final class NativeMinecraftPanel extends JPanel {
    private static final String[] LOADERS = {"VANILLA", "FABRIC", "QUILT", "FORGE", "NEOFORGE"};
    private final NativeClientContext context;
    private final DefaultTableModel serverRows = rows("Name", "Address", "Loader", "Status");
    private final DefaultTableModel profileRows = rows("ID", "Type", "Minecraft", "Loader", "Template");
    private final DefaultTableModel worldRows = rows("Preview", "World", "Server profile", "State");
    private final JTable servers = new JTable(serverRows), profiles = new JTable(profileRows), worlds = new JTable(worldRows);
    private final JTextArea log = new JTextArea(5, 70);
    private final JLabel activity = new JLabel("Ready"), account = new JLabel();
    private final JProgressBar progress = new JProgressBar();
    private final JButton cancel = new JButton("Cancel operation");
    private final List<JButton> actions = new ArrayList<>();
    private List<SavedServerRepository.SavedServer> serverValues = new ArrayList<>();
    private List<Profile> profileValues = new ArrayList<>();
    private List<World> worldValues = new ArrayList<>();
    private volatile Cancellation cancellation;
    private volatile boolean busy;

    public NativeMinecraftPanel(NativeClientContext context) {
        super(new BorderLayout(8, 8)); this.context = context;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Servers", serverPanel()); tabs.addTab("Profiles", profilePanel());
        tabs.addTab("Virtual worlds", worldPanel()); tabs.addTab("Account & settings", settingsPanel());
        add(tabs, BorderLayout.CENTER);
        log.setEditable(false); log.setLineWrap(true); log.setWrapStyleWord(true);
        JPanel footer = new JPanel(new BorderLayout());
        JPanel state = new JPanel(new BorderLayout(8, 0)); state.add(activity); state.add(progress, BorderLayout.EAST);
        footer.add(state, BorderLayout.NORTH); footer.add(new JScrollPane(log)); footer.add(cancel, BorderLayout.EAST);
        cancel.setEnabled(false); cancel.addActionListener(e -> { Cancellation token = cancellation; if (token != null) token.cancel(); });
        add(footer, BorderLayout.SOUTH);
        run("Load profiles and saved servers", () -> {
            Path vanilla = defaultMinecraftDirectory().resolve("servers.dat");
            if (context.servers().list().isEmpty() && Files.isRegularFile(vanilla)) context.servers().importVanilla(vanilla, "VANILLA");
            return load();
        }, this::render);
    }

    private static DefaultTableModel rows(String... columns) {
        return new DefaultTableModel(columns, 0) { @Override public boolean isCellEditable(int row, int column) { return false; } @Override public Class<?> getColumnClass(int column){return columns[column].equals("Preview")?Icon.class:Object.class;} };
    }
    private JPanel tablePanel(JTable table, JPanel buttons) {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); table.setAutoCreateRowSorter(true);
        JPanel panel = new JPanel(new BorderLayout(4,4)); panel.add(new JScrollPane(table));
        JScrollPane toolbar = new JScrollPane(buttons,ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        toolbar.setBorder(BorderFactory.createEmptyBorder());
        toolbar.setPreferredSize(new Dimension(0,buttons.getPreferredSize().height+18));
        panel.add(toolbar,BorderLayout.SOUTH); return panel;
    }
    private JButton button(JPanel panel, String label, Runnable operation) {
        JButton button = new JButton(label); button.addActionListener(e -> { try { operation.run(); } catch (Exception error) { showError(error); } });
        actions.add(button); panel.add(button); return button;
    }
    private JPanel serverPanel() {
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        button(buttons,"Add", () -> editServer(null)); button(buttons,"Edit", () -> editServer(server()));
        button(buttons,"Remove", () -> { SavedServerRepository.SavedServer s = server(); if (confirm("Remove saved server " + s.getName() + "? Minecraft's servers.dat will be preserved.")) mutate("Remove server", () -> {context.servers().delete(s.getId()); return null;}); });
        button(buttons,"Import servers.dat", () -> { Path path = chooseFile("Choose Minecraft servers.dat"); if(path != null) mutate("Import servers", () -> context.servers().importVanilla(path,"VANILLA")); });
        button(buttons,"Refresh status", () -> { SavedServerRepository.SavedServer s=server(); run("Contact server",()->context.ping(s.getAddress()),status->{ int index=serverValues.indexOf(s); if(index>=0)serverRows.setValueAt(status.toString(),index,3); }); });
        button(buttons,"Join", () -> joinServer(server()));
        return tablePanel(servers,buttons);
    }
    private void editServer(SavedServerRepository.SavedServer existing) {
        JTextField name=new JTextField(existing==null?"":existing.getName()), address=new JTextField(existing==null?"":existing.getAddress());
        JComboBox<String> loader=new JComboBox<>(LOADERS); if(existing!=null)loader.setSelectedItem(existing.getLoader());
        if(!form("Saved server", new String[]{"Name","Address (host:port)","Required loader (not discoverable by ping)"},new JComponent[]{name,address,loader}))return;
        String savedName=name.getText(),savedAddress=address.getText(),savedLoader=loader.getSelectedItem().toString();
        mutate("Save server",()-> { if(existing==null)context.servers().add(savedName,savedAddress,savedLoader); else context.servers().update(existing.getId(),savedName,savedAddress,savedLoader); return null; });
    }
    private void joinServer(SavedServerRepository.SavedServer server) {
        run("Read server version",()->context.ping(server.getAddress()),status->{
            String detected=detectVersion(status.getVersion());String version=detected.isEmpty()?input("Minecraft version (server did not report one; check its requirements)",""):detected; if(version==null)return;
            run("Find compatible local profiles",()->new ProfileResolver(context.profiles()).resolve(version,server.getLoader(),emptyToNull(context.settings().get("defaultTemplate",""))),resolution->{
                if(resolution.getState()==ProfileResolver.State.READY || resolution.getState()==ProfileResolver.State.CHOOSE_PROFILE) {
                    Profile selected=resolution.getProfiles().size()==1?resolution.getProfiles().get(0):chooseProfile(resolution.getProfiles(),"Client profile"); if(selected!=null)prepareJoin(selected,server);
                } else if(resolution.getState()==ProfileResolver.State.CONFIRM_TEMPLATE_CLONE) {
                    Profile template=resolution.getProfiles().get(0); String id=input("New profile cloned from " + template.getId(),"client-"+version); if(id==null)return;
                    run("Check template for target version",()->context.updater().planClone(template.getId(),id,version,cancellation::isCancelled),plan->approvePlan(plan,p->launch(p,server.getAddress())));
                } else {
                    String id=input("No matching profile. Create a client profile ID", "client-"+version); if(id==null)return;
                    run("Create client profile",()->context.profiles().create(id,ProfileType.MODS,version,server.getLoader(),false),p->prepareJoin(p,server));
                }
            });
        });
    }
    private void prepareJoin(Profile profile,SavedServerRepository.SavedServer server) {
        run("Check compatible mods",()->context.updater().check(profile.getId(),profile.getGameVersion(),cancellation::isCancelled), plan->{if(plan.canApply()&&plan.getChanges().stream().allMatch(change->change.getAction()==ProfileUpdateService.Action.KEEP))launch(profile,server.getAddress());else approvePlan(plan,p->launch(p,server.getAddress()));});
    }
    private void launch(Profile profile,String address) {
        if(profile.getType()!=ProfileType.MODS||profile.isTemplate())throw new IllegalArgumentException("Select a non-template client profile");
        run("Prepare and launch Minecraft",()-> { context.ensureLoader(profile.getId(),cancellation); return context.launch(profile.getId(),address,cancellation); }, process->{append("Started profile " + profile.getId() + ". Use Stop client to close it.");run("Reload running profiles",this::load,this::render);});
    }

    private JPanel profilePanel() {
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.LEFT));
        button(buttons,"Create",this::createProfile); button(buttons,"Clone / change version",()->planProfile(true));
        button(buttons,"Check / update",()->planProfile(false));
        button(buttons,"Rename",()-> { Profile p=profile(); String id=input("New profile ID",p.getId()); if(id!=null)mutate("Rename profile",()->{ensureUnreferenced(p); context.profiles().rename(p.getId(),id); if(p.getId().equals(context.settings().get("defaultTemplate",""))){context.settings().set("defaultTemplate",id);context.settings().save();} return null;}); });
        button(buttons,"Delete",()-> { Profile p=profile(); if(confirm("Permanently delete profile " + p.getId() + " and its local files?"))mutate("Delete profile",()->{ensureUnreferenced(p);context.profiles().delete(p.getId());if(p.getId().equals(context.settings().get("defaultTemplate",""))){context.settings().set("defaultTemplate","");context.settings().save();}return null;}); });
        button(buttons,"Toggle template",()-> { Profile p=profile();mutate("Change template",()->{if(!p.isTemplate())ensureUnreferenced(p);return context.profiles().updateTemplate(p.getId(),!p.isTemplate());}); });
        button(buttons,"Install loader",()-> {Profile p=profile();String version=input("Loader version (blank = official stable/recommended)","");if(version!=null)run("Install official loader",()->context.installLoader(p.getId(),emptyToNull(version),cancellation),id->append("Installed "+id));});
        button(buttons,"Launch",()->launch(profile(),null)); button(buttons,"Stop client",()->{ Profile p=profile();run("Stop client",()->{context.stopClient(p.getId());return load();},this::render); });
        button(buttons,"Refresh",()->run("Reload",this::load,this::render));
        return tablePanel(profiles,buttons);
    }
    private void ensureUnreferenced(Profile profile)throws IOException {
        for(World world:context.worlds().list()){
            if(world.getProfileId().equals(profile.getId()))throw new IOException("Profile is referenced by world " + world.getId());
            Path config=world.getDirectory().resolve("world-launch.properties");if(Files.exists(config,LinkOption.NOFOLLOW_LINKS)){
                if(!Files.isRegularFile(config,LinkOption.NOFOLLOW_LINKS))throw new IOException("Invalid world launch configuration");
                java.util.Properties saved=new java.util.Properties();try(java.io.InputStream input=Files.newInputStream(config,LinkOption.NOFOLLOW_LINKS)){saved.load(input);}
                if(profile.getId().equals(saved.getProperty("clientProfileId")))throw new IOException("Client profile is configured for world "+world.getId()+"; reconfigure the world first");
            }
        }
    }
    private void createProfile() {
        JTextField id=new JTextField(),version=new JTextField("1.20.4");JComboBox<ProfileType> type=new JComboBox<>(ProfileType.values());JComboBox<String> loader=new JComboBox<>(LOADERS);type.addActionListener(e->loader.setModel(new DefaultComboBoxModel<>(type.getSelectedItem()==ProfileType.PLUGINS?new String[]{"PAPER","SPIGOT","PURPUR","BUKKIT","FOLIA"}:LOADERS)));JCheckBox template=new JCheckBox("Template (clone before launch)");
        if(form("New isolated profile",new String[]{"ID","Type","Minecraft version","Loader",""},new JComponent[]{id,type,version,loader,template})){
            String savedId=id.getText(),savedVersion=version.getText(),savedLoader=loader.getSelectedItem().toString();ProfileType savedType=(ProfileType)type.getSelectedItem();boolean savedTemplate=template.isSelected();
            mutate("Create profile",()->context.profiles().create(savedId,savedType,savedVersion,savedLoader,savedTemplate));
        }
    }
    private void planProfile(boolean clone) {
        Profile p=profile();String target=input("Target Minecraft version",p.getGameVersion());if(target==null)return;
        String id=clone?input("New profile ID",p.getId()+"-copy"):p.getId();if(id==null)return;
        run("Resolve compatible assets",()->clone?context.updater().planClone(p.getId(),id,target,cancellation::isCancelled):context.updater().check(p.getId(),target,cancellation::isCancelled),plan->approvePlan(plan, ignored->run("Reload profiles",this::load,this::render)));
    }
    private void approvePlan(ProfileUpdateService.Plan plan,Consumer<Profile> next) {
        JTextArea text=new JTextArea(plan.getSummary(),14,65);text.setEditable(false);text.setCaretPosition(0);
        if(!plan.canApply()){JOptionPane.showMessageDialog(this,new JScrollPane(text),"Check failed; profile unchanged",JOptionPane.ERROR_MESSAGE);return;}
        if(JOptionPane.showConfirmDialog(this,new JScrollPane(text),"Apply this plan? Disabled jars require this approval",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        run("Apply approved plan",()->context.updater().apply(plan,true,cancellation::isCancelled),p->{append("Updated " + p.getId()); next.accept(p);});
    }

    private JPanel worldPanel() {
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.LEFT));
        button(buttons,"Create",()-> { List<Profile> candidates=new ArrayList<>();for(Profile p:profileValues)if(p.getType()!=ProfileType.MODS&&!p.isTemplate())candidates.add(p);Profile p=chooseProfile(candidates,"Server profile");if(p==null)return;String id=input("World ID","");if(id!=null)mutate("Create world",()->context.worlds().create(id,p.getId())); });
        button(buttons,"Configure",this::configureWorld);button(buttons,"Review EULA",this::worldEula);
        button(buttons,"Play",()->playWorld(false));button(buttons,"Play & share",()->playWorld(true));
        button(buttons,"Share address",()-> { World w=world();run("Get public join address",()->{WorldSessionManager.Session s=context.activeWorld(w.getId());if(s==null)throw new IOException("Start this world with Play & share first");if(s.getJoinAddress()==null)s.share();return s;},s->{String address=s.getJoinAddress();if(address==null)append(s.getShareMessage());else{Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(address),null);append("Copied join address: "+address);} }); });
        button(buttons,"Stop",()-> {World w=world();mutate("Stop world",()->{context.stopWorld(w.getId());return null;});});
        button(buttons,"Refresh",()->run("Reload worlds",this::load,this::render));
        JPanel panel=tablePanel(worlds,buttons);worlds.setRowHeight(52);panel.add(new JLabel("Worlds are local servers with independent saves. Multiplayer requires matching server mods/plugins."),BorderLayout.NORTH);return panel;
    }
    private void worldEula() {
        World w=world();JCheckBox accepted=new JCheckBox("I have read and accept the Minecraft EULA for this server");
        JPanel panel=new JPanel(new BorderLayout());JTextArea terms=new JTextArea("Review https://www.minecraft.net/eula before accepting.\nThis changes only this world's eula.txt.\nWorld: "+w.getDirectory());terms.setEditable(false);panel.add(terms);panel.add(accepted,BorderLayout.SOUTH);
        if(JOptionPane.showConfirmDialog(this,panel,"Minecraft EULA",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION&&accepted.isSelected())mutate("Record user's EULA acceptance",()->{ context.worlds().requireOwned(w);Files.write(w.getDirectory().resolve("eula.txt"),"eula=true\n".getBytes(StandardCharsets.UTF_8),StandardOpenOption.WRITE,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,LinkOption.NOFOLLOW_LINKS);return null;});
    }
    private void configureWorld() {
        World w=world();
        Profile serverProfile=profileValues.stream().filter(p->p.getId().equals(w.getProfileId())).findFirst().orElseThrow(()->new IllegalArgumentException("Missing server profile"));
        List<Profile> candidates=new ArrayList<>();
        for(Profile p:profileValues)if(p.getType()==ProfileType.MODS&&!p.isTemplate()&&p.getGameVersion().equals(serverProfile.getGameVersion())&&(serverProfile.getType()!=ProfileType.MODS_SERVER||p.getLoader().equalsIgnoreCase(serverProfile.getLoader())))candidates.add(p);
        Profile client=chooseProfile(candidates,"Matching client profile");if(client==null)return;
        Path jar=chooseFile("Choose the installed server jar matching this world's server profile");if(jar==null)return;
        mutate("Save world launch settings",()->{context.configureWorld(w.getId(),client.getId(),jar);return null;});
    }
    private void playWorld(boolean share) {
        World w=world();
        if(share&&!confirm("Share this local server? It will bind to your LAN and request a temporary UPnP router mapping. UPnP/CGNAT may prevent public access. Stop closes the mapping."))return;
        run("Check world launch configuration",()->context.worldConfig(w.getId()),config->{
            run("Prepare local server and client",()->{
                context.worlds().requireEulaAccepted(w); // validates consent without changing a running world
                context.ensureLoader(config.getClientProfileId(),cancellation);
                return context.playWorld(w.getId(),share,cancellation);
            },session->{append("World ready on port "+session.getPort()+". "+session.getShareMessage());run("Reload worlds",this::load,this::render);});
        });
    }

    private JPanel settingsPanel() {
        JPanel panel=new JPanel(new BorderLayout());JPanel fields=new JPanel(new GridLayout(0,2,8,8));
        JTextField clientId=new JTextField(context.settings().get("clientId","")),template=new JTextField(context.settings().get("defaultTemplate","")), username=new JTextField(context.settings().get("username","Player")),cache=new JTextField(context.settings().get("cacheDirectory",""));
        JTextArea javaHomes=new JTextArea(context.settings().get("javaHomes",""),4,35);JCheckBox offline=new JCheckBox("Offline fixture mode (no licensed online play)",Boolean.parseBoolean(context.settings().get("offlineFixtureMode","false")));
        String[] labels={"Registered Microsoft application client ID","Default template ID (optional)","Java homes (one per line)","Cache directory (restart required)","Offline username","Local testing"};
        JComponent[] components={clientId,template,new JScrollPane(javaHomes),cache,username,offline};for(int i=0;i<labels.length;i++){fields.add(new JLabel(labels[i]));fields.add(components[i]);}panel.add(fields,BorderLayout.NORTH);
        JTextArea help=new JTextArea("Use your own approved Microsoft application ID; no shared secret or borrowed client ID is included. Tokens remain in memory and are cleared on sign-out/exit.\nJava homes must contain release and bin/java; install the exact major version required by Minecraft.\nVanilla servers.dat is imported read-only. Profiles, configurations and worlds stay separate from your normal Minecraft installation.");help.setEditable(false);help.setLineWrap(true);help.setWrapStyleWord(true);panel.add(help);
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.LEFT));button(buttons,"Save settings",()->{String id=clientId.getText().trim(),base=template.getText().trim(),homes=javaHomes.getText(),cachePath=cache.getText().trim(),user=username.getText().trim(),fixture=Boolean.toString(offline.isSelected());mutate("Save settings",()-> {context.settings().set("clientId",id);context.settings().set("defaultTemplate",base);context.settings().set("javaHomes",homes);context.settings().set("cacheDirectory",cachePath);context.settings().set("username",user);context.settings().set("offlineFixtureMode",fixture);context.settings().save();return null;});});
        button(buttons,"Sign in with Microsoft",()->signIn(offline));button(buttons,"Sign out",()->{context.signOut();account.setText(context.getAccountLabel());});account.setText(context.getAccountLabel());buttons.add(account);panel.add(buttons,BorderLayout.SOUTH);return panel;
    }
    private void signIn(JCheckBox offlineMode) {
        run("Request Microsoft device code",()->context.authenticator().begin(cancellation),authorization->{
            JTextArea instruction=new JTextArea("Open in your browser: "+authorization.getVerificationUri()+"\nEnter this code: "+authorization.getUserCode()+"\nContinue only after reviewing the account and permissions on Microsoft's site.");instruction.setEditable(false);
            if(JOptionPane.showConfirmDialog(this,instruction,"Microsoft sign-in",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
            run("Waiting for Microsoft sign-in (Cancel to stop)",()->{MicrosoftDeviceCodeAuth.AuthResult result=context.authenticator().poll(authorization,cancellation);cancellation.check();context.signedIn(result);context.settings().set("offlineFixtureMode","false");context.settings().save();return result;},result->{offlineMode.setSelected(false);account.setText(context.getAccountLabel());append("Signed in as "+context.getAccountLabel());});
        });
    }

    private final class Snapshot {
        List<SavedServerRepository.SavedServer> servers;List<Profile> profiles;List<World> worlds;java.util.Map<String,Icon> previews=new java.util.HashMap<>();
    }
    private Snapshot load()throws IOException {Snapshot state=new Snapshot();state.servers=context.servers().list();state.profiles=context.profiles().list();state.worlds=context.worlds().list();for(World world:state.worlds){context.worlds().requireOwned(world);state.previews.put(world.getId(),worldPreview(world.getDirectory()));}return state;}
    private void render(Snapshot state) {
        serverValues=state.servers;profileValues=state.profiles;worldValues=state.worlds;serverRows.setRowCount(0);profileRows.setRowCount(0);worldRows.setRowCount(0);
        for(SavedServerRepository.SavedServer s:serverValues)serverRows.addRow(new Object[]{s.getName(),s.getAddress(),s.getLoader(),"Not checked"});
        for(Profile p:profileValues)profileRows.addRow(new Object[]{p.getId(),p.getType(),p.getGameVersion(),p.getLoader(),p.isTemplate()});
        for(World w:worldValues){WorldSessionManager.Session session=context.activeWorld(w.getId());worldRows.addRow(new Object[]{state.previews.get(w.getId()),w.getId(),w.getProfileId(),session==null?"Stopped":session.getFailure()!=null?"Failed: "+session.getFailure().getMessage():"Running :"+session.getPort()});}
        account.setText(context.getAccountLabel());
    }
    /** Decode only small, direct PNG previews, off the UI thread. */
    static Icon worldPreview(Path world) {
        for(String relative:new String[]{"server-icon.png","world/icon.png"}) {
            Path path=world.resolve(relative);
            try {
                if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>1024*1024||!path.toRealPath().startsWith(world.toRealPath()))continue;
                try(javax.imageio.stream.ImageInputStream input=javax.imageio.ImageIO.createImageInputStream(path.toFile())) {
                    java.util.Iterator<javax.imageio.ImageReader> readers=javax.imageio.ImageIO.getImageReaders(input);
                    if(!readers.hasNext())continue;javax.imageio.ImageReader reader=readers.next();
                    try {reader.setInput(input);if(!reader.getFormatName().equalsIgnoreCase("png")||reader.getWidth(0)>512||reader.getHeight(0)>512)continue;
                        return new ImageIcon(reader.read(0).getScaledInstance(48,48,Image.SCALE_SMOOTH));
                    } finally {reader.dispose();}
                }
            }catch(IOException|RuntimeException ignored){ /* A missing/corrupt preview never blocks playing a world. */ }
        }
        return null;
    }
    private <T> void mutate(String label,Callable<T> operation){run(label,()->{T value=operation.call();return load();},this::render);}
    private <T> void run(String label,Callable<T> operation,Consumer<T> complete) {
        if(busy){append("Wait for the current operation or cancel it.");return;}busy=true;cancellation=new Cancellation();Cancellation token=cancellation;setBusy(true,label);
        new SwingWorker<T,Void>(){
            @Override protected T doInBackground()throws Exception{token.check();return operation.call();}
            @Override protected void done(){busy=false;setBusy(false,"Ready");try{T result=get();if(token.isCancelled()){append("Operation completed before cancellation could stop it. Completed changes are retained; no next launch will start.");NativeMinecraftPanel.this.run("Reload completed state",NativeMinecraftPanel.this::load,NativeMinecraftPanel.this::render);return;}complete.accept(result);}catch(Exception error){Throwable cause=error.getCause()==null?error:error.getCause();if(token.isCancelled())append("Cancelled: "+cause.getMessage());else showError(cause);}}
        }.execute();
    }
    private void setBusy(boolean value,String label){for(JButton button:actions)button.setEnabled(!value);cancel.setEnabled(value);activity.setText(label);progress.setIndeterminate(value);}
    private void append(String message){log.append(message+"\n");log.setCaretPosition(log.getDocument().getLength());}
    private void showError(Throwable error){String message=error.getMessage()==null?error.getClass().getSimpleName():error.getMessage();append("Error: "+message);JOptionPane.showMessageDialog(this,message,"Minecraft operation failed",JOptionPane.ERROR_MESSAGE);}
    private SavedServerRepository.SavedServer server(){return serverValues.get(selected(servers));}
    private Profile profile(){return profileValues.get(selected(profiles));}
    private World world(){return worldValues.get(selected(worlds));}
    private int selected(JTable table){int row=table.getSelectedRow();if(row<0)throw new IllegalArgumentException("Select a row first");return table.convertRowIndexToModel(row);}
    private boolean confirm(String text){return JOptionPane.showConfirmDialog(this,text,"Confirm",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION;}
    private String input(String text,String value){return (String)JOptionPane.showInputDialog(this,text,"Minecraft",JOptionPane.QUESTION_MESSAGE,null,null,value);}
    private boolean form(String title,String[] labels,JComponent[] values){JPanel panel=new JPanel(new GridLayout(0,2,8,8));for(int i=0;i<labels.length;i++){panel.add(new JLabel(labels[i]));panel.add(values[i]);}return JOptionPane.showConfirmDialog(this,panel,title,JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION;}
    private Profile chooseProfile(List<Profile> candidates,String label){if(candidates.isEmpty())throw new IllegalArgumentException("Create a suitable profile first");String[] ids=candidates.stream().map(Profile::getId).toArray(String[]::new);String id=(String)JOptionPane.showInputDialog(this,label,"Choose profile",JOptionPane.QUESTION_MESSAGE,null,ids,ids[0]);if(id==null)return null;return candidates.stream().filter(p->p.getId().equals(id)).findFirst().orElse(null);}
    private Path chooseFile(String label){JFileChooser chooser=new JFileChooser();chooser.setDialogTitle(label);return chooser.showOpenDialog(this)==JFileChooser.APPROVE_OPTION?chooser.getSelectedFile().toPath():null;}
    private static String emptyToNull(String value){return value==null||value.trim().isEmpty()?null:value.trim();}
    static String detectVersion(String status){if(status!=null){java.util.regex.Matcher match=java.util.regex.Pattern.compile("(?<![0-9.])1\\.[0-9]+(?:\\.[0-9]+)?(?![0-9.])").matcher(status);if(match.find()){String version=match.group();while(match.find())if(!version.equals(match.group()))return "";return version;}}return "";}
    public static Path defaultMinecraftDirectory(){String os=System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT);if(os.contains("win")){String appData=System.getenv("APPDATA");return Paths.get(appData==null?System.getProperty("user.home"):appData,".minecraft");}if(os.contains("mac"))return Paths.get(System.getProperty("user.home"),"Library","Application Support","minecraft");return Paths.get(System.getProperty("user.home"),".minecraft");}
}
