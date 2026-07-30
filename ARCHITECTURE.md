# Architektur

## Datenfluss

1. Simple Voice Chat empfängt ein Mikrofonpaket.
2. `SpeechService` bestimmt über `VoiceDistanceEvent` die lokale Sprechreichweite.
3. `PhoneService` liefert gegebenenfalls eine aktive Gegenstelle und den Hörermodus.
4. `RadioService` liefert bei aktivem TX die berechtigten Empfänger desselben Kanals.
5. `RpVoicechatPlugin` leitet das originale Opus-Paket ohne erneute Kodierung weiter.
6. Das ursprüngliche räumliche Paket bleibt bestehen, damit die Umgebung den Sprecher hört.

## Module

- `config`: getrennte, validierte Serverkonfigurationen
- `state`: atomar gespeicherte Spielerprofile und Mobilfunkmasten
- `service`: Zustandsautomaten und fachliche Zugriffsregeln
- `api`: stabiler öffentlicher Einstiegspunkt und Mutationsergebnisse
- `phone`: Nummernnormalisierung/-vergabe und unveränderliche Historienmodelle
- `voice`: Integration und Audiokanäle der Simple-Voice-Chat-API
- `content`: Minecraft-Items und Mobilfunkmast-Block
- `item`: Auflösung konfigurierter Geräteitems
- `command`: Bedienung und Administration
- `network`: typisierte Client-/Server-Aktionen und Status-Synchronisierung
- `client.gui`: getrennte Telefon- und Funkgerät-Screens
- `client.gui.component`: gemeinsame blurfreie Handheld-Verankerung und UI-Bausteine
- `client.keybind`: registrierte, frei belegbare Steuerung
- `client.hud`: rein darstellendes, konfigurierbares Overlay
- `client.audio`: clientseitige Lautstärkeanpassung ausschließlich für RP-VCA-Funkkanäle
- `compatibility`: allgemeine optionale App-Schnittstellen
- `api`: optionale Provider-Schnittstellen ohne harte Laufzeitabhängigkeit
- `permissions`: kombinierte Vanilla- und Institutionsrechte

Alle relevanten Entscheidungen liegen auf dem Server. Clients können keine Empfänger,
Reichweiten, Rollen oder Netzabdeckung vorgeben.

## Erweiterungspunkte

Relaisstationen oder Signalstärken können hinter `TowerRegistry.hasCoverage` ergänzt werden.
Digitale Funkgruppen und Verschlüsselungskennzeichen gehören in `RadioService`. Warteschlangen
oder Leitstellenterminals können den Zielresolver von `PhoneService` erweitern, ohne das
Voice-Routing neu zu strukturieren.

## Vertrauensgrenze

GUIs und Keybinds senden ausschließlich Absichten. Itembesitz, Netzabdeckung,
Gesprächszustand, Kanalzugriff und Institutionsmitgliedschaft werden erneut auf dem Server
geprüft. Der Client erhält nur eine kompakte `CommunicationStatus`-Ansicht für HUD und
Screens.

## Handheld-Darstellung

`HandheldScreen` verankert Geräte mit einem kleinen Sicherheitsabstand unten rechts,
begrenzt ihre Größe auf den aktuellen GUI-Raum und unterdrückt sowohl Blur als auch
Vollbild-Gradienten. Telefon und Funkgerät teilen nur Rahmenfarben und Bedienelemente;
ihre fachlichen Layouts bleiben getrennt. Optionale App-Kacheln entstehen ausschließlich
aus der serverseitig gefilterten `phoneApps`-Liste und verschwinden bei einem
Integrationsausfall auch aus einem bereits geöffneten Screen.

## Optionale Integrationen

Optionale Mods registrieren ausschließlich öffentliche Provider über `RpVcaApi`:
`InstitutionMembershipProvider`, `DeviceCapabilityProvider` und
`PhoneApplicationProvider`. RP-VCA referenziert keine TerraNexus-Klasse und verwendet
keine Reflection. TerraNexus bindet diese API nur `compileOnly` ein und lädt seinen Adapter
erst nach einem positiven Fabric-Mod-Check. Fehlt einer der Mods, bleiben beide eigenständig
lauffähig. Vanilla-Scoreboard-Teamrechte bleiben zusätzlich aktiv.

`DeviceItemResolver` trennt Gerätefähigkeit und GUI-Verantwortung. Das RP-VCA-Handy ist
Telefon und öffnet den Standard-Screen; ein externes TerraNexus-Handy ist ebenfalls Telefon,
öffnet aber ausschließlich seine eigene Oberfläche.

## Telefonpersistenz und API

`PlayerProfiles` bleibt der einzige Eigentümer von `players.json`. `PhoneService` ist die
öffentliche, servergebundene Mutationsgrenze und synchronisiert Änderungen unmittelbar.
`RpVcaApi` gibt ausschließlich den laufenden Service zurück; mutable Profile und Collections
bleiben intern.

Schema 2 ergänzt jedes Profil um eine begrenzte Anrufhistorie. Fehlende Felder erhalten
sichere Standardwerte. Historieneinträge speichern Nummern und Anzeigenamen als Snapshot,
während nach außen ausschließlich immutable `CallHistoryEntryView`-Records gelangen.

## Native SVC-Flüsterintegration

`SpeechService` führt `WHISPER` in der konfigurierbaren Modusreihenfolge, setzt jedoch keine
synthetische Flüsterdistanz. `RpVoicechatPlugin` übernimmt den Flüsterzustand ausschließlich
aus dem echten `MicrophonePacket#isWhispering()` und lässt native Pakete von SVC behandeln.
Die Client-Bridge `SvcWhisperCompatibility` benutzt nur die öffentliche `VoicechatClientApi`.

API 2.6.20 stellt keinen öffentlichen Setter für den lokalen Mikrofon-Flüsterzustand bereit.
Darum manipuliert RP-VCA weder interne SVC-Keybinds noch interne Speicherklassen. Zum
Aktivieren bleibt die native SVC-Flüstertaste erforderlich; der HUD-Zusatz `SVC-Taste`
kennzeichnet diesen Zustand transparent.
