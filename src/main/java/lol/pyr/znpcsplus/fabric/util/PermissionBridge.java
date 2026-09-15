package lol.pyr.znpcsplus.fabric.util;
import net.minecraft.server.network.ServerPlayerEntity;
import java.lang.reflect.Method;
public final class PermissionBridge{private PermissionBridge(){}
 public static boolean has(ServerPlayerEntity p,String node){if(node==null||node.isBlank())return true;try{Class<?>provider=Class.forName("net.luckperms.api.LuckPermsProvider");Object api=provider.getMethod("get").invoke(null);Object um=api.getClass().getMethod("getUserManager").invoke(api);Object u=um.getClass().getMethod("getUser",java.util.UUID.class).invoke(um,p.getUuid());if(u!=null){Object cd=u.getClass().getMethod("getCachedData").invoke(u);Object pd=cd.getClass().getMethod("getPermissionData").invoke(cd);Object ts=pd.getClass().getMethod("checkPermission",String.class).invoke(pd,node);Method ab=ts.getClass().getMethod("asBoolean");return(boolean)ab.invoke(ts);}}catch(Throwable ignored){}return p.getCommandSource().hasPermissionLevel(2);}
}