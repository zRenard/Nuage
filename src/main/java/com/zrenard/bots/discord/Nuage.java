package com.zrenard.bots.discord;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.function.Function;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

public class Nuage extends ListenerAdapter {
    public static final String ADMIN_TAGNAMES = "admin_tagnames";
    private static final String VERSION = "Alpha (et ça veut pas dire supérieure)";
    public static final String DESIGNER_TAGNAME = "designer_tagname";
    public static final String BOTNAME = "botname";
    private static List<String> quotes;
    private static final List<String> magic8ballResponses = new ArrayList<>(
            List.of("It is certain", "Outlook good", "You may rely on it", "Ask again later",
                    "Concentrate and ask again", "Reply hazy, try again", "My reply is no",
                    "My sources say no"));
    private static List<String> cursewords;
    private static HashMap<String, String> simpleCommands;
    // private static Map<String,Map> query; ?
    private static final ArrayList<String> queries = new ArrayList<>(); // Example : List.of("(.*)((hello)|(salut))
                                                                        // (@)?${botname}(.*)")
    private static final ArrayList<ArrayList<String>> responsesApplication = new ArrayList<>(); // Example : List.of(new
                                                                                                // ArrayList<>(List.of("zRenard#0668",
                                                                                                // "*")))
    private static final ArrayList<ArrayList<String>> responses = new ArrayList<>(); // Example : List.of(new
                                                                                     // ArrayList<>(List.of("Salut
                                                                                     // ${name} ! Mon concepteur
                                                                                     // d'Amour", "Hello ${name} ça va
                                                                                     // ?")))
    private static Properties prop = new Properties();
    private int statsQuotes = 0;
    private static final LocalDateTime dateStart = LocalDateTime.now();
    private static final Random ran = new java.security.SecureRandom();
    private static String token;
    private static final Logger logger = Logger.getLogger(Nuage.class.getName());
    private static net.dv8tion.jda.api.JDA jda;

    public static void main(String[] args) {
        // Setup Logger
        try {
            var fileTxt = new FileHandler("logging.txt");
            var fileHTML = new FileHandler("logging.html");

            fileTxt.setFormatter(new SimpleFormatter());
            logger.addHandler(fileTxt);
            fileHTML.setFormatter(new HtmlLogFormatter());
            logger.addHandler(fileHTML);
        } catch (IOException e) {
            System.exit(2);
        }

        // Load Properties, Quotes, commands, and answer/responses
        loadSettings();
        logger.info(() -> "Queries: " + queries);
        logger.info(() -> "Responses Application: " + responsesApplication);
        logger.info(() -> "Responses: " + responses);

        // Load token
        List<String> tokenData = loadFile(prop.getProperty("token"));
        if (tokenData.isEmpty()) {
            logger.severe(() -> String.format("No token file found : %s or empty", prop.getProperty("token")));
            System.exit(1);
        } else {
            token = tokenData.get(0); // First line
        }

        // Join
        var builder = JDABuilder.createDefault(token);

        // Disable cache for member activities (streaming/games/spotify)
        builder.enableIntents(GatewayIntent.MESSAGE_CONTENT);
        builder.disableCache(CacheFlag.ACTIVITY);
        builder.disableCache(CacheFlag.MEMBER_OVERRIDES, CacheFlag.VOICE_STATE);
        // Disable presence updates and typing events
        builder.disableIntents(GatewayIntent.GUILD_PRESENCES, GatewayIntent.GUILD_MESSAGE_TYPING);
        builder.addEventListeners(new Nuage());
        jda = builder.build();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // ignore bots message
        if (event.getAuthor().isBot()) {
            return;
        }

        // Basic logging
        String messageContent = event.getMessage().getContentDisplay().trim().toLowerCase();
        logger.info(() -> "Message from " + event.getAuthor().getAsTag() + ":" + messageContent);

        filterCurseWords(event, messageContent);

        HashMap<String, String> localVariables = getLocalVariables(event);
        logger.info(() -> "localVariables: " + localVariables);

        // Find simple command
        analyzeSimpleCommands(event, messageContent, localVariables);

        // Find Complex command
        analyzeComplexCommands(event, messageContent, localVariables);

    }

    private HashMap<String, String> getLocalVariables(MessageReceivedEvent event) {
        // Compute variable that can be used in simple command
        HashMap<String, String> localVariables = loadVariable(event);
        localVariables.put("${listofcommand}", simpleCommands.keySet().stream().sorted().toList().toString());
        return localVariables;
    }

