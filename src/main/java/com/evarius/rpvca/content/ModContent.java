package com.evarius.rpvca.content;

import com.evarius.rpvca.RpVoiceAddon;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.util.Identifier;

public final class ModContent {
    public static final Item MOBILE_PHONE = registerItem("mobile_phone",
            key -> new MobilePhoneItem(new Item.Settings().registryKey(key).maxCount(1)));
    public static final Item RADIO = registerItem("radio",
            key -> new RadioItem(new Item.Settings().registryKey(key).maxCount(1)));
    public static final Block CELL_TOWER = registerBlock("cell_tower",
            key -> new CellTowerBlock(AbstractBlock.Settings.create()
                    .registryKey(key)
                    .strength(4.0F, 8.0F)
                    .sounds(BlockSoundGroup.METAL)
                    .pistonBehavior(PistonBehavior.BLOCK)
                    .requiresTool()));
    public static final ItemGroup COMMUNICATION_GROUP = Registry.register(Registries.ITEM_GROUP,
            RpVoiceAddon.id("communication"),
            FabricItemGroup.builder()
                    .icon(() -> new ItemStack(MOBILE_PHONE))
                    .displayName(net.minecraft.text.Text.translatable("itemgroup.rp-vca.communication"))
                    .entries((context, entries) -> {
                        entries.add(MOBILE_PHONE);
                        entries.add(RADIO);
                        entries.add(CELL_TOWER);
                    }).build());

    private ModContent() {
    }

    public static void initialize() {
        // Triggers static registration. Entries are declared exactly once in the dedicated group.
    }

    private static Item registerItem(String path, java.util.function.Function<RegistryKey<Item>, Item> factory) {
        Identifier id = RpVoiceAddon.id(path);
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);
        return Registry.register(Registries.ITEM, key, factory.apply(key));
    }

    private static Block registerBlock(String path, java.util.function.Function<RegistryKey<Block>, Block> factory) {
        Identifier id = RpVoiceAddon.id(path);
        RegistryKey<Block> blockKey = RegistryKey.of(RegistryKeys.BLOCK, id);
        Block block = Registry.register(Registries.BLOCK, blockKey, factory.apply(blockKey));
        RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, id);
        Registry.register(Registries.ITEM, itemKey, new BlockItem(block, new Item.Settings().registryKey(itemKey)));
        return block;
    }
}
