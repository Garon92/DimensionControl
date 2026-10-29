# Dimension Control (Forge 1.7.10)

Serverový mod: do zvolených dimenzí se hráč dostane, jen když drží v ruce nastavený předmět.
Kontrola probíhá u každého hráče zvlášť a předmět se nespotřebuje. Volitelně si hráč po prvním
vstupu dimenzi trvale odemkne a předmět pak už nepotřebuje.

**Hotový mod:** [`dist/DimensionControl-1.0.0.jar`](dist/DimensionControl-1.0.0.jar). Jar patří do složky `mods` na serveru.
Hráči ho nepotřebují, ale nevadí, když ho mají.

## Co umí

- Každá dimenze může mít jiný předmět (`modid:item` + metadata) a vlastní chybovou zprávu.
- Bez předmětu se teleport zablokuje dřív, než začne. Hráč zůstane, kde je, a dostane zprávu v chatu
  (nejvýš jednou za 5 s).
- Kontroluje se každý hráč zvlášť. Když portál otevře někdo jiný, hráč bez předmětu jím neprojde.
- Funguje s portály ostatních modů. Kontroluje se každý přesun hráče mezi dimenzemi, nejen vanilla portály.
- Návrat do Overworldu se standardně neomezuje (`alwaysAllowOverworld=true`).
- Trvalé odemčení (`unlockAfterFirstEntry=true`): po prvním průchodu s předmětem už ho hráč pro danou
  dimenzi nepotřebuje. Odemčení se ukládá do `world/data/DimensionControl.dat` a přežije smrt, odhlášení
  i restart serveru.
- Zablokovaného hráče, který stojí v portálu, vrátí na poslední bezpečné místo před portálem.
  Hlavně kvůli End portálu, pod kterým bývá láva.
- Operátoři (OP) mají obchvat (lze vypnout).

## Konfigurace

Soubor `config/dimensioncontrol.cfg` vznikne při prvním spuštění. **Ukázková pravidla pro Nether
(křesadlo) a End (oko Enderu) jsou hned aktivní.** Uprav je, nebo jim nastav `enabled=false`.

Každé pravidlo je jeden blok v kategorii `dimensions`. Nové pravidlo přidáš tak, že zkopíruješ blok
a dáš mu jiné jméno:

```
dimensions {
    twilight {
        I:dimensionId=7
        S:dimensionName=Twilight Forest
        B:enabled=true
        S:item=minecraft:diamond
        I:meta=-1
        S:itemName=
        S:message=&cDo dimenze &e%dim% &cse dostaneš jen s předmětem &e%item% &cv ruce.
        B:unlockAfterFirstEntry=true
    }
}
```

| Klíč | Význam |
|---|---|
| `dimensionId` | ID dimenze. Seznam všech ID vypíše příkaz `/dimaccess dims`. |
| `dimensionName` | Název pro `%dim%` ve zprávách. |
| `item` | Předmět ve tvaru `modid:item` (např. `minecraft:ender_eye`). U modových předmětů je to jejich registrační název. |
| `meta` | Metadata předmětu, `-1` = libovolná. |
| `itemName` | Vlastní název pro `%item%`. Prázdné = každý hráč uvidí název v jazyce svého klienta. |
| `message` | Chybová zpráva. Barvy přes `&` (`&c`, `&e`…), proměnné `%dim%`, `%item%`, `%player%`. |
| `unlockAfterFirstEntry` | `true` = po prvním vstupu s předmětem trvale odemčeno. |

Obecná nastavení (`general`):

| Klíč | Výchozí | Význam |
|---|---|---|
| `opsBypass` | `true` | OP mohou všude bez předmětu (a nic se jim neodemyká). |
| `creativeBypass` | `false` | Hráči v creative mohou všude bez předmětu. |
| `alwaysAllowOverworld` | `true` | Pravidlo pro dimenzi 0 se ignoruje. |
| `messageCooldownTicks` | `100` | Rozestup chybových zpráv (20 ticků = 1 s). |
| `portalCooldownTicks` | `40` | Cooldown portálu po zablokování. |
| `pushBackFromPortal` | `true` | Vrátit zablokovaného hráče z portálu na bezpečné místo. |
| `fallbackCheck` | `true` | Záložní kontrola pro mody, které by hlavní kontrolu obešly. |
| `logBlockedAttempts` | `false` | Zapisovat každé zablokování do logu. |
| `unlockMessage` | `&aDimenze &e%dim% &aje pro tebe nyní trvale odemčena.` | Zpráva po odemčení, prázdné = žádná. |

Změny configu se načtou příkazem `/dimaccess reload` bez restartu. Neznámý předmět v configu se ohlásí
v logu i v chatu. Do takové dimenze se pak s předmětem dostat nedá, dokud se chyba neopraví.

### ID dimenzí modů z vašeho serveru

