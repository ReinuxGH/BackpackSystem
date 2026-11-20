package com.oscar.backpacks;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class BackpackHolder implements InventoryHolder {
    private final UUID backpackId;
    private Inventory inventory;
    private int page;

    public BackpackHolder(UUID backpackId) { this.backpackId = backpackId; }
    public UUID backpackId() { return backpackId; }
    public int page() { return page; }
    public void page(int page) { this.page = page; }
    public void inventory(Inventory inventory) { this.inventory = inventory; }
    @Override public Inventory getInventory() { return inventory; }
}
