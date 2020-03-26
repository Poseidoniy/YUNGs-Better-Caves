package com.yungnickyoung.minecraft.bettercaves.config;

import com.yungnickyoung.minecraft.bettercaves.config.cave.*;
import com.yungnickyoung.minecraft.bettercaves.config.cavern.ConfigCaverns;
import net.minecraftforge.common.config.Config;

public class ConfigCaveGen {
    @Config.Name("Caves")
    @Config.Comment("Settings used in the generation of caves.")
    public ConfigCaves caves = new ConfigCaves();

    @Config.Name("Caverns")
    @Config.Comment("Settings used in the generation of caverns. Caverns are spacious caves at low altitudes.")
    public ConfigCaverns caverns = new ConfigCaverns();

    @Config.Name("Water Regions")
    @Config.Comment("Settings used in the generation of water regions.")
    public ConfigWaterRegions waterRegions = new ConfigWaterRegions();

    @Config.Name("Miscellaneous")
    @Config.Comment("Miscellaneous settings used in cave and cavern generation.")
    public ConfigMisc miscellaneous = new ConfigMisc();
}
