package club.without.dereku.lazypickup;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public final class LazyPickup extends JavaPlugin implements Listener {

    private static final Object LOCK = new Object();

    @Override
    public void onEnable() {
        this.getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        event.getEntity().setPickupDelay(Short.MAX_VALUE);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL) {
            return;
        }

        if (event.isBlockInHand()) {
            return;
        }

        final Player player = event.getPlayer();
        final Block targetBlock = player.getTargetBlock(null, 8);
        if (targetBlock == null) {
            return;
        }
        final Location blockLocation = targetBlock.getLocation().add(0.5D, 0.5D, 0.5D);
        final Location subtracted = blockLocation.clone().subtract(player.getLocation());
        final List<Entity> nearbyEntities = player.getNearbyEntities(
                this.fixRadius(subtracted.getX()), this.fixRadius(subtracted.getY()), this.fixRadius(subtracted.getZ())
        );
        if (nearbyEntities.isEmpty()) {
            return;
        }
        final LinkedList<Item> nearItems = nearbyEntities.stream()
                .filter(e -> e.getType() == EntityType.DROPPED_ITEM)
                .filter(e -> blockLocation.distance(e.getLocation()) < 1.25D)
                .map(e -> (Item) e).collect(Collectors.toCollection(LinkedList::new));
        if (nearItems.isEmpty()) {
            return;
        }

        Item targetCandidat;
        if (nearItems.size() == 1) {
            targetCandidat = nearItems.getFirst();
        } else {
            nearItems.sort((o1, o2) -> {
                final double distance1 = o1.getLocation().distance(blockLocation);
                final double distance2 = o2.getLocation().distance(blockLocation);
                return Double.compare(distance1, distance2);
            });
            targetCandidat = nearItems.getFirst();
        }

        synchronized (LazyPickup.LOCK) {
            this.proccessPickup(player, targetCandidat);
        }
    }

    private void proccessPickup(Player player, Item item) {
        if (item.isDead()) {
            return;
        }
        final ItemStack itemStack = item.getItemStack();
        final int canHold = this.canHold(player, itemStack);
        final int remaining = itemStack.getAmount() - canHold;
        final PlayerPickupItemEvent ppie = new PlayerPickupItemEvent(player, item, remaining);
        this.getServer().getPluginManager().callEvent(ppie);
        if (ppie.isCancelled()) {
            return;
        }
        if (remaining > 0) {
            final ItemStack clone = new ItemStack(itemStack);
            clone.setAmount(remaining);
            item.setItemStack(clone);
            itemStack.setAmount(itemStack.getAmount() - remaining);
        } else {
            item.remove();
        }
        player.getInventory().addItem(itemStack);
        //noinspection deprecation
        player.updateInventory();
        player.playSound(player.getLocation(), Sound.ITEM_PICKUP, 1.0F, 1.0F);
    }

    private int canHold(Player player, ItemStack itemStack) {
        final PlayerInventory inventory = player.getInventory();
        if (inventory.firstEmpty() != -1) {
            return itemStack.getAmount();
        }

        int remain = itemStack.getAmount();
        for (ItemStack is : inventory.getContents()) {
            if (remain <= 0) {
                remain = 0;
                break;
            }

            if (itemStack.isSimilar(is)) {
                int max = Math.min(itemStack.getMaxStackSize(), inventory.getMaxStackSize());
                remain -= max - is.getAmount();
            }
        }

        return itemStack.getAmount() - remain;
    }

    private double fixRadius(double d) {
        return Math.abs(d) * 2D + 0.75D;
    }
}