Ověřeno na testovacím serveru s výchozími configy těch modů. **Na vašem serveru si je zkontroluj
příkazem `/dimaccess dims`**, protože ID jde v configu modů změnit.

| Mod | Dimenze (ID) |
|---|---|
| Twilight Forest 2.4.3 | Twilight Forest (7) |
| Aether Legacy 1.1.2.5 | Aether (4) |
| Aether II 1.6 | Aether (3), Dungeon (4) (jen ze statické analýzy, viz níže) |
| The Erebus 0.4.7 | Erebus (66) |
| Atum 0.6.77 | Atum (17) |
| The Betweenlands 1.0.6 | Betweenlands (20) |
| AbyssalCraft 1.9.1.3 | Abyssal Wasteland (50), Dreadlands (51), Omothol (52), Dark Realm (53) |
| Extra Utilities 1.2.12 | Deep Dark / „Underdark“ (-100), Last Millenium (-112) |
| Runic Dungeons 1.1.7b | Dungeon (-34) |
| Glacia 7.5b | **ID se volí při startu** (u nás vyšlo 2). Doporučuji v `Glacia.cfg` nastavit `UseCustomDimensionID=true`, jinak se ID může po přidání dalšího modu změnit. |

## Příkazy (OP)

| Příkaz | Co dělá |
|---|---|
| `/dimaccess rules` | Vypíše pravidla. |
| `/dimaccess dims` | Vypíše všechny dimenze na serveru s jejich ID a pravidly. |
| `/dimaccess list <hráč>` | Co má hráč odemčeno. |
| `/dimaccess grant <hráč> <dim>` | Ručně odemkne dimenzi. |
| `/dimaccess revoke <hráč> <dim\|all>` | Odebere odemčení. |
| `/dimaccess reload` | Znovu načte config. |

Hráče lze zadat i offline, pokud už někdy byl na serveru.

## Jak to funguje

Forge 1.7.10 nemá žádný event, kterým by šel teleport do jiné dimenze zrušit předem.
`PlayerChangedDimensionEvent` přichází až po něm. Mod je proto i coremod: na začátek dvou metod,
kterými v 1.7.10 projde každý standardní přesun hráče, vloží kontrolu:

1. `EntityPlayerMP.travelToDimension(int)`: vanilla portály a mody, které ji volají.
   Musí se hlídat zvlášť, protože vanilla při cestě do Endu přesune hráče na souřadnice
   spawnu Endu ještě *před* samotným přenosem.
2. `ServerConfigurationManager.transferPlayerToDimension(hráč, dim, Teleporter)`: metoda, kterou
   s vlastním teleporterem volají téměř všechny dimenzionální mody.

Pro mody, které by hráče přesouvaly „ručně“ mimo tyto metody, běží každý tick záložní kontrola.
Pozná změnu dimenze, která neprošla kontrolou, a hráče bez přístupu vrátí tam, odkud přišel.

Ochrana proti smyčkám a spamu:
- zprávy chodí nejvýš jednou za `messageCooldownTicks`,
- po zablokování dostane hráč cooldown portálu,
- vlastní přesuny modu se znovu nekontrolují,
- opakované vracení záložní kontrolou se zaloguje jako varování.

Když mod, jehož teleport byl zrušen, hráče i tak někam přesune (Runic Dungeons ho posílá na
souřadnice 7, 3, 7), vrátí se hráč na konci ticku na místo, kde stál.

## Otestováno

Všechno na skutečném dedikovaném serveru **Forge 1.7.10-10.13.4.1614** (Java 8). Hráče představovali
headless klienti přes síťový protokol 1.7.10 včetně FML handshake, takže portály spouštěla stejná
serverová logika jako u normálního hráče. Testy jsou ve složce [`test/`](test/).

**Vanilla a obecné chování: 47/47 kontrol.** [`test/e2e-test.js`](test/e2e-test.js) ověřuje:
- hráč bez předmětu neprojde Nether portálem, který zapálil jiný hráč, a dostane jednu zprávu;
- hráč s předmětem projde, dostane zprávu o odemčení a předmět mu zůstane;
- po odemčení projde bez předmětu, `revoke` ho znovu zablokuje, `grant` ho pustí;
- dva hráči ve stejném portálu ve stejnou chvíli: s předmětem projde, bez něj ne;
- End portál nad lávou: hráč bez oka Enderu je vrácen na bezpečné místo bez zranění;
  s okem projde;
- 11 s stání v End portálu = 3 zprávy (ne stovky);
- přesun stylem modů přes `transferPlayerToDimension`, přímé `travelToDimension`, styl Runic
  Dungeons a ruční přenos mimo obě metody (záložní kontrola hráče vrátí);
- OP obchvat (bez odemčení), návrat do Overworldu bez omezení;
- `/dimaccess` rules / dims / list / grant / revoke / reload, hlášení neznámého předmětu;
- odemčení přežije restart serveru (ověřeno ručně po testu).

