package com.lime.sansblok;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
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

    // Temel haritalar ve takip mekanizmaları
    public final Map<Location, Integer> activeBlocks = new HashMap<>();
    public final Map<Location, String> internalHolograms = new HashMap<>(); 
    public final Map<UUID, Long> clickCooldown = new HashMap<>();
    
    private final Map<Location, BukkitTask> yenilenmeTasklari = new HashMap<>();
    private final Map<Location, Integer> kalanSureler = new HashMap<>();
    private final Map<Location, String> blockTypeCache = new HashMap<>();

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
            String type = blockTypeCache.getOrDefault(loc, "default");
            loc.getBlock().setType(getMaterialFromConfig(type, "material", Material.SPONGE));
        }
        yenilenmeTasklari.clear();
        kalanSureler.clear();
        clickCooldown.clear();

        for (String holoIsim : internalHolograms.values()) {
            hologramSilGarantili(holoIsim);
        }
        internalHolograms.clear();
        activeBlocks.clear();
        blockTypeCache.clear();
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
            sender.sendMessage("§a[SansBlok] Ayarlar yenilendi ve tüm sistemler sıfırlandı!");
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
            String blockType = (args.length > 1) ? args[1] : "default";
            Material mat = getMaterialFromConfig(blockType, "material", Material.SPONGE);
            String displayName = getStringFromConfig(blockType, "display-name", "§e§lŞans Bloğu");

            ItemStack sansBlogu = new ItemStack(mat);
            ItemMeta meta = sansBlogu.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(displayName.replace("&", "§"));
                meta.setLore(Arrays.asList("§7Yere koy ve şansını dene!", "§7Sol tıkla vurarak direnci zorla.", "§8Type: " + blockType));
                sansBlogu.setItemMeta(meta);
            }

            player.getInventory().addItem(sansBlogu);
            player.sendMessage("§aBaşarıyla 1 adet " + displayName + " §aaçık bloğu aldın!");
            return true;
        }

        player.sendMessage("§7Kullanım: §e/sansblok al [blok_tipi] §7veya §e/sansblok reload");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("sansblok") && args.length == 1) {
            List<String> completions = new ArrayList<>();
            if (sender.hasPermission("sansblok.admin")) {
                completions.add("al");
                completions.add("reload");
            }
            return completions;
        } else if (args.length == 2 && args[0].equalsIgnoreCase("al")) {
            List<String> types = new ArrayList<>();
            types.add("default");
            ConfigurationSection sec = getConfig().getConfigurationSection("block-types");
            if (sec != null) {
                types.addAll(sec.getKeys(false));
            }
            return types;
        }
        return Collections.emptyList();
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (!item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName() || !meta.hasLore()) return;

        String blockType = "default";
        for (String line : meta.getLore()) {
            if (line.startsWith("§8Type: ")) {
                blockType = line.replace("§8Type: ", "").trim();
                break;
            }
        }

        Block block = event.getBlock();
        Location loc = block.getLocation();
        
        if (yenilenmeTasklari.containsKey(loc)) {
            event.setCancelled(true);
            return;
        }

        int maxCan = getIntFromConfig(blockType, "max-health", 10);
        activeBlocks.put(loc, maxCan);
        blockTypeCache.put(loc, blockType);
        
        hologramGuncelleGarantili(loc, maxCan, maxCan, false);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location loc = block.getLocation();
        Player player = event.getPlayer();

        if (activeBlocks.containsKey(loc) || yenilenmeTasklari.containsKey(loc)) {
            if (player.getGameMode().name().equals("CREATIVE")) {
                String holoIsim = internalHolograms.remove(loc);
                if (holoIsim != null) hologramSilGarantili(holoIsim);
                activeBlocks.remove(loc);
                kalanSureler.remove(loc);
                blockTypeCache.remove(loc);
                
                BukkitTask task = yenilenmeTasklari.remove(loc);
                if (task != null) task.cancel();
                player.sendMessage("§a[SansBlok] Şans bloğu verileri haritadan tamamen kaldırıldı!");
            } else {
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
        
        long cooldownSuresi = getConfig().getLong("hit-cooldown-seconds", 1) * 1000L; 
        long sonVurus = clickCooldown.getOrDefault(playerUUID, 0L);
        
        if ((simdi - sonVurus) < cooldownSuresi) return; 
        clickCooldown.put(playerUUID, simdi);

        String blockType = blockTypeCache.getOrDefault(loc, "default");
        int kalanCan = activeBlocks.get(loc) - 1;
        int maxCan = getIntFromConfig(blockType, "max-health", 10);

        // Reis istediğin gibi ödüller çöp oldu, SADECE 1 adet tüp mercan geliyor!
        player.getInventory().addItem(new ItemStack(Material.TUBE_CORAL, 1));

        // Ses Efekti Tetikleme
        String sesAdi = getStringFromConfig(blockType, "sounds.on-hit", "BLOCK_NOTE_BLOCK_PLING");
        try { player.playSound(loc, Sound.valueOf(sesAdi), 1.0f, 1.0f); } catch (Exception ignored){}

        if (kalanCan > 0) {
            activeBlocks.put(loc, kalanCan);
            hologramGuncelleGarantili(loc, kalanCan, maxCan, false);
            
            int progressedStage = (int) (((double) (maxCan - kalanCan) / maxCan) * 9);
            player.sendBlockDamage(loc, (float) progressedStage / 9f);
        } else {
            Material brokenMat = getMaterialFromConfig(blockType, "broken-block-material", Material.BEDROCK);
            block.setType(brokenMat);
            activeBlocks.remove(loc);
            
            String breakSes = getStringFromConfig(blockType, "sounds.on-break", "ENTITY_GENERIC_EXPLODE");
            try { player.playSound(loc, Sound.valueOf(breakSes), 1.0f, 1.0f); } catch (Exception ignored){}

            bedrockGeriSayimBaslat(loc, blockType);
        }
    }

    private void bedrockGeriSayimBaslat(final Location loc, final String blockType) {
        int respawnSaniye = getIntFromConfig(blockType, "respawn-time-seconds", 60);
        kalanSureler.put(loc, respawnSaniye);

        hologramGuncelleGarantili(loc, 0, 0, true);

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
                    
                    Material mat = getMaterialFromConfig(blockType, "material", Material.SPONGE);
                    loc.getBlock().setType(mat);
                    
                    int maxCan = getIntFromConfig(blockType, "max-health", 10);
                    activeBlocks.put(loc, maxCan);
                    
                    hologramGuncelleGarantili(loc, maxCan, maxCan, false);
                } else {
                    kalanSureler.put(loc, sure);
                    hologramGuncelleGarantili(loc, 0, 0, true);
                }
            }
        }.runTaskTimer(this, 20L, 20L);

        yenilenmeTasklari.put(loc, task);
    }

    // --- EN COOOL VE LOGSUZ FANCYHOLOGRAMS ENJEKSİYONU ---

    private void hologramGuncelleGarantili(Location loc, int kalanCan, int maxCan, boolean bedrockModu) {
        String holoIsim = internalHolograms.get(loc);
        String blockType = blockTypeCache.getOrDefault(loc, "default");
        List<String> yeniSatirlar = getDinamikSatirlar(loc, kalanCan, maxCan, bedrockModu, blockType);

        try {
            Class<?> apiClass = Class.forName("de.oliver.fancyholograms.api.FancyHologramsAPI");
            Object apiInstance = apiClass.getMethod("get").invoke(null);
            Object managerInstance = apiClass.getMethod("getHologramManager").invoke(apiInstance);

            Class<?> dataClass = Class.forName("de.oliver.fancyholograms.api.data.TextHologramData");
            Class<?> holoClass = Class.forName("de.oliver.fancyholograms.api.hologram.Hologram");

            if (holoIsim == null) {
                holoIsim = "sb-" + (100000 + new Random().nextInt(899999));
                internalHolograms.put(loc, holoIsim);

                double heightOffset = getConfig().getDouble("default-block-settings.hologram.height-offset", 2.5);
                if (!blockType.equals("default") && getConfig().contains("block-types." + blockType + ".hologram.height-offset")) {
                    heightOffset = getConfig().getDouble("block-types." + blockType + ".hologram.height-offset");
                }
                
                Location holoLoc = loc.clone().add(0.5, heightOffset, 0.5);
                Object dataInstance = dataClass.getConstructor(String.class, Location.class).newInstance(holoIsim, holoLoc);

                try {
                    dataClass.getMethod("setLines", List.class).invoke(dataInstance, yeniSatirlar);
                } catch (Exception e) {
                    List<String> lines = (List<String>) dataClass.getMethod("getLines").invoke(dataInstance);
                    lines.clear();
                    lines.addAll(yeniSatirlar);
                }

                Object hologramInstance = apiClass.getMethod("createHologram", Class.forName("de.oliver.fancyholograms.api.data.HologramData")).invoke(apiInstance, dataInstance);
                managerInstance.getClass().getMethod("addHologram", holoClass).invoke(managerInstance, hologramInstance);
            } else {
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
        } catch (Exception ignored) {}
    }

    private void hologramSilGarantili(String holoIsim) {
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

    private List<String> getDinamikSatirlar(Location loc, int kalanCan, int maxCan, boolean bedrockModu, String blockType) {
        List<String> satirlar = new ArrayList<>();
        String path = bedrockModu ? "hologram.broken-lines" : "hologram.active-lines";
        
        List<String> şablonSatirlar = getStringListFromConfig(blockType, path);
        if (şablonSatirlar.isEmpty()) {
            if (bedrockModu) {
                şablonSatirlar = Arrays.asList("&cKırıldı!", "&eYenileniyor: {respawn_timer}");
            } else {
                şablonSatirlar = Arrays.asList("{display_name}", "&7Beni kırmak için vur!", "{health_bar}");
            }
        }

        String displayName = getStringFromConfig(blockType, "display-name", "Şans Bloğu");
        String bar = buildHealthBar(kalanCan, maxCan, blockType);

        int toplamSaniye = kalanSureler.getOrDefault(loc, 60);
        int dakika = toplamSaniye / 60;
        int saniye = toplamSaniye % 60;
        String sureMetni = String.format("%02d:%02d", dakika, saniye);

        for (String s : şablonSatirlar) {
            satirlar.add(s
                .replace("{display_name}", displayName)
                .replace("{health_bar}", bar)
                .replace("{respawn_timer}", sureMetni)
                .replace("{current_health}", String.valueOf(kalanCan))
                .replace("{max_health}", String.valueOf(maxCan))
                .replace("&", "§"));
        }
        return satirlar;
    }

    private String buildHealthBar(int kalan, int max, String type) {
        if (max <= 0) return "";
        int barLength = getConfig().getInt("default-block-settings.hologram.health-bar-length", 10);
        if (!type.equals("default") && getConfig().contains("block-types." + type + ".hologram.health-bar-length")) {
            barLength = getConfig().getInt("block-types." + type + ".hologram.health-bar-length");
        }

        String filledChar = getStringFromConfig(type, "hologram.health-bar-filled-segment", "|");
        String emptyChar = getStringFromConfig(type, "hologram.health-bar-empty-segment", "|");

        double oran = (double) kalan / max;
        int filledLength = (int) Math.round(oran * barLength);
        
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < barLength; i++) {
            if (i < filledLength) {
                builder.append(filledChar);
            } else {
                builder.append(emptyChar);
            }
        }
        return builder.toString();
    }

    // --- GÜVENLİ CONFIG OKUMA METOTLARI ---

    private String getStringFromConfig(String type, String subPath, String def) {
        if (!type.equals("default") && getConfig().contains("block-types." + type + "." + subPath)) {
            return getConfig().getString("block-types." + type + "." + subPath);
        }
        return getConfig().getString("default-block-settings." + subPath, def);
    }

    private int getIntFromConfig(String type, String subPath, int def) {
        if (!type.equals("default") && getConfig().contains("block-types." + type + "." + subPath)) {
            return getConfig().getInt("block-types." + type + "." + subPath);
        }
        return getConfig().getInt("default-block-settings." + subPath, def);
    }

    private Material getMaterialFromConfig(String type, String subPath, Material def) {
        String name = getStringFromConfig(type, subPath, def.name());
        try { return Material.valueOf(name.toUpperCase()); } catch (Exception e) { return def; }
    }

    private List<String> getStringListFromConfig(String type, String subPath) {
        if (!type.equals("default") && getConfig().contains("block-types." + type + "." + subPath)) {
            return getConfig().getStringList("block-types." + type + "." + subPath);
        }
        return getConfig().getStringList("default-block-settings." + subPath);
    }
}
