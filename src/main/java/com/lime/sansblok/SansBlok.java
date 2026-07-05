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

    // Arkadaşının eklentisindeki zorunlu Map yapıları
    public Map<Location, Integer> activeBlocks = new HashMap<>();
    public Map<Location, String> internalHolograms = new HashMap<>(); // Lokasyona karşılık gelen hologram isimleri
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
        hologramOlusturVeGuncelleKomutla(loc, maxCan, maxCan, false);
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

        vurusOduluVer(player);

        if (kalanCan > 0) {
            activeBlocks.put(loc, kalanCan);
            hologramOlusturVeGuncelleKomutla(loc, kalanCan, maxCan, false);
            player.sendMessage("§7Bloğun kalan direnci: §e" + kalanCan);
            
            int progressedStage = (int) (((double) (maxCan - kalanCan) / maxCan) * 9);
            player.sendBlockDamage(loc, (float) progressedStage / 9f);
        } else {
            block.setType(Material.BEDROCK);
            activeBlocks.remove(loc);
            
            anaOdulVer(player, loc);
            bedrockGeriSayimBaslat(loc);
        }
    }

    private void vurusOduluVer(Player player) {
        ItemStack coin = new ItemStack(Material.TUBE_CORAL, 1);
        player.getInventory().addItem(coin);
        player.sendMessage("§a1x sansblogu coin aldın");
    }

    private void bedrockGeriSayimBaslat(final Location loc) {
        final int toplamSaniye = 1800;
        kalanSureler.put(loc, toplamSaniye);

        hologramOlusturVeGuncelleKomutla(loc, 0, 0, true);

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
                    
                    hologramOlusturVeGuncelleKomutla(loc, maxCan, maxCan, false);
                } else {
                    kalanSureler.put(loc, sure);
                    hologramOlusturVeGuncelleKomutla(loc, 0, 0, true);
                }
            }
        }.runTaskTimer(this, 20L, 20L);

        yenilenmeTasklari.put(loc, task);
    }

    // --- KOMUT TABANLI GARANTİLİ HOLOGRAM YÖNETİMİ ---

    private void hologramOlusturVeGuncelleKomutla(Location loc, int kalanCan, int maxCan, boolean bedrockModu) {
        String holoIsim = internalHolograms.get(loc);
        
        // Eğer bu lokasyonda önceden kalan bir hologram adı yoksa yeni isim üret ve sıfırdan oluştur
        if (holoIsim == null) {
            holoIsim = "sb-" + UUID.randomUUID().toString().substring(0, 6);
            internalHolograms.put(loc, holoIsim);
            
            double yukseklik = getConfig().getDouble("sans-blogu.hologram-yükseklik", 2.2);
            double x = loc.getX() + 0.5;
            double y = loc.getY() + yukseklik;
            double z = loc.getZ() + 0.5;
            String dunya = loc.getWorld() != null ? loc.getWorld().getName() : "world";

            // 1. Adım: Hologramı Dünyada Yarat
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), String.format("hologram create %s", holoIsim));
            // 2. Adım: Doğru Konuma Taşı
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), String.format("hologram teleport %s %f %f %f %s", holoIsim, x, y, z, dunya));
        }

        // Satırları güncelle (Her güncellemede eski satırlar tamamen uçurulup yenileri basılır)
        List<String> satirlar = getDinamikSatirlar(loc, kalanCan, maxCan, bedrockModu);
        
        // 3. Adım: Mevcut satırları temizle ve sırayla yeni metinleri bas
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), String.format("hologram remove_line %s 1", holoIsim));
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), String.format("hologram remove_line %s 2", holoIsim));

        for (String satir : satirlar) {
            // FancyHolograms komut formatına göre satır ekleme işlemi yapılıyor
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), String.format("hologram add_line %s %s", holoIsim, satir));
        }
    }

    private void hologramSilKomutla(String holoIsim) {
        if (holoIsim != null) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), String.format("hologram remove %s", holoIsim));
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
