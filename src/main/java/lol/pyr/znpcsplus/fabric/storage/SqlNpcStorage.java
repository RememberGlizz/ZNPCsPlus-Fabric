package lol.pyr.znpcsplus.fabric.storage;

import lol.pyr.znpcsplus.fabric.npc.Npc;
import org.yaml.snakeyaml.Yaml;

import java.sql.*;
import java.util.*;

public final class SqlNpcStorage implements NpcStorage {
    private final Connection connection;
    private final String table;
    private final boolean mysql;
    private final NpcSerde serde=new NpcSerde();
    private final Yaml yaml=new Yaml();

    public static SqlNpcStorage sqlite(String file)throws Exception{
        return new SqlNpcStorage(DriverManager.getConnection("jdbc:sqlite:"+file),"znpcsplus_npcs",false);
    }
    public static SqlNpcStorage mysql(String host,int port,String db,String user,String pass,String prefix,boolean ssl)throws Exception{
        String safe=prefix==null?"znpcsplus_":prefix.replaceAll("[^A-Za-z0-9_]","");
        String url="jdbc:mysql://"+host+":"+port+"/"+db+"?useSSL="+ssl+"&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        return new SqlNpcStorage(DriverManager.getConnection(url,user,pass),safe+"npcs",true);
    }
    private SqlNpcStorage(Connection c,String table,boolean mysql)throws Exception{this.connection=c;this.table=table;this.mysql=mysql;init();}
    private void init()throws Exception{try(Statement s=connection.createStatement()){s.executeUpdate("CREATE TABLE IF NOT EXISTS "+table+" (id VARCHAR(128) PRIMARY KEY, data "+(mysql?"LONGTEXT":"TEXT")+" NOT NULL)");}}
    @Override public Collection<Npc> load()throws Exception{
        List<Npc>out=new ArrayList<>();try(Statement s=connection.createStatement();ResultSet rs=s.executeQuery("SELECT data FROM "+table)){while(rs.next()){Object o=yaml.load(rs.getString(1));if(o instanceof Map<?,?>m)out.add(serde.deserialize((Map<String,Object>)m));}}return out;
    }
    @Override public void save(Collection<Npc> npcs)throws Exception{
        connection.setAutoCommit(false);
        try{
            Set<String>ids=new HashSet<>();
            String sql=mysql?"INSERT INTO "+table+" (id,data) VALUES (?,?) ON DUPLICATE KEY UPDATE data=VALUES(data)":"INSERT INTO "+table+" (id,data) VALUES (?,?) ON CONFLICT(id) DO UPDATE SET data=excluded.data";
            try(PreparedStatement p=connection.prepareStatement(sql)){for(Npc n:npcs)if(n.save){ids.add(n.id);p.setString(1,n.id);p.setString(2,yaml.dump(serde.serialize(n)));p.addBatch();}p.executeBatch();}
            try(Statement s=connection.createStatement();ResultSet rs=s.executeQuery("SELECT id FROM "+table)){List<String>remove=new ArrayList<>();while(rs.next())if(!ids.contains(rs.getString(1)))remove.add(rs.getString(1));try(PreparedStatement d=connection.prepareStatement("DELETE FROM "+table+" WHERE id=?")){for(String id:remove){d.setString(1,id);d.addBatch();}d.executeBatch();}}
            connection.commit();
        }catch(Exception e){connection.rollback();throw e;}finally{connection.setAutoCommit(true);}
    }
    @Override public void delete(String id)throws Exception{try(PreparedStatement p=connection.prepareStatement("DELETE FROM "+table+" WHERE id=?")){p.setString(1,id);p.executeUpdate();}}
    @Override public void close()throws Exception{connection.close();}
}
