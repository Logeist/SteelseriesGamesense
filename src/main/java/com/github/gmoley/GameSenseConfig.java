package com.github.gmoley;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("gamesenseConfig")
public interface GameSenseConfig extends Config {

    @ConfigItem(
            keyName = "useOled",
            name = "Use OLED Display",
            description = "Controls whether or not to use the OLED display",
            position = 1
    )
    default boolean useOled() {
        return false;
    }

    @ConfigItem(
            keyName = "useCombinedEvent",
            name = "Use Combined Event",
            description = "Controls whether or not to use a single event for all OLED updates (highly recommended)",
            position = 2
    )
    default boolean useCombinedEvent() {
        return true;
    }

}