    private void analyzeSimpleCommands(MessageReceivedEvent event, String messageContent,
            HashMap<String, String> localVariables) {
        // Replace variable in simpleCommand (except for run() commands)
        Map<String, String> analyzedSimpleCommands = simpleCommands.entrySet().stream()
                .filter(x -> !x.getValue().startsWith("run(") || !x.getValue().endsWith(")"))
                .collect(Collectors.toMap(x -> "!" + x.getKey(), x -> localVariables.entrySet().stream()
                        .map(entryToReplace -> (Function<String, String>) s -> s.replace(entryToReplace.getKey(),
                                entryToReplace.getValue()))
                        .reduce(Function.identity(), Function::andThen)
                        .apply(x.getValue())));

        // Check if the message starts with !
        if (messageContent.startsWith("!")) {
            String command = messageContent.split(" ")[0].substring(1); // Remove !
            
            // Check if raw command exists and is a run() command
            if (simpleCommands.containsKey(command)) {
                String commandValue = simpleCommands.get(command);
                
                if (commandValue.startsWith("run(") && commandValue.endsWith(")")) {
                    // Extract method name and invoke it
                    String methodName = commandValue.substring(4, commandValue.length() - 1);
                    invokeMethod(methodName, event, messageContent);
                } else if (analyzedSimpleCommands.containsKey("!" + command)) {
                    // Send the analyzed (variable-replaced) response
                    event.getMessage().reply(analyzedSimpleCommands.get("!" + command)).queue();
                }
            } else if (simpleCommands.containsKey("default")) {
                // Use default command if it exists
                String defaultResponse = localVariables.entrySet().stream()
                        .map(entryToReplace -> (Function<String, String>) s -> s.replace(entryToReplace.getKey(),
                                entryToReplace.getValue()))
                        .reduce(Function.identity(), Function::andThen)
                        .apply(simpleCommands.get("default"));
                if (defaultResponse != null) {
                    event.getMessage().reply(defaultResponse).queue();
                }
            }
        }
    }
    
    private void invokeMethod(String methodName, MessageReceivedEvent event, String messageContent) {
    try {
            // Try first with (MessageReceivedEvent, String) signature
            try {
                Method method = this.getClass().getDeclaredMethod(methodName, MessageReceivedEvent.class, String.class);
                method.invoke(this, event, messageContent);
            } catch (NoSuchMethodException e) {
                // If not found, try with (MessageReceivedEvent) signature
                Method method = this.getClass().getDeclaredMethod(methodName, MessageReceivedEvent.class);
                method.invoke(this, event);
            }
        } catch (NoSuchMethodException e) {
            logger.warning(() -> "Method not found: " + methodName);
            event.getMessage().reply("Méthode introuvable: " + methodName).queue();
        } catch (IllegalAccessException | SecurityException | InvocationTargetException e) {
            logger.severe(() -> "Error invoking method: " + methodName + " - " + e.getMessage());
            event.getMessage().reply("Erreur lors de l'exécution de la commande").queue();
        }
    }

    private void analyzeComplexCommands(MessageReceivedEvent event, String messageContent,
            HashMap<String, String> localVariables) {

        for (String regexp : queries) {
            String parsedRegexp = localVariables.entrySet().stream()
                    .map(entryToReplace -> (Function<String, String>) s -> s.replace(entryToReplace.getKey(),
                            entryToReplace.getValue()))
                    .reduce(Function.identity(), Function::andThen)
                    .apply(regexp);
            if (messageContent.toLowerCase().matches(parsedRegexp.toLowerCase())) {
                int found = queries.indexOf(regexp);
                String responseFound;
                if (responsesApplication.get(found).contains(event.getAuthor().getAsTag())) {
                    int foundAuthorTag = responsesApplication.get(found).indexOf(event.getAuthor().getAsTag());
                    responseFound = responses.get(found).get(foundAuthorTag);
                } else {
                    responseFound = responses.get(found).get(responsesApplication.get(found).indexOf("*"));
                }
                responseFound = localVariables.entrySet().stream()
                        .map(entryToReplace -> (Function<String, String>) s -> s.replace(entryToReplace.getKey(),
                                entryToReplace.getValue()))
                        .reduce(Function.identity(), Function::andThen)
                        .apply(responseFound);
                if (responseFound != null) {
                    event.getMessage().reply(responseFound).queue();
                }
            }
        }
    }

    @SuppressWarnings("unused") // Used by reflection
    private void quote(MessageReceivedEvent event) {
        if (quotes.isEmpty()) {
            event.getMessage().reply("Oauis bah tu es gentil " + event.getAuthor().getName() + " mais pas encore !")
                    .queue();
        } else {
            statsQuotes += 1;
            event.getChannel().sendMessage(quotes.get(ran.nextInt(quotes.size()))).queue();
        }
    }

