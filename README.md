# Dimension Control (Forge 1.7.10)

Serverový mod: do zvolených dimenzí se hráč dostane, jen když drží v ruce nastavený předmět.
Kontrola probíhá u každého hráče zvlášť. Standardně se předmět nespotřebuje, volitelně se při vstupu vezme
1 kus. Volitelně si hráč po prvním vstupu dimenzi trvale odemkne a předmět pak už nepotřebuje.

**Hotový mod:** [`dist/DimensionControl-1.2.0.jar`](dist/DimensionControl-1.2.0.jar). Jar patří do složky `mods` na serveru.
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
- Spotřeba předmětu (`consumeItem=true`): při vstupu se hráči vezme 1 kus z ruky. Nastavuje se pro každou
  dimenzi zvlášť, viz [Spotřeba předmětu](#spotřeba-předmětu).
- Zablokovaný hráč nedostane achievement za vstup ani dárky Aetheru (kniha, padák). Dostane je až při
  skutečném vstupu.
- Operátoři (OP) se kontrolují stejně jako ostatní. Obchvat pro OP jde zapnout (`opsBypass=true`).
- Překlep v configu neotevře portály: soubor s chybou se nenačte a platí poslední dobrá verze,
  viz [Chyba v configu](#chyba-v-configu).
- Chyby v configu (neznámý předmět, neexistující dimenze) vypíše `/dimaccess rules` a log. Volitelně je OP
  uvidí i v chatu po přihlášení (`showAdminNotices=true`).

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
        B:consumeItem=false
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
| `consumeItem` | `true` = při vstupu se vezme 1 kus předmětu z ruky. Výchozí `false`. |

Obecná nastavení (`general`):

| Klíč | Výchozí | Význam |
|---|---|---|
| `opsBypass` | `false` | `true` = OP mohou všude bez předmětu (a nic se jim neodemyká). V singleplayeru se zapnutými cheaty je OP i majitel světa. |
| `creativeBypass` | `false` | Hráči v creative mohou všude bez předmětu. |
| `alwaysAllowOverworld` | `true` | Pravidlo pro dimenzi 0 se ignoruje. |
| `messageCooldownTicks` | `100` | Rozestup chybových zpráv (20 ticků = 1 s). |
| `portalCooldownTicks` | `40` | Cooldown portálu po zablokování. |
| `pushBackFromPortal` | `true` | Vrátit zablokovaného hráče z portálu na bezpečné místo. |
| `fallbackCheck` | `true` | Záložní kontrola pro mody, které by hlavní kontrolu obešly. |
| `logBlockedAttempts` | `false` | Zapisovat každé zablokování do logu. |
| `unlockMessage` | `&aDimenze &e%dim% &aje pro tebe nyní trvale odemčena.` | Zpráva po odemčení, prázdné = žádná. |
| `consumeMessage` | `&7Vstup do &e%dim% &7tě stál &e%item%&7.` | Zpráva po spotřebování předmětu, prázdné = žádná. |
| `showAdminNotices` | `false` | `true` = OP uvidí po přihlášení chyby v configu a hráč dostane zprávu, když ho pustil jen OP nebo creative obchvat. |

Změny configu se načtou příkazem `/dimaccess reload` bez restartu. Neznámý předmět nebo dimenze, která
na serveru neexistuje, se ohlásí v logu, po `/dimaccess reload` a v `/dimaccess rules`. Do dimenze s neznámým
předmětem se s předmětem dostat nedá, dokud se chyba neopraví.

### Chyba v configu

Soubor, který Forge neumí přečíst, sám přejmenuje na `.errored` a začne s prázdným. Všechna pravidla by zmizela
a všechny portály by se otevřely. Hodnotu, kterou nepřečte, zase potichu nahradí výchozí. Mod proto soubor
zkontroluje dřív, než ho Forge dostane:

- Soubor s chybou se nenačte a zůstane beze změny. Chyba i s číslem řádku je v logu, po `/dimaccess reload`
  a v `/dimaccess rules`.
- Platí poslední dobrá verze: po `reload` ta, která už běží, po restartu kopie
  `config/dimensioncontrol.cfg.lastgood`. Tu mod uloží po každém úspěšném načtení.
- Když dobrá verze ještě neexistuje, žádná dimenze omezená není a výpis chyb to řekne.
- Hlídá se `}` navíc nebo chybějící, `{` na vlastním řádku, překlep nebo mezera navíc v čísle a v `true`/`false`
  a dva bloky se stejným názvem po kopírování.
- Pravidlo bez `dimensionId` (spadne na dimenzi 0, která se neomezuje) se ohlásí jako chyba pravidla.

### Spotřeba předmětu

| `consumeItem` | `unlockAfterFirstEntry` | Co se stane |
|---|---|---|
| `false` | `true` | Předmět stačí ukázat jednou, zůstane hráči. Dimenze je pak odemčená. |
| `false` | `false` | Předmět musí držet při každém vstupu, zůstane mu. |
| `true` | `true` | Předmět se vezme jen při prvním vstupu a dimenze je pak natrvalo odemčená. **Doporučeno pro předměty z questů:** kdo ho jednou odevzdal, nemůže se zaseknout. |
| `true` | `false` | Každý vstup stojí 1 kus. Když dojdou, hráč se dovnitř nedostane, takže předmět musí jít znovu získat. |

Bere se vždy právě 1 kus z vybraného slotu. Nic se nebere hráčům, které pustil obchvat nebo trvalé odemčení,
hráčům v creative a zablokovaným hráčům.

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
| Glacia 7.5b | **ID se volí při startu** (u nás vyšlo 2) a po přidání dalšího modu se může změnit. V `Glacia.cfg` proto nastav najednou `UseCustomDimensionID=true` a `I:Glacia` na číslo, které teď ukazuje `/dimaccess dims`. Samotné `UseCustomDimensionID=true` by dimenzi přesunulo na výchozích 6. |

## Příkazy (OP)

Všechny příkazy vyžadují OP. Hráč bez OP dostane odmítnutí a sám si nic neodemkne (otestováno). V singleplayeru
s cheaty je OP majitel světa a na LAN s cheaty mají příkazy všichni, takže se to pozná až na serveru.

| Příkaz | Co dělá |
|---|---|
| `/dimaccess rules` | Vypíše pravidla (včetně spotřeby), stav hlavní kontroly, OP obchvatu a chyby v configu. |
| `/dimaccess dims` | Vypíše všechny dimenze na serveru s jejich ID a pravidly. |
| `/dimaccess check <hráč> [dim]` | U online hráče ukáže pro každé pravidlo, jestli by ho pustilo a proč (nic nemění). |
| `/dimaccess list <hráč>` | Co má hráč odemčeno. |
| `/dimaccess grant <hráč> <dim>` | Ručně odemkne dimenzi. Platí u pravidel s `unlockAfterFirstEntry=true`, u ostatních příkaz upozorní, že se odemčení nepoužije. |
| `/dimaccess revoke <hráč> <dim\|all>` | Odebere odemčení. |
| `/dimaccess reload` | Znovu načte config. |

Hráče lze zadat i offline, pokud už někdy byl na serveru.

## Mod jako by nefungoval?

1. **`/dimaccess check <tvoje jméno>`**: ukáže u každé dimenze, jestli tě pustí a proč.
   „obchvat: OP (opsBypass=true)“ znamená, že se kontrola na tebe vůbec nevztahuje.
   Vyzkoušej to s `opsBypass=false` nebo jako hráč bez OP.
2. **`/dimaccess rules`**: první řádek musí říkat „Hlavní kontrola: aktivní“. Pod ním jsou vypsané chyby
   v configu (překlep v názvu předmětu, dimenze, která neexistuje).
3. **`/dimaccess dims`**: zkontroluj, že `dimensionId` v pravidlech odpovídají skutečným ID na serveru.
   Pravidlo pro dimenzi, která neexistuje, ukáže `/dimaccess check` jako `NEHLÍDÁ`.

## Questy (HQM, BetterQuesting, CustomNPCs)

Dimenze se dá otevřít dvěma způsoby: quest dá předmět, který hráč donese k portálu, nebo quest spustí
`/dimaccess grant` a hráč nemá co ztratit ani půjčit. Dimension Control pro to nepotřebuje žádnou úpravu.

| Mod | Jak na to | Jak je to ověřené |
|---|---|---|
| HQM The Journey 4.4.4 | Odměna příkazem v HQM není, takže klíčem je předmět. Pravidlo ho má v `item`, ideálně s `consumeItem=true` a `unlockAfterFirstEntry=true`. Předmět se vezme při prvním vstupu a dimenze zůstane odemčená. | Chybějící příkazy jsou z kódu HQM. Pravidlo se spotřebou a odemčením je otestované, přímo s předmětem z questu ne. |
| BetterQuesting 3.0.328 | Sám typy odměn nemá, potřebuje Standard Expansion. Tam odměna `command`: `/dimaccess grant VAR_NAME <dim>`. | Ze zdrojáků GTNH verze Standard Expansion, nespuštěno. |
| CustomNPCs | V questu Advanced, Command: `/dimaccess grant @dp <dim>`. `@dp` dosadí jméno hráče. | Ze zdrojáků, jar jsem neměl. Cestu přes command block ověřil pomocný mod. |

- **CustomNPCs na dedikovaném serveru** potřebuje `enable-command-block=true` v `server.properties`, protože příkaz
  spouští přes dočasný command block. Bez toho se quest dokončí a dimenze zůstane bez hlášky zavřená. Test ukázal obě
  varianty. V singleplayeru a na LAN je to zapnuté vždy.
- **`OpsOnly=true`** v `config/CustomNpcs.cfg`: podle zdrojáků si jinak NPC hůlku vyrobí kdokoli a může s ní upravit
  questy. To je potřeba zavřít dřív, než quest rozdává přístup.
- Když má dimenzi otevírat jen quest, dej pravidlu předmět, který v běžné hře nikdo nezíská (`minecraft:command_block`).
- **Hardcore režim HQM** je ve výchozím stavu vypnutý. Po zapnutí (`/hqm hardcore`) stojí každý pád do voidu v Aetheru
  s modem Aether Void jeden život (otestováno). Po posledním životě hráče na serveru zabanuje a v singleplayeru smaže
  svět (z kódu). BetterQuesting má podobný `/bq_admin hardcore` (z kódu).
- HQM má i vlastní blok Quest Portal, kterým projde jen hráč se splněným questem (z kódu, nezkoušeno).

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

**Vanilla a obecné chování: 82/82 kontrol.** [`test/e2e-test.js`](test/e2e-test.js) ověřuje:
- hráč bez předmětu neprojde Nether portálem, který zapálil jiný hráč, a dostane jednu zprávu;
- hráč s předmětem projde, dostane zprávu o odemčení a předmět mu zůstane;
- po odemčení projde bez předmětu, `revoke` ho znovu zablokuje, `grant` ho pustí;
- dva hráči ve stejném portálu ve stejnou chvíli: s předmětem projde, bez něj ne;
- End portál nad lávou: hráč bez oka Enderu je vrácen na bezpečné místo bez zranění;
  s okem projde;
- 11 s stání v End portálu = 3 zprávy (ne stovky);
- přesun stylem modů přes `transferPlayerToDimension`, přímé `travelToDimension`, styl Runic
  Dungeons a ruční přenos mimo obě metody (záložní kontrola hráče vrátí);
- OP je ve výchozím stavu blokovaný jako ostatní; s `opsBypass=true` projde a nic se mu neodemkne;
  návrat do Overworldu bez omezení;
- spotřeba: přes portál (oba hooky), `transferPlayerToDimension`, styl Runic Dungeons i ruční přenos se vezme
  právě 1 kus a stačí i poslední kus ve stacku; s odemčením jen poprvé; špatný předmět, creative a OP obchvat
  o nic nepřijdou;
- hlášky `showAdminNotices`: ve výchozím stavu vypnuté, po zapnutí se ukážou;
- `/dimaccess` rules / dims / check / list / grant / revoke / reload; hlášení neznámého předmětu
  a neexistující dimenze; hráč bez OP je odmítnut a sám si nic neodemkne;
- odemčení přežije restart serveru (ověřeno ručně po testu).

**Upravovaný config: 31/31 kontrol, včetně skutečných restartů.** [`test/e2e-config.js`](test/e2e-config.js) ověřuje:
- `}` navíc, číslo s mezerou, `true` s mezerou, dvojitý blok, chybějící `}`, `{` na vlastním řádku: soubor zůstane
  beze změny, nevznikne `.errored`, pravidla platí dál a hráč bez předmětu je pořád blokovaný;
- poznámka s vykřičníkem a skutečný config z ostrého serveru projdou bez chyby;
- smazaný blok po `reload` zmizí, pravidlo bez `dimensionId` se ohlásí;
- `grant` přes dočasný command block nic neudělá s `enable-command-block=false` a funguje s `true`;
- chybný config bez poslední dobré verze: žádná dimenze není omezená a výpis to řekne.

**Skutečné mody: 12 portálů z 9 modů.** [`test/e2e-mods.js`](test/e2e-mods.js) běží na jednom dedikovaném serveru
se všemi mody včetně HQM The Journey, BetterQuesting, Aether Void, Plastic Ore Toggle a Dedicated Server Fix.
U každého portálu je hráč bez předmětu zablokován, hráč s předmětem projde a vezme se mu právě 1 kus. Zablokovaný hráč
nedostal žádný achievement ani dárek, hráč po skutečném vstupu ano.

| Mod | Portál | Kudy jde přesun | Výsledek |
|---|---|---|---|
| Twilight Forest 2.4.3 | portál z květin | `transferPlayerToDimension` | ✅ |
| Aether Legacy 1.1.2.5 | glowstone portál | `transferPlayerToDimension` | ✅ |
| Glacia 7.5b | portál | `transferPlayerToDimension` | ✅ |
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
- **Glacia 7.5b** shodí dedikovaný server krátce po vstupu hráče do své dimenze chybou
  `NoSuchMethodError: Block.func_149637_q`. Stejně padá i bez Dimension Control a v singleplayeru ani na LAN se to
  neprojeví. Opravuje to [Dedicated Server Fix](https://github.com/Garon92/RevenModFixes).
- **AbyssalCraft:** do Dark Realm (53) se padá z Omotholu pod y=0 a hráče tam pošle i smrt bosse
  J'zahara. Pokud Dark Realm omezíš, hráč bez přístupu v Omotholu propadne do voidu a zemře.
  Doporučuji pro 53 pravidlo nedávat.
- **Skutečný herní klient** se v testech nespouštěl. Portály spouštěli headless klienti, takže vzhled na obrazovce
  hráče ověřený není.
- **Kdo už v dimenzi je** (odhlásil se v ní, nebo se tam respawnul po smrti), tomu mod nic nedělá.
  Kontroluje se vstup, ne pobyt.
- Kontroluje se jen předmět **v ruce** (vybraný slot hotbaru), tak jak bylo zadáno.

## Sestavení ze zdrojáků

Potřeba je JDK 21 (spouští Gradle) a JDK 8 (kompilace; když chybí, Gradle si ho stáhne sám).

```bash
JAVA_HOME=<cesta k JDK 21> ./gradlew build
```

Výsledek je `build/libs/DimensionControl-1.2.0.jar`. Verze `-dev.jar` je jen pro vývojové prostředí.
Build používá RetroFuturaGradle 1.4.9 a Gradle 8.14.3.

## Testy

- `testmod/`: pomocný mod jen pro testy (příkazy `/dctest`, které simulují různé způsoby teleportu).
  **Na ostrý server nepatří.**
- `test/e2e-test.js`, `test/e2e-config.js` a `test/e2e-mods.js`: běží proti dedikovanému serveru, který čte příkazy
  z `cmds.txt` a píše výstup do `server.out` (`online-mode=false`, flat svět). Potřebují balíček
  `minecraft-protocol` z npm. Postup je v hlavičce každého souboru. `test/server.sh` server spouští a zastavuje,
  `e2e-config.js` ho používá sám kvůli restartům.
- `reference-mods/`: jary testovaných modů stažené z CurseForge (cca 160 MB). Slouží jen k testům
  a dají se smazat.

## Licence

Public domain ([Unlicense](LICENSE)).
