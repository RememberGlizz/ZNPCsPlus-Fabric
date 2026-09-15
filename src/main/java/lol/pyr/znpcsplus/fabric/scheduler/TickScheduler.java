package lol.pyr.znpcsplus.fabric.scheduler;
import net.minecraft.server.MinecraftServer;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicLong;
public final class TickScheduler {
 private final PriorityQueue<Task> tasks=new PriorityQueue<>(); private final AtomicLong seq=new AtomicLong(); private long tick;
 public synchronized void later(long delayTicks,Runnable r){tasks.add(new Task(tick+Math.max(0,delayTicks),seq.getAndIncrement(),r));}
 public void tick(MinecraftServer server){tick++; while(true){Task t; synchronized(this){t=tasks.peek(); if(t==null||t.when>tick)return; tasks.poll();} try{t.runnable.run();}catch(Throwable e){org.slf4j.LoggerFactory.getLogger("ZNPCsPlus-Fabric").error("Scheduled NPC task failed",e);}}}
 public synchronized void clear(){tasks.clear();}
 private record Task(long when,long order,Runnable runnable) implements Comparable<Task>{public int compareTo(Task o){int c=Long.compare(when,o.when);return c!=0?c:Long.compare(order,o.order);}}
}