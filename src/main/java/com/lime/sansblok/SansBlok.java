package com.lime.sansblok;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class SansBlok extends JavaPlugin implements Listener {

    private final Map<Location, Integer> blokCanlari = new HashMap<>();
    private final Map<Location, Hologram> blokHologramlari = new HashMap<>();
    private final Random random = new Random();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        for (Hologram holo : blokHologramlari.values()) {
            if (holo != null) holo.delete();
        }
        blokHologramlari.clear();
        blokCanlari.clear();
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.SPONGE) return;

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
            hologramSil(loc);
            blokCanlari.remove(loc);
            odulVer(player, loc);
        }
    }

    private void hologramOlustur(Location loc, int kalanCan, int maxCan) {
        double yukseklik = getConfig().getDouble("sans-blogu.hologram-yükseklik", 2.2);
        Location holoLoc = loc.clone().add(0.5, yukseklik, 0.5);

        List<String> satirlar = getHologramSatirlari(kalanCan, maxCan);
        Hologram hologram = DHAPI.createHologram(UUID.randomUUID().toString(), holoLoc, satirlar);
        blokHologramlari.put(loc, hologram);
    }

    private void hologramGuncelle(Location loc, int kalanCan, int maxCan) {
        Hologram hologram = blokHologramlari.get(loc);
        if (hologram != null) {
            List<String> yeniSatirlar = getHologramSatirlari(kalanCan, maxCan);
            DHAPI.setHologramLines(hologram, yeniSatirlar);
        }
    }

    private void hologramSil(Location loc) {
        Hologram hologram = blokHologramlari.remove(loc);
        if (hologram != null) hologram.delete();
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

        int rastgeleSayi = random.nextInt(toplamSans);
        int sayac = 0;
        String secilenOdul = null;

        for (String key : odulAnahtarlari) {
            sayac += section.getInt(key + ".sans-orani", 0);
            if (rastgeleSayi < sayac) {
                secilenOdul = key;
                break;
            }
        }

        if (secilenOdul != null) {
            String mesaj = section.getString(secilenOdul + ".mesaj");
            if (mesaj != null && !mesaj.isEmpty()) {
                player.sendMessage(mesaj.replace("&", "§").replace("%player%", player.getName()));
            }

            List<String> komutlar = section.getStringList(secilenOdul + ".komutlar");
            for (String komut : komutlar) {
                String duzenlenenKomut = komut
                        .replace("%player%", player.getName())
                        .replace("%x%", String.valueOf(loc.getBlockX()))
                        .replace("%y%", String.valueOf(loc.getBlockY()))
                        .replace("%z%", String.valueOf(loc.getBlockZ()));
                
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), duzenlenenKomut);
            }
        }
    }
}