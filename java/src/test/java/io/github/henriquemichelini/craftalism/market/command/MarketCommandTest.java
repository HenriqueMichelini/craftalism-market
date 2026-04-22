package io.github.henriquemichelini.craftalism.market.command;

import io.github.henriquemichelini.craftalism.market.browse.MarketBrowseSnapshotService;
import io.github.henriquemichelini.craftalism.market.gui.MarketGuiService;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketCommandTest {
    @Test
    void rejectsNonPlayerSenders() {
        List<String> messages = new ArrayList<>();
        CommandSender sender = (CommandSender) Proxy.newProxyInstance(
                CommandSender.class.getClassLoader(),
                new Class[]{CommandSender.class},
                (proxy, method, args) -> {
                    if ("sendMessage".equals(method.getName())) {
                        Object payload = args[0];
                        if (payload instanceof String text) {
                            messages.add(text);
                        } else if (payload instanceof Component component) {
                            messages.add(component.toString());
                        }
                        return null;
                    }

                    if (method.getReturnType().isPrimitive()) {
                        return primitiveDefault(method.getReturnType());
                    }
                    return null;
                }
        );

        MarketBrowseSnapshotService snapshotService = new MarketBrowseSnapshotService(
                () -> { throw new AssertionError("snapshot load should not run for non-players"); },
                directExecutor()
        );
        MarketGuiService guiService = new MarketGuiService(
                null,
                snapshotService,
                (itemId, side, quantity, snapshotVersion) -> { throw new AssertionError("quote client should not be used"); },
                (itemId, side, quantity, quoteToken, snapshotVersion) -> { throw new AssertionError("execute client should not be used"); },
                null,
                new io.github.henriquemichelini.craftalism.market.session.MarketSessionRegistry(),
                new org.bukkit.configuration.file.YamlConfiguration()
        );
        MarketCommand command = new MarketCommand(fakePlugin(), snapshotService, guiService);

        boolean handled = command.onCommand(sender, fakeCommand(), "market", new String[0]);

        assertTrue(handled);
        assertEquals(1, messages.size());
        assertTrue(messages.getFirst().contains("Only players can use /market."));
    }

    private Plugin fakePlugin() {
        return (Plugin) Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class[]{Plugin.class},
                (proxy, method, args) -> {
                    if (method.getReturnType().isPrimitive()) {
                        return primitiveDefault(method.getReturnType());
                    }
                    return null;
                }
        );
    }

    private Command fakeCommand() {
        return new Command("market") {
            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                return true;
            }
        };
    }

    private Executor directExecutor() {
        return Runnable::run;
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
}
