package lol.pyr.znpcsplus.fabric.hologram;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemType;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.nbt.*;
import com.github.retrooper.packetevents.protocol.nbt.codec.NBTCodec;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

public final class ItemCodec {
    private ItemCodec(){}
    public static boolean valid(String in){
        if(in==null||in.isBlank())return false;
        String s=stripPrefix(in);int brace=s.indexOf('{');String name=brace==-1?s:s.substring(0,brace);
        ItemType type=ItemTypes.getByName(name.contains(":")?name.toLowerCase():"minecraft:"+name.toLowerCase());
        if(type==null)return false;
        if(brace==-1)return true;
        try{NBTCodec.jsonToNBT(JsonParser.parseString(s.substring(brace)));return true;}catch(Exception e){return false;}
    }
    public static ItemStack decode(String in){
        if(in==null||in.isBlank())return ItemStack.EMPTY;
        String s=stripPrefix(in);int brace=s.indexOf('{');String name=brace==-1?s:s.substring(0,brace);int amount=1;NBTCompound nbt=new NBTCompound();
        if(brace!=-1){try{JsonElement e=JsonParser.parseString(s.substring(brace));nbt=(NBTCompound)NBTCodec.jsonToNBT(e);NBTNumber count=nbt.getNumberTagOrNull("Count");if(count!=null){nbt.removeTag("Count");amount=Math.max(1,Math.min(127,count.getAsInt()));}}catch(Exception ignored){}}
        ItemType type=ItemTypes.getByName(name.contains(":")?name.toLowerCase():"minecraft:"+name.toLowerCase());if(type==null)type=ItemTypes.STONE;
        return ItemStack.builder().type(type).amount(amount).nbt(nbt).build();
    }
    public static String encode(ItemStack stack){
        if(stack==null||stack.isEmpty())return "item:air";
        NBTCompound nbt=stack.getNBT();if(nbt==null)nbt=new NBTCompound();if(stack.getAmount()>1)nbt.setTag("Count",new NBTInt(stack.getAmount()));
        String id=stack.getType().getName().toString().replace("minecraft:","");
        return nbt.isEmpty()?"item:"+id:"item:"+id+NBTCodec.nbtToJson(nbt,true);
    }
    private static String stripPrefix(String s){return s.regionMatches(true,0,"item:",0,5)?s.substring(5):s;}
}
