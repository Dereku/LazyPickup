package club.without.dereku.lazypickup;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

public final class LazyPickup extends JavaPlugin implements Listener {
    @Override
    public void onEnable() {
        this.getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        event.getEntity().setPickupDelay(32767);
    }

    @EventHandler
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof Item)) {
            return;
        }

        final Player player = event.getPlayer();
        final Item rightClicked = (Item) event.getRightClicked();
        final ItemStack itemStack = rightClicked.getItemStack();
        final int canHold = this.canHold(player, itemStack);
        final int remaining = itemStack.getAmount() - canHold;
        final PlayerPickupItemEvent ppie = new PlayerPickupItemEvent(player, rightClicked, remaining);
        ppie.setCancelled(!player.getCanPickupItems());
        this.getServer().getPluginManager().callEvent(ppie);
        if (ppie.isCancelled()) {
            //TODO: Message?
            return;
        }
        if (remaining > 0) {
            final ItemStack clone = new ItemStack(itemStack);
            clone.setAmount(remaining);
            rightClicked.setItemStack(clone);
            itemStack.setAmount(itemStack.getAmount() - remaining);
        } else {
            rightClicked.remove();
        }
        player.getInventory().addItem(itemStack);
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
}
