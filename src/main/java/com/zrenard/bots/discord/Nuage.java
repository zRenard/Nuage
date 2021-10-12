package com.zrenard.bots.discord;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import javax.security.auth.login.LoginException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Nuage extends ListenerAdapter {
    public static final String ADMIN_TAGNAMES = "admin_tagnames";
    private static final String VERSION = "Alpha (et ça veut pas dire supérieure)";
    public static final String DESIGNER_TAGNAME = "designer_tagname";
    public static final String BOTNAME = "botname";
    private static List<String> quotes;
    private static List<String> magic8ballResponses =
            new ArrayList<>(List.of("It is certain","Outlook good","You may rely on it","Ask again later",
                    "Concentrate and ask again","Reply hazy, try again","My reply is no",
                    "My sources say no"));
    private static List<String> cursewords;
    private static HashMap<String,String> simpleCommands;
    //private static Map<String,Map> query; ?
    private static final ArrayList<String> queries =
            new ArrayList<>(List.of("(.*)((hello)|(salut)) (@)?${botname}(.*)"));
    private static final ArrayList<ArrayList<String>> responsesApplication =
            new ArrayList<>(List.of(new ArrayList<>(List.of("zRenard#0668","*"))));
    private static final ArrayList<ArrayList<String>> responses =
            new ArrayList<>(List.of(new ArrayList<>(List.of("Salut ${name} ! Mon concepteur d'Amour","Hello ${name} ça va ?"))));
    private static Properties prop = new Properties();
    private int statsQuotes = 0;
    private static final LocalDateTime dateStart = LocalDateTime.now();
    private static final Random ran = new java.security.SecureRandom();
    private static String token;
    private static Logger logger;

    public static void main(String[] args) throws LoginException {
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

        // Load token
        List<String> tokenData = loadFile(prop.getProperty("token"));
        if (tokenData.isEmpty()) {
            logger.severe("No token file found : "+prop.getProperty("token")+ " or empty");
            System.exit(1);
        } else {
            token = tokenData.get(0); // First line
        }

        // Join
        var builder = JDABuilder.createDefault(token);

        // Disable cache for member activities (streaming/games/spotify)
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
        if(event.getAuthor().isBot()) {
            return;
        }

        // Basic logging
        String messageContent = event.getMessage().getContentDisplay().trim();
        logger.info(() -> "Message from "+ event.getAuthor().getAsTag() + ":" + messageContent);

        // Filter cursewords
        for (String regexpBW : cursewords) {
            if (messageContent.toLowerCase().matches(regexpBW.toLowerCase())) {
                if(!event.getGuild().getSelfMember().hasPermission(Permission.MESSAGE_MANAGE)) {
                    logger.warning("No permission to delete messages in #" + event.getChannel().getName());
                    return;
                }
                event.getMessage().delete().queue(
                        done->
                                event.getChannel().sendMessage(event.getAuthor().getAsMention() + ", you cannot say that!").queue()
                        ,error->{
                            logger.warning("Error deleting message with curse word");
                            logger.warning(error.getMessage());
                });
            }
        }

        // Compute variable that can be used in simple command
        HashMap<String,String> localVariables = loadVariable(event);
        localVariables.put("${listofcommand}",simpleCommands.keySet().stream().sorted().collect(Collectors.toList()).toString());

        // Replace variable in simpleCommand
        Map<String, String> analyzedSimpleCommands = simpleCommands.entrySet().stream()
                .collect(Collectors.toMap(x -> "!"+x.getKey(), x ->
                localVariables.entrySet().stream()
                        .map(entryToReplace -> (Function<String, String>) s -> s.replace(entryToReplace.getKey(), entryToReplace.getValue()))
                        .reduce(Function.identity(), Function::andThen)
                        .apply(x.getValue())
                ));

        // Check if the message sent belong to a command
        if (analyzedSimpleCommands.entrySet().stream().anyMatch(x -> messageContent.toLowerCase().startsWith(x.getKey().toLowerCase()))) {
            event.getMessage().reply(analyzedSimpleCommands.get(messageContent.split(" ")[0])).queue();
        }

        // Find Complex command
        for (String regexp : queries) {
            String parsedRegexp = localVariables.entrySet().stream()
                    .map(entryToReplace -> (Function<String, String>) s -> s.replace(entryToReplace.getKey(), entryToReplace.getValue()))
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
                        .map(entryToReplace -> (Function<String, String>) s -> s.replace(entryToReplace.getKey(), entryToReplace.getValue()))
                        .reduce(Function.identity(), Function::andThen)
                        .apply(responseFound);
                event.getMessage().reply(responseFound).queue();
            }
        }

        if (messageContent.equals("!magic8")) {
            magic8ball(event,messageContent.split(" ")[1].trim());
        }

        if (messageContent.equals("!quote")||messageContent.equals("!cite")) {
            quote(event);
        }

        if (messageContent.equals("!reload")) {
            reload(event);
        }

        if (messageContent.equals("!stats")) {
            stats(event);
        }
    }

    private void quote(MessageReceivedEvent event) {
        if (quotes.isEmpty()) {
            event.getMessage().reply("Oauis bah tu es gentil "+ event.getAuthor().getName() +" mais pas encore !").queue();
        } else {
            statsQuotes += 1;
            event.getChannel().sendMessage(quotes.get(ran.nextInt(quotes.size()))).queue();
        }
    }

    private void magic8ball(MessageReceivedEvent event, String question) {
        if (question.isEmpty()) {
            event.getMessage().reply("Oauis bah tu es gentil "+ event.getAuthor().getName() +" comment tu veux que je reponde si tu pose pas de question !").queue();
        } else {
            event.getMessage().reply("Alors pour "+ question + " la réponse est : " + magic8ballResponses.get(ran.nextInt(magic8ballResponses.size()))).queue();
        }
    }

    private void stats(MessageReceivedEvent event) {
        String response = "Je suis en version "+ VERSION+"\n";
        response = response.concat("Je me suis démarré "+ dateStart.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)+"\n");
        event.getMessage().reply(response + "Il y a "+quotes.size() +" citations," +
                " et on me les a demande " + statsQuotes + " fois").queue();
    }

    private void reload(MessageReceivedEvent event) {
        if (isDesigner(event.getAuthor())|| isAdmin(event.getAuthor())) {
            loadSettings();
            event.getMessage().reply(quotes.size() + " citations rechargées\n" +
                    simpleCommands.size() + " commandes rechargées\n"+
                    queries.size() + " responses rechargées"
            ).queue();
        } else {
            event.getMessage().reply("Je te connait pas toi, tu n'es pas mon papa "+ event.getAuthor().getName() +" !").queue();
        }
    }

    private static void loadSettings() {
        prop = loadProperties("settings.properties");
        quotes = loadFile(prop.getProperty("quote_filename"));
        cursewords = Stream.of(prop.getProperty("crusewords").split(";")).collect(Collectors.toList());
        loadQueriesFile(prop.getProperty("complex_command_filename"));
        simpleCommands = new HashMap<>((Map) loadProperties(prop.getProperty("simple_command_filename")));
    }

    private static void loadQueriesFile(String xmlFilename) {
        // Load complex queries/responses from xml file
        // queries

        // responsesApplication

        // responses
    }

    private static Properties loadProperties(String filename) {
        var prop = new Properties();
        // Load Properties
        try (InputStream input = new FileInputStream(filename)) {
            prop.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (FileNotFoundException e) {
            logger.severe("ERROR : File not found "+filename);
        } catch (IOException e) {
            logger.severe(e.getMessage());
        }
        return prop;
    }

    private static HashMap<String,String> loadVariable(MessageReceivedEvent event) {
        HashMap<String,String> variables = new HashMap<>();
        String contentDisplay = event.getMessage().getContentDisplay();
        String command = contentDisplay.split(" ")[0];
        var now = LocalDateTime.now();

        variables.put("${name}",event.getAuthor().getName());
        variables.put("${version}",VERSION);
        variables.put("${date}",now.format(DateTimeFormatter.ISO_LOCAL_DATE));
        variables.put("${time}",now.format(DateTimeFormatter.ISO_LOCAL_TIME).substring(0,8));
        // Variables from properties (do we map all ?")
        variables.put("${designer_tagname}" , prop.getProperty(DESIGNER_TAGNAME));
        variables.put("${designer_name}", prop.getProperty(DESIGNER_TAGNAME).split("#")[0]);

        variables.put("${admin_tagnames}", prop.getProperty(ADMIN_TAGNAMES));
        variables.put("${admin_names}", Arrays.stream(prop.getProperty(ADMIN_TAGNAMES).split(";")).map(x-> x.split("#")[0]).sorted().collect(Collectors.toList()).toString());

        variables.put("${botname}",prop.getProperty(BOTNAME));
        variables.put("${content}", contentDisplay.replaceFirst(command,""));
        variables.put("${command}",command);
        variables.put("${start_date}",dateStart.format(DateTimeFormatter.ISO_LOCAL_DATE));
        variables.put("${start_time}",dateStart.format(DateTimeFormatter.ISO_LOCAL_DATE).substring(0,8));
        variables.put("${start_days}",String.valueOf(ChronoUnit.DAYS.between(dateStart, now)));
        variables.put("${start_hours}",String.valueOf(ChronoUnit.HOURS.between(dateStart, now)%24));
        variables.put("${start_minutes}",String.valueOf(ChronoUnit.MINUTES.between(dateStart, now)%60));
        variables.put("${start_seconds}",String.valueOf(ChronoUnit.SECONDS.between(dateStart, now)%60));
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
        // Match a author tag set in properties
        return prop.getProperty(DESIGNER_TAGNAME).equalsIgnoreCase(author.getAsTag());
    }

    private boolean isAdmin(User author) {
        // Match a admin tag list set in properties
        return Arrays.stream(prop.getProperty(ADMIN_TAGNAMES).split(";")).anyMatch(x-> x.equalsIgnoreCase(author.getAsTag()));
    }
}