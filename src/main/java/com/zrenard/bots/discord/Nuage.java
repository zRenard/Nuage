package com.zrenard.bots.discord;

import net.dv8tion.jda.api.JDABuilder;
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
import java.util.stream.Collectors;

// TODO : Quote with images
// TODO : Reminder (anniversaire ou juste chrono ?)
// TODO : Simple command with basic parameters in responses
// TODO : Complex command with parameters in input ?
// TODO : Weather as an example of complex command ?
// TODO : Move stats to simple command
// TODO : Add list of bad words (simple list of regexpr)
// TODO : Merge all data in one xml file ?
// TODO :         // Add simple command
//        // !add command xxx xxxxxxx
//        // Add quote
//        // !add quote xxxxx
public class Nuage extends ListenerAdapter {
    private static final String VERSION = "Alpha (et ça veut pas dire supérieure)";
    private static List<String> quotes;
    private static HashMap<String,String> simpleCommands;
    //private static Map<String,Map> query; ?
    private static final ArrayList<String> queries =
            new ArrayList<>(List.of("(.*)((hello)|(salut)) (@)?${botname}(.*)"));
    private static final ArrayList<ArrayList<String>> responsesApplication =
            new ArrayList<>(List.of(new ArrayList<>(List.of("zRenard#0668","*"))));
    private static final ArrayList<ArrayList<String>> responses =
            new ArrayList<>(List.of(new ArrayList<>(List.of("Salut ${name} ! Mon concepteur d'amour","Hello ${name} ça va ?"))));
    private static Properties prop = new Properties();
    private int statsQuotes = 0;
    private static LocalDateTime dateStart = LocalDateTime.now();
    private static Random ran = new Random();
    private static String token;

    public static void main(String[] args) throws LoginException {
        // Load Properties
        prop = loadProperties("settings.properties");

        // Load token
        List<String> tokenData = loadFile(prop.getProperty("token"));
        if (tokenData.isEmpty()) {
            System.err.println("No token file found : "+prop.getProperty("token")+ " or empty");
            System.exit(1);
        } else {
            token = tokenData.get(0); // First line
        }

        // Join
        JDABuilder builder = JDABuilder.createDefault(token);

        // Load Quotes
        quotes = loadFile(prop.getProperty("quote_filename"));
        // Load simple commands
        simpleCommands = new HashMap<>((Map) loadProperties(prop.getProperty("simple_command_filename")));
        // Load complex commands
        loadQueriesFile(prop.getProperty("complex_command_filename"));

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
        LocalDateTime now = LocalDateTime.now();

        // ignore bots message
        if(event.getAuthor().isBot()) {
            return;
        }

        // Basic logging
        String messageContent = event.getMessage().getContentDisplay();
        System.out.println("Message from "+ event.getAuthor().getAsTag() + ":" + messageContent);

        // Compute variable that can be used in simplecommand
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

        if (messageContent.equals("!quote")||messageContent.equals("!cite")) {
            if (quotes.isEmpty()) {
                event.getMessage().reply("Oauis bah tu est gentil "+event.getAuthor().getName() +" mais pas encore !").queue();
            } else {
                statsQuotes += 1;
                event.getChannel().sendMessage(quotes.get(ran.nextInt(quotes.size()))).queue();
            }
            return;
        }

        if (messageContent.equals("!reload")) {
            if (isDesigner(event.getAuthor())|| isAdmin(event.getAuthor())) {
                prop = loadProperties("settings.properties");
                quotes = loadFile(prop.getProperty("quote_filename"));
                loadQueriesFile(prop.getProperty("complex_command_filename"));
                simpleCommands = new HashMap<String, String>((Map) loadProperties(prop.getProperty("simple_command_filename")));
                event.getMessage().reply(quotes.size() + " citations rechargées\n" +
                        simpleCommands.size() + " commandes rechargées\n"+
                        queries.size() + " responses rechargées"
                ).queue();
                return;
            } else {
                event.getMessage().reply("Je te connait pas toi, tu n'est pas mon papa "+event.getAuthor().getName() +" !").queue();
                return;
            }
        }

        if (messageContent.equals("!stats")) {
            String response = "Je suis en version "+ VERSION+"\n";
            response = response.concat("Je me suis demarer "+ dateStart.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)+"\n");
            event.getMessage().reply(response + "Il y a "+quotes.size() +" citations," +
                    " et on me les a demande " + statsQuotes + " fois").queue();
            return;
        }
    }

    private static void loadQueriesFile(String xmlFilename) {

    }

    private static Properties loadProperties(String filename) {
        Properties prop = new Properties();
        // Load Properties
        try (InputStream input = new FileInputStream(filename)) {
            prop.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (FileNotFoundException e) {
            System.err.println("ERROR : File not found "+filename);
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
        return prop;
    }

    private static HashMap<String,String> loadVariable(MessageReceivedEvent event) {
        HashMap<String,String> variables = new HashMap<>();
        String contentDisplay = event.getMessage().getContentDisplay();
        String command = contentDisplay.split(" ")[0];
        LocalDateTime now = LocalDateTime.now();

        variables.put("${name}",event.getAuthor().getName());
        variables.put("${version}",VERSION);
        variables.put("${date}",now.format(DateTimeFormatter.ISO_LOCAL_DATE));
        variables.put("${time}",now.format(DateTimeFormatter.ISO_LOCAL_TIME).substring(0,8));
        // Variables from properties (do we map all ?")
        variables.put("${designer_tagname}",prop.getProperty("designer_tagname"));
        variables.put("${designer_name}", prop.getProperty("designer_tagname").split("#")[0]);

        variables.put("${admin_tagnames}", prop.getProperty("admin_tagnames"));
        variables.put("${admin_names}", Arrays.stream(prop.getProperty("admin_tagnames").split(";")).map(x-> x.split("#")[0]).sorted().collect(Collectors.toList()).toString());

        variables.put("${botname}",prop.getProperty("botname"));
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
            System.err.println(e.getMessage());
        }
        return Collections.emptyList();
    }

    private boolean isDesigner(User author) {
        // Match a author tag set in properties
        return prop.getProperty("designer_tagname").toLowerCase().equals(author.getAsTag().toLowerCase());
    }

    private boolean isAdmin(User author) {
        // Match a admin tag list set in properties
        return Arrays.stream(prop.getProperty("admin_list").split(";")).anyMatch(x-> x.toLowerCase().equals(author.getAsTag().toLowerCase()));
    }
}