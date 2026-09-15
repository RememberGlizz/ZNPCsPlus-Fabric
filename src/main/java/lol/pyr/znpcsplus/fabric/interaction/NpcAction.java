package lol.pyr.znpcsplus.fabric.interaction;
import java.util.*;
public final class NpcAction {
 public enum ActionKind{CONSOLE,PLAYER_COMMAND,MESSAGE,PLAYER_CHAT,SWITCH_SERVER}
 public UUID id=UUID.randomUUID(); public ActionKind kind=ActionKind.MESSAGE; public InteractionType interactionType=InteractionType.ANY_CLICK; public long cooldown=0L; public long delay=0L; public String value="";
 public NpcAction(){} public NpcAction(ActionKind k,InteractionType i,long c,long d,String v){kind=k;interactionType=i;cooldown=c;delay=d;value=v;}
 public static ActionKind parseKind(String s){return switch(s.toLowerCase(Locale.ROOT).replace("-","_")){case"console","consolecommand","console_command"->ActionKind.CONSOLE;case"player","playercommand","player_command","command"->ActionKind.PLAYER_COMMAND;case"message","msg"->ActionKind.MESSAGE;case"chat","playerchat","player_chat"->ActionKind.PLAYER_CHAT;case"server","switchserver","switch_server","bungee"->ActionKind.SWITCH_SERVER;default->throw new IllegalArgumentException("Unknown action type: "+s);};}
}