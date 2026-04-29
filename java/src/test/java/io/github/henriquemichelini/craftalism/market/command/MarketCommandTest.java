package io.github.henriquemichelini.craftalism.market.command;

import io.github.henriquemichelini.craftalism.market.browse.MarketBrowseSnapshotService;
import io.github.henriquemichelini.craftalism.market.gui.MarketGuiService;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.CompletionException;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

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
                (ignoredPlayerId, itemId, side, quantity, snapshotVersion) -> { throw new AssertionError("quote client should not be used"); },
                (ignoredPlayerId, itemId, side, quantity, quoteToken, snapshotVersion) -> { throw new AssertionError("execute client should not be used"); },
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

    @Test
    void noCacheFailureMessagesPlayerAndLogsRootCause() {
        List<String> playerMessages = new ArrayList<>();
        UUID playerId = UUID.randomUUID();
        Player player = fakePlayer(playerId, true, playerMessages);
        YamlConfiguration config = new YamlConfiguration();
        config.set("messages.unavailable-no-cache", "&cNo market cache is available.");
        TestLogger testLogger = new TestLogger();
        MarketBrowseSnapshotService snapshotService = new MarketBrowseSnapshotService(
                () -> { throw new AssertionError("snapshot load should not run"); },
                directExecutor()
        );
        MarketGuiService guiService = new MarketGuiService(
                null,
                snapshotService,
                (ignoredPlayerId, itemId, side, quantity, snapshotVersion) -> { throw new AssertionError("quote client should not be used"); },
                (ignoredPlayerId, itemId, side, quantity, quoteToken, snapshotVersion) -> { throw new AssertionError("execute client should not be used"); },
                null,
                new io.github.henriquemichelini.craftalism.market.session.MarketSessionRegistry(),
                new YamlConfiguration()
        );
        MarketCommand command = new MarketCommand(
                fakePlugin(config, testLogger.logger()),
                snapshotService,
                guiService
        );

        command.handleResult(
                player,
                null,
                new CompletionException(new IllegalStateException("api down"))
        );

        assertEquals(1, playerMessages.size());
        assertTrue(playerMessages.getFirst().contains("No market cache is available."));
        assertTrue(testLogger.messages(Level.WARNING).stream().anyMatch(message ->
                message.contains("Unable to open market for " + playerId)
                        && message.contains("api down")
        ));
    }

    @Test
    void asyncResultForOfflinePlayerIsIgnored() {
        List<String> playerMessages = new ArrayList<>();
        UUID playerId = UUID.randomUUID();
        Player player = fakePlayer(playerId, false, playerMessages);
        TestLogger testLogger = new TestLogger();
        MarketBrowseSnapshotService snapshotService = new MarketBrowseSnapshotService(
                () -> { throw new AssertionError("snapshot load should not run"); },
                directExecutor()
        );
        MarketGuiService guiService = new MarketGuiService(
                null,
                snapshotService,
                (ignoredPlayerId, itemId, side, quantity, snapshotVersion) -> { throw new AssertionError("quote client should not be used"); },
                (ignoredPlayerId, itemId, side, quantity, quoteToken, snapshotVersion) -> { throw new AssertionError("execute client should not be used"); },
                null,
                new io.github.henriquemichelini.craftalism.market.session.MarketSessionRegistry(),
                new YamlConfiguration()
        );
        MarketCommand command = new MarketCommand(
                fakePlugin(new YamlConfiguration(), testLogger.logger()),
                snapshotService,
                guiService
        );

        command.handleResult(
                player,
                null,
                new CompletionException(new IllegalStateException("api down"))
        );

        assertTrue(playerMessages.isEmpty());
        assertTrue(testLogger.messages(Level.WARNING).isEmpty());
    }

    private Plugin fakePlugin() {
        return fakePlugin(new YamlConfiguration(), Logger.getLogger("MarketCommandTest"));
    }

    private Plugin fakePlugin(YamlConfiguration config, Logger logger) {
        return (Plugin) Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class[]{Plugin.class},
                (proxy, method, args) -> {
                    if ("getConfig".equals(method.getName())) {
                        return config;
                    }
                    if ("getLogger".equals(method.getName())) {
                        return logger;
                    }
                    if (method.getReturnType().isPrimitive()) {
                        return primitiveDefault(method.getReturnType());
                    }
                    return null;
                }
        );
    }

    private Player fakePlayer(UUID playerId, boolean online, List<String> messages) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class[]{Player.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getUniqueId" -> {
                            return playerId;
                        }
                        case "isOnline" -> {
                            return online;
                        }
                        case "sendMessage" -> {
                            Object payload = args[0];
                            if (payload instanceof String text) {
                                messages.add(text);
                            } else if (payload instanceof Component component) {
                                messages.add(component.toString());
                            }
                            return null;
                        }
                        default -> {
                            if (method.getReturnType().isPrimitive()) {
                                return primitiveDefault(method.getReturnType());
                            }
                            return null;
                        }
                    }
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

    private static final class TestLogger {
        private final Logger logger = Logger.getAnonymousLogger();
        private final List<LogRecord> records = new ArrayList<>();

        private TestLogger() {
            logger.setUseParentHandlers(false);
            for (Handler handler : logger.getHandlers()) {
                logger.removeHandler(handler);
            }
            logger.addHandler(new Handler() {
                @Override
                public void publish(LogRecord record) {
                    records.add(record);
                }

                @Override
                public void flush() {}

                @Override
                public void close() {}
            });
        }

        private Logger logger() {
            return logger;
        }

        private List<String> messages(Level level) {
            return records.stream()
                    .filter(record -> record.getLevel().equals(level))
                    .map(LogRecord::getMessage)
                    .toList();
        }
    }
}
