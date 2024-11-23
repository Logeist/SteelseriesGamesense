package com.github.gmoley;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("gamesenseConfig")
public interface GameSenseConfig extends Config {

    @ConfigItem(
            keyName = "useOled",
            name = "Use OLED Display",
            description = "Controls whether or not to use the OLED display"
    )
    default boolean useOled() {
        return false;
    }

}
