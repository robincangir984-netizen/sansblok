package com.lime.sansblok;

import de.oliver.fancyholograms.api.FancyHologramsAPI;
import de.oliver.fancyholograms.api.HologramManager;
import de.oliver.fancyholograms.api.data.HologramData;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;
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
    private final Map<Location, Hologram> blokHologramlari = new HashMap<>();
    private final Random random = new Random();
    private HologramManager hologramManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        
        this.hologramManager = FancyHologramsAPI.get().getHologramManager();

        if (getCommand("sansblok") != null) {
            getCommand("sansblok").setExecutor(this);
        }
    }

    @Override
    public void onDisable() {
        for (Hologram holo : blokHologramlari.values()) {
            if (holo != null) {
                hologramManager.removeHologram(holo);
            }
        }
        blokHologramlari.clear();
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
        
        // Sıkı Güvenlik Kontrolü: Yere konan şey sünger değilse direkt geç
        if (item.getType() != Material.SPONGE) return;
        
        // Sıkı Güvenlik Kontrolü: İsim veya Lore yoksa ya da yanlışsa kesinlikle sayma
        if (!item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName() || !meta.hasLore()) return;
        
        // İsim tam olarak "§e§lŞans Bloğu" değilse sıradan süngerdir, işleme alma
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

        // Eğer kırılan yer daha önce bizim kaydettiğimiz bir şans bloğu konumu değilse hiçbir şey yapma
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
            hologramSil(loc);
            blokCanlari.remove(loc);
            odulVer(player, loc);
        }
    }

    private void hologramOlustur(Location loc, int kalanCan, int maxCan) {
        double yukseklik = getConfig().getDouble("sans-blogu.hologram-yükseklik", 2.2);
        Location holoLoc = loc.clone().add(0.5, yukseklik, 0.5);

        String isim = "sansblok-" + UUID.randomUUID().toString().substring(0, 8);
        TextHologramData data = new TextHologramData(isim, holoLoc);
        
        List<String> satirlar = getHologramSatirlari(kalanCan, maxCan);
        data.setLines(satirlar);
        
        Hologram hologram = FancyHologramsAPI.get().createHologram(data);
        hologramManager.addHologram(hologram);
        blokHologramlari.put(loc, hologram);
    }

    private void hologramGuncelle(Location loc, int kalanCan, int maxCan) {
        Hologram hologram = blokHologramlari.get(loc);
        if (hologram != null && hologram.getData() instanceof TextHologramData) {
            TextHologramData data = (TextHologramData) hologram.getData();
            data.setLines(getHologramSatirlari(kalanCan, maxCan));
            hologram.refreshForAll();
        }
    }

    private void hologramSil(Location loc) {
        Hologram hologram = blokHologramlari.remove(loc);
        if (hologram != null) {
            hologramManager.removeHologram(hologram);
        }
    }

    private List<String> getHologramSatirlari(int kalanCan, int maxCan) {
        List<String> orijinalSatirlar = getConfig().getStringList("sans-blogu.hologram-satirlari");
        List<String> guncelSatirlar = new ArrayList<>();

        for (String satir : orijinalSatirlar) {
            String gecici = satir
                    .replace("%kalan_can%", String.valueOf(kalanCan))
                    .replace("%toplam_can%", String.valueOf(maxCan))
                    .replace("&", "§");
            guncelSatirlar.add(gecici);
        }
        return guncelSatirlar;
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
