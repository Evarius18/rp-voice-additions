package com.evarius.rpvca.api;

/** Stable result values for siren operations performed by players or integrations. */
public enum SirenActionResult {
    SUCCESS,
    DISABLED,
    NOT_FOUND,
    NOT_ALLOWED,
    INVALID_REQUEST,
    NO_LINKED_SIRENS,
    PROGRAMMER_REQUIRED,
    LIMIT_REACHED,
    ALREADY_ACTIVE,
    VOICE_CHAT_UNAVAILABLE,
    AUDIO_UNAVAILABLE,
    STORAGE_ERROR
}
