package com.example.ui;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.PacketType;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;

public class CursorMenuPlugin extends JavaPlugin {

    private ProtocolManager protocolManager;
    private final Map<Player, ArmorStand> playerCursors = new HashMap<>();
    private final Map<Player, TextDisplay> playerDisplays = new HashMap<>();
    private final Map<Player, ItemDisplay> playerItemDisplays = new HashMap<>();
    private final Map<String, MenuLayout> menuLayouts = new HashMap<>();
    private boolean debugMode;
    private double cursorOffsetYaw;
    private double cursorOffsetPitch;
    private String cameraWorld;
    private double cameraX, cameraY, cameraZ;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        protocolManager = ProtocolLibrary.getProtocolManager();

        // Kiểm tra phiên bản cấu hình
        if (!checkConfigVersion()) {
            getLogger().warning("Config version mismatch. Updating config.yml...");
            updateConfig();
        }

        // Nạp cấu hình
        loadConfig();

        // Đăng ký listener
        registerUseEntityPacketListener();

        getCommand("reloadmenu").setExecutor(new Commands(this));
        getCommand("startmenu").setExecutor(new Commands(this));
        getCommand("stopmenu").setExecutor(new Commands(this));

        getLogger().info("CursorMenuPlugin has been enabled!");
    }

    @Override
    public void onDisable() {
        playerCursors.values().forEach(ArmorStand::remove);
        playerDisplays.values().forEach(TextDisplay::remove);
        playerItemDisplays.values().forEach(ItemDisplay::remove);
        getLogger().info("CursorMenuPlugin has been disabled!");
    }

    private boolean checkConfigVersion() {
        String currentVersion = getConfig().getString("version", "1.1");
        String expectedVersion = "1.1";
        return currentVersion.equals(expectedVersion);
    }

    private void updateConfig() {
        getConfig().set("version", "1.1");

        if (!getConfig().contains("menu.cursor-offset")) {
            getConfig().set("menu.cursor-offset.yaw", 0);
            getConfig().set("menu.cursor-offset.pitch", 1);
        }

        if (!getConfig().contains("menu.camera-position")) {
            getConfig().set("menu.camera-position.world", "world");
            getConfig().set("menu.camera-position.x", 0);
            getConfig().set("menu.camera-position.y", 2);
            getConfig().set("menu.camera-position.z", 0);
        }

        if (!getConfig().contains("menu.layout")) {
            ConfigurationSection layoutSection = getConfig().createSection("menu.layout");
            ConfigurationSection layout1 = layoutSection.createSection("layout1");
            layout1.set("name", "Start");
            layout1.set("MinYaw", 0);
            layout1.set("MaxYaw", 20);
            layout1.set("MinPitch", 1);
            layout1.set("MaxPitch", 10);
            layout1.set("command", "say test");
            layout1.set("type", "player");

            ConfigurationSection layout2 = layoutSection.createSection("layout2");
            layout2.set("name", "Options");
            layout2.set("MinYaw", 21);
            layout2.set("MaxYaw", 40);
            layout2.set("MinPitch", 1);
            layout2.set("MaxPitch", 10);
            layout2.set("command", "say test");
            layout2.set("type", "console");
        }

        saveConfig();
        getLogger().info("Config.yml has been updated to the latest version.");
    }

    public void reloadPluginConfig() {
        reloadConfig(); // Nạp lại cấu hình từ file config.yml

        // Xóa dữ liệu cũ để tránh lỗi khi cập nhật
        playerCursors.clear();
        playerDisplays.clear();
        playerItemDisplays.clear();
        menuLayouts.clear();

        // Nạp lại các giá trị mới từ cấu hình
        loadConfig();

        getLogger().info("Config has been successfully reloaded.");
    }

    private void loadConfig() {
        debugMode = getConfig().getBoolean("Debug", false);
        cursorOffsetYaw = getConfig().getDouble("menu.cursor-offset.yaw", 0);
        cursorOffsetPitch = getConfig().getDouble("menu.cursor-offset.pitch", 1);

        cameraWorld = getConfig().getString("menu.camera-position.world", "world");
        cameraX = getConfig().getDouble("menu.camera-position.x", 0);
        cameraY = getConfig().getDouble("menu.camera-position.y", 2);
        cameraZ = getConfig().getDouble("menu.camera-position.z", 0);

        ConfigurationSection layoutSection = getConfig().getConfigurationSection("menu.layout");
        if (layoutSection != null) {
            for (String key : layoutSection.getKeys(false)) {
                ConfigurationSection layout = layoutSection.getConfigurationSection(key);
                if (layout != null) {
                    String name = layout.getString("name", "Unnamed");
                    double minYaw = layout.getDouble("MinYaw", 0); // Lấy giá trị MinYaw từ config
                    double maxYaw = layout.getDouble("MaxYaw", 0); // Lấy giá trị MaxYaw từ config
                    double minPitch = layout.getDouble("MinPitch", 0); // Lấy giá trị MinPitch từ config
                    double maxPitch = layout.getDouble("MaxPitch", 0); // Lấy giá trị MaxPitch từ config
                    String command = layout.getString("command", "");
                    String type = layout.getString("type", "player");

                    menuLayouts.put(key, new MenuLayout(name, minYaw, maxYaw, minPitch, maxPitch, command, type));
                }
            }
        }

        if (debugMode) {
            getLogger().info("Loaded " + menuLayouts.size() + " menu layouts from config.");
        }
    }

    public void setupCursor(Player player) {
        World world = Bukkit.getWorld(cameraWorld);
        if (world == null) {
            getLogger().warning("World " + cameraWorld + " not found!");
            return;
        }

        // Lấy vị trí từ config và tạo ArmorStand tại đó
        Location location = new Location(world, cameraX, cameraY, cameraZ);
        ArmorStand cursor = spawnCursorArmorStand(location);
        playerCursors.put(player, cursor);

        TextDisplay textDisplay = spawnCursorTextDisplay(location);
        playerDisplays.put(player, textDisplay);

        ItemDisplay itemDisplay = spawnCursorItemDisplay(location);
        playerItemDisplays.put(player, itemDisplay);

        // Gửi camera packet đến ArmorStand
        sendCameraPacket(player, cursor);

        // Mount player to cursor (ArmorStand)
        mountPlayerToVehicle(player, cursor);

        // Ẩn player và không cho va chạm
        player.setInvisible(true);
        player.setCollidable(false);

        if (debugMode) {
            player.sendMessage(ChatColor.GREEN + "Cursor menu activated!");
        }
    }

    public void stopCursor(Player player) {
        ArmorStand cursor = playerCursors.remove(player);
        if (cursor != null) {
            cursor.remove();
        }

        TextDisplay textDisplay = playerDisplays.remove(player);
        if (textDisplay != null) {
            textDisplay.remove();
        }

        ItemDisplay itemDisplay = playerItemDisplays.remove(player);
        if (itemDisplay != null) {
            itemDisplay.remove();
        }

        if (debugMode) {
            player.sendMessage(ChatColor.RED + "Cursor menu deactivated!");
        }
    }

    private ArmorStand spawnCursorArmorStand(Location location) {
        ArmorStand armorStand = location.getWorld().spawn(location, ArmorStand.class);
        armorStand.setGravity(false);
        armorStand.setVisible(false);
        armorStand.setMarker(true);  // Giữ cho ArmorStand như một "dấu hiệu"
        return armorStand;
    }

    private TextDisplay spawnCursorTextDisplay(Location location) {
        TextDisplay textDisplay = location.getWorld().spawn(location, TextDisplay.class);
        textDisplay.setCustomName("Cursor: Yaw 0 | Pitch 0");
        textDisplay.setCustomNameVisible(true);
        textDisplay.setBillboard(Display.Billboard.CENTER);
        return textDisplay;
    }

    private ItemDisplay spawnCursorItemDisplay(Location location) {
        ItemDisplay itemDisplay = location.getWorld().spawn(location, ItemDisplay.class);
        itemDisplay.setItemStack(new ItemStack(Material.ARROW));
        itemDisplay.setBillboard(Display.Billboard.CENTER);
        itemDisplay.setRotation(location.getYaw(), location.getPitch());
        return itemDisplay;
    }

    private void sendCameraPacket(Player player, Entity entity) {
        try {
            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.CAMERA);
            packet.getIntegers().write(0, entity.getEntityId());
            protocolManager.sendServerPacket(player, packet);
        } catch (Exception e) {
            getLogger().warning("Failed to send camera packet: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void mountPlayerToVehicle(Player player, Entity entity) {
        protocolManager.addPacketListener(new PacketAdapter(this, PacketType.Play.Client.LOOK) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                Player player = event.getPlayer();
                if (!playerCursors.containsKey(player)) return;

                float yaw = event.getPacket().getFloat().read(0); // Giá trị yaw
                float pitch = event.getPacket().getFloat().read(1); // Giá trị pitch

                yaw = normalizeYaw(yaw + (float) cursorOffsetYaw);
                pitch = clampPitch(pitch + (float) cursorOffsetPitch, -90, 90);

                updateCursorPosition(player, yaw, pitch);

                if (debugMode) {
                    player.sendMessage(ChatColor.YELLOW + String.format("Yaw: %.1f, Pitch: %.1f", yaw, pitch));
                }
                event.setCancelled(true);
            }
        });
    }

    private void updateCursorPosition(Player player, float yaw, float pitch) {
        TextDisplay textDisplay = playerDisplays.get(player);
        ItemDisplay itemDisplay = playerItemDisplays.get(player);
        if (textDisplay == null || itemDisplay == null) return;

        // Cập nhật tên hiển thị với giá trị yaw và pitch
        textDisplay.setCustomName(String.format("Cursor: Yaw %.1f | Pitch %.1f", yaw, pitch));

        // Cập nhật hướng của item display
        itemDisplay.setRotation(yaw, pitch);

        // Cập nhật vị trí của item display
        ArmorStand cursor = playerCursors.get(player);
        if (cursor != null) {
            Location cursorLocation = cursor.getLocation();
            cursorLocation.setYaw(yaw);
            cursorLocation.setPitch(pitch);

            Vector direction = cursorLocation.getDirection();
            Location itemLocation = cursorLocation.add(direction.multiply(0.5)); // Đẩy item display ra trước
            itemDisplay.teleport(itemLocation);
        }
    }

    private float normalizeYaw(float yaw) {
        while (yaw < -180) yaw += 360;
        while (yaw > 180) yaw -= 360;
        return yaw;
    }

    private float clampPitch(float pitch, float min, float max) {
        return Math.max(min, Math.min(max, pitch));
    }

    private void registerUseEntityPacketListener() {
        protocolManager.addPacketListener(new PacketAdapter(this, PacketType.Play.Client.USE_ENTITY) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                Player player = event.getPlayer();
                if (!playerCursors.containsKey(player)) return;

                PacketContainer packet = event.getPacket();
                int entityId = packet.getIntegers().read(0);
                Entity targetEntity = null;
                for (World world : Bukkit.getWorlds()) {
                    targetEntity = world.getEntities().stream().filter(e -> e.getEntityId() == entityId).findFirst().orElse(null);
                    if (targetEntity != null) break;
                }

                if (targetEntity != null) {
                    if (debugMode) {
                        player.sendMessage(ChatColor.GREEN + "You interacted with: " + targetEntity.getType());
                    }

                    // Additional logic for interaction
                }
            }
        });
    }

    private static class MenuLayout {
        String name;
        double minYaw;
        double maxYaw;
        double minPitch;
        double maxPitch;
        String command;
        String type;

        public MenuLayout(String name, double minYaw, double maxYaw, double minPitch, double maxPitch, String command, String type) {
            this.name = name;
            this.minYaw = minYaw;
            this.maxYaw = maxYaw;
            this.minPitch = minPitch;
            this.maxPitch = maxPitch;
            this.command = command;
            this.type = type;
        }
    }
}
