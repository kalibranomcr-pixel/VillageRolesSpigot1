package de.openai.villageroles;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import java.util.*;

public final class VillageRolesPaper extends JavaPlugin implements Listener, CommandExecutor {
  enum Role { POLICE, BUILDER, NIGHT_SCOUT, BREEDER, EXPLORER, MAYOR, DOCTOR, SHOPKEEPER }
  record Repair(UUID world,int x,int y,int z,String data){}
  private NamespacedKey roleKey;
  private final Deque<Repair> repairs=new ArrayDeque<>();
  private final Map<UUID,Long> cooldown=new HashMap<>();
  private long ticks;

  @Override public void onEnable(){
    saveDefaultConfig();
    roleKey=new NamespacedKey(this,"role");
    getServer().getPluginManager().registerEvents(this,this);
    PluginCommand c=getCommand("vrole"); if(c!=null)c.setExecutor(this);
    getServer().getScheduler().runTaskTimer(this,this::tick,20L,10L);
    getLogger().info("VillageRolesSpigot enabled");
  }

  @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
  public void explode(EntityExplodeEvent e){capture(e.blockList());}
  @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
  public void explode(BlockExplodeEvent e){capture(e.blockList());}
  private void capture(List<Block> blocks){
    if(!getConfig().getBoolean("repair-village-damage",true))return;
    int n=0,max=getConfig().getInt("max-repair-blocks-per-event",64);
    for(Block b:blocks){if(n++>=max)break;if(!b.getType().isAir()&&!isProtectedTechnicalBlock(b.getType()))repairs.add(new Repair(b.getWorld().getUID(),b.getX(),b.getY(),b.getZ(),b.getBlockData().getAsString()));}
  }

  private void tick(){
    ticks+=10;
    for(World w:Bukkit.getWorlds()) for(Villager v:w.getEntitiesByClass(Villager.class)){
      Role r=role(v); if(r==null||v.isDead())continue;
      switch(r){
        case POLICE -> police(v);
        case BUILDER -> builder(v);
        case NIGHT_SCOUT -> scout(v);
        case BREEDER -> breeder(v);
        case EXPLORER -> explorer(v);
        case MAYOR -> mayor(v);
        case DOCTOR -> doctor(v);
        case SHOPKEEPER -> shopkeeper(v);
      }
    }
  }

  private Role role(Villager v){
    String s=v.getPersistentDataContainer().get(roleKey,PersistentDataType.STRING);
    if(s==null)return null; try{return Role.valueOf(s);}catch(Exception e){return null;}
  }
  private void setRole(Villager v,Role r){
    v.getPersistentDataContainer().set(roleKey,PersistentDataType.STRING,r.name());
    v.setCustomName("§6["+r.name().replace('_',' ')+"]§r Villager");
    v.setCustomNameVisible(true);
    EntityEquipment eq=v.getEquipment();
    if(eq!=null&&r==Role.POLICE)eq.setItemInMainHand(new ItemStack(Material.IRON_SWORD));
  }

  private void police(Villager v){
    double rad=getConfig().getDouble("police-sight-radius",16);
    Monster best=null; double bd=Double.MAX_VALUE;
    for(Entity e:v.getNearbyEntities(rad,rad/2,rad)) if(e instanceof Monster m){
      double d=v.getLocation().distanceSquared(m.getLocation()); if(d<bd){bd=d;best=m;}
    }
    if(best==null)return;
    move(v,best.getLocation(),.27);
    if(bd<=6.25&&ready(v,20))best.damage(getConfig().getDouble("police-damage",4),v);
  }

  private void builder(Villager v){
    Repair best=null; double bd=Double.MAX_VALUE,rad=getConfig().getDouble("builder-site-search-radius",64);
    for(Repair r:repairs){
      World w=Bukkit.getWorld(r.world()); if(w!=v.getWorld())continue;
      double d=v.getLocation().distanceSquared(new Location(w,r.x()+.5,r.y(),r.z()+.5));
      if(d<bd&&d<=rad*rad){bd=d;best=r;}
    }
    if(best==null)return;
    World w=Bukkit.getWorld(best.world()); if(w==null){repairs.remove(best);return;}
    Location t=new Location(w,best.x()+.5,best.y(),best.z()+.5); move(v,t,.22);
    if(bd<9&&ready(v,getConfig().getInt("builder-place-interval",40))){
      try{Block b=w.getBlockAt(best.x(),best.y(),best.z()); if(b.getType().isAir())b.setBlockData(Bukkit.createBlockData(best.data()),false);}catch(Exception ignored){}
      repairs.remove(best);
    }
  }

