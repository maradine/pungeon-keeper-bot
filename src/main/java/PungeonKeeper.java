import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Properties;

import javax.security.auth.login.LoginException;

public class PungeonKeeper extends ListenerAdapter {

    private static int puntThreshold = 3;
    private static String puntEmoteName = "punt";
    private static String pungeonDwellerRoleName = "PUNGEON DWELLER";
    private static String botdevChannelName = "botdev";
    private static ZonedDateTime lastPungeonEmpty;
    private static Properties props = null;

    public static void main(String[] args) throws LoginException {
        System.out.println("Entering main loop.  Setting daily timestamp.");
        lastPungeonEmpty = ZonedDateTime.now().withZoneSameInstant(ZoneId.of("UTC"));
        System.out.println(lastPungeonEmpty);

        try {
            FileInputStream fis = new FileInputStream("pungeonkeeper.properties");
            props = new Properties();
            props.load(fis);
        } catch (IOException ioe) {
            System.out.println("Attempted to load file input stream from file - ioexception");
            System.out.println(ioe);
            System.exit(1);
        }

        String token = props.getProperty("botToken");

        JDABuilder builder = new JDABuilder(AccountType.BOT);
        builder.setToken(token);
        builder.addEventListener(new PungeonKeeper());
        builder.buildAsync();

        System.out.println("Instantiation complete.");
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {

        //ignore ourselves (and other bots)
        if (event.getAuthor().isBot()) {
            return;
        }

        System.out.println("We received a message from " +
                event.getAuthor().getName() + ": " +
                event.getMessage().getContentDisplay()
        );

        if(event.getMessage().getContentRaw().equals("!ping")) {
            event.getChannel().sendMessage("Pong!").queue();
        }

        emptyPungeonCheck(event.getGuild());


    }

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        System.out.println("we received a reaction from " + event.getMember().getEffectiveName());

        // is this reaction a punt request?
        if (event.getReaction().getReactionEmote().getName().equals(puntEmoteName)) {
            //event.getChannel().sendMessage("punt request from "+ event.getMember().getEffectiveName()).queue();

            // what was the original message this reaction is attached to?
            long messageID = event.getReaction().getMessageIdLong();
            Message originalMessage = event.getChannel().getMessageById(messageID).complete();

            String messageText = originalMessage.getContentRaw();
            System.out.println("message id " + messageText);

            // how many punt reactions does this original message now have?
            // note that we have to get the count here because the count on the reaction event object
            // itself is immutable, effectively stateless, and carries a null count.
            List<MessageReaction> reactionList = originalMessage.getReactions();
            for (MessageReaction r : reactionList) {
                if (r.getReactionEmote().getName().equals("punt")) {
                    int puntCount = r.getCount();
                    //event.getChannel().sendMessage("punt count now at "+ puntCount).queue();
                    if (puntCount >= puntThreshold) {
                        puntMember(originalMessage.getMember(), event.getTextChannel());
                    }
                }
            }



        }
    }

    private void puntMember(Member member, TextChannel notifyChannel) {
        List<Role> probablePDRoles = member.getGuild().getRolesByName(pungeonDwellerRoleName, false);
        if (probablePDRoles.size() != 1) {
            // TODO message the botdev channel that something is wrong and fail silently
            // there should only ever be one role that matches.
            return;
        }

        Role pdRole = probablePDRoles.get(0);

        // if we're here, there is exactly one PD role, let's see if the member is in it
        List<Role> roles = member.getRoles();
        boolean inPungeon = false;

        // flag if they're already in the pungeon
        for (Role r : roles) {
            if (r.equals(pdRole)) {
                inPungeon = true;
            }
        }

        //if not, put them there
        if (!inPungeon) {
            member.getGuild().getController().addSingleRoleToMember(member, pdRole).complete();
            notifyChannel.sendMessage(member.getEffectiveName() + " has been escorted to the Pungeon.").queue();
        }
    }

    private void emptyPungeonCheck(Guild guild) {

        ZonedDateTime now = ZonedDateTime.now().withZoneSameInstant(ZoneId.of("UTC"));
        System.out.println("now: " + now);

        ZonedDateTime edge = ZonedDateTime.now().withHour(6)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
                .withZoneSameInstant(ZoneId.of("UTC"));
        System.out.println("edge: " + edge);

        //is the old timestamp before 6am today?
        //is the current timestamp after 6am today?
        if (lastPungeonEmpty.isBefore(edge) && now.isAfter(edge)) {
            System.out.println("Empty the pungeon!");
            //find every member of the pungeon dweller role and remove them

            List<Role> probablePDRoles = guild.getRolesByName(pungeonDwellerRoleName, false);
            if (probablePDRoles.size() != 1) {
                // TODO message the botdev channel that something is wrong and fail silently
                // there should only ever be one role that matches.
                return;
            }
            Role pdRole = probablePDRoles.get(0);

            List<Member> memberList = guild.getMembers();
            for (Member m: memberList) {
                guild.getController().removeSingleRoleFromMember(m, pdRole);
            }

            //reset the empty time
            lastPungeonEmpty = ZonedDateTime.now().withZoneSameInstant(ZoneId.of("UTC"));

        }




    }


}