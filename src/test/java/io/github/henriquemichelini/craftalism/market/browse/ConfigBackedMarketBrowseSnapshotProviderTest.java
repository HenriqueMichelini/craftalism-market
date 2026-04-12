package io.github.henriquemichelini.craftalism.market.browse;

import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigBackedMarketBrowseSnapshotProviderTest {
    @Test
    void loadsConfiguredCategoriesAndItems() throws InvalidConfigurationException {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.loadFromString("""
                market:
                  read-only: true
                  categories:
                    farming:
                      title: "&aFarming"
                      icon: WHEAT
                      description:
                        - "&7Browse staple crops."
                      items:
                        wheat:
                          display-name: "&fWheat"
                          material: WHEAT
                          description:
                            - "&7Common crop."
                          buy-estimate: "4.8 coins"
                          sell-estimate: "4.1 coins"
                          variation-percent: "+2.3%"
                          stock-display: "Healthy"
                          last-updated: "Fixture preview"
                """);

        MarketBrowseSnapshot snapshot = new ConfigBackedMarketBrowseSnapshotProvider(configuration).loadSnapshot();

        assertTrue(snapshot.readOnly());
        assertEquals(1, snapshot.categories().size());
        assertEquals("&aFarming", snapshot.categories().getFirst().title());
        assertEquals(Material.WHEAT, snapshot.categories().getFirst().icon());
        assertEquals(1, snapshot.categories().getFirst().items().size());
        assertEquals("4.8 coins", snapshot.categories().getFirst().items().getFirst().buyEstimate());
    }

    @Test
    void rejectsInvalidMaterials() throws InvalidConfigurationException {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.loadFromString("""
                market:
                  categories:
                    broken:
                      icon: NOT_A_REAL_MATERIAL
                """);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new ConfigBackedMarketBrowseSnapshotProvider(configuration).loadSnapshot()
        );

        assertTrue(error.getMessage().contains("Invalid material"));
    }
}
