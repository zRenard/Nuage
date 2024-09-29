package com.zrenard.bots.discord;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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

import javax.xml.parsers.*;

import org.w3c.dom.*;

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
    private static final ArrayList<String> queries = new ArrayList<>(); // Example : List.of("(.*)((hello)|(salut)) (@)?${botname}(.*)")
    private static final ArrayList<ArrayList<String>> responsesApplication = new ArrayList<>(); // Example : List.of(new ArrayList<>(List.of("zRenard#0668", "*")))
    private static final ArrayList<ArrayList<String>> responses = new ArrayList<>(); // Example : List.of(new ArrayList<>(List.of("Salut ${name} ! Mon concepteur d'Amour", "Hello ${name} ça va ?")))
    private static Properties prop = new Properties();
    private int statsQuotes = 0;
    private static final LocalDateTime dateStart = LocalDateTime.now();
    private static final Random ran = new java.security.SecureRandom();
    private static String token;
    private static Logger logger;

    public static void main(String[] args) {
        // Setup Logger
        try {
            logger = Logger.getLogger(Nuage.class.getName());
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
        logger.info("Queries: " + queries);
        logger.info("Responses Application: " + responsesApplication);
        logger.info("Responses: " + responses);

        // Load token
        List<String> tokenData = loadFile(prop.getProperty("token"));
        if (tokenData.isEmpty()) {
            logger.severe("No token file found : " + prop.getProperty("token") + " or empty");
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
        builder.build();
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

        // Find simple command
        analyzeSimpleCommands(event, messageContent, localVariables);

        // Find Complex command
        analyseComplexCommands(event, messageContent, localVariables);

        if (messageContent.startsWith("!magic8")) {
            magic8ball(event, messageContent);
        }

        if (messageContent.equals("!quit")) {
            quit(event);
            System.exit(0);
        }

        if (messageContent.equals("!quote") || messageContent.equals("!cite")) {
            quote(event);
        }

        if (messageContent.equals("!reload")) {
            reload(event);
            stats(event);
        }

        if (messageContent.equals("!stats")) {
            // @TODO add same data as reload
            // 6 citations rechargées
            // 29 commandes rechargées
            // 1 responses rechargées
            // instead of
            // Je suis en version Alpha (et ça veut pas dire supérieure)
            // Je me suis démarré 2022-06-01T14:33:44.5134119
            // Il y a 6 citations, et on me les a demande 0 fois
            stats(event);
        }
    }

    private HashMap<String, String> getLocalVariables(MessageReceivedEvent event) {
        // Compute variable that can be used in simple command
        HashMap<String, String> localVariables = loadVariable(event);
        localVariables.put("${listofcommand}", simpleCommands.keySet().stream().sorted().toList().toString());
        return localVariables;
    }

    private void analyzeSimpleCommands(MessageReceivedEvent event, String messageContent,
            HashMap<String, String> localVariables) {
        // Replace variable in simpleCommand
        Map<String, String> analyzedSimpleCommands = simpleCommands.entrySet().stream()
                .collect(Collectors.toMap(x -> "!" + x.getKey(), x -> localVariables.entrySet().stream()
                        .map(entryToReplace -> (Function<String, String>) s -> s.replace(entryToReplace.getKey(),
                                entryToReplace.getValue()))
                        .reduce(Function.identity(), Function::andThen)
                        .apply(x.getValue())));
        // Check if the message sent belong to a command
        if (analyzedSimpleCommands.entrySet().stream()
                .anyMatch(x -> messageContent.toLowerCase().startsWith(x.getKey().toLowerCase()))) {
            event.getMessage().reply(analyzedSimpleCommands.get(messageContent.split(" ")[0])).queue();
        }
    }

    private void analyseComplexCommands(MessageReceivedEvent event, String messageContent,
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
                event.getMessage().reply(responseFound).queue();
            }
        }
    }

    private void quote(MessageReceivedEvent event) {
        if (quotes.isEmpty()) {
            event.getMessage().reply("Oauis bah tu es gentil " + event.getAuthor().getName() + " mais pas encore !")
                    .queue();
        } else {
            statsQuotes += 1;
            event.getChannel().sendMessage(quotes.get(ran.nextInt(quotes.size()))).queue();
        }
    }

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

    private void stats(MessageReceivedEvent event) {
        String response = "Je suis en version " + VERSION + "\n";
        response = response
                .concat("Je me suis démarré " + dateStart.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "\n");
        event.getMessage().reply(response + "Il y a " + quotes.size() + " citations," +
                " et on me les a demande " + statsQuotes + " fois").queue();
    }

    private void quit(MessageReceivedEvent event) {
        if (isDesigner(event.getAuthor()) || isAdmin(event.getAuthor())) {
            event.getMessage().reply("Ha bah d'accord .... je me casse alors.\n").queue();
        } else {
            event.getMessage().reply("Je te connait pas toi, tu n'es pas autorisé à faire cette commande !").queue();
        }
    }

    private void reload(MessageReceivedEvent event) {
        if (isDesigner(event.getAuthor()) || isAdmin(event.getAuthor())) {
            loadSettings();
            event.getMessage().reply(quotes.size() + " citations rechargées\n" +
                    simpleCommands.size() + " commandes rechargées\n" +
                    queries.size() + " responses rechargées").queue();
        } else {
            event.getMessage()
                    .reply("Je te connait pas toi, tu n'es pas mon papa " + event.getAuthor().getName() + " !").queue();
        }
    }

    private static void loadSettings() {
        prop = loadProperties("settings.properties");
        quotes = loadFile(prop.getProperty("quote_filename"));
        cursewords = Stream.of(prop.getProperty("crusewords").split(";")).toList();
        loadQueriesFile(prop.getProperty("complex_command_filename"));
        simpleCommands = new HashMap<>(loadSimpleCommands(prop.getProperty("simple_command_filename")));
    }

    private static void loadQueriesFile(String xmlFilename) {
        try {
            File xmlFile = new File(xmlFilename);
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
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

                            reponseApplicationForThisList.add(toTagname);
                            reponseForThisList.add(text);
                        }
                    }
                    responsesApplication.add(reponseApplicationForThisList);
                    responses.add(reponseForThisList);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void filterCurseWords(MessageReceivedEvent event, String messageContent) {
        for (String regexpBW : cursewords) {
            if (messageContent.toLowerCase().matches(regexpBW.toLowerCase())) {
                if (!event.getGuild().getSelfMember().hasPermission(Permission.MESSAGE_MANAGE)) {
                    logger.warning("No permission to delete messages in #" + event.getChannel().getName());
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
        var prop = new Properties();
        // Load Properties
        try (InputStream input = new FileInputStream(filename)) {
            prop.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (FileNotFoundException e) {
            logger.severe("ERROR : File not found " + filename);
        } catch (IOException e) {
            logger.severe(e.getMessage());
        }
        return prop;
    }

    private static HashMap<String, String> loadVariable(MessageReceivedEvent event) {
        HashMap<String, String> variables = new HashMap<>();
        String contentDisplay = event.getMessage().getContentDisplay();
        String command = contentDisplay.split(" ")[0];
        Locale.setDefault(Locale.FRENCH);
        LocalDateTime now = LocalDateTime.now();
        variables.put("${name}", event.getAuthor().getName());
        variables.put("${version}", VERSION);
        logger.info(now.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())));
        logger.info(now.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withLocale(Locale.getDefault())));
        variables.put("${date}",
                now.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(Locale.getDefault())));
        variables.put("${time}",
                now.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault())));
        // Variables from properties (do we map all ?")
        variables.put("${designer_tagname}", prop.getProperty(DESIGNER_TAGNAME));
        variables.put("${designer_name}", prop.getProperty(DESIGNER_TAGNAME).split("#")[0]);

        variables.put("${admin_tagnames}", prop.getProperty(ADMIN_TAGNAMES));
        variables.put("${admin_names}", Arrays.stream(prop.getProperty(ADMIN_TAGNAMES).split(";"))
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
        // Match an admin tag list set in properties
        return Arrays.stream(prop.getProperty(ADMIN_TAGNAMES).split(";"))
                .anyMatch(x -> x.equalsIgnoreCase(author.getAsTag()));
    }
}