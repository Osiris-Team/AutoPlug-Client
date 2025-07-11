import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

class ThisProject extends JPM.Project {

    public ThisProject(List<String> args) {
        // Override default configurations
        this.groupId = "com.osiris.autoplug.client";
        this.artifactId = "AutoPlug-Client";
        this.version = "8.3.5";
        this.mainClass = "com.osiris.autoplug.client.Main";
        this.jarName = "AutoPlug-Client-original.jar";
        this.fatJarName = "AutoPlug-Client.jar";
        this.javaVersionSource = "9";
        this.javaVersionTarget = "9";

        // Add repositories
        addRepository("https://oss.sonatype.org/content/repositories/snapshots");
        addRepository("https://jitpack.io");
        addRepository("https://repo.panda-lang.org/");
        addRepository("https://repo.codemc.io/repository/maven-public/");

        // Force dependencies
        forceImplementation("net.java.dev.jna:jna:5.14.0");
        forceImplementation("net.java.dev.jna:jna-platform:5.14.0");
        forceImplementation("commons-io:commons-io:2.16.1");
        forceImplementation("com.github.Osiris-Team:jansi:2.4.6");
        forceImplementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.23");
        forceImplementation("org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23");
        forceImplementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.21");
        forceImplementation("org.slf4j:slf4j-api:2.0.13");

        forceImplementation("org.jline:jline:3.26.1");
        forceImplementation("org.jline:jline-reader:3.20.0");
        forceImplementation("org.jetbrains:annotations:23.0.0");

        // Add dependencies
        implementation("net.java.dev.jna:jna:5.14.0");
        implementation("net.java.dev.jna:jna-platform:5.14.0");
        implementation("commons-io:commons-io:2.16.1");
        implementation("com.github.Osiris-Team:jansi:2.4.6");
        implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.23");
        implementation("org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23");
        implementation("org.slf4j:slf4j-api:2.0.13");
        implementation("org.slf4j:slf4j-nop:2.0.13");
        implementation("com.github.Osiris-Team:jlib:18.6");
        implementation("com.github.Osiris-Team:Dyml:9.8.3");
        implementation("com.github.Osiris-Team:Better-Thread:5.1.2");
        implementation("com.github.Osiris-Team:Better-Layout:1.4.0");
        implementation("com.github.Osiris-Team:jProcesses2:2.1.9");
        implementation("com.github.oshi:oshi-core:6.6.1");
        implementation("net.lingala.zip4j:zip4j:2.11.3");
        implementation("org.rauschig:jarchivelib:1.2.0");
        implementation("org.jetbrains:annotations:23.0.0");
        implementation("commons-lang:commons-lang:2.6");
        implementation("commons-net:commons-net:3.9.0");
        implementation("com.github.mwiede:jsch:0.2.17");
        implementation("org.quartz-scheduler:quartz:2.3.2");
        implementation("org.tomlj:tomlj:1.0.0");
        implementation("com.formdev:flatlaf:2.2");
        implementation("org.apache.sshd:sshd-core:2.13.0");
        implementation("org.json:json:20250107");

        // Google Drive
        implementation("com.google.api-client:google-api-client:2.0.0");
        implementation("com.google.oauth-client:google-oauth-client-jetty:1.34.1");
        implementation("com.google.apis:google-api-services-drive:v3-rev20220815-2.0.0");
        forceImplementation("com.google.http-client:google-http-client:1.42.0");
        forceImplementation("org.apache.httpcomponents:httpcore:4.4.15");
        forceImplementation("com.google.http-client:google-http-client-gson:1.42.1");
        forceImplementation("com.google.guava:guava:31.1-jre");
        forceImplementation("com.google.errorprone:error_prone_annotations:2.27.0");
        forceImplementation("com.google.code.gson:gson:2.11.0");

        // Minecraft client:
        implementation("org.nanohttpd:nanohttpd:2.3.1");
        implementation("org.glavo.kala:kala-compress:1.27.1-1");

        testImplementation("org.junit.jupiter:junit-jupiter:5.9.2");

        // Add custom plugins
        plugins.add(new AutoPlugPropertiesPlugin());
    }

