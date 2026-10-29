// End-to-end test of DimensionControl on a real Forge 1.7.10 dedicated server.
// Players are headless protocol clients (minecraft-protocol), the server is driven through its console.
//
// Usage: NODE_PATH=<dir with minecraft-protocol> node e2e-test.js <server dir> <port>
// The server must run with stdin fed from <server dir>/cmds.txt, its output in <server dir>/server.out,
// online-mode=false, a flat world, DimensionControl + the dctest helper mod in mods/.

const fs = require('fs');
const L = require('./lib');

L.init(process.argv[2], parseInt(process.argv[3] || '25599', 10));
const { check, results, cmd, logSize, logSince, waitFor, console_, where, sleep, Bot, DENY, UNLOCK, tp, give, clear,
  backToOverworld, setGeneral } = L;
const CONFIG = L.CONFIG();

async function main() {
  // One after another: Forge 1.7.10 shares one internal channel between concurrent FML handshakes
  const alice = new Bot('Alice');
  await alice.ready();
  const bob = new Bot('Bob');
  await bob.ready();
  const spawn = { x: Math.floor(alice.pos.x), y: Math.round(alice.pos.y), z: Math.floor(alice.pos.z) };
  console.log('spawn', spawn, 'dims', alice.dim, bob.dim);
  check('bots joined the overworld', alice.dim === 0 && bob.dim === 0);

  const g = spawn.y; // ground level players stand on
  const px = spawn.x + 6;
  const pz = spawn.z;
  for (const name of ['Alice', 'Bob']) {
    cmd(`clear ${name}`);
    cmd(`gamemode 0 ${name}`);
    cmd(`dimaccess revoke ${name} all`);
  }

  // ---- Nether portal frame, lit by Alice with flint and steel ----
  for (let x = px; x <= px + 3; x++) {
    cmd(`setblock ${x} ${g} ${pz} minecraft:obsidian`);
    cmd(`setblock ${x} ${g + 4} ${pz} minecraft:obsidian`);
  }
  for (let y = g + 1; y <= g + 3; y++) {
    cmd(`setblock ${px} ${y} ${pz} minecraft:obsidian`);
    cmd(`setblock ${px + 3} ${y} ${pz} minecraft:obsidian`);
  }
  await give(alice, 'minecraft:flint_and_steel');
  const front = [px + 1.5, g, pz + 2.5]; // safe spot in front of the portal
  const inside = [px + 1.5, g + 1, pz + 0.5];
  await tp(alice, ...front);
  await sleep(500);
  alice.client.write('block_place', {
    location: { x: px + 1, y: g, z: pz },
    direction: 1,
    heldItem: { blockId: 259, itemCount: 1, itemDamage: 0, nbtData: undefined },
    cursorX: 8,
    cursorY: 16,
    cursorZ: 8,
  });
  const lit = await console_(`testforblock ${px + 1} ${g + 1} ${pz} minecraft:portal`, /Successfully found the block|did not match|is not/);
  check('Alice lit the Nether portal', /Successfully found the block/.test(lit));

  // ---- T1: Bob, no item, walks into the portal Alice opened ----
  await tp(bob, ...front);
  await sleep(1000); // let the server record the safe spot
  let c = bob.chat.length;
  await tp(bob, ...inside);
  await sleep(6000);
  let w = await where('Bob');
  check('T1 Bob without item stays in the overworld', bob.dim === 0 && w.dim === 0, `dim=${w.dim}`);
  check('T1 Bob got the denial message once', bob.messagesSince(c, DENY).length === 1, JSON.stringify(bob.chat.slice(c)));
  check('T1 message names the item for client translation', bob.chat.slice(c).some((m) => m.includes('{item.flintAndSteel.name}')));
  check('T1 Bob was pushed back out of the portal', Math.abs(w.z - (pz + 2.5)) < 0.6, `z=${w.z} expected ${pz + 2.5}`);

  // ---- T2: Alice holds flint and steel and walks in ----
  c = alice.chat.length;
  await tp(alice, ...inside);
  await waitFor(() => alice.dim === -1, 10000);
  check('T2 Alice with the item enters the Nether', alice.dim === -1);
  check('T2 Alice got the unlock message', alice.messagesSince(c, UNLOCK).length === 1, JSON.stringify(alice.chat.slice(c)));
  let out = await console_('dimaccess list Alice', /má odemčeno/);
  check('T2 unlock is stored', /Alice.*má odemčeno: -1 \(Nether\)/.test(out));
  out = await console_('clear Alice minecraft:flint_and_steel', /Cleared the inventory|Could not clear/);
  check('T2 flint and steel was not consumed', /removing 1 items/.test(out), out.trim().split('\n').pop());

  // ---- T3: return to the overworld is never restricted ----
  cmd('dctest transfer Alice 0');
  await waitFor(() => alice.dim === 0, 5000);
  check('T3 return to the overworld without item', alice.dim === 0);

  // ---- T4: unlocked, no item -> allowed ----
  await tp(alice, ...front);
  await sleep(1000);
  c = alice.chat.length;
  await tp(alice, ...inside);
  await waitFor(() => alice.dim === -1, 10000);
  check('T4 unlocked Alice enters without the item', alice.dim === -1);
  check('T4 no second unlock message', alice.messagesSince(c, UNLOCK).length === 0);
  await backToOverworld(alice);

  // ---- T5: admin revokes, access is gone again ----
  out = await console_('dimaccess revoke Alice -1', /odebráno|neměl/);
  check('T5 /dimaccess revoke', /odebráno odemčení dimenze -1/.test(out));
  await tp(alice, ...front);
  await sleep(1000);
  c = alice.chat.length;
  await tp(alice, ...inside);
  await sleep(6000);
  check('T5 revoked Alice is blocked again', alice.dim === 0 && alice.messagesSince(c, DENY).length === 1);

  // ---- T6: admin grants ----
  out = await console_('dimaccess grant Bob -1', /odemčena pro|už má/);
  check('T6 /dimaccess grant', /odemčena pro Bob/.test(out));
  await tp(bob, ...front);
  await sleep(1000);
  await tp(bob, ...inside);
  await waitFor(() => bob.dim === -1, 10000);
  check('T6 granted Bob enters without item', bob.dim === -1);
  await backToOverworld(bob);
  await console_('dimaccess revoke Bob all', /odebráno/);

  // ---- T7: mod-style transfer via ServerConfigurationManager with a custom teleporter ----
  await tp(bob, ...front);
  await sleep(500);
  c = bob.chat.length;
  out = await console_('dctest transfer Bob -1', /DCTEST transfer Bob/);
  await sleep(500);
  check('T7 SCM transfer without item is blocked', bob.dim === 0 && /now in 0/.test(out), out.trim().split('\n').pop());
  check('T7 denial message', bob.messagesSince(c, DENY).length === 1);
  await give(bob, 'minecraft:flint_and_steel');
  cmd('dctest transfer Bob -1');
  await waitFor(() => bob.dim === -1, 5000);
  check('T7 SCM transfer with item is allowed', bob.dim === -1);
  await backToOverworld(bob);
  await console_('dimaccess revoke Bob all', /odebráno/);
  await clear(bob);

  // ---- T8: travelToDimension(1) directly; vanilla would first move the player to the End platform coords ----
  await sleep(5500); // message cooldown since T7
  await tp(bob, ...front);
  await sleep(500);
  c = bob.chat.length;
  await console_('dctest travel Bob 1', /DCTEST travel Bob/);
  await sleep(500);
  w = await where('Bob');
  check('T8 travelToDimension to the End without eye is blocked', w.dim === 0 && bob.dim === 0);
  check('T8 player was not moved to the End platform coordinates', Math.abs(w.x - front[0]) < 0.6 && Math.abs(w.z - front[2]) < 0.6, `x=${w.x} z=${w.z}`);
  check('T8 denial message', bob.messagesSince(c, DENY).length >= 1);

  // ---- T8b: Runic Dungeons style: the portal moves the player to (7, 3, 7) even when the transfer is cancelled ----
  await sleep(5500);
  c = bob.chat.length;
  await console_('dctest runic Bob -1', /DCTEST runic Bob/);
  await sleep(1000);
  w = await where('Bob');
  check('T8b Runic-style portal without item is blocked', w.dim === 0 && bob.dim === 0);
  check('T8b Bob was put back where he stood, not left at 7,3,7', Math.abs(w.x - front[0]) < 0.6 && Math.abs(w.z - front[2]) < 0.6, `x=${w.x} y=${w.y} z=${w.z}`);
  check('T8b denial message', bob.messagesSince(c, DENY).length === 1);

  // ---- T9: hand-written transfer that bypasses both hooks -> fallback sends the player back ----
  await sleep(5500); // message cooldown
  c = bob.chat.length;
  const mark = logSize();
  const r0 = bob.respawns.length;
  cmd('dctest manual Bob -1');
  await waitFor(() => bob.respawns.length >= r0 + 2 && bob.dim === 0, 5000, 'Bob sent back');
  await sleep(500);
  w = await where('Bob');
  check('T9 fallback sent Bob back to the overworld', w.dim === 0 && bob.dim === 0, `dim=${w.dim}`);
  check('T9 Bob is back where he stood', Math.abs(w.x - front[0]) < 0.6 && Math.abs(w.z - front[2]) < 0.6, `x=${w.x} z=${w.z}`);
  check('T9 denial message', bob.messagesSince(c, DENY).length === 1);
  check('T9 fallback logged', /Fallback check: Bob got into dimension -1/.test(logSince(mark)));
  await give(bob, 'minecraft:flint_and_steel');
  cmd('dctest manual Bob -1');
  await waitFor(() => bob.dim === -1, 5000);
  await sleep(1500);
  check('T9 hand-written transfer with item stays allowed', bob.dim === -1);
  await backToOverworld(bob);
  await console_('dimaccess revoke Bob all', /odebráno/);
  await clear(bob);

  // ---- T10: End portal over lava ----
  const ex = spawn.x - 10;
  const ez = spawn.z;
  await console_(`dctest endportal ${ex} ${g} ${ez}`, /DCTEST endportal built/);
  const endFront = [ex + 3.5, g, ez + 0.5];
  await tp(bob, ...endFront);
  await sleep(1000);
  c = bob.chat.length;
  await tp(bob, ex + 0.5, g, ez + 0.5);
  await sleep(2000);
  w = await where('Bob');
  check('T10 End portal without eye is blocked', w.dim === 0 && bob.dim === 0);
  check('T10 Bob was moved back out of the End portal', Math.abs(w.x - endFront[0]) < 0.6, `x=${w.x} expected ${endFront[0]}`);
  check('T10 Bob is unhurt', w.hp >= 20, `hp=${w.hp}`);
  check('T10 one denial message', bob.messagesSince(c, DENY).length === 1, JSON.stringify(bob.chat.slice(c)));
  await give(bob, 'minecraft:ender_eye');
  c = bob.chat.length;
  await tp(bob, ex + 0.5, g, ez + 0.5);
  await waitFor(() => bob.dim === 1, 5000);
  check('T10 End portal with eye of ender works', bob.dim === 1);
  check('T10 unlock message for the End', bob.messagesSince(c, UNLOCK).length === 1);
  await backToOverworld(bob);
  await console_('dimaccess revoke Bob all', /odebráno/);
  await clear(bob);

  // ---- T11: no chat spam while standing in an End portal (push back disabled) ----
  setGeneral('pushBackFromPortal', 'false');
  out = await console_('dimaccess reload', /Config znovu načten/);
  await tp(bob, ...endFront);
  await sleep(500);
  c = bob.chat.length;
  await tp(bob, ex + 0.5, g, ez + 0.5);
  await sleep(11000);
  const spam = bob.messagesSince(c, DENY).length;
  check('T11 standing 11 s in the End portal: at most 3 messages', spam >= 2 && spam <= 3, `messages=${spam}`);
  check('T11 still in the overworld', bob.dim === 0);
  setGeneral('pushBackFromPortal', 'true');
  await console_('dimaccess reload', /Config znovu načten/);
  await tp(bob, ...endFront);

  // ---- T12: operators bypass ----
  await console_('op Bob', /Opped Bob/);
  await tp(bob, ...front);
  await sleep(3000); // portal cooldown left over from T11
  await tp(bob, ...inside);
  await waitFor(() => bob.dim === -1, 10000);
  check('T12 OP enters without item', bob.dim === -1);
  out = await console_('dimaccess list Bob', /má odemčeno/);
  check('T12 OP bypass does not unlock', /má odemčeno: nic/.test(out));
  await backToOverworld(bob);
  await console_('deop Bob', /De-opped Bob/);

  // ---- T13: two players in the same portal at the same time ----
  await clear(alice);
  await clear(bob);
  await give(alice, 'minecraft:flint_and_steel');
  await tp(alice, ...front);
  await tp(bob, px + 1.5, g, pz + 3.5);
  await sleep(1000);
  const bc = bob.chat.length;
  await Promise.all([tp(alice, ...inside), tp(bob, px + 2.5, g + 1, pz + 0.5)]);
  await sleep(6000);
  check('T13 same portal: Alice (item) went through', alice.dim === -1);
  check('T13 same portal: Bob (no item) stayed', bob.dim === 0 && bob.messagesSince(bc, DENY).length === 1);
  await backToOverworld(alice);

  // ---- T14: unknown item in the config is reported ----
  fs.appendFileSync(CONFIG, '\ndimensions {\n    broken {\n        I:dimensionId=7\n        S:item=nosuchmod:nothing\n    }\n}\n');
  out = await console_('dimaccess reload', /Config znovu načten[\s\S]*unknown item 'nosuchmod:nothing'/);
  check('T14 reload reports an unknown item', !!out);
  out = await console_('dimaccess rules', /neznámý předmět/);
  check('T14 /dimaccess rules marks the broken rule', /7 .*nosuchmod:nothing.*neznámý předmět/.test(out));

  out = await console_('dimaccess dims', /1 The End → minecraft:ender_eye/);
  check('T15 /dimaccess dims lists dimensions with their rules', /0 Overworld.*bez omezení/.test(out) && /1 The End.*ender_eye/.test(out), out.split('\n').filter((l) => /INFO\]: §e/.test(l)).join(' | '));

  check('no bot was kicked', !alice.kicked && !bob.kicked, `${alice.kicked || ''} ${bob.kicked || ''}`);

  alice.quit();
  bob.quit();
  await sleep(500);
  const failed = results.filter((r) => !r.ok);
  console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
  process.exit(failed.length ? 1 : 0);
}

main().catch((e) => {
  console.log('ERROR', e.stack || e);
  process.exit(2);
});
