package com.evarius.rpvca.api;

/** Privacy-safe caller label resolved from the viewer's own address book. */
public record CallerDisplay(String primaryText, String formattedNumber,
                            boolean savedContact, boolean anonymous) {
}
