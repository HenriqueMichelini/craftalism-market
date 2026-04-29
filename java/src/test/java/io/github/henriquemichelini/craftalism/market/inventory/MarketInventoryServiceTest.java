package io.github.henriquemichelini.craftalism.market.inventory;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketInventoryServiceTest {

    @Test
    void countReadsStorageContentsOnly() {
        MarketInventoryService service = new MarketInventoryService();
        FakeInventory inventory = new FakeInventory(new ItemStack[0]);

        assertEquals(0, service.count(fakePlayer(inventory), Material.WHEAT));
        assertEquals(true, inventory.storageRead.get());
        assertEquals(false, inventory.contentsRead.get());
    }

    @Test
    void removeWritesStorageContentsOnly() {
        MarketInventoryService service = new MarketInventoryService();
        FakeInventory inventory = new FakeInventory(new ItemStack[0]);

        int removed = service.remove(fakePlayer(inventory), Material.WHEAT, 4);

        assertEquals(0, removed);
        assertEquals(true, inventory.storageRead.get());
        assertEquals(true, inventory.storageWritten.get());
        assertEquals(false, inventory.contentsRead.get());
        assertEquals(false, inventory.setItemCalled.get());
    }

    private Player fakePlayer(FakeInventory inventory) {
        PlayerInventory playerInventory = (PlayerInventory) Proxy.newProxyInstance(
            PlayerInventory.class.getClassLoader(),
            new Class[] { PlayerInventory.class },
            (proxy, method, args) -> switch (method.getName()) {
                case "getStorageContents" -> {
                    inventory.storageRead.set(true);
                    yield inventory.storage.clone();
                }
                case "setStorageContents" -> {
                    inventory.storageWritten.set(true);
                    inventory.storage = (ItemStack[]) args[0];
                    yield null;
                }
                case "getContents" -> {
                    inventory.contentsRead.set(true);
                    yield new ItemStack[0];
                }
                case "setItem" -> {
                    inventory.setItemCalled.set(true);
                    yield null;
                }
                default -> primitiveDefault(method.getReturnType());
            }
        );

        return (Player) Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class[] { Player.class },
            (proxy, method, args) -> switch (method.getName()) {
                case "getInventory" -> playerInventory;
                default -> primitiveDefault(method.getReturnType());
            }
        );
    }

    private Object primitiveDefault(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0d;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == char.class) {
            return '\0';
        }
        return null;
    }

    private static final class FakeInventory {
        private ItemStack[] storage;
        private final AtomicBoolean storageRead = new AtomicBoolean();
        private final AtomicBoolean storageWritten = new AtomicBoolean();
        private final AtomicBoolean contentsRead = new AtomicBoolean();
        private final AtomicBoolean setItemCalled = new AtomicBoolean();

        private FakeInventory(ItemStack[] storage) {
            this.storage = storage;
        }
    }
}