    public static void main(String[] _args) throws Exception {
        List<String> args = Arrays.asList(_args);
        File cwd = new File(System.getProperty("user.dir"));
        ThisProject project = new ThisProject(args);
        project.generatePom();

        List<String> mavenArgs = new ArrayList<>();
        mavenArgs.add("clean");mavenArgs.add("package");
        //mavenArgs.addAll(Arrays.asList("dependency:purge-local-repository -Dinclude:jansi -DresolutionFuzziness=artifactId -Dverbose".split(" ")));
        //mavenArgs.add("install");
        if(!args.contains("test")) mavenArgs.add("-DskipTests");
        JPM.executeMaven(mavenArgs.toArray(new String[0]));

        File testServerDir = new File(cwd+"/AP-TEST-SERVER");
        testServerDir.mkdirs();
        String jarName = project.artifactId+".jar";
        Files.copy(new File(cwd+"/target/"+jarName).toPath(),
                new File(testServerDir+"/"+jarName).toPath(),
                StandardCopyOption.REPLACE_EXISTING);

        File testServerDir2 = new File(cwd+"/AP-TEST-SERVER/AP-TEST-SERVER-LIVE");
        testServerDir2.mkdirs();
        Files.copy(new File(cwd+"/target/"+jarName).toPath(),
                new File(testServerDir2+"/"+jarName).toPath(),
                StandardCopyOption.REPLACE_EXISTING);
        if(args.contains("andRun")){
            Process result = new ProcessBuilder("java", "-jar", jarName).directory(testServerDir).inheritIO().start();
        }
    }

    /**
     * Custom plugin for generating autoplug.properties.
     *     Important for AutoPlugs Self-Updater!
     *     Generates a autoplug.properties file inside the generated jar file, with the stuff inside <properties>
     *     Also take look at <build> where these properties get turned into the actual autoplug.properties file.
     */
    private static class AutoPlugPropertiesPlugin extends JPM.Plugin {
        public AutoPlugPropertiesPlugin() {
            super("org.codehaus.mojo", "properties-maven-plugin", "1.0.0");
            onBeforeToXML((project, pom) -> {
                JPM.Execution execution = new JPM.Execution("generate-properties", "generate-resources");
                execution.addGoal("write-project-properties");
                execution.putConfiguration("outputFile", "${project.build.outputDirectory}/autoplug.properties");
                addExecution(execution);

                pom.put("properties java.version", project.javaVersionSource);
                pom.put("properties version", project.version);
                //         The main class or this jars entry point. Is optional.
                pom.put("properties main-class", project.mainClass);
                pom.put("properties slf4j.version", "2.0.13");
                pom.put("properties name", project.artifactId);
                pom.put("properties project.build.sourceEncoding", "UTF-8");
                pom.put("properties id", "0");
                //        The installation path, is where this jar should be installed to
                //        You can enter a directory path in linux and windows format.
                //        A dot "." like above means in the current jars working directory aka System.getProperty("user.dir").
                //        Some examples:
                //        ./installDir/MyApp.jar or C:/HelloDir/MyApp.jar
                pom.put("properties installation-path", "./"+project.artifactId+".jar");
            });
        }
    }
}

class ThirdPartyPlugins extends JPM.Plugins{
    // Add third party plugins below, find them here: https://github.com/topics/1jpm-plugin?o=desc&s=updated
    // (If you want to develop a plugin take a look at "JPM.Clean" class further below to get started)
}

// 1JPM version 2.2.0 by Osiris-Team: https://github.com/Osiris-Team/1JPM
// To upgrade JPM, replace the JPM class below with its newer version
public class JPM {
    public static final List<Plugin> plugins = new ArrayList<>();
    private static final String mavenVersion = "3.9.8";
    private static final String mavenWrapperVersion = "3.3.2";
    private static final String mavenWrapperScriptUrlBase = "https://raw.githubusercontent.com/apache/maven-wrapper/maven-wrapper-"+ mavenWrapperVersion +"/maven-wrapper-distribution/src/resources/";
    private static final String mavenWrapperJarUrl = "https://repo1.maven.org/maven2/org/apache/maven/wrapper/maven-wrapper/"+ mavenWrapperVersion +"/maven-wrapper-"+ mavenWrapperVersion +".jar";
    private static final String mavenWrapperPropsContent = "distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/"+ mavenVersion +"/apache-maven-"+ mavenVersion +"-bin.zip";

