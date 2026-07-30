package com.evarius.rpvca.item;

import com.evarius.rpvca.api.DeviceCapabilities;
import net.minecraft.item.ItemStack;

/** Server-side result of resolving a phone held in either hand. */
public record ResolvedPhoneDevice(ItemStack stack, DeviceCapabilities capabilities,
                                  String screenProviderId) {
}
