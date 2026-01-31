package com.iridium.iridiumskyblock.listeners;

import com.iridium.iridiumskyblock.IridiumSkyblock;
import com.iridium.iridiumskyblock.configs.Schematics;
import com.iridium.iridiumskyblock.database.Island;
import com.iridium.iridiumskyblock.database.User;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Optional;

public class PlayerPortalListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();

        if (!IridiumSkyblock.getInstance().getIslandManager().isInSkyblockWorld(from.getWorld())) {
            return;
        }

        // ALWAYS resolve island from LOCATION (never cache)
        Optional<Island> islandOptional =
                IridiumSkyblock.getInstance().getIslandManager().getTeamViaLocation(from);

        if (!islandOptional.isPresent()) {
            event.setCancelled(true);
            player.sendMessage("no");
            return;
        }

        Island island = islandOptional.get();
        World targetWorld = null;
        Location destination = null;

        if (event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            destination = handleNetherPortal(event, island);
        } else if (event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            destination = handleEndPortal(event, island);
        }

        if (destination == null) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        Location finalDestination = destination;
        Bukkit.getScheduler().runTaskLater(IridiumSkyblock.getInstance(), () -> {
            if (!player.isOnline()) return;

            player.teleport(finalDestination);

            // Border update AFTER teleport
            Bukkit.getScheduler().runTaskLater(IridiumSkyblock.getInstance(), () -> {
                if (player.isOnline()) {
                    IridiumSkyblock.getInstance().getIslandManager().sendIslandBorder(player);
                }
            }, 5L);
        }, 1L);
    }

    /* ==================== NETHER ==================== */

    private Location handleNetherPortal(PlayerPortalEvent event, Island island) {
        Location from = event.getFrom();
        World fromWorld = from.getWorld();

        String overworld = IridiumSkyblock.getInstance().getConfiguration().worldName;
        String nether = overworld + "_nether";

        if (!IridiumSkyblock.getInstance().getConfiguration()
                .enabledWorlds.getOrDefault(World.Environment.NETHER, true)) {
            event.getPlayer().sendMessage(
                    IridiumSkyblock.getInstance().getMessages().netherIslandsDisabled);
            return null;
        }

        if (island.getLevel() <
                IridiumSkyblock.getInstance().getConfiguration().netherUnlockLevel) {
            event.getPlayer().sendMessage(
                    IridiumSkyblock.getInstance().getMessages().netherLocked
                            .replace("%level%",
                                    String.valueOf(IridiumSkyblock.getInstance()
                                            .getConfiguration().netherUnlockLevel)));
            return null;
        }

        if (fromWorld.getName().equals(overworld)) {
            World target = Bukkit.getWorld(nether);
            if (target == null) return null;
            ensureNetherIslandExists(island, target);
            return calculateDestination(from, island, target);
        }

        if (fromWorld.getName().equals(nether)) {
            World target = Bukkit.getWorld(overworld);
            return target == null ? null : calculateDestination(from, island, target);
        }

        return null;
    }

    private void ensureNetherIslandExists(Island island, World netherWorld) {
        Location center = island.getCenter(netherWorld);
        // Check if island exists by looking at the center block
        if (center.getBlock().getType().isAir()) {
            // Island doesn't exist, try to paste it
            Bukkit.getScheduler().runTask(IridiumSkyblock.getInstance(), () -> {
                try {
                    // Get the island's schematic config
                    IridiumSkyblock.getInstance()
                            .getIslandManager()
                            .getTeamViaID(island.getId())
                            .ifPresent(team -> {
                                // Find which schematic this island uses
                                // Since we can't easily determine the exact schematic,
                                // we'll regenerate using the default schematic
                                Schematics.SchematicConfig schematic = IridiumSkyblock.getInstance()
                                        .getSchematics()
                                        .schematics
                                        .values()
                                        .stream()
                                        .findFirst()
                                        .orElse(null);

                                if (schematic != null && schematic.nether != null) {
                                    IridiumSkyblock.getInstance()
                                            .getSchematicManager()
                                            .pasteSchematic(island, schematic.nether, netherWorld);
                                    IridiumSkyblock.getInstance().getLogger()
                                            .info("Auto-generated missing nether island for " + island.getName());
                                }
                            });
                } catch (Exception e) {
                    IridiumSkyblock.getInstance().getLogger()
                            .warning("Failed to auto-generate nether island: " + e.getMessage());
                }
            });
        }
    }

    private void ensureEndIslandExists(Island island, World endWorld) {
        Location center = island.getCenter(endWorld);
        // Check if island exists by looking at the center block
        if (center.getBlock().getType().isAir()) {
            // Island doesn't exist, try to paste it
            Bukkit.getScheduler().runTask(IridiumSkyblock.getInstance(), () -> {
                try {
                    IridiumSkyblock.getInstance()
                            .getIslandManager()
                            .getTeamViaID(island.getId())
                            .ifPresent(team -> {
                                Schematics.SchematicConfig schematic = IridiumSkyblock.getInstance()
                                        .getSchematics()
                                        .schematics
                                        .values()
                                        .stream()
                                        .findFirst()
                                        .orElse(null);

                                if (schematic != null && schematic.end != null) {
                                    IridiumSkyblock.getInstance()
                                            .getSchematicManager()
                                            .pasteSchematic(island, schematic.end, endWorld);
                                    IridiumSkyblock.getInstance().getLogger()
                                            .info("Auto-generated missing end island for " + island.getName());
                                }
                            });
                } catch (Exception e) {
                    IridiumSkyblock.getInstance().getLogger()
                            .warning("Failed to auto-generate end island: " + e.getMessage());
                }
            });
        }
    }

    /* ==================== END ==================== */

    private Location handleEndPortal(PlayerPortalEvent event, Island island) {
        Location from = event.getFrom();
        World fromWorld = from.getWorld();

        String overworld = IridiumSkyblock.getInstance().getConfiguration().worldName;
        String end = overworld + "_the_end";

        if (!IridiumSkyblock.getInstance().getConfiguration()
                .enabledWorlds.getOrDefault(World.Environment.THE_END, true)) {
            event.getPlayer().sendMessage(
                    IridiumSkyblock.getInstance().getMessages().endIslandsDisabled);
            return null;
        }

        if (island.getLevel() <
                IridiumSkyblock.getInstance().getConfiguration().endUnlockLevel) {
            event.getPlayer().sendMessage(
                    IridiumSkyblock.getInstance().getMessages().endLocked
                            .replace("%level%",
                                    String.valueOf(IridiumSkyblock.getInstance()
                                            .getConfiguration().endUnlockLevel)));
            return null;
        }

        if (fromWorld.getName().equals(end)) {
            return island.getHome();
        }

        World target = Bukkit.getWorld(end);
        if (target == null) return null;

        ensureEndIslandExists(island, target);
        return calculateDestination(from, island, target);
    }

    /* ==================== DESTINATION ==================== */

    private Location calculateDestination(Location from, Island island, World targetWorld) {
        Location fromCenter = island.getCenter(from.getWorld());
        Location toCenter = island.getCenter(targetWorld);

        // Check if target island has a portal - if so, spawn there
        Location portalLocation = findPortalLocation(island, targetWorld);
        if (portalLocation != null) {
            return portalLocation;
        }

        double dx = from.getX() - fromCenter.getX();
        double dz = from.getZ() - fromCenter.getZ();

        Location dest = new Location(
                targetWorld,
                toCenter.getX() + dx,
                64,
                toCenter.getZ() + dz
        );

        return findSafeLocation(dest, island);
    }

    private Location findPortalLocation(Island island, World world) {
        Location center = island.getCenter(world);
        // Search around the center for portals within island bounds
        int searchRadius = Math.min(island.getSize() / 2, 50); // Cap at 50 blocks

        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int z = -searchRadius; z <= searchRadius; z++) {
                int blockX = center.getBlockX() + x;
                int blockZ = center.getBlockZ() + z;

                if (!island.isInIsland(blockX, blockZ)) continue;

                for (int y = world.getMaxHeight() - 1; y >= world.getMinHeight(); y--) {
                    if (world.getBlockAt(blockX, y, blockZ).getType().name().contains("PORTAL")) {
                        // Found a portal, find safe spawn location next to it
                        Location portalLoc = new Location(world, blockX + 0.5, y, blockZ + 0.5);
                        Location safeSpawn = findSafeSpawnNearPortal(portalLoc, island);
                        if (safeSpawn != null) {
                            return safeSpawn;
                        }
                    }
                }
            }
        }
        return null;
    }

    private Location findSafeSpawnNearPortal(Location portalLoc, Island island) {
        World w = portalLoc.getWorld();
        int px = portalLoc.getBlockX();
        int py = portalLoc.getBlockY();
        int pz = portalLoc.getBlockZ();

        // Check positions around the portal (N, S, E, W)
        int[][] offsets = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
        for (int[] offset : offsets) {
            int x = px + offset[0];
            int z = pz + offset[1];

            if (!island.isInIsland(x, z)) continue;

            // Check at portal height and one below
            for (int y = py; y >= py - 1; y--) {
                if (w.getBlockAt(x, y, z).getType().isSolid()
                        && !w.getBlockAt(x, y + 1, z).getType().isSolid()
                        && !w.getBlockAt(x, y + 2, z).getType().isSolid()) {
                    return new Location(w, x + 0.5, y + 1, z + 0.5);
                }
            }
        }
        return null;
    }

    private Location findSafeLocation(Location loc, Island island) {
        World w = loc.getWorld();
        int centerX = loc.getBlockX();
        int centerZ = loc.getBlockZ();

        // First try the exact location
        Location safeLoc = findSafeLocationAtXZ(w, centerX, centerZ, island);
        if (safeLoc != null) return safeLoc;

        // Search in expanding squares around the target location
        for (int radius = 1; radius <= 5; radius++) {
            // Top edge
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                safeLoc = findSafeLocationAtXZ(w, x, centerZ - radius, island);
                if (safeLoc != null) return safeLoc;
            }
            // Bottom edge
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                safeLoc = findSafeLocationAtXZ(w, x, centerZ + radius, island);
                if (safeLoc != null) return safeLoc;
            }
            // Left edge (excluding corners)
            for (int z = centerZ - radius + 1; z <= centerZ + radius - 1; z++) {
                safeLoc = findSafeLocationAtXZ(w, centerX - radius, z, island);
                if (safeLoc != null) return safeLoc;
            }
            // Right edge (excluding corners)
            for (int z = centerZ - radius + 1; z <= centerZ + radius - 1; z++) {
                safeLoc = findSafeLocationAtXZ(w, centerX + radius, z, island);
                if (safeLoc != null) return safeLoc;
            }
        }

        // Fallback to island home if no safe location found
        Location home = island.getHome();
        if (home != null && home.getWorld().equals(w)) {
            return home;
        }

        // Final fallback to island center at y=64
        return island.getCenter(w).clone().add(0, 64, 0);
    }

    private Location findSafeLocationAtXZ(World w, int x, int z, Island island) {
        if (!island.isInIsland(x, z)) return null;

        for (int y = w.getMaxHeight() - 1; y >= w.getMinHeight(); y--) {
            if (w.getBlockAt(x, y, z).getType().isSolid()
                    && !w.getBlockAt(x, y + 1, z).getType().isSolid()
                    && !w.getBlockAt(x, y + 2, z).getType().isSolid()) {
                return new Location(w, x + 0.5, y + 1, z + 0.5);
            }
        }
        return null;
    }
}
