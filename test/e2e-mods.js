// End-to-end test with real dimension mods installed on the server (Twilight Forest, Aether Legacy, Glacia,
// Erebus, Atum, Betweenlands, AbyssalCraft, Extra Utilities, Runic Dungeons). Each mod portal block is placed in
// the overworld and used by a bot exactly like a player would: standing in it or right-clicking it.
// Bob has nothing in hand and must stay out, Alice holds a diamond and must get in.
//
// Usage: NODE_PATH=<dir with minecraft-protocol> node e2e-mods.js <server dir> <port>

const fs = require('fs');
const L = require('./lib');

L.init(process.argv[2], parseInt(process.argv[3] || '25600', 10));
const { check, results, cmd, logSize, logSince, waitFor, console_, where, sleep, Bot, tp, clear } = L;
const CONFIG = L.CONFIG();

const ITEM = 'minecraft:diamond';

// block, metadata, how it is used, target dimension (name as shown by /dimaccess dims, resolved at runtime)
const PORTALS = [
  { mod: 'Twilight Forest', block: 'TwilightForest:tile.TFPortal', use: 'stand', dim: 'Twilight Forest' },
  { mod: 'Aether Legacy', block: 'aether_legacy:aether_portal', use: 'stand', dim: 'the_aether' },
  { mod: 'Glacia', block: 'Glacia:Glacialportal', use: 'stand', dim: 'Glacia' },
  { mod: 'Erebus', block: 'erebus:portal', use: 'stand', dim: 'Erebus' },
  { mod: 'Atum', block: 'atum:tile.portal', use: 'stand', dim: 'Atum' },
  { mod: 'Betweenlands', block: 'thebetweenlands:treePortalBlock', use: 'stand', dim: 'Betweenlands' },
  { mod: 'AbyssalCraft (Abyssal Wasteland)', block: 'abyssalcraft:portal', use: 'stand', dim: 'The Abyssal Wasteland' },
  { mod: 'AbyssalCraft (Dreadlands)', block: 'abyssalcraft:dreadportal', use: 'stand', dim: 'The Dreadlands' },
  { mod: 'AbyssalCraft (Omothol)', block: 'abyssalcraft:omotholportal', use: 'stand', dim: 'Omothol' },
  { mod: 'Extra Utilities (Deep Dark)', block: 'ExtraUtilities:dark_portal', meta: 0, use: 'click', dim: 'Underdark' },
  { mod: 'Extra Utilities (Last Millenium)', block: 'ExtraUtilities:dark_portal', meta: 2, use: 'click', dim: 'The Last Millenium' },
  { mod: 'Runic Dungeons', block: 'runicdungeons:tile.runicPortal', use: 'click', dim: 'Dungeon' },
];