    @SuppressWarnings("unused") // Used by reflection
    private void magic8ball(MessageReceivedEvent event, String messageContent) {
        String question = (Arrays.stream(messageContent.split(" ", 2)).count() == 2)
                ? messageContent.split(" ", 2)[1].trim()
                : "";
        if (question.equals("")) {
            event.getMessage().reply("Oauis bah tu es gentil " + event.getAuthor().getName()
                    + " comment tu veux que je respond si tu pose pas de question !").queue();
        } else {
            event.getMessage().reply("Alors pour la question : \"" + question + "\" la réponse est : "
                    + magic8ballResponses.get(ran.nextInt(magic8ballResponses.size()))).queue();
        }
    }

    @SuppressWarnings("unused") // Used by reflection
    private void stats(MessageReceivedEvent event) {
        String response = "Je suis en version " + VERSION + "\n";
        response = response
                .concat("Je me suis démarré " + dateStart.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "\n");
        response = response.concat("Il y a " + quotes.size() + " citations," +
                " et on me les a demande " + statsQuotes + " fois\n");
        response = response.concat("Je connais " + simpleCommands.size() + " commandes simples et "
                + queries.size() + " commandes complexes");

        if (response != null) {
            event.getMessage().reply(response).queue();
        }
    }

    @SuppressWarnings("unused") // Used by reflection
    private void quit(MessageReceivedEvent event) {
        if (isDesigner(event.getAuthor()) || isAdmin(event.getAuthor())) {
            event.getMessage().reply("Ha bah d'accord .... je me casse alors.\n").queue(
                    success -> {
                        try {
                            Thread.sleep(1000); 
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        jda.shutdown();
                        System.exit(0);
                    });
        } else {
            event.getMessage().reply("Je te connait pas toi, tu n'es pas autorisé à faire cette commande !").queue();
        }
    }

    @SuppressWarnings("unused") // Used by reflection
    private void reload(MessageReceivedEvent event) {
        if (isDesigner(event.getAuthor()) || isAdmin(event.getAuthor())) {
            loadSettings();
            event.getMessage().reply(quotes.size() + " citations rechargées\n" +
                    simpleCommands.size() + " commandes simples rechargées\n" +
                    queries.size() + " commandes complexes rechargées").queue();
        } else {
            event.getMessage()
                    .reply("Je te connaît pas toi, tu n'es pas mon papa, " + event.getAuthor().getName() + " !")
                    .queue();
        }
    }

    private static void loadSettings() {
        prop = loadProperties("settings.properties");
        quotes = loadFile(prop.getProperty("quote_filename"));
        cursewords = Stream.of(prop.getProperty("cursewords").split(";")).toList();
        // Empty complex queries in case of reload
        queries.clear();
        responsesApplication.clear();
        responses.clear();
        // Load complex queries
        loadQueriesFile(prop.getProperty("complex_command_filename"));
        if (logger.isLoggable(java.util.logging.Level.INFO)) {
            logger.info(queries.toString());
            logger.info(responsesApplication.toString());
            logger.info(responses.toString());
        }
        simpleCommands = new HashMap<>(loadSimpleCommands(prop.getProperty("simple_command_filename")));
    }

    private static void loadQueriesFile(String xmlFilename) {
        try {
            File xmlFile = new File(xmlFilename);
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(xmlFile);
            doc.getDocumentElement().normalize();

            NodeList queryList = doc.getElementsByTagName("query");

            for (int i = 0; i < queryList.getLength(); i++) {
                Node queryNode = queryList.item(i);

                if (queryNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element queryElement = (Element) queryNode;
                    queries.add(queryElement.getElementsByTagName("regexpr").item(0).getTextContent());

                    NodeList responseList = queryElement.getElementsByTagName("reponse");
                    ArrayList<String> reponseApplicationForThisList = new ArrayList<>();
                    ArrayList<String> reponseForThisList = new ArrayList<>();

                    for (int j = 0; j < responseList.getLength(); j++) {
                        Node responseNode = responseList.item(j);

                        if (responseNode.getNodeType() == Node.ELEMENT_NODE) {
                            Element responseElement = (Element) responseNode;
                            String toTagname = responseElement.getElementsByTagName("to_tagname").item(0)
                                    .getTextContent();
                            String text = responseElement.getElementsByTagName("text").item(0).getTextContent();

                            // Replace ${designer_tagname} with actual designer tagname from properties
                            if (toTagname.equals("${designer_tagname}")) {
                                toTagname = prop.getProperty(DESIGNER_TAGNAME);
                            }

                            // Replace ${admin_tagnames} with all admin tagnames from properties
                            if (toTagname.equals("${admin_tagnames}")) {
                                String[] adminTagnames = prop.getProperty(ADMIN_TAGNAMES).split(";");
                                for (String adminTag : adminTagnames) {
                                    reponseApplicationForThisList.add(adminTag.trim());
                                    reponseForThisList.add(text);
                                }
                            } else {
                                reponseApplicationForThisList.add(toTagname);
                                reponseForThisList.add(text);
                            }
                        }
                    }
                    responsesApplication.add(reponseApplicationForThisList);
                    responses.add(reponseForThisList);
                }
            }
        } catch (IOException | ParserConfigurationException | DOMException | SAXException e) {
            logger.severe(e.getMessage());
        }
    }

    private void filterCurseWords(MessageReceivedEvent event, String messageContent) {
        for (String regexpBW : cursewords) {
            if (messageContent.toLowerCase().matches(regexpBW.toLowerCase())) {
                if (!event.getGuild().getSelfMember().hasPermission(Permission.MESSAGE_MANAGE)) {
                    logger.warning(() -> "No permission to delete messages in #" + event.getChannel().getName());
                    return;
                }
                event.getMessage().delete().queue(
                        done -> event.getChannel()
                                .sendMessage(event.getAuthor().getAsMention() + ", you cannot say that!").queue(),
                        error -> {
                            logger.warning("Error deleting message with curse word");
                            logger.warning(error.getMessage());
                        });
            }
        }
    }

    private static Map<String, String> loadSimpleCommands(String filename) {
        Properties properties = loadProperties(filename);
        return properties.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> (String) e.getKey(),
                        e -> (String) e.getValue()));
    }

    private static Properties loadProperties(String filename) {
        var properties = new Properties();
        // Load Properties
        try (InputStream input = new FileInputStream(filename)) {
            properties.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (FileNotFoundException e) {
            logger.severe(() -> "ERROR : File not found " + filename);
        } catch (IOException e) {
            logger.severe(e.getMessage());
        }
        return properties;
    }

    private static HashMap<String, String> loadVariable(MessageReceivedEvent event) {
        HashMap<String, String> variables = new HashMap<>();
        String contentDisplay = event.getMessage().getContentDisplay();
        String command = contentDisplay.split(" ")[0];
        Locale.setDefault(Locale.FRENCH);
        LocalDateTime now = LocalDateTime.now();
        variables.put("${name}", event.getAuthor().getName());
        variables.put("${version}", VERSION);
        if (logger.isLoggable(java.util.logging.Level.INFO)) {
            logger.info(
                    now.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())));
        }
        if (logger.isLoggable(java.util.logging.Level.INFO)) {
            logger.info(
                    now.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withLocale(Locale.getDefault())));
        }
        variables.put("${date}",
                now.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(Locale.getDefault())));
        variables.put("${time}",
                now.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault())));
        // Variables from properties (do we map all ?")
        variables.put("${designer_tagname}", prop.getProperty(DESIGNER_TAGNAME));
        variables.put("${designer_name}", prop.getProperty(DESIGNER_TAGNAME).split("#")[0]);

        variables.put("${admin_tagnames}", prop.getProperty(ADMIN_TAGNAMES));
        variables.put("${admin_names}", Arrays.stream(prop.getProperty(ADMIN_TAGNAMES).split(";"))
                .filter(x -> !x.equalsIgnoreCase(prop.getProperty(DESIGNER_TAGNAME)))
                .map(x -> x.split("#")[0]).sorted().toList().toString());

        variables.put("${botname}", prop.getProperty(BOTNAME));
        variables.put("${content}", contentDisplay.replaceFirst(command, ""));
        variables.put("${command}", command);
        variables.put("${start_date}", dateStart.format(DateTimeFormatter.ISO_LOCAL_DATE));
        variables.put("${start_time}", dateStart.format(DateTimeFormatter.ISO_LOCAL_DATE).substring(0, 8));
        variables.put("${start_days}", String.valueOf(ChronoUnit.DAYS.between(dateStart, now)));
        variables.put("${start_hours}", String.valueOf(ChronoUnit.HOURS.between(dateStart, now) % 24));
        variables.put("${start_minutes}", String.valueOf(ChronoUnit.MINUTES.between(dateStart, now) % 60));
        variables.put("${start_seconds}", String.valueOf(ChronoUnit.SECONDS.between(dateStart, now) % 60));
        return variables;
    }

    private static List<String> loadFile(String fileName) {
        try {
            return Files.readAllLines(Paths.get(fileName));
        } catch (IOException e) {
            logger.severe(e.getMessage());
        }
        return Collections.emptyList();
    }

    private boolean isDesigner(User author) {
        // Match an author tag set in properties
        return prop.getProperty(DESIGNER_TAGNAME).equalsIgnoreCase(author.getAsTag());
    }

    private boolean isAdmin(User author) {
        // Match an admin tag list set in properties or designer
        return isDesigner(author) || Arrays.stream(prop.getProperty(ADMIN_TAGNAMES).split(";"))
                .anyMatch(x -> x.equalsIgnoreCase(author.getAsTag()));
    }
}