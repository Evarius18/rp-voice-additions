# RP Voice Additions

RP Voice Additions ist eine eigenständige Fabric-Erweiterung für
[Simple Voice Chat](https://modrepo.de/minecraft/voicechat/overview). Die Mod ergänzt
konfigurierbare Sprechreichweiten, Telefone, Notrufnummern, Funkkanäle und ein optionales
Mobilfunknetz, ohne Simple Voice Chat zu verändern.

## Voraussetzungen

- Minecraft 1.21.11
- Java 21
- Fabric Loader 0.19.3 oder neuer
- Fabric API
- Simple Voice Chat 1.21.11-2.6.22 oder neuer, auf Client und Server

## Funktionen

- Sprechmodi `quiet`, `normal`, `shout` und `scream` mit serverseitigen Reichweiten
- Mobiltelefon-Item mit persistent zugewiesener Rufnummer und Kontakten
- direkte Anrufe über Spielername, Rufnummer oder Kontaktname
- serverseitig validierte Kontaktverwaltung und persistente Anrufhistorie
- konfigurierbare, eindeutige Telefonnummern nach Vorwahl-/Ziffernschema
- Annehmen, Ablehnen, Auflegen sowie privater Hörer- und räumlicher Lautsprechermodus
- frei konfigurierbare Notrufnummern und Weiterleitung an Spieler eines Scoreboard-Teams
- Funkgerät mit konfigurierbaren, optional teamgeschützten Kanälen
- umschaltbare Funkübertragung (TX) bei weiterhin hörbarer Umgebung
- optionale Reichweitenbegrenzung für Funk
- modularer Mastbaukasten mit Basis, Segmenten, Signal-, Mobilfunk- und Digitalfunkaufsätzen
- platzierbare, persistent registrierte Mobilfunkmasten und optionale Digitalfunkrelais
- verknüpfbare Sirenen mit Feueralarm, Warnung, Entwarnung und Probealarm
- Sirenensteuerung mit Szenarien, persistenten Terminen sowie Live- und gespeicherten Durchsagen
- eigene Lautstärkeregler „Telefon“ und „Funk“ in Simple Voice Chat
- eigenständige Portrait-Handy-GUI und kompakte Funkgerät-GUI
- konfigurierbare Geräteitems, einschließlich `terranexus:mobile_phone`
- registrierte Minecraft-Keybinds und konfigurierbares Kommunikations-HUD
- optionale TerraNexus-Handy-App und institutionsbasierte Funkrechte

Das normale Umgebungsgespräch wird bei Telefon- und Funkübertragungen nicht unterdrückt.
Menschen in der Nähe hören den Sprecher deshalb weiterhin räumlich.

## Bedienung

### Sprechen

```text
/rpvoice
/rpvoice range
/rpvoice range <quiet|normal|shout|scream>
```

Natives Flüstern von Simple Voice Chat bleibt erhalten und wird von den zusätzlichen Modi
nicht überschrieben. `WHISPER` kann in `speech.json` Teil der Durchschaltreihenfolge sein.
Da die öffentliche Simple-Voice-Chat-API 2.6.20 keinen Setter für den Mikrofon-Flüsterzustand
anbietet, wird dafür weiterhin die native SVC-Flüstertaste verwendet. RP-VCA erkennt den
echten Paketstatus serverseitig und zeigt im HUD an, wenn die SVC-Taste noch erforderlich ist.

### Telefon

```text
/phone
/phone call <Spieler|Nummer|Kontakt>
/phone answer
/phone decline
/phone hangup
/phone speaker
/phone contacts
/phone contacts add <Name> <Nummer>
/phone contacts remove <Name>
```

Ein Rechtsklick mit einem konfigurierten Telefon öffnet die Portrait-GUI. Dort stehen
Wählfeld, Kontakte, Notruf, Anrufhistorie, Gesprächssteuerung und optionale Apps zur Verfügung.
Kontakte sowie einzelne Historieneinträge können direkt verwaltet werden. Vor dem vollständigen
Leeren der Historie ist eine zweite Bestätigung erforderlich.
Die Oberfläche erscheint als unverwischtes Handgerät unten rechts; TerraNexus-Apps werden
nur eingeblendet, wenn die serverseitige optionale Integration tatsächlich verfügbar ist.

### Funk

```text
/radio
/radio channels
/radio tune <Kanal>
/radio transmit
/radio off
```

Ein Rechtsklick mit einem konfigurierten Funkgerät öffnet die kompakte Funkoberfläche.
Nur während TX beziehungsweise gedrückter Funk-PTT-Taste wird die eigene Stimme auf den
Kanal geroutet.
Auch das Funkgerät bleibt unten rechts über der unveränderten Spielwelt und nutzt eine
eigene technische LCD-/Hardwaredarstellung statt des Smartphone-Rasters.

### Sirenen

Die Blöcke `mast_sirene_zwei` und `mast_sirene_drei` sind räumliche Sirenenendpunkte.
Platziere eine `siren_controller`-Steuerung und öffne sie per Rechtsklick. Über
„Sirene verbinden“ wird ein Verknüpfungsmodus aktiviert. Dabei muss das
`siren_programmer`-Programmiergerät in Haupt- oder Nebenhand gehalten werden; der nächste
Rechtsklick mit dem Gerät auf eine Sirene verbindet oder trennt sie. Die Entfernung zwischen Steuerung und Sirene ist danach
für die Auslösung unerheblich. Dimensionsübergreifende Verknüpfungen sind standardmäßig aus.

Die Steuerung kann konfigurierte Szenarien sofort auslösen, Signale stoppen und Alarme für
`HH:mm` oder eine Minutenangabe terminieren. Im Reiter „Durchsagen“ lassen sich Live-Audio
über alle verbundenen Sirenen übertragen sowie Mikrofonaufnahmen persistent speichern und
später wiedergeben. Normales räumliches Sprechen wird bei einer Live-Durchsage nicht blockiert.

Signalpegel und maximale Hördistanz werden über `signalGain` und `audibleDistance` in
`siren.json` geregelt. Ältere 512-Block-Standardkonfigurationen werden einmalig auf die
realistischere Reichweite von 192 Blöcken migriert. Sound Physics Remastered verarbeitet die
positionsgebundenen Simple-Voice-Chat-Kanäle automatisch, wenn dessen Option
„Simple Voice Chat integration“ auf dem Client aktiviert ist.

### Keybinds und HUD

Die Kategorie „RP Voice Additions“ erscheint unter den Minecraft-Steuerungseinstellungen.
Standardmäßig sind Akzenttaste für den Sprachmodus, `P` für das Handy, `R` für das
Funkgerät und linkes `Alt` für Funk-PTT belegt. Direktmodi und Gesprächstasten sind zunächst
unbelegt, damit keine bestehenden Tasten überschrieben werden.

Das HUD zeigt Sprechmodus, Reichweite, Anrufzustand, Gesprächsgegenstelle, Lautsprecher,
Funkkanal sowie RX/TX an. Position und sichtbare Bereiche werden in `hud.json` eingestellt.

### Administration

```text
/rpvoice reload
/celltower list
```

Beide Befehle benötigen Operator-Level 2. Die Aufsätze `rp-vca:mast_mobilfunk` und
`rp-vca:mast_digitalfunk` werden beim Platzieren registriert und beim Abbau wieder entfernt.

## Konfiguration

Beim ersten Start entstehen unter `config/rp-voice-additions/`:

- `speech.json` – Sprachmodi und Reichweiten
- `phone.json` – Telefon, Rufnummernlänge, Klingeldauer und Lautsprecherreichweite
- `emergency.json` – Notrufnummern und zuständige Scoreboard-Teams
- `radio.json` – Funkkanäle, Teamzugriff, Dimensionsgrenze und optionale Maximalreichweite
- `infrastructure.json` – Aktivierung und Reichweite des Mobilfunknetzes
- `devices.json` – erlaubte Item-IDs für Handy und Funkgerät
- `hud.json` – Sichtbarkeit, Position und Anzeigedauer des HUD
- `compatibility.json` – TerraNexus-Apps und Institution-zu-Funkkanal-Mapping
- `siren.json` – Reichweite, Rechte, Szenarien, Verknüpfungs-, Termin- und Aufnahmelimits
- `client.json` – lokale Funklautstärke aus der Funkgeräte-GUI

Mit `phone.requireCoverage=true` benötigen beide Gesprächsteilnehmer Netzabdeckung.
`infrastructure.enabled=true` aktiviert die Prüfung gegen `mast_mobilfunk`. Bei Ausfall
der Abdeckung wird ein laufendes Gespräch getrennt.

Mit `digitalRadioRelaysEnabled=true` erweitert `mast_digitalfunk` begrenzte Funkkanäle.
Sender und Empfänger müssen jeweils innerhalb `digitalRadioRelayRange` eines registrierten
Digitalfunkaufsatzes liegen. Bei `radio.maximumRange=0` bleibt Funk wie bisher unbegrenzt.

Geschützte Notruf- und Funkrollen werden absichtlich über Vanilla-Scoreboard-Teams
abgebildet, damit keine RP-Hauptmod oder Permission-Mod zwingend erforderlich ist:

```text
/team add police
/team join police <Spieler>
```

Spielerdaten und Mastpositionen werden weltbezogen in
`<Welt>/rp-voice-additions/` gespeichert.
Sirenen, Steuerungen und Termine liegen in `sirens.json`; gespeicherte Durchsagen werden als
48-kHz-Mono-PCM unter `<Welt>/rp-voice-additions/announcements/` abgelegt.

### TerraNexus

Ist die Mod-ID `terranexus` vorhanden, wird die TerraNexus-Anwendungsübersicht als App
angeboten. Soll zusätzlich ein TerraNexus-Item das RP-VCA-Handy öffnen, kann dessen Item-ID
in `devices.json` ergänzt werden. Ohne TerraNexus enthält die Standardkonfiguration keinerlei
TerraNexus-Itemzuweisung oder sichtbare Verknüpfung. Die Kopplung erfolgt ausschließlich über
öffentliche Provider und wird bei einer inkompatiblen oder fehlenden Version automatisch deaktiviert.

`compatibility.json` ordnet Institutions-ID, -Name oder -Typ einer Liste von Funkkanälen zu.
Ein Spieler erhält Zugriff, wenn irgendeine seiner TerraNexus-Mitgliedschaften auf den
gewählten Kanal passt. Vanilla-Scoreboard-Teams bleiben parallel als Fallback aktiv.

TerraNexus oder eine andere optionale Mod kann Sirenen ohne interne Klassen oder Reflection
über `RpVcaApi.getSirenService()` ansteuern. Die API unterstützt Controllerauflistung,
Szenarioauslösung, Stopp, Terminierung und Live-Durchsagen. RP-VCA bleibt Eigentümer von
Persistenz, Berechtigungsprüfung und Voice-Routing. Berechtigte Scoreboard-Teams und
Institutionsschlüssel werden in `siren.json` konfiguriert.

## Bauen

```text
./gradlew build
```

Das fertige Artefakt liegt anschließend in `build/libs/`.

### Mastmodelle neu erzeugen

Die gelieferten Mastmodelle stammen aus dem Minecraft-1.21.11-Format. Der Branch behält
zunächst die bereits geprüften, abwärtskompatibel konvertierten Laufzeit-Assets bei. Nach
Änderungen an den Originalen können sie erneut erzeugt werden:

```text
python scripts/convert_blockbench_models.py <Originalordner> \
  src/main/resources/assets/rp-vca/models/block \
  --allow-lossy --report-json build/model-converter/mast-report.json
```

`--allow-lossy` bleibt für Backports bewusst explizit: Fünf kleine Elemente des
Mobilfunkaufsatzes sind im älteren Vanilla-Modellformat nicht exakt darstellbar. Der
Konverter berechnet die beste Näherung und dokumentiert deren Geometriefehler.

## Technische Struktur

Simple Voice Chat liefert die Mikrofonpakete. Eine zentrale serverseitige Routing-Schicht
leitet dasselbe Opus-Paket zusätzlich an private Telefonkanäle, räumliche
Lautsprecherkanäle oder Funkempfänger weiter. Telefon-, Funk-, Infrastruktur- und
Sprechzustand bleiben getrennte Dienste und greifen nur über klar definierte Abfragen
aufeinander zu.

## Einfache Erweiterungspunkte

- Weitere Handy-Apps implementieren `PhoneIntegration` und werden über den
  `CompatibilityManager` bereitgestellt.
- Weitere Rechtequellen können im `RadioPermissionResolver` kombiniert werden.
- Neue Client-Aktionen werden als serverseitig validierte `DeviceActionPayload`-Aktionen
  ergänzt.
- Verschlüsselung und Relais können auf den bestehenden
  kanalbezogenen Voice-Chat-Kategorien aufbauen.

## Öffentliche Integrations-API

Optionale Mods können nach einem Fabric-Mod-Loaded-Check über
`RpVcaApi.getPhoneService()` auf den laufenden `PhoneService` zugreifen. Öffentliche
Methoden stehen für Kontaktmutationen, unveränderliche Kontakt- und Historienansichten,
Historienlöschung, Telefonnummernvergabe und Gesprächssteuerung bereit. Interne
`PlayerProfiles`-Objekte werden nicht über den Service-Locator freigegeben.

Institutionsmods können Funkmitgliedschaften über
`RpVcaApi.registerInstitutionMembershipProvider(...)` bereitstellen. RP-VCA greift dafür
weder per Reflection noch direkt auf fremde Speicherklassen zu. Optionale Geräte, Apps und
Institutionsschlüssel werden über öffentliche Provider in `RpVcaApi` registriert.

Die Spielerdatei besitzt ab Schema 2 zusätzlich `callHistory`. Bestehende Profile ohne dieses
Feld werden beim Laden mit einer leeren Liste migriert; vorhandene Nummern und Kontakte
bleiben erhalten.

Administrative Historienlöschung:

```text
/rpvoice phone history clear <Spieler>
/rpvoice phone history clear-all
```

Das benötigte Operator-Level wird in `phone.json` über `historyAdminPermissionLevel` konfiguriert.