    public static void main(String[] args) throws Exception {
        ThisProject.main(args);
    }

    public static void executeMaven(String... args) throws IOException, InterruptedException {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

        ProcessBuilder p = new ProcessBuilder();
        List<String> finalArgs = new ArrayList<>();
        File userDir = new File(System.getProperty("user.dir"));
        File mavenWrapperFile = new File(userDir, "mvnw" + (isWindows ? ".cmd" : ""));
        File propertiesFile = new File(userDir, ".mvn/wrapper/maven-wrapper.properties");
        File mavenWrapperJarFile = new File(userDir, ".mvn/wrapper/maven-wrapper.jar");

        if (!mavenWrapperFile.exists()) {
            downloadMavenWrapper(mavenWrapperFile);
            if(!isWindows) mavenWrapperFile.setExecutable(true);
        }
        if(!mavenWrapperJarFile.exists()) downloadMavenWrapperJar(mavenWrapperJarFile);
        if (!propertiesFile.exists()) createMavenWrapperProperties(propertiesFile);

        finalArgs.add(mavenWrapperFile.getAbsolutePath());
        finalArgs.addAll(Arrays.asList(args));
        p.command(finalArgs);
        p.inheritIO();
        System.out.print("Executing: ");
        for (String arg : finalArgs) {
            System.out.print(arg+" ");
        }
        System.out.println();
        Process result = p.start();
        result.waitFor();
        if(result.exitValue() != 0)
            throw new RuntimeException("Maven ("+mavenWrapperFile.getName()+") finished with an error ("+result.exitValue()+"): "+mavenWrapperFile.getAbsolutePath());
    }

