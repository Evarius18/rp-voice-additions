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
- `integration.terranexus`: isolierte Reflection-Bridge ohne harte Abhängigkeit
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

`CompatibilityManager` prüft zunächst die Fabric-Modliste. TerraNexus-Klassen werden erst
danach und ausschließlich per Reflection aufgelöst. Ein Linkage- oder API-Fehler deaktiviert
die Integration für die laufende Sitzung, während Telefon, Funk und Scoreboard-Teamrechte
normal weiterarbeiten.
