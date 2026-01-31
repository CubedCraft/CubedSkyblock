package com.iridium.iridiumskyblock.listeners;

import com.iridium.iridiumcore.utils.StringUtils;
import com.iridium.iridiumskyblock.IridiumSkyblock;
import com.iridium.iridiumskyblock.database.Island;
import com.iridium.iridiumskyblock.database.LostItems;
import com.iridium.iridiumskyblock.database.User;
import com.iridium.iridiumskyblock.managers.IslandRegionManager;
import com.iridium.iridiumskyblock.utils.LocationUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

import com.iridium.iridiumskyblock.enhancements.VoidEnhancementData;

public class PlayerMoveListener implements Listener {

    // Cooldown map to prevent message spam (player UUID -> last message time)
    private final Map<UUID, Long> messageCooldowns = new HashMap<>();
    private static final long MESSAGE_COOLDOWN_MS = 2000; // 2 seconds between messages

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only check if player actually moved blocks (not just head movement)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        User user = IridiumSkyblock.getInstance().getUserManager().getUser(player);

        // Check if they're in a skyblock world
        if (!IridiumSkyblock.getInstance().getIslandManager().isInSkyblockWorld(event.getTo().getWorld())) {
            return;
        }

        Island island;

        // Check for WorldGuard region exit if enabled
        Optional<Island> islandOp = user.getCurrentIsland();
        if (islandOp.isPresent()) {
            island = islandOp.get();
        } else if (user.getIsland().isPresent()) {
            island = user.getIsland().get();
        } else {
            return;
        }


        IslandRegionManager regionManager = IridiumSkyblock.getInstance().getIslandRegionManager();
        if (regionManager != null && IridiumSkyblock.getInstance().getConfiguration().useWorldGuardRegions) {
            if ( (event.getPlayer().getLocation().getY() < LocationUtils.getMinHeight(event.getPlayer().getWorld())) || (regionManager.isExitingIslandRegion(player, event.getFrom(), event.getTo()))) {
                VoidEnhancementData voidEnhancementData = IridiumSkyblock.getInstance()
                        .getEnhancements().voidEnhancement.levels
                        .get(IridiumSkyblock.getInstance().getTeamManager().getTeamEnhancement(island, "void").getLevel());

                if (voidEnhancementData == null || !voidEnhancementData.enabled) return;

                if (!IridiumSkyblock.getInstance().getTeamManager().teleport(event.getPlayer(), event.getPlayer().getLocation(), island)) return;

                event.getPlayer().sendMessage(StringUtils.color(IridiumSkyblock.getInstance().getMessages().voidTeleport
                        .replace("%prefix%", IridiumSkyblock.getInstance().getConfiguration().prefix)));

                if (voidEnhancementData.itemLossChance <= 0) return;

                ArrayList<ItemStack> lostItems = new ArrayList<>();
                for (ItemStack item : event.getPlayer().getInventory().getContents()) {
                    if (item == null) continue;

                    ItemStack originalItem = item.clone();

                    int lostAmount = 0;
                    for (int i = 0; i < item.getAmount(); i++) {
                        if (Math.random() * 100 <= voidEnhancementData.itemLossChance) {
                            lostAmount++;
                        }
                    }

                    if (lostAmount == 0) continue;

                    int newAmount = originalItem.getAmount() - lostAmount;
                    item.setAmount(newAmount);

                    originalItem.setAmount(lostAmount);
                    lostItems.add(originalItem);
                }

                IridiumSkyblock.getInstance().getDatabaseManager().getLostItemsTableManager().addEntry(new LostItems(
                        event.getPlayer().getUniqueId(), lostItems.toArray(new ItemStack[0])));

                event.getPlayer().sendMessage(StringUtils.color(IridiumSkyblock.getInstance()
                        .getMessages().voidLostItems
                        .replace("%prefix%", IridiumSkyblock.getInstance().getConfiguration().prefix)
                        .replace("%items%", lostItems.stream()
                                .map(item -> IridiumSkyblock.getInstance().getMessages().itemsString
                                        .replace("%amount%", String.valueOf(item.getAmount()))
                                        .replace("%item_name%", item.getItemMeta().hasDisplayName() ? item.getItemMeta().getDisplayName() : "%type%")
                                        .replace("%type%", item.getType().name().trim().replace("_", " ")))
                                .collect(Collectors.joining(", ")))
                ));
            }
        }
    }
}