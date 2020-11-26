package net.fabricmc.loader.api.metadata;

import com.mojang.serialization.DynamicOps;

import net.fabricmc.loader.metadata.CustomValueOpsImpl;

public interface CustomValueOps extends DynamicOps<CustomValue> {
	CustomValueOps INSTANCE = CustomValueOpsImpl.INSTANCE;
}
