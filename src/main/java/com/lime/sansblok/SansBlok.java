package com.lime.sansblok;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
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

    // Arkadaşının eklentisindeki 3 zorunlu orijinal Map yapısı
    public Map<Location, Integer> activeBlocks = new HashMap<>();
    public Map<Location, String> internalHolograms = new HashMap<>(); 
    public Map<UUID, Long> clickCooldown = new HashMap<>();
    
    private final Map<Location, BukkitTask> yenilenmeTasklari = new HashMap<>();
    private final Map<Location, Integer> kalanSureler = new HashMap<>();

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
        hologramGuncelleKomutla(loc, maxCan, maxCan, false);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location loc = block.getLocation();
        Player player = event.getPlayer();

        // Blok ister aktif Şans Bloğu (Sponge) olsun ister yenilenme aşamasında (Bedrock) olsun
        if (activeBlocks.containsKey(loc) || yenilenmeTasklari.containsKey(loc)) {
            // Sadece Creative modundakiler kırınca tamamen kaldırsın
            if (player.getGameMode().name().equals("CREATIVE")) {
                String holoIsim = internalHolograms.remove(loc);
                if (holoIsim != null) {
                    hologramSilKomutla(holoIsim);
                }
                activeBlocks.remove(loc);
                kalanSureler.remove(loc);
                
                BukkitTask task = yenilenmeTasklari.remove(loc);
                if (task != null) {
                    task.cancel();
                }
                player.sendMessage("§a[SansBlok] Şans bloğu ve tüm hologram verileri başarıyla haritadan kaldırıldı!");
            } else {
                // Hayatta kalma modundakiler kıramaz, iptal et
                event.setCancelled(true);
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

        // Sadece tüp mercan veriliyor
        player.getInventory().addItem(new ItemStack(Material.TUBE_CORAL, 1));

        if (kalanCan > 0) {
            activeBlocks.put(loc, kalanCan);
            hologramGuncelleKomutla(loc, kalanCan, maxCan, false);
            
            int progressedStage = (int) (((double) (maxCan - kalanCan) / maxCan) * 9);
            player.sendBlockDamage(loc, (float) progressedStage / 9f);
        } else {
            block.setType(Material.BEDROCK);
            activeBlocks.remove(loc);
            
            bedrockGeriSayimBaslat(loc);
        }
    }

    private void bedrockGeriSayimBaslat(final Location loc) {
        final int toplamSaniye = 1800;
        kalanSureler.put(loc, toplamSaniye);

        hologramGuncelleKomutla(loc, 0, 0, true);

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
                    
                    hologramGuncelleKomutla(loc, maxCan, maxCan, false);
                } else {
                    kalanSureler.put(loc, sure);
                    hologramGuncelleKomutla(loc, 0, 0, true);
                }
            }
        }.runTaskTimer(this, 20L, 20L);

        yenilenmeTasklari.put(loc, task);
    }

    // --- /holo create TABANLI DÜZELTİLMİŞ GECİKMELİ KOMUT SİSTEMİ ---

    private void hologramGuncelleKomutla(Location loc, int kalanCan, int maxCan, boolean bedrockModu) {
        String holoIsim = internalHolograms.get(loc);
        List<String> satirlar = getDinamikSatirlar(loc, kalanCan, maxCan, bedrockModu);

        if (holoIsim == null) {
            final String yeniHoloIsim = "sb-" + (100000 + new Random().nextInt(899999));
            internalHolograms.put(loc, yeniHoloIsim);

            double yukseklik = getConfig().getDouble("sans-blogu.hologram-yükseklik", 2.2);
            double x = loc.getX() + 0.5;
            double y = loc.getY() + yukseklik;
            double z = loc.getZ() + 0.5;
            String dunya = loc.getWorld() != null ? loc.getWorld().getName() : "world";

            // Hologramı oluştur
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "holo create " + yeniHoloIsim);
            
            // Konumlandır
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), String.format(Locale.US, "holo teleport %s %f %f %f %s", yeniHoloIsim, x, y, z, dunya));

            // Hatanın çözümü için gecikmeyi 3 tick'e çektik, eklenti hologramı tamamen kaydettikten sonra satırları ekleyecek.
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (internalHolograms.containsValue(yeniHoloIsim)) {
                        satirlariYaz(yeniHoloIsim, satirlar);
                    }
                }
            }.runTaskLater(this, 3L);

        } else {
            satirlariYaz(holoIsim, satirlar);
        }
    }

    private void satirlariYaz(String holoIsim, List<String> satirlar) {
        // Eski satırların kalıntılarını silmek için komut gönder
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "holo remove_line " + holoIsim + " 1");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "holo remove_line " + holoIsim + " 2");

        // Güvenli şekilde sırayla ekleme yap
        for (String satir : satirlar) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "holo add_line " + holoIsim + " " + satir);
        }
    }

    private void hologramSilKomutla(String holoIsim) {
        if (holoIsim != null) {
            // Önce satır temizliği yapıp sonra hologramı kökten siliyoruz
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "holo remove_line " + holoIsim + " 1");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "holo remove_line " + holoIsim + " 2");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "holo remove " + holoIsim);
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
}
