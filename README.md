# RP Voice Additions

RP Voice Additions is a standalone Fabric extension for
[Simple Voice Chat](https://modrepo.de/minecraft/voicechat/overview). The mod adds
configurable speaking ranges, phones, emergency numbers, radio channels, and an optional
cellular network without modifying Simple Voice Chat.

## Requirements

* Minecraft 1.21.8
* Java 21
* Fabric Loader 0.19.3 or newer
* Fabric API
* Simple Voice Chat with API 2.6.20 or newer, on both client and server

## Features

* Speaking modes `quiet`, `normal`, `shout`, and `scream` with server-side ranges
* Mobile phone item with a persistently assigned phone number and contacts
* Direct calls via player name, phone number, or contact name
* Server-side validated contact management and persistent call history
* Configurable, unique phone numbers based on area-code/digit schemes
* Answering, declining, hanging up, as well as private earpiece and spatial speaker modes
* Freely configurable emergency numbers and forwarding to players on a scoreboard team
* Radio with configurable, optionally team-protected channels
* Toggleable radio transmission (TX) while ambient audio remains audible
* Optional range limitation for radio
* Modular mast construction system with base, segments, signal, cellular, and digital-radio attachments
* Placeable, persistently registered cellular towers and optional digital-radio relays
* Linkable sirens with fire alarm, warning, all-clear, and test alarm
* Siren control with scenarios, persistent schedules, and live or saved announcements
* Dedicated “Phone” and “Radio” volume controls in Simple Voice Chat
* Standalone portrait phone GUI and compact radio GUI
* Configurable device items, including `terranexus:mobile_phone`
* Registered Minecraft keybinds and configurable communication HUD
* Optional TerraNexus phone app and institution-based radio permissions

Normal proximity voice chat is not suppressed during phone or radio transmissions.
Nearby players therefore continue to hear the speaker spatially.

## Usage

### Speaking

```text
/rpvoice
/rpvoice range
/rpvoice range <quiet|normal|shout|scream>
```

Native whispering from Simple Voice Chat remains available and is not overridden by the additional modes.
`WHISPER` can be part of the switching sequence in `speech.json`.
Because the public Simple Voice Chat API 2.6.20 does not provide a setter for the microphone whisper state,
the native SVC whisper key is still used for this. RP-VCA detects the actual packet state on the server side
and indicates in the HUD when the SVC key is still required.

### Phone

```text
/phone
/phone call <Player|Number|Contact>
/phone answer
/phone decline
/phone hangup
/phone speaker
/phone contacts
/phone contacts add <Name> <Number>
/phone contacts remove <Name>
```

Right-clicking with a configured phone opens the portrait GUI. It provides a dial pad,
contacts, emergency calling, call history, call controls, and optional apps.
Contacts and individual history entries can be managed directly. A second confirmation
is required before completely clearing the history.

The interface appears as a non-blurred handheld device in the bottom-right corner;
TerraNexus apps are shown only when the optional server-side integration is actually available.

### Radio

```text
/radio
/radio channels
/radio tune <Channel>
/radio transmit
/radio off
```

Right-clicking with a configured radio opens the compact radio interface.
The player's own voice is routed to the channel only while TX is active or while the
radio PTT key is held down.

The radio also remains in the bottom-right corner over the unchanged game world and uses
its own technical LCD/hardware-style presentation instead of the smartphone grid.

### Sirens

The `mast_sirene_zwei` and `mast_sirene_drei` blocks are spatial siren endpoints.
Place a `siren_controller` and open it by right-clicking. Selecting
“Connect Siren” activates linking mode. The `siren_programmer` programming device
must be held in either the main hand or off-hand; the next right-click with the device
on a siren links or unlinks it. The distance between the controller and siren is irrelevant
afterwards. Cross-dimensional links are disabled by default.

The controller can immediately trigger configured scenarios, stop signals, and schedule alarms
for an `HH:mm` time or a specified number of minutes. In the “Announcements” tab,
live audio can be transmitted through all connected sirens, while microphone recordings
can be stored persistently and played back later. Normal spatial speech is not blocked
during a live announcement.

Signal level and maximum audible distance are controlled using `signalGain` and
`audibleDistance` in `siren.json`. Older default configurations using a 512-block range
are migrated once to the more realistic range of 192 blocks. Sound Physics Remastered
automatically processes the position-based Simple Voice Chat channels when its
“Simple Voice Chat integration” option is enabled on the client.

### Keybinds and HUD

The “RP Voice Additions” category appears in the Minecraft controls settings.
By default, the accent key is assigned to the speech mode, `P` to the phone, `R` to the
radio, and left `Alt` to radio PTT. Direct modes and call-control keys are initially
unassigned so that no existing keybindings are overwritten.

The HUD displays the speaking mode, range, call status, remote call participant, speaker mode,
radio channel, and RX/TX status. Its position and visible sections are configured in `hud.json`.

### Administration

```text
/rpvoice reload
/celltower list
```

Both commands require operator level 2. The `rp-vca:mast_mobilfunk` and
`rp-vca:mast_digitalfunk` attachments are registered when placed and removed from the registry
when broken.

## Configuration

On first launch, the following files are created under `config/rp-voice-additions/`:

* `speech.json` – speech modes and ranges
* `phone.json` – phone settings, phone-number length, ring duration, and speaker range
* `emergency.json` – emergency numbers and responsible scoreboard teams
* `radio.json` – radio channels, team access, dimension restrictions, and optional maximum range
* `infrastructure.json` – activation and range of the cellular network
* `devices.json` – allowed item IDs for phones and radios
* `hud.json` – HUD visibility, position, and display duration
* `compatibility.json` – TerraNexus apps and institution-to-radio-channel mapping
* `siren.json` – range, permissions, scenarios, linking, scheduling, and recording limits
* `client.json` – local radio volume from the radio GUI

With `phone.requireCoverage=true`, both call participants require network coverage.
`infrastructure.enabled=true` enables the coverage check against `mast_mobilfunk`.
If coverage is lost, an active call is disconnected.

With `digitalRadioRelaysEnabled=true`, `mast_digitalfunk` extends range-limited radio channels.
The sender and receiver must each be within `digitalRadioRelayRange` of a registered
digital-radio attachment. If `radio.maximumRange=0`, radio remains unlimited as before.

Protected emergency and radio roles are deliberately implemented using vanilla scoreboard teams
so that neither a main RP mod nor a permissions mod is mandatory:

```text
/team add police
/team join police <Player>
```

Player data and mast positions are stored per world in
`<World>/rp-voice-additions/`.
Sirens, controllers, and schedules are stored in `sirens.json`; saved announcements are stored
as 48 kHz mono PCM under `<World>/rp-voice-additions/announcements/`.

### TerraNexus

If the mod ID `terranexus` is present, the TerraNexus application overview is offered as an app.
If a TerraNexus item should additionally open the RP-VCA phone, its item ID can be added
to `devices.json`. Without TerraNexus, the default configuration contains no TerraNexus
item assignment or visible integration. Integration is performed exclusively through public
providers and is automatically disabled if the version is missing or incompatible.

`compatibility.json` maps an institution ID, name, or type to a list of radio channels.
A player receives access if any of their TerraNexus memberships matches the selected channel.
Vanilla scoreboard teams remain active in parallel as a fallback.

TerraNexus or another optional mod can control sirens without internal classes or reflection
through `RpVcaApi.getSirenService()`. The API supports controller listing, scenario triggering,
stopping, scheduling, and live announcements. RP-VCA remains responsible for persistence,
permission checks, and voice routing. Authorized scoreboard teams and institution keys
are configured in `siren.json`.

## Building

```text
./gradlew build
```

The finished artifact is then located in `build/libs/`.

### Regenerating Mast Models

The supplied Blockbench mast models use the Minecraft 1.21.11 format with multi-axis
element rotations. The installed models are runtime assets converted for 1.21.8.
After modifying the originals, they must be regenerated:

```text
python scripts/convert_blockbench_models.py <OriginalFolder> \
  src/main/resources/assets/rp-vca/models/block \
  --allow-lossy --report-json build/model-converter/mast-report.json
```

`--allow-lossy` is deliberately explicit: five small elements of the cellular attachment
cannot be represented exactly in the older vanilla model format. The converter calculates
the best approximation and documents the resulting geometry errors. A test prevents
unconverted 1.21.11 rotations from accidentally being shipped as 1.21.8 resources.

## Technical Structure

Simple Voice Chat provides the microphone packets. A central server-side routing layer
additionally forwards the same Opus packet to private phone channels, spatial speaker channels,
or radio recipients. Phone, radio, infrastructure, and speaking state remain separate services
and interact only through clearly defined queries.

## Simple Extension Points

* Additional phone apps implement `PhoneIntegration` and are provided through the
  `CompatibilityManager`.
* Additional permission sources can be combined in the `RadioPermissionResolver`.
* New client actions are added as server-side validated `DeviceActionPayload` actions.
* Encryption and relays can build on the existing channel-based voice-chat categories.

## Public Integration API

Optional mods can access the active `PhoneService` through
`RpVcaApi.getPhoneService()` after performing a Fabric mod-loaded check. Public methods
are available for contact mutations, immutable contact and history views, history clearing,
phone-number assignment, and call control. Internal `PlayerProfiles` objects are not exposed
through the service locator.

Institution mods can provide radio memberships through
`RpVcaApi.registerInstitutionMembershipProvider(...)`. RP-VCA does not use reflection
or directly access storage classes belonging to other mods. Optional devices, apps,
and institution keys are registered through public providers in `RpVcaApi`.

Starting with schema 2, the player file additionally contains `callHistory`. Existing profiles
without this field are migrated with an empty list when loaded; existing numbers and contacts
are preserved.

Administrative history clearing:

```text
/rpvoice phone history clear <Player>
/rpvoice phone history clear-all
```

The required operator level is configured in `phone.json` using
`historyAdminPermissionLevel`.
