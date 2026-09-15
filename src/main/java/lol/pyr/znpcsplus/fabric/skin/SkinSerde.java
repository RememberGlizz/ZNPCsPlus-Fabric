package lol.pyr.znpcsplus.fabric.skin;
import java.util.*;
public final class SkinSerde{private SkinSerde(){}
 public static String serialize(SkinDescriptor d){
  if(d==null)return null;
  if(d instanceof MirrorSkinDescriptor)return "mirror";
  if(d instanceof DynamicSkinDescriptor x)return "fetching;"+x.template();
  if(d instanceof UuidSkinDescriptor x)return "fetching-uuid;"+x.uuid();
  SkinData data=null;if(d instanceof StaticSkinDescriptor x)data=x.data();else if(d instanceof UrlSkinDescriptor x)data=x.data();else if(d instanceof FileSkinDescriptor x)data=x.data();
  if(data!=null&&data.texture()!=null)return "prefetched;textures;"+data.texture()+";"+(data.signature()==null?"":data.signature());
  if(d instanceof StaticSkinDescriptor x)return "fetching;"+x.name();
  return d.kind()+";"+d.argument();
 }
 public static SkinDescriptor deserialize(String s){
  if(s==null||s.isBlank())return null;String[]a=s.split(";",-1);String k=a[0].toLowerCase(Locale.ROOT);
  return switch(k){case"mirror"->new MirrorSkinDescriptor();case"fetching"->new DynamicSkinDescriptor(join(a,1));case"fetching-uuid"->new UuidSkinDescriptor(join(a,1));
   case"prefetched"->{SkinData d=null;for(int i=1;i+2<a.length;i+=3)if("textures".equalsIgnoreCase(a[i])){d=new SkinData(a[i+1],a[i+2].isEmpty()?null:a[i+2]);break;}yield new StaticSkinDescriptor("",d);}
   default->null;};
 }
 private static String join(String[]a,int from){return String.join(";",Arrays.copyOfRange(a,from,a.length));}
}