package com.lime.sansblok;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class SansBlok extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<Location, Integer> blokCanlari = new HashMap<>();
    private final Map<Location, String> blokHologramIsimleri = new HashMap<>();
    private final Random random = new Random();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);

        if (getCommand("sansblok") != null) {
            getCommand("sansblok").setExecutor(this);
        }
    }

    @Override
    public void onDisable() {
        for (String holoIsim : blokHologramIsimleri.values()) {
            hologramSilKomutla(holoIsim);
        }
        blokHologramIsimleri.clear();
        blokCanlari.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cBu komutu sadece oyuncular kullanabilir.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("sansblok.admin")) {
            player.sendMessage("§cBu komutu kullanmak için yetkiniz yok.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("al")) {
            ItemStack sansBlogu = new ItemStack(Material.SPONGE);
            ItemMeta meta = sansBlogu.getItemMeta();
            
            if (meta != null) {
                meta.setDisplayName("§e§lŞans Bloğu");
                meta.setLore(Arrays.asList("§7Yere koy ve şansını dene!", "§7Kırmak için direnci zorla."));
                sansBlogu.setItemMeta(meta);
            }

            player.getInventory().addItem(sansBlogu);
            player.sendMessage("§aBaşarıyla 1 adet Şans Bloğu aldın!");
            return true;
        }

        player.sendMessage("§7Kullanım: §e/sansblok al");
        return true;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        
        if (item.getType() != Material.SPONGE) return;
        if (!item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName() || !meta.hasLore()) return;
        if (!meta.getDisplayName().equals("§e§lŞans Bloğu")) return;

        Block block = event.getBlock();
        Location loc = block.getLocation();
        int maxCan = getConfig().getInt("sans-blogu.can", 5);
        
        blokCanlari.put(loc, maxCan);
        hologramOlustur(loc, maxCan, maxCan);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location loc = block.getLocation();

        if (!blokCanlari.containsKey(loc)) return;

        Player player = event.getPlayer();
        int kalanCan = blokCanlari.get(loc) - 1;
        int maxCan = getConfig().getInt("sans-blogu.can", 5);

        if (kalanCan > 0) {
            event.setCancelled(true);
            blokCanlari.put(loc, kalanCan);
            hologramGuncelle(loc, kalanCan, maxCan);
            player.sendMessage("§7Bloğun kalan direnci: §e" + kalanCan);
        } else {
            String holoIsim = blokHologramIsimleri.remove(loc);
            if (holoIsim != null) {
                hologramSilKomutla(holoIsim);
            }
            blokCanlari.remove(loc);
            odulVer(player, loc);
        }
    }

    private void hologramOlustur(Location loc, int kalanCan, int maxCan) {
        double yukseklik = getConfig().getDouble("sans-blogu.hologram-yükseklik", 2.2);
        Location holoLoc = loc.clone().add(0.5, yukseklik, 0.5);
        String holoIsim = "sb-" + UUID.randomUUID().toString().substring(0, 6);
        
        blokHologramIsimleri.put(loc, holoIsim);

        // FancyHolograms komutlarını konsoldan tetikliyoruz
        String dunya = holoLoc.getWorld().getName();
        double x = holoLoc.getX();
        double y = holoLoc.getY();
        double z = holoLoc.getZ();

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "hologram create " + holoIsim);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "hologram teleport " + holoIsim + " " + dunya + " " + x + " " + y + " " + z);
        
        hologramSatirlariniYukle(holoIsim, kalanCan, maxCan);
    }

    private void hologramGuncelle(Location loc, int kalanCan, int maxCan) {
        String holoIsim = blokHologramIsimleri.get(loc);
        if (holoIsim != null) {
            hologramSatirlariniYukle(holoIsim, kalanCan, maxCan);
        }
    }

    private void hologramSatirlariniYukle(String holoIsim, int kalanCan, int maxCan) {
        List<String> orijinalSatirlar = getConfig().getStringList("sans-blogu.hologram-satirlari");
        
        // Önce eski satırları temizle
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "hologram edit " + holoIsim + " clear_lines");

        for (String satir : orijinalSatirlar) {
            String gecici = satir
                    .replace("%kalan_can%", String.valueOf(kalanCan))
                    .replace("%toplam_can%", String.valueOf(maxCan))
                    .replace("&", "§");
            
            // Komut satırındaki boşlukları korumak için tırnak içine alıyoruz
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "hologram edit " + holoIsim + " add_line \"" + gecici + "\"");
        }
    }

    private void hologramSilKomutla(String holoIsim) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "hologram delete " + holoIsim);
    }

    private void odulVer(Player player, Location loc) {
        ConfigurationSection section = getConfig().getConfigurationSection("oduller");
        if (section == null) return;

        Set<String> odulAnahtarlari = section.getKeys(false);
        if (odulAnahtarlari.isEmpty()) return;

        int toplamSans = 0;
        for (String key : odulAnahtarlari) {
            toplamSans += section.getInt(key + ".sans-orani", 0);
        }

        int randomNum = random.nextInt(toplamSans);
        int counter = 0;
        String chosenReward = null;

        for (String key : odulAnahtarlari) {
            counter += section.getInt(key + ".sans-orani", 0);
            if (randomNum < counter) {
                chosenReward = key;
                break;
            }
        }

        if (chosenReward != null) {
            String mesaj = section.getString(chosenReward + ".mesaj");
            if (mesaj != null && !mesaj.isEmpty()) {
                player.sendMessage(mesaj.replace("&", "§").replace("%player%", player.getName()));
            }

            List<String> komutlar = section.getStringList(chosenReward + ".komutlar");
            for (String komut : komutlar) {
                String dynamicCmd = komut
                        .replace("%player%", player.getName())
                        .replace("%x%", String.valueOf(loc.getBlockX()))
                        .replace("%y%", String.valueOf(loc.getBlockY()))
                        .replace("%z%", String.valueOf(loc.getBlockZ()));
                
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), dynamicCmd);
            }
        }
    }
}