  private boolean isProtectedTechnicalBlock(Material m){
    String n=m.name();
    return n.contains("REDSTONE")||n.contains("PISTON")||n.contains("RAIL")||
      n.contains("CHEST")||n.contains("SHULKER")||n.contains("SPAWNER")||
      n.contains("HOPPER")||n.contains("DROPPER")||n.contains("DISPENSER")||
      n.contains("OBSERVER")||n.contains("REPEATER")||n.contains("COMPARATOR")||
      n.contains("LEVER")||n.contains("BUTTON")||n.contains("PRESSURE_PLATE")||
      n.contains("TRIPWIRE")||n.contains("DAYLIGHT_DETECTOR")||
      n.contains("SCULK_SENSOR")||n.contains("CRAFTER")||n.contains("FURNACE")||
      n.contains("SMOKER")||n.contains("BLAST_FURNACE")||n.contains("BARREL")||
      n.contains("BEACON")||n.contains("END_PORTAL")||n.contains("COMMAND_BLOCK");
  }

  private void scout(Villager v){
    if(v.getWorld().getTime()<12000)return;
    if(!ready(v,getConfig().getInt("scout-place-interval",60)))return;
    Location c=v.getLocation();
    for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++){
      Block b=c.getWorld().getBlockAt(c.getBlockX()+x,c.getBlockY(),c.getBlockZ()+z);
      if(b.getType().isAir()&&b.getRelative(0,-1,0).getType().isSolid()&&b.getLightLevel()<7){b.setType(Material.TORCH,false);return;}
    }
  }

  private void breeder(Villager v){
    double r=getConfig().getDouble("breeder-radius",32);
    Animals best=null; double bd=Double.MAX_VALUE;
    for(Entity e:v.getNearbyEntities(r,8,r))if(e instanceof Animals a){double d=v.getLocation().distanceSquared(a.getLocation());if(d<bd){bd=d;best=a;}}
    if(best!=null&&bd>9)moveAnimal(best,v.getLocation(),.10);
  }

  private void explorer(Villager v){if(ready(v,200))wander(v,12,.16);}
  private void mayor(Villager v){if(ready(v,300))v.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,v.getLocation().add(0,2,0),5);}
  private void doctor(Villager v){
    if(!getConfig().getBoolean("doctor-healing",true)||!ready(v,getConfig().getInt("doctor-heal-interval",200)))return;
    double amount=getConfig().getDouble("doctor-heal-amount",6);
    for(Entity e:v.getNearbyEntities(8,5,8))if(e instanceof LivingEntity l&&l.getHealth()<l.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()){
      l.setHealth(Math.min(l.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue(),l.getHealth()+amount));break;
    }
  }
  private void shopkeeper(Villager v){if(ready(v,24000))v.setVillagerLevel(Math.max(2,v.getVillagerLevel()));}

  private boolean ready(Entity e,long wait){long n=cooldown.getOrDefault(e.getUniqueId(),0L);if(ticks<n)return false;cooldown.put(e.getUniqueId(),ticks+wait);return true;}
  private void move(Villager v,Location t,double speed){Vector d=t.toVector().subtract(v.getLocation().toVector());if(d.lengthSquared()<.5)return;d.normalize().multiply(speed);d.setY(Math.max(-.08,Math.min(.18,d.getY())));v.setVelocity(d);}
  private void moveAnimal(Animals a,Location t,double speed){Vector d=t.toVector().subtract(a.getLocation().toVector());if(d.lengthSquared()>1)a.setVelocity(d.normalize().multiply(speed));}
  private void wander(Villager v,double radius,double speed){double a=Math.random()*Math.PI*2;move(v,v.getLocation().clone().add(Math.cos(a)*radius,0,Math.sin(a)*radius),speed);}

  @Override public boolean onCommand(CommandSender s,Command c,String label,String[] a){
    if(!(s instanceof Player p)){s.sendMessage("Players only.");return true;}
    if(a.length<1){p.sendMessage("§e/vrole <police|builder|night_scout|breeder|explorer|mayor|doctor|shopkeeper>");return true;}
    Role r;try{r=Role.valueOf(a[0].toUpperCase(Locale.ROOT).replace('-','_'));}catch(Exception ex){p.sendMessage("§cUnbekannte Rolle.");return true;}
    Villager best=null;double bd=64;
    for(Entity e:p.getNearbyEntities(8,8,8))if(e instanceof Villager v){double d=p.getLocation().distanceSquared(v.getLocation());if(d<bd){bd=d;best=v;}}
    if(best==null){p.sendMessage("§cKein Villager im Umkreis von 8 Blöcken.");return true;}
    setRole(best,r);p.sendMessage("§aRolle gesetzt: §f"+r);return true;
  }
}
