package com.evarius.rpvca.content;

import com.evarius.rpvca.RpVoiceAddon;
import com.evarius.rpvca.state.TowerRegistry;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

public final class ModContent {
    public static final Item MOBILE_PHONE = registerItem("mobile_phone",
            key -> new MobilePhoneItem(new Item.Properties().setId(key).stacksTo(1)));
    public static final Item RADIO = registerItem("radio",
            key -> new RadioItem(new Item.Properties().setId(key).stacksTo(1)));
    public static final Item SIREN_PROGRAMMER = registerItem("siren_programmer",
            key -> new Item(new Item.Properties().setId(key).stacksTo(1)));
    public static final Block MAST_BASIS = registerBlock("mast_basis",
            key -> new Block(mastSettings(key)));
    public static final Block MAST = registerBlock("mast",
            key -> new Block(mastSettings(key)));
    public static final Block MAST_SIRENE_ZWEI = registerBlock("mast_sirene_zwei",
            key -> new SirenBlock(mastSettings(key), "two_head"));
    public static final Block MAST_SIRENE_DREI = registerBlock("mast_sirene_drei",
            key -> new SirenBlock(mastSettings(key), "three_head"));
    public static final Block MAST_MOBILFUNK = registerBlock("mast_mobilfunk",
            key -> new FunctionalMastBlock(mastSettings(key), TowerRegistry.TowerType.CELLULAR));
    public static final Block MAST_DIGITALFUNK = registerBlock("mast_digitalfunk",
            key -> new FunctionalMastBlock(mastSettings(key), TowerRegistry.TowerType.DIGITAL_RADIO));
    public static final Block SIREN_CONTROLLER = registerBlock("siren_controller",
            key -> new SirenControllerBlock(BlockBehaviour.Properties.of()
                    .setId(key)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)));
    public static final CreativeModeTab COMMUNICATION_GROUP = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,
            RpVoiceAddon.id("communication"),
            FabricItemGroup.builder()
                    .icon(() -> new ItemStack(MOBILE_PHONE))
                    .title(net.minecraft.network.chat.Component.translatable("itemgroup.rp-vca.communication"))
                    .displayItems((context, entries) -> {
                        entries.accept(MOBILE_PHONE);
                        entries.accept(RADIO);
                        entries.accept(SIREN_PROGRAMMER);
                        entries.accept(MAST_BASIS);
                        entries.accept(MAST);
                        entries.accept(MAST_SIRENE_ZWEI);
                        entries.accept(MAST_SIRENE_DREI);
                        entries.accept(MAST_MOBILFUNK);
                        entries.accept(MAST_DIGITALFUNK);
                        entries.accept(SIREN_CONTROLLER);
                    }).build());

    private ModContent() {
    }

    public static void initialize() {
        // Triggers static registration. Entries are declared exactly once in the dedicated group.
    }

    private static Item registerItem(String path, java.util.function.Function<ResourceKey<Item>, Item> factory) {
        Identifier id = RpVoiceAddon.id(path);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(key));
    }

    private static Block registerBlock(String path, java.util.function.Function<ResourceKey<Block>, Block> factory) {
        Identifier id = RpVoiceAddon.id(path);
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
        Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, factory.apply(blockKey));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
        Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey)));
        return block;
    }

    private static BlockBehaviour.Properties mastSettings(ResourceKey<Block> key) {
        return BlockBehaviour.Properties.of()
                .setId(key)
                .strength(4.0F, 8.0F)
                .sound(SoundType.METAL)
                .pushReaction(PushReaction.BLOCK)
                .requiresCorrectToolForDrops()
                .noOcclusion();
    }
}
