package io.papermc.thisisarandompluginr;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.java.JavaPlugin;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;

public class ElevatorPlugin extends JavaPlugin implements Listener {

    private final String TARGET_ELEVATOR_KEY = "televator:televator";
    private final int MAX_SEARCH_DISTANCE = 64;
    private final int TELEPORT_COOLDOWN_MS = 1000; // 1 second cooldown

    // Prevent spam and accidental teleports
    private final HashMap<UUID, Long> lastTeleportTimes = new HashMap<>();
    
    // Whitelist for passable blocks
    private static final Set<Material> PASSABLE_BLOCKS = new HashSet<>();
    static {
        PASSABLE_BLOCKS.add(Material.AIR);
        PASSABLE_BLOCKS.add(Material.CAVE_AIR);
        PASSABLE_BLOCKS.add(Material.WATER);
        PASSABLE_BLOCKS.add(Material.LAVA);
        // Add other safe materials (like signs, tall grass) if desired
    }

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Elevator Plugin safely active. Tracking block: " + TARGET_ELEVATOR_KEY);
    }

    @Override
    public void onDisable() {
        lastTeleportTimes.clear();
    }

    private boolean isElevatorBlock(Block block) {
        if (block == null) return false;
        String blockDataString = block.getBlockData().getAsString();
        return blockDataString.startsWith(TARGET_ELEVATOR_KEY) || blockDataString.startsWith("minecraft:" + TARGET_ELEVATOR_KEY);
    }

    private boolean isSafeBlock(Block block) {
        return PASSABLE_BLOCKS.contains(block.getType());
    }

    // --- GO UP ---
    @EventHandler
    public void onPlayerJump(PlayerJumpEvent event) {
        Player player = event.getPlayer();
        Block blockUnder = player.getLocation().getBlock().getRelative(0, -1, 0);

        if (isElevatorBlock(blockUnder)) {
            tryTeleport(player, blockUnder, true);
        }
    }

    // --- GO DOWN ---
    @EventHandler
    public void onPlayerCrouch(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        Player player = event.getPlayer();
        if (!player.isOnGround()) return;
        
        Block blockUnder = player.getLocation().getBlock().getRelative(0, -1, 0);

        if (isElevatorBlock(blockUnder)) {
            tryTeleport(player, blockUnder, false);
        }
    }

    // --- TELEPORTATION LOGIC ---
    private void tryTeleport(Player player, Block startBlock, boolean goUp) {
        // Cooldown check
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        if (lastTeleportTimes.containsKey(playerId)) {
            if (currentTime - lastTeleportTimes.get(playerId) < TELEPORT_COOLDOWN_MS) {
                return; // Silently abort if within cooldown
            }
        }

        Location destination = findElevatorBlock(startBlock, goUp);
        
        if (destination != null) {
            lastTeleportTimes.put(playerId, currentTime);

            Location playerLoc = player.getLocation();
            destination.setYaw(playerLoc.getYaw());
            destination.setPitch(playerLoc.getPitch());
            
            player.teleport(destination);
            float pitch = goUp ? 1.2f : 0.8f;
            player.playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, pitch);
        }
    }

    // --- FLOOR SEARCH ---
    private Location findElevatorBlock(Block startBlock, boolean goUp) {
        int direction = goUp ? 1 : -1;
        Block current = startBlock;

        for (int i = 1; i < MAX_SEARCH_DISTANCE; i++) {
            current = current.getRelative(0, direction, 0);

            if (isElevatorBlock(current)) {
                Block safe1 = current.getRelative(0, 1, 0);
                Block safe2 = current.getRelative(0, 2, 0);

                if (isSafeBlock(safe1) && isSafeBlock(safe2)) {
                    return current.getLocation().add(0.5, 1.0, 0.5);
                }
            }
        }
        return null;
    }

    // --- MEMORY CLEANUP ---
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastTeleportTimes.remove(event.getPlayer().getUniqueId());
    }
}