    private static void downloadMavenWrapper(File script) throws IOException {
        String wrapperUrl = mavenWrapperScriptUrlBase + script.getName();

        System.out.println("Downloading file from: " + wrapperUrl);
        URL url = new URL(wrapperUrl);
        script.getParentFile().mkdirs();
        Files.copy(url.openStream(), script.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private static void downloadMavenWrapperJar(File jar) throws IOException {
        String wrapperUrl = mavenWrapperJarUrl;

        System.out.println("Downloading file from: " + wrapperUrl);
        URL url = new URL(wrapperUrl);
        jar.getParentFile().mkdirs();
        Files.copy(url.openStream(), jar.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private static void createMavenWrapperProperties(File propertiesFile) throws IOException {
        // Create the .mvn directory if it doesn't exist
        File mvnDir = propertiesFile.getParentFile();
        if (!mvnDir.exists()) {
            mvnDir.mkdirs();
        }

        // Write default properties content to the file
        try (FileWriter writer = new FileWriter(propertiesFile)) {
            writer.write(mavenWrapperPropsContent);
        }
    }

    //
    // API and Models
    //

    public static class Plugins {
    }

    public static interface ConsumerWithException<T> extends Serializable {
        void accept(T t) throws Exception;
    }

    public static class Dependency {
        public String groupId;
        public String artifactId;
        public String version;
        public String scope;
        public List<Dependency> transitiveDependencies;

        public Dependency(String groupId, String artifactId, String version) {
            this(groupId, artifactId, version, "compile", new ArrayList<>());
        }

        public Dependency(String groupId, String artifactId, String version, String scope) {
            this(groupId, artifactId, version, scope, new ArrayList<>());
        }

        public Dependency(String groupId, String artifactId, String version, String scope, List<Dependency> transitiveDependencies) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
            this.scope = scope;
            this.transitiveDependencies = transitiveDependencies;
        }

        @Override
        public String toString() {
            return groupId + ":" + artifactId + ":" + version + ":" + scope;
        }

        public XML toXML(){
            XML xml = new XML("dependency");
            xml.put("groupId", groupId);
            xml.put("artifactId", artifactId);
            xml.put("version", version);
            if (scope != null) {
                xml.put("scope", scope);
            }
            return xml;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Dependency that = (Dependency) o;
            return Objects.equals(groupId, that.groupId) &&
                    Objects.equals(artifactId, that.artifactId) &&
                    Objects.equals(version, that.version) &&
                    Objects.equals(scope, that.scope);
        }

        @Override
        public int hashCode() {
            return Objects.hash(groupId, artifactId, version, scope);
        }
    }

    public static class Repository{
        public String id;
        public String url;

        public Repository(String id, String url) {
            this.id = id;
            this.url = url;
        }

        public static Repository fromUrl(String url){
            String id = url.split("//")[1].split("/")[0].replace(".", "").replace("-", "");
            return new Repository(id, url);
        }

        public XML toXML(){
            XML xml = new XML("repository");
            xml.put("id", id);
            xml.put("url", url);
            return xml;
        }
    }

    public static class XML {
        private Document document;
        private Element root;

        // Constructor initializes the XML document with a root element.
        public XML(String rootName) {
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = factory.newDocumentBuilder();
                document = builder.newDocument();
                root = document.createElement(rootName);
                document.appendChild(root);
            } catch (ParserConfigurationException e) {
                e.printStackTrace();
            }
        }

        // Method to append another XML object to this XML document's root.
        public XML add(XML otherXML) {
            Node importedNode = document.importNode(otherXML.root, true);
            root.appendChild(importedNode);
            return this;
        }

        // Method to append another XML object to a specific element in this XML document.
        public XML add(String key, XML otherXML) {
            Element parentElement = getOrCreateElement(key);
            Node importedNode = document.importNode(otherXML.root, true);
            parentElement.appendChild(importedNode);
            return this;
        }

        // Method to add a value to the XML document at the specified path.
        public XML put(String key, String value) {
            Element currentElement = getOrCreateElement(key);
            if(value != null && !value.isEmpty())
                currentElement.setTextContent(value);
            return this;
        }

        // Method to add a comment to the XML document at the specified path.
        public XML putComment(String key, String comment) {
            Element currentElement = getOrCreateElement(key);
            Node parentNode = currentElement.getParentNode();
            Node commentNode = document.createComment(comment);

            // Insert the comment before the specified element.
            parentNode.insertBefore(commentNode, currentElement);
            return this;
        }

        public XML putAttributes(String key, String... attributes) {
            if (attributes.length % 2 != 0) {
                throw new IllegalArgumentException("Attributes must be in key-value pairs.");
            }

            Element currentElement = getOrCreateElement(key);

            // Iterate over pairs of strings to set each attribute on the element.
            for (int i = 0; i < attributes.length; i += 2) {
                String attrName = attributes[i];
                String attrValue = attributes[i + 1];
                currentElement.setAttribute(attrName, attrValue);
            }
            return this;
        }

        // Method to add attributes to an element in the XML document at the specified path.
        public XML putAttributes(String key, Map<String, String> attributes) {
            Element currentElement = getOrCreateElement(key);

            // Set each attribute in the map on the element.
            for (Map.Entry<String, String> entry : attributes.entrySet()) {
                currentElement.setAttribute(entry.getKey(), entry.getValue());
            }
            return this;
        }

        // Helper method to traverse or create elements based on a path.
        private Element getOrCreateElement(String key) {
            if (key == null || key.trim().isEmpty()) return root;
            String[] path = key.split(" ");
            Element currentElement = root;

            for (String nodeName : path) {
                Element childElement = null;
                NodeList children = currentElement.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    Node child = children.item(i);
                    if (child.getNodeType() == Node.ELEMENT_NODE && child.getNodeName().equals(nodeName)) {
                        childElement = (Element) child;
                        break;
                    }
                }

                if (childElement == null) {
                    childElement = document.createElement(nodeName);
                    currentElement.appendChild(childElement);
                }
                currentElement = childElement;
            }

            return currentElement;
        }

        // Method to convert the XML document to a pretty-printed string.
        public String toString() {
            try {
                javax.xml.transform.TransformerFactory transformerFactory = javax.xml.transform.TransformerFactory.newInstance();
                javax.xml.transform.Transformer transformer = transformerFactory.newTransformer();

                // Enable pretty printing with indentation and newlines.
                transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4"); // Adjust indent space as needed

                javax.xml.transform.dom.DOMSource domSource = new javax.xml.transform.dom.DOMSource(document);
                java.io.StringWriter writer = new java.io.StringWriter();
                javax.xml.transform.stream.StreamResult result = new javax.xml.transform.stream.StreamResult(writer);
                transformer.transform(domSource, result);

                return writer.toString();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        public static void main(String[] args) {
            // Example usage of the XML class.
            XML xml = new XML("root");
            xml.put("this is a key", "value");
            xml.put("this is another key", "another value");
            xml.putComment("this is another", "This is a comment for 'another'");
            Map<String, String> atr = new HashMap<>();
            atr.put("attr1", "value1");
            atr.put("attr2", "value2");
            xml.putAttributes("this is a key", atr);
            System.out.println(xml.toString());
        }
    }

    public static class Plugin {
        public List<BiConsumer<Project, XML>> beforeToXMLListeners = new CopyOnWriteArrayList<>();
        protected String groupId;
        protected String artifactId;
        protected String version;

        // Gets cleared after execute
        protected Map<String, String> configuration = new HashMap<>();
        // Gets cleared after execute
        protected List<Execution> executions = new ArrayList<>();
        // Gets cleared after execute
        protected List<Dependency> dependencies = new ArrayList<>();

        public Plugin(String groupId, String artifactId, String version) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
        }

        public Plugin putConfiguration(String key, String value) {
            configuration.put(key, value);
            return this;
        }

        public Execution addExecution(String id, String phase){
            Execution execution = new Execution(id, phase);
            executions.add(execution);
            return execution;
        }

        public Execution addExecution(Execution execution) {
            executions.add(execution);
            return execution;
        }

        public Plugin addDependency(Dependency dependency) {
            dependencies.add(dependency);
            return this;
        }

        public Plugin onBeforeToXML(BiConsumer<Project, XML> code){
            beforeToXMLListeners.add(code);
            return this;
        }

        private void executeBeforeToXML(Project project, XML projectXML) {
            for (BiConsumer<Project, XML> code : beforeToXMLListeners) {
                code.accept(project, projectXML);
            }
        }

        private void executeAfterToXML(Project project) {
            configuration.clear();
            executions.clear();
            dependencies.clear();
        }

        /**
         * Usually you will override this.
         */
        public XML toXML(Project project, XML projectXML) {
            executeBeforeToXML(project, projectXML);

            // Create an XML object for the <plugin> element
            XML xml = new XML("plugin");
            xml.put("groupId", groupId);
            xml.put("artifactId", artifactId);
            xml.put("version", version);

            // Add <configuration> elements if present
            if (!configuration.isEmpty()) {
                for (Map.Entry<String, String> entry : configuration.entrySet()) {
                    xml.put("configuration " + entry.getKey(), entry.getValue());
                }
            }

            // Add <executions> if not empty
            if (!executions.isEmpty()) {
                for (Execution execution : executions) {
                    xml.add("executions", execution.toXML());
                }
            }

            // Add <dependencies> if not empty
            if (!dependencies.isEmpty()) {
                for (Dependency dependency : dependencies) {
                    xml.add("dependencies", dependency.toXML());
                }
            }

            executeAfterToXML(project);
            return xml;
        }
    }

    public static class Execution {
        private String id;
        private String phase;
        private List<String> goals;
        private Map<String, String> configuration;

        public Execution(String id, String phase) {
            this.id = id;
            this.phase = phase;
            this.goals = new ArrayList<>();
            this.configuration = new HashMap<>();
        }

        public Execution addGoal(String goal) {
            goals.add(goal);
            return this;
        }

        public Execution putConfiguration(String key, String value) {
            configuration.put(key, value);
            return this;
        }

        public XML toXML() {
            // Create an instance of XML with the root element <execution>
            XML xml = new XML("execution");

            // Add <id> element
            if(id != null && !id.isEmpty()) xml.put("id", id);

            // Add <phase> element if it is not null or empty
            if (phase != null && !phase.isEmpty()) {
                xml.put("phase", phase);
            }

            // Add <goals> element if goals list is not empty
            if (!goals.isEmpty()) {
                for (String goal : goals) {
                    XML goalXml = new XML("goal");
                    goalXml.put("", goal);
                    xml.add("goals", goalXml);
                }
            }

            // Add <configuration> element if configuration map is not empty
            if (!configuration.isEmpty()) {
                xml.put("configuration", ""); // Placeholder for <configuration> element
                for (Map.Entry<String, String> entry : configuration.entrySet()) {
                    xml.put("configuration " + entry.getKey(), entry.getValue());
                }
            }

            // Return the XML configuration as a string
            return xml;
        }
    }

    public static class Project {
        protected String jarName = "output.jar";
        protected String fatJarName = "output-fat.jar";
        protected String mainClass = "com.example.Main";
        protected String groupId = "com.example";
        protected String artifactId = "project";
        protected String version = "1.0.0";
        protected String javaVersionSource = "8";
        protected String javaVersionTarget = "8";
        protected List<Repository> repositories = new ArrayList<>();
        protected List<Dependency> dependenciesManaged = new ArrayList<>();
        protected List<Dependency> dependencies = new ArrayList<>();
        protected List<Plugin> plugins = new ArrayList<>();
        protected List<String> compilerArgs = new ArrayList<>();

        public void addRepository(String url){
            repositories.add(Repository.fromUrl(url));
        }

        public void testImplementation(String s){
            String[] split = s.split(":");
            if(split.length < 3) throw new RuntimeException("Does not contain all required details: "+s);
            addDependency(split[0], split[1], split[2]).scope = "test";
        }

        public void implementation(String s){
            String[] split = s.split(":");
            if(split.length < 3) throw new RuntimeException("Does not contain all required details: "+s);
            addDependency(split[0], split[1], split[2]);
        }

        public Dependency addDependency(String groupId, String artifactId, String version) {
            Dependency dep = new Dependency(groupId, artifactId, version);
            dependencies.add(dep);
            return dep;
        }

        public void forceImplementation(String s){
            String[] split = s.split(":");
            if(split.length < 3) throw new RuntimeException("Does not contain all required details: "+s);
            forceDependency(split[0], split[1], split[2]);
        }

        public void forceDependency(String groupId, String artifactId, String version) {
            dependenciesManaged.add(new Dependency(groupId, artifactId, version));
        }

        public void addCompilerArg(String arg) {
            compilerArgs.add(arg);
        }

        public void generatePom() throws IOException {
            // Create a new XML document with the root element <project>
            XML pom = new XML("project");
            pom.putComment("", "\n\n\n\nAUTO-GENERATED FILE, CHANGES SHOULD BE DONE IN ./JPM.java or ./src/main/java/JPM.java\n\n\n\n");
            pom.putAttributes("",
                    "xmlns", "http://maven.apache.org/POM/4.0.0",
                    "xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance",
                    "xsi:schemaLocation", "http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd"
            );

            // Add <modelVersion> element
            pom.put("modelVersion", "4.0.0");

            // Add main project identifiers
            pom.put("groupId", groupId);
            pom.put("artifactId", artifactId);
            pom.put("version", version);

            // Add <properties> element
            pom.put("properties project.build.sourceEncoding", "UTF-8");

            // Add <repositories> if not empty
            if (!repositories.isEmpty()) {
                for (Repository rep : repositories) {
                    pom.add("repositories", rep.toXML());
                }
            }

            // Add <dependencyManagement> if there are managed dependencies
            if (!dependenciesManaged.isEmpty()) {
                for (Dependency dep : dependenciesManaged) {
                    pom.add("dependencyManagement dependencies", dep.toXML());
                }
            }

            // Add <dependencies> if there are dependencies
            if (!dependencies.isEmpty()) {
                for (Dependency dep : dependencies) {
                    pom.add("dependencies", dep.toXML());
                }
            }

            // Add <build> section with plugins and resources
            for (Plugin plugin : JPM.plugins) {
                pom.add("build plugins", plugin.toXML(this, pom));
            }
            for (Plugin plugin : plugins) {
                pom.add("build plugins", plugin.toXML(this, pom));
            }

            // Add resources with a comment
            pom.putComment("build resources", "Sometimes unfiltered resources cause unexpected behaviour, thus enable filtering.");
            pom.put("build resources resource directory", "src/main/resources");
            pom.put("build resources resource filtering", "true");

            // Write to pom.xml
            File pomFile = new File(System.getProperty("user.dir") + "/pom.xml");
            try (FileWriter writer = new FileWriter(pomFile)) {
                writer.write(pom.toString());
            }
            System.out.println("Generated pom.xml file.");
        }
    }

    static {
        plugins.add(CompilerPlugin.get);
    }
    public static class CompilerPlugin extends Plugin {
        public static CompilerPlugin get = new CompilerPlugin();
        public CompilerPlugin() {
            super("org.apache.maven.plugins", "maven-compiler-plugin", "3.8.1");
            onBeforeToXML((project, pom) -> {
                putConfiguration("source", project.javaVersionSource);
                putConfiguration("target", project.javaVersionTarget);

                // Add compiler arguments from the project
                if (!project.compilerArgs.isEmpty()) {
                    for (String arg : project.compilerArgs) {
                        putConfiguration("compilerArgs arg", arg);
                    }
                }
            });
        }
    }

    static {
        plugins.add(JarPlugin.get);
    }
    public static class JarPlugin extends Plugin {
        public static JarPlugin get = new JarPlugin();
        public JarPlugin() {
            super("org.apache.maven.plugins", "maven-jar-plugin", "3.2.0");
            onBeforeToXML((project, pom) -> {
                putConfiguration("archive manifest addClasspath", "true");
                putConfiguration("archive manifest mainClass", project.mainClass);
                putConfiguration("finalName", project.jarName.replace(".jar", ""));
            });
        }
    }

    static {
        //plugins.add(AssemblyPlugin.get);
    }
    public static class AssemblyPlugin extends Plugin {
        public static AssemblyPlugin get = new AssemblyPlugin();
        public AssemblyPlugin() {
            super("org.apache.maven.plugins", "maven-assembly-plugin", "3.3.0");
            onBeforeToXML((project, pom) -> {
                putConfiguration("descriptorRefs descriptorRef", "jar-with-dependencies");
                putConfiguration("archive manifest mainClass", project.mainClass);
                putConfiguration("finalName", project.fatJarName.replace(".jar", ""));
                putConfiguration("appendAssemblyId", "false");

                addExecution("make-assembly", "package")
                        .addGoal("single");
            });
        }
    }

    static {
        plugins.add(ShadePlugin.get);
    }
    public static class ShadePlugin extends Plugin {
        public static ShadePlugin get = new ShadePlugin();
        public ShadePlugin() {
            super("org.apache.maven.plugins", "maven-shade-plugin", "3.2.1");
            onBeforeToXML((project, pom) -> {
                putConfiguration("finalName", project.fatJarName.replace(".jar", ""));

                addExecution(null, "package")
                        .addGoal("shade")
                        .putConfiguration("createDependencyReducedPom", "false")
                ;
            });
        }
    }

    static {
        plugins.add(SourcePlugin.get);
    }
    public static class SourcePlugin extends Plugin {
        public static SourcePlugin get = new SourcePlugin();
        public SourcePlugin() {
            super("org.apache.maven.plugins", "maven-source-plugin", "3.2.1");
            onBeforeToXML((project, pom) -> {
                addExecution("attach-sources", null)
                        .addGoal("jar");
            });
        }
    }

    static {
        plugins.add(JavadocPlugin.get);
    }
    public static class JavadocPlugin extends Plugin {
        public static JavadocPlugin get = new JavadocPlugin();
        public JavadocPlugin() {
            super("org.apache.maven.plugins", "maven-javadoc-plugin", "3.0.0");
            onBeforeToXML((project, pom) -> {
                addExecution("resource-bundles", "package")
                        .addGoal("resource-bundle")
                        .addGoal("test-resource-bundle")
                        .putConfiguration("doclint", "none")
                        .putConfiguration("detectOfflineLinks", "false");
            });
        }
    }

    static {
        plugins.add(EnforcerPlugin.get);
    }
    public static class EnforcerPlugin extends Plugin {
        public static EnforcerPlugin get = new EnforcerPlugin();
        public EnforcerPlugin() {
            super("org.apache.maven.plugins", "maven-enforcer-plugin", "3.3.0");
            onBeforeToXML((project, pom) -> {
                addExecution("enforce", null)
                        .addGoal("enforce")
                        .putConfiguration("rules dependencyConvergence", "");
            });
        }
    }
}


