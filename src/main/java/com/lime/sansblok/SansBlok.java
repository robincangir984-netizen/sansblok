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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

public final class SansBlok extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<Location, Integer> blokCanlari = new HashMap<>();
    private final Map<Location, Object> blokHologramlari = new HashMap<>();
    private final Map<Location, BukkitTask> yenilenmeTasklari = new HashMap<>();
    private final Map<Location, Integer> kalanSureler = new HashMap<>();
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
        hologramlariVeBloklariTemizle();
    }

    private void hologramlariVeBloklariTemizle() {
        // Aktif yenilenme görevlerini iptal et ve bedrockları süngere çevir
        for (BukkitTask task : yenilenmeTasklari.values()) {
            if (task != null) task.cancel();
        }
        for (Location loc : yenilenmeTasklari.keySet()) {
            loc.getBlock().setType(Material.SPONGE);
        }
        yenilenmeTasklari.clear();
        kalanSureler.clear();

        // Hologramları kaldır
        for (Object holo : blokHologramlari.values()) {
            fancyHologramSil(holo);
        }
        blokHologramlari.clear();
        blokCanlari.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("sansblok.admin")) {
                sender.sendMessage("§cBu komutu kullanmak için yetkiniz yok.");
                return true;
            }
            reloadConfig();
            hologramlariVeBloklariTemizle();
            sender.sendMessage("§a[SansBlok] Eklenti ayarları başarıyla yenilendi ve tüm şans blokları sıfırlandı!");
            return true;
        }

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

        player.sendMessage("§7Kullanım: §e/sansblok al §7veya §e/sansblok reload");
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
        
        // Eğer zaten bekleyen bir konumsa yerleştirilmesin
        if (yenilenmeTasklari.containsKey(loc)) {
            event.setCancelled(true);
            return;
        }

        int maxCan = getConfig().getInt("sans-blogu.can", 5);
        blokCanlari.put(loc, maxCan);
        hologramOlustur(loc, maxCan, maxCan, false);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location loc = block.getLocation();

        // Durum 1: Yenilenme aşamasındaki Bedrock'ı koruma
        if (yenilenmeTasklari.containsKey(loc)) {
            event.setCancelled(true);
            return;
        }

        // Durum 2: Şans Bloğuna vurulma anı
        if (!blokCanlari.containsKey(loc)) return;

        Player player = event.getPlayer();
        int kalanCan = blokCanlari.get(loc) - 1;
        int maxCan = getConfig().getInt("sans-blogu.can", 5);

        // Her vuruşta tetiklenen ödül sistemi
        vurusOduluVer(player);

        if (kalanCan > 0) {
            event.setCancelled(true);
            blokCanlari.put(loc, kalanCan);
            hologramGuncelle(loc, kalanCan, maxCan, false);
            player.sendMessage("§7Bloğun kalan direnci: §e" + kalanCan);
        } else {
            // Son vuruş: Blok yok edilmiyor, Bedrock'a dönüştürülüyor!
            event.setCancelled(true);
            block.setType(Material.BEDROCK);
            blokCanlari.remove(loc);
            
            // Konfigürasyondaki büyük ödülü tetikle
            anaOdulVer(player, loc);

            // 30 dakikalık (1800 saniye) geri sayım sürecini başlat
            bedrockGeriSayimBaslat(loc);
        }
    }

    private void vurusOduluVer(Player player) {
        ItemStack coin = new ItemStack(Material.TUBE_CORAL, 1);
        player.getInventory().addItem(coin);
        player.sendMessage("§a1x sansblogu coin aldın");
    }

    private void bedrockGeriSayimBaslat(final Location loc) {
        final int toplamSaniye = 1800; // 30 Dakika
        kalanSureler.put(loc, toplamSaniye);

        // İlk bedrock hologram görünümünü ayarla
        hologramGuncelle(loc, 0, 0, true);

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                int sure = kalanSureler.get(loc) - 1;
                
                if (sure <= 0) {
                    // Süre bitti: Bloğu tekrar süngere çevir ve canını sıfırla
                    cancel();
                    yenilenmeTasklari.remove(loc);
                    kalanSureler.remove(loc);
                    
                    loc.getBlock().setType(Material.SPONGE);
                    int maxCan = getConfig().getInt("sans-blogu.can", 5);
                    blokCanlari.put(loc, maxCan);
                    
                    hologramGuncelle(loc, maxCan, maxCan, false);
                } else {
                    kalanSureler.put(loc, sure);
                    hologramGuncelle(loc, 0, 0, true);
                }
            }
        }.runTaskTimer(this, 20L, 20L);

        yenilenmeTasklari.put(loc, task);
    }

    // --- FANCYHOLOGRAMS ENTEGRASYONU ---
    
    private void hologramOlustur(Location loc, int kalanCan, int maxCan, boolean bedrockModu) {
        try {
            double yukseklik = getConfig().getDouble("sans-blogu.hologram-yükseklik", 2.2);
            Location holoLoc = loc.clone().add(0.5, yukseklik, 0.5);
            String holoIsim = "sb-" + UUID.randomUUID().toString().substring(0, 6);

            Class<?> textDataClass = Class.forName("de.oliver.fancyholograms.api.data.TextHologramData");
            Constructor<?> constructor = textDataClass.getConstructor(String.class, Location.class);
            Object holoData = constructor.newInstance(holoIsim, holoLoc);

            Method setLinesMethod = textDataClass.getMethod("setLines", List.class);
            setLinesMethod.invoke(holoData, getDinamikSatirlar(loc, kalanCan, maxCan, bedrockModu));

            Class<?> apiClass = Class.forName("de.oliver.fancyholograms.api.FancyHologramsAPI");
            Method getApiMethod = apiClass.getMethod("get");
            Object apiInstance = getApiMethod.invoke(null);
            
            Method createMethod = apiInstance.getClass().getMethod("createHologram", Class.forName("de.oliver.fancyholograms.api.data.HologramData"));
            Object hologram = createMethod.invoke(apiInstance, holoData);

            Method getManagerMethod = apiInstance.getClass().getMethod("getHologramManager");
            Object managerInstance = getManagerMethod.invoke(apiInstance);

            Method addHologramMethod = managerInstance.getClass().getMethod("addHologram", Class.forName("de.oliver.fancyholograms.api.hologram.Hologram"));
            addHologramMethod.invoke(managerInstance, hologram);

            blokHologramlari.put(loc, hologram);
        } catch (Exception e) {
            getLogger().warning("Hologram olusturulamadi: " + e.getMessage());
        }
    }

    private void hologramGuncelle(Location loc, int kalanCan, int maxCan, boolean bedrockModu) {
        Object hologram = blokHologramlari.get(loc);
        if (hologram == null) {
            // Eğer o konumda henüz hologram yoksa oluştur
            hologramOlustur(loc, kalanCan, maxCan, bedrockModu);
            return;
        }

        try {
            Method javaGetData = hologram.getClass().getMethod("getData");
            Object holoData = javaGetData.invoke(hologram);

            Class<?> textDataClass = Class.forName("de.oliver.fancyholograms.api.data.TextHologramData");
            if (textDataClass.isInstance(holoData)) {
                Method setLinesMethod = textDataClass.getMethod("setLines", List.class);
                setLinesMethod.invoke(holoData, getDinamikSatirlar(loc, kalanCan, maxCan, bedrockModu));

                Method refreshMethod = hologram.getClass().getMethod("refreshForAll");
                refreshMethod.invoke(hologram);
            }
        } catch (Exception e) {
            getLogger().warning("Hologram yenilenemedi: " + e.getMessage());
        }
    }

    private void fancyHologramSil(Object hologram) {
        try {
            if (hologram == null) return;
            Class<?> apiClass = Class.forName("de.oliver.fancyholograms.api.FancyHologramsAPI");
            Method getApiMethod = apiClass.getMethod("get");
            Object apiInstance = getApiMethod.invoke(null);

            Method getManagerMethod = apiInstance.getClass().getMethod("getHologramManager");
            Object managerInstance = getManagerMethod.invoke(apiInstance);

            Method removeMethod = managerInstance.getClass().getMethod("removeHologram", Class.forName("de.oliver.fancyholograms.api.hologram.Hologram"));
            removeMethod.invoke(managerInstance, hologram);
        } catch (Exception e) {
            // Pas geç
        }
    }

    private List<String> getDinamikSatirlar(Location loc, int kalanCan, int maxCan, boolean bedrockModu) {
        List<String> satirlar = new ArrayList<>();
        
        if (bedrockModu) {
            int toplamSaniye = kalanSureler.getOrDefault(loc, 1800);
            int dakika = toplamSaniye / 60;
            int saniye = toplamSaniye % 60;
            String sureMetni = String.format("%02d:%02d", dakika, saniye);
            
            satirlar.add("§c§lYENİLENİYOR");
            satirlar.add("§7Kalan Süre: §e" + sureMetni);
        } else {
            List<String> orijinalSatirlar = getConfig().getStringList("sans-blogu.hologram-satirlari");
            for (String satir : orijinalSatirlar) {
                satirlar.add(satir
                        .replace("%kalan_can%", String.valueOf(kalanCan))
                        .replace("%toplam_can%", String.valueOf(maxCan))
                        .replace("&", "§"));
            }
        }
        return satirlar;
    }

    private void anaOdulVer(Player player, Location loc) {
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