async function main() {
  // Claim the server's mod list in the FML handshake: these mods are required on the client
  const modsLine = await console_('dctest mods', /DCTEST mods (\S+)/);
  const mods = /DCTEST mods (\S+)/.exec(modsLine)[1].split('|').filter(Boolean).map((m) => m.split('='));

  // Dimension IDs as registered on this server (Glacia picks its ID at startup)
  const dimsOut = await console_('dimaccess dims', /Overworld/);
  const dimIds = {};
  for (const m of dimsOut.matchAll(/INFO\]: (-?\d+) (.+?) (?:\(bez omezení\)|→)/g)) dimIds[m[2].trim()] = +m[1];
  for (const p of PORTALS) p.dimId = dimIds[p.dim];
  const missing = PORTALS.filter((p) => p.dimId === undefined);
  for (const p of missing) console.log(`SKIP  ${p.mod}: dimension "${p.dim}" is not registered (mod not installed)`);
  PORTALS.splice(0, PORTALS.length, ...PORTALS.filter((p) => p.dimId !== undefined));

  // One rule per mod dimension: hold a diamond
  let rules = '\ndimensions {\n';
  for (const id of [...new Set(PORTALS.map((p) => p.dimId))]) {
    rules += `    mod_${String(id).replace('-', 'm')} {\n        I:dimensionId=${id}\n        S:item=${ITEM}\n        B:unlockAfterFirstEntry=false\n        B:consumeItem=true\n    }\n`;
  }
  fs.appendFileSync(CONFIG, rules + '}\n');
  L.setGeneral('logBlockedAttempts', 'true');
  const reload = await console_('dimaccess reload', /Config znovu načten, pravidel: \d+/);
  const ruleCount = new Set(PORTALS.map((p) => p.dimId)).size + 2; // + the Nether and End examples
  check(`rules for ${PORTALS.length} mod portals loaded`, reload.includes(`pravidel: ${ruleCount}`), reload.match(/pravidel: \d+/)[0]);

  const alice = new Bot('Alice', mods);
  await alice.ready();
  const bob = new Bot('Bob', mods);
  await bob.ready();
  check('bots joined with the server mod list', alice.dim === 0 && bob.dim === 0);
  // Flat world ground level. Portals must stand on the ground: e.g. Aether Legacy only forgets that a player was in
  // its portal once they stand on a solid block again.
  const g = 4;
  const sx = Math.floor(alice.pos.x);
  const sz = Math.floor(alice.pos.z);
  await clear(alice);
  await clear(bob);

  let i = 0;
  for (const p of PORTALS) {
    const x = sx + 8 + 6 * (i % 6);
    const z = sz + 8 + 8 * Math.floor(i / 6);
    i++;
    const meta = p.meta || 0;
    cmd(`dctest place ${x} ${g} ${z} ${p.block} ${meta}`);
    if (p.use === 'stand') cmd(`dctest place ${x} ${g + 1} ${z} ${p.block} ${meta}`);
    await sleep(300);
    const front = [x + 0.5, g, z + 2.5];
    const inside = [x + 0.5, g, z + 0.5];

    // Bob: no item
    await tp(bob, ...front);
    await sleep(800);
    let mark = logSize();
    if (p.use === 'stand') {
      await tp(bob, ...inside);
    } else {
      rightClick(bob, x, g, z);
    }
    const blocked = await waitFor(() => new RegExp(`Blocked Bob from entering dimension ${p.dimId} `).test(logSince(mark)), 12000);
    await sleep(1000);
    const w = await where('Bob');
    check(`${p.mod}: blocked without item`, !!blocked && bob.dim === 0 && w.dim === 0, `dim=${w.dim}`);
    if (p.use === 'click') {
      check(`${p.mod}: Bob still stands where he clicked`, Math.abs(w.x - front[0]) < 0.6 && Math.abs(w.z - front[2]) < 0.6, `x=${w.x} y=${w.y} z=${w.z}`);
    }
    await tp(bob, x + 0.5, g, z + 5.5); // out of the way

    // Alice: holds three diamonds, entering must cost exactly one (mods reach the hooks in different ways)
    cmd(`dctest hold Alice ${ITEM} 3`);
    await waitFor(() => alice.inv[36] === 264 && alice.counts[36] === 3, 5000, 'Alice holding 3 diamonds');
    await tp(alice, ...front);
    await sleep(800);
    if (p.use === 'stand') {
      await tp(alice, ...inside);
    } else {
      rightClick(alice, x, g, z);
    }
    await waitFor(() => alice.dim === p.dimId, 20000);
    // The first visit generates terrain and portals, which can stall the server for a while
    const wa = await where('Alice', 120000);
    check(`${p.mod}: allowed with the item (dim ${p.dimId})`, wa.dim === p.dimId && alice.dim === p.dimId, `server dim=${wa.dim} client dim=${alice.dim}`);
    await sleep(500);
    check(`${p.mod}: took exactly one item`, L.held(alice) === 2, `held=${L.held(alice)}`);
    if (wa.dim !== 0) {
      cmd('dctest manual Alice 0');
      await waitFor(() => alice.dim === 0, 60000, 'Alice back');
      await where('Alice', 60000);
      await sleep(1000);
    }
  }

  check('no bot was kicked', !alice.kicked && !bob.kicked, `${alice.kicked || ''} ${bob.kicked || ''}`);
  alice.quit();
  bob.quit();
  await sleep(500);
  const failed = results.filter((r) => !r.ok);
  console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
  process.exit(failed.length ? 1 : 0);
}

// Right-click the top face of a block with whatever is in hand
function rightClick(bot, x, y, z) {
  bot.client.write('block_place', {
    location: { x, y, z },
    direction: 1,
    heldItem: { blockId: -1 },
    cursorX: 8,
    cursorY: 16,
    cursorZ: 8,
  });
}

main().catch((e) => {
  console.log('ERROR', e.stack || e);
  process.exit(2);
});