**Skutečné mody: 12 portálů z 9 modů.** [`test/e2e-mods.js`](test/e2e-mods.js) má u každého portálu
dvě kontroly: hráč bez předmětu je zablokován a hráč s předmětem projde.

| Mod | Portál | Kudy jde přesun | Výsledek |
|---|---|---|---|
| Twilight Forest 2.4.3 | portál z květin | `transferPlayerToDimension` | ✅ |
| Aether Legacy 1.1.2.5 | glowstone portál | `transferPlayerToDimension` | ✅ |
| Glacia 7.5b | portál | `transferPlayerToDimension` | ✅ (viz poznámka) |
| The Erebus 0.4.7 | portál | `transferPlayerToDimension` (volá ho 2×) | ✅ |
| Atum 0.6.77 | portál | `transferPlayerToDimension` | ✅ |
| The Betweenlands 1.0.6 | portál ve stromě | `transferPlayerToDimension` | ✅ |
| AbyssalCraft 1.9.1.3 | Abyssal Wasteland, Dreadlands, Omothol | `transferPlayerToDimension` | ✅ (3 portály) |
| Extra Utilities 1.2.12 | Deep Dark, Last Millenium (pravý klik) | `transferPlayerToDimension` | ✅ (2 portály) |
| Runic Dungeons 1.1.7b | runový portál (pravý klik) | `travelToDimension` | ✅ (hráč zůstane na místě) |

Mod funguje i vedle coremodu Betweenlands, který upravuje stejnou třídu `ServerConfigurationManager`.
Statická analýza kódu všech těchto modů ukázala, že žádný z nich nepřesouvá hráče mimo dvě hlídané
metody. Záložní kontrola tak u nich slouží jen jako pojistka.

## Co otestované není / na co si dát pozor

- **Thermos / KCauldron / Crucible** (hybridní Bukkit servery) nejsou otestované. Hooky jsou psané pro
  čistý Forge. Na hybridu by v nejhorším případě fungovala jen záložní kontrola. Při startu je to vidět
  v logu: chybí řádek `Dimension access hooks are active`.
- **Aether II** se nedal spustit, protože potřebuje `gilded-games-util`. Podle analýzy kódu jde vstup
  přes `transferPlayerToDimension`, tedy přes hlídanou cestu, ale za běhu to ověřené není.
  Pozor: Aether II po smrti vrací hráče do dimenze, kde naposledy spal. Bez odemčení a bez předmětu
  (inventář je po smrti prázdný) ho mod nepustí a hráč se respawne v Overworldu.
- **Glacia 7.5b** (verze z CurseForge, kterou jsem testoval) shodí server krátce po vstupu hráče do své
  dimenze chybou `NoSuchMethodError: Block.func_149637_q` v `BlockGlaciaCrystalOfPower`. **Stejně padá
  i bez Dimension Control** (ověřeno zvlášť). Je to chyba té verze Glacie, ne tohoto modu.
- **AbyssalCraft:** do Dark Realm (53) se padá z Omotholu pod y=0 a hráče tam pošle i smrt bosse
  J'zahara. Pokud Dark Realm omezíš, hráč bez přístupu v Omotholu propadne do voidu a zemře.
  Doporučuji pro 53 pravidlo nedávat.
- **Achievementy:** Twilight Forest a AbyssalCraft dávají achievement za vstup ještě před teleportem,
  takže ho zablokovaný hráč dostane taky. Jde jen o kosmetiku.
- **Kdo už v dimenzi je** (odhlásil se v ní, nebo se tam respawnul po smrti), tomu mod nic nedělá.
  Kontroluje se vstup, ne pobyt.
- Kontroluje se jen předmět **v ruce** (vybraný slot hotbaru), tak jak bylo zadáno.

## Sestavení ze zdrojáků

Potřeba je JDK 21 (spouští Gradle) a JDK 8 (kompilace; když chybí, Gradle si ho stáhne sám).

```bash
JAVA_HOME=<cesta k JDK 21> ./gradlew build
```

Výsledek je `build/libs/DimensionControl-1.0.0.jar`. Verze `-dev.jar` je jen pro vývojové prostředí.
Build používá RetroFuturaGradle 1.4.9 a Gradle 8.14.3.

## Testy

- `testmod/`: pomocný mod jen pro testy (příkazy `/dctest`, které simulují různé způsoby teleportu).
  **Na ostrý server nepatří.**
- `test/e2e-test.js` a `test/e2e-mods.js`: běží proti dedikovanému serveru, který čte příkazy
  z `cmds.txt` a píše výstup do `server.out` (`online-mode=false`, flat svět). Potřebují balíček
  `minecraft-protocol` z npm. Postup je v hlavičce každého souboru.
- `reference-mods/`: jary testovaných modů stažené z CurseForge (cca 160 MB). Slouží jen k testům
  a dají se smazat.

## Licence

Public domain ([Unlicense](LICENSE)).
