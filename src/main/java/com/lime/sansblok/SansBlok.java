package com.lime.sansblok;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public final class SansBlok extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    // Arkadaşının eklentisindeki 3 zorunlu Map yapısı (Derleme hatası almamak için String olarak tutuyoruz)
    public Map<Location, Integer> activeBlocks = new HashMap<>();
    public Map<Location, String> internalHolograms = new HashMap<>(); 
    public Map<UUID, Long> clickCooldown = new HashMap<>();
    
    private final Map<Location, BukkitTask> yenilenmeTasklari = new HashMap<>();
    private final Map<Location, Integer> kalanSureler = new HashMap<>();
    private final Random random = new Random();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);

        if (getCommand("sansblok") != null) {
            getCommand("sansblok").setExecutor(this);
            getCommand("sansblok").setTabCompleter(this);
        }
    }

    @Override
    public void onDisable() {
        hologramlariVeBloklariTemizle();
    }

    private void hologramlariVeBloklariTemizle() {
        for (BukkitTask task : yenilenmeTasklari.values()) {
            if (task != null) task.cancel();
        }
        for (Location loc : yenilenmeTasklari.keySet()) {
            loc.getBlock().setType(Material.SPONGE);
        }
        yenilenmeTasklari.clear();
        kalanSureler.clear();
        clickCooldown.clear();

        for (String holoIsim : internalHolograms.values()) {
            hologramSilKomutla(holoIsim);
        }
        internalHolograms.clear();
        activeBlocks.clear();
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
                meta.setLore(Arrays.asList("§7Yere koy ve şansını dene!", "§7Sol tıkla vurarak direnci zorla."));
                sansBlogu.setItemMeta(meta);
            }

            player.getInventory().addItem(sansBlogu);
            player.sendMessage("§aBaşarıyla 1 adet Şans Bloğu aldın!");
            return true;
        }

        player.sendMessage("§7Kullanım: §e/sansblok al §7veya §e/sansblok reload");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("sansblok")) {
            if (args.length == 1) {
                List<String> completions = new ArrayList<>();
                if (sender.hasPermission("sansblok.admin")) {
                    completions.add("al");
                    completions.add("reload");
                }
                List<String> result = new ArrayList<>();
                for (String s : completions) {
                    if (s.toLowerCase().startsWith(args[0].toLowerCase())) {
                        result.add(s);
                    }
                }
                return result;
            }
        }
        return Collections.emptyList();
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
        
        if (yenilenmeTasklari.containsKey(loc)) {
            event.setCancelled(true);
            return;
        }

        int maxCan = getConfig().getInt("sans-blogu.can", 5);
        activeBlocks.put(loc, maxCan);
        hologramGuncelleSifirBagimlilik(loc, maxCan, maxCan, false);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location loc = block.getLocation();

        if (activeBlocks.containsKey(loc) || yenilenmeTasklari.containsKey(loc)) {
            if (!event.getPlayer().getGameMode().name().equals("CREATIVE")) {
                event.setCancelled(true);
            } else {
                String holoIsim = internalHolograms.remove(loc);
                if (holoIsim != null) hologramSilKomutla(holoIsim);
                activeBlocks.remove(loc);
                BukkitTask task = yenilenmeTasklari.remove(loc);
                if (task != null) task.cancel();
                kalanSureler.remove(loc);
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        
        Block block = event.getClickedBlock();
        if (block == null) return;
        Location loc = block.getLocation();

        if (yenilenmeTasklari.containsKey(loc)) {
            event.setCancelled(true);
            return;
        }

        if (!activeBlocks.containsKey(loc)) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        long simdi = System.currentTimeMillis();
        
        long cooldownSuresi = getConfig().getLong("sans-blogu.makro-koruma-ms", 400); 
        long sonVurus = clickCooldown.getOrDefault(playerUUID, 0L);
        
        if ((simdi - sonVurus) < cooldownSuresi) {
            return; 
        }
        
        clickCooldown.put(playerUUID, simdi);

        int kalanCan = activeBlocks.get(loc) - 1;
        int maxCan = getConfig().getInt("sans-blogu.can", 5);

        // REİS: Sadece mercan veriliyor, chate çöp mesaj falan gitmiyor.
        player.getInventory().addItem(new ItemStack(Material.TUBE_CORAL, 1));

        if (kalanCan > 0) {
            activeBlocks.put(loc, kalanCan);
            hologramGuncelleSifirBagimlilik(loc, kalanCan, maxCan, false);
            
            int progressedStage = (int) (((double) (maxCan - kalanCan) / maxCan) * 9);
            player.sendBlockDamage(loc, (float) progressedStage / 9f);
        } else {
            block.setType(Material.BEDROCK);
            activeBlocks.remove(loc);
            
            anaOdulVer(player, loc);
            bedrockGeriSayimBaslat(loc);
        }
    }

    private void bedrockGeriSayimBaslat(final Location loc) {
        final int toplamSaniye = 1800;
        kalanSureler.put(loc, toplamSaniye);

        hologramGuncelleSifirBagimlilik(loc, 0, 0, true);

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!kalanSureler.containsKey(loc)) {
                    cancel();
                    return;
                }
                
                int sure = kalanSureler.get(loc) - 1;
                
                if (sure <= 0) {
                    cancel();
                    yenilenmeTasklari.remove(loc);
                    kalanSureler.remove(loc);
                    
                    loc.getBlock().setType(Material.SPONGE);
                    int maxCan = getConfig().getInt("sans-blogu.can", 5);
                    activeBlocks.put(loc, maxCan);
                    
                    hologramGuncelleSifirBagimlilik(loc, maxCan, maxCan, false);
                } else {
                    kalanSureler.put(loc, sure);
                    hologramGuncelleSifirBagimlilik(loc, 0, 0, true);
                }
            }
        }.runTaskTimer(this, 20L, 20L);

        yenilenmeTasklari.put(loc, task);
    }

    // --- SIFIR IMPORT - DOĞRUDAN REFLECTION ILE FANCYHOLOGRAMS YÖNETİMİ ---

    private void hologramGuncelleSifirBagimlilik(Location loc, int kalanCan, int maxCan, boolean bedrockModu) {
        String holoIsim = internalHolograms.get(loc);
        List<String> yeniSatirlar = getDinamikSatirlar(loc, kalanCan, maxCan, bedrockModu);

        try {
            // FancyHolograms API sınıfını ve yöneticisini çalışma zamanında (Runtime) buluyoruz
            Class<?> apiClass = Class.forName("de.oliver.fancyholograms.api.FancyHologramsAPI");
            Object apiInstance = apiClass.getMethod("get").invoke(null);
            Object managerInstance = apiClass.getMethod("getHologramManager").invoke(apiInstance);

            Class<?> dataClass = Class.forName("de.oliver.fancyholograms.api.data.TextHologramData");
            Class<?> holoClass = Class.forName("de.oliver.fancyholograms.api.hologram.Hologram");

            if (holoIsim == null) {
                // Sıfırdan hologram adı üret
                holoIsim = "sb-" + new Random().nextInt(900000);
                internalHolograms.put(loc, holoIsim);

                double yukseklik = getConfig().getDouble("sans-blogu.hologram-yükseklik", 2.2);
                Location holoLoc = loc.clone().add(0.5, yukseklik, 0.5);

                // TextHologramData nesnesini oluştur
                Object dataInstance = dataClass.getConstructor(String.class, Location.class).newInstance(holoIsim, holoLoc);

                // Satırları içeri aktar (metot adı versiyona göre değişirse ikisini de deniyoruz)
                try {
                    dataClass.getMethod("setLines", List.class).invoke(dataInstance, yeniSatirlar);
                } catch (Exception e) {
                    List<String> lines = (List<String>) dataClass.getMethod("getLines").invoke(dataInstance);
                    lines.clear();
                    lines.addAll(yeniSatirlar);
                }

                // Hologramı oluştur ve haritaya ekle
                Object hologramInstance = apiClass.getMethod("createHologram", Class.forName("de.oliver.fancyholograms.api.data.HologramData")).invoke(apiInstance, dataInstance);
                managerInstance.getClass().getMethod("addHologram", holoClass).invoke(managerInstance, hologramInstance);
            } else {
                // Var olan hologramı ismiyle bulup güncelle
                Object hologramInstance = managerInstance.getClass().getMethod("getHologram", String.class).invoke(managerInstance, holoIsim);
                if (hologramInstance != null) {
                    Object dataInstance = holoClass.getMethod("getData").invoke(hologramInstance);
                    try {
                        dataClass.getMethod("setLines", List.class).invoke(dataInstance, yeniSatirlar);
                    } catch (Exception e) {
                        List<String> lines = (List<String>) dataClass.getMethod("getLines").invoke(dataInstance);
                        lines.clear();
                        lines.addAll(yeniSatirlar);
                    }
                    holoClass.getMethod("refreshForAll").invoke(hologramInstance);
                }
            }
        } catch (Exception e) {
            // Sunucuda FancyHolograms yoksa veya Runtime hatası koruması
        }
    }

    private void hologramSilKomutla(String holoIsim) {
        if (holoIsim == null) return;
        try {
            Class<?> apiClass = Class.forName("de.oliver.fancyholograms.api.FancyHologramsAPI");
            Object apiInstance = apiClass.getMethod("get").invoke(null);
            Object managerInstance = apiClass.getMethod("getHologramManager").invoke(apiInstance);
            Object hologramInstance = managerInstance.getClass().getMethod("getHologram", String.class).invoke(managerInstance, holoIsim);
            
            if (hologramInstance != null) {
                Class<?> holoClass = Class.forName("de.oliver.fancyholograms.api.hologram.Hologram");
                managerInstance.getClass().getMethod("removeHologram", holoClass).invoke(managerInstance, hologramInstance);
            }
        } catch (Exception ignored) {}
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
