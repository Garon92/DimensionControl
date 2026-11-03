// How DimensionControl handles broken and hand-edited configs, including real server restarts.
// Usage: JAVA8=<java 8 binary> NODE_PATH=<dir with minecraft-protocol> node e2e-config.js <server dir> <port>
// The server dir needs DimensionControl + the dctest helper in mods/, and the server must not be running.

const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const L = require('./lib');

const DIR = process.argv[2];
L.init(DIR, parseInt(process.argv[3] || '25599', 10));
const { check, results, cmd, logSize, logSince, waitFor, console_, sleep, Bot } = L;
const CONFIG = path.join(DIR, 'config', 'dimensioncontrol.cfg');
const LASTGOOD = `${CONFIG}.lastgood`;
const PROPS = path.join(DIR, 'server.properties');

const HEALTHY = 'dimensions {\n    nether {\n        I:dimensionId=-1\n        S:dimensionName=Nether\n        S:item=minecraft:diamond\n    }\n}\n';
const server = (action) => execFileSync(path.join(__dirname, 'server.sh'), [action, DIR], { stdio: 'pipe', timeout: 240000 });
const errored = () => fs.readdirSync(path.join(DIR, 'config')).filter((f) => f.includes('.errored'));
const read = () => fs.readFileSync(CONFIG, 'utf8');
const write = (text) => fs.writeFileSync(CONFIG, text);

async function rules() {
  const mark = logSize();
  cmd('dimaccess rules');
  await waitFor(() => /Hlavní kontrola/.test(logSince(mark)), 5000, 'dimaccess rules');
  await sleep(400);
  return logSince(mark);
}

async function reload() {
  const mark = logSize();
  cmd('dimaccess reload');
  await waitFor(() => /Config znovu načten/.test(logSince(mark)), 5000, 'dimaccess reload');
  await sleep(400);
  return logSince(mark);
}

async function isBlocked() {
  const out = await console_('dctest transfer Bob -1', /DCTEST transfer Bob/);
  const blocked = /now in 0/.test(out);
  if (!blocked) {
    cmd('dctest manual Bob 0');
    await sleep(1500);
  }
  return blocked;
}

async function start() {
  server('start');
  const bob = new Bot('Bob');
  await bob.ready();
  return bob;
}

async function stop(bob) {
  bob.quit();
  await sleep(500);
  server('stop');
}

async function main() {
  fs.mkdirSync(path.join(DIR, 'config'), { recursive: true });
  for (const f of fs.readdirSync(path.join(DIR, 'config')).filter((x) => x.startsWith('dimensioncontrol'))) fs.unlinkSync(path.join(DIR, 'config', f));
  fs.rmSync(path.join(DIR, 'world'), { recursive: true, force: true });
  write(HEALTHY);

  // ---- A healthy start keeps a copy ----
  let bob = await start();
  let out = await rules();
  check('C1 healthy config: rule active', /-1 \(Nether\): minecraft:diamond/.test(out));
  check('C1 last good copy exists', fs.existsSync(LASTGOOD));
  check('C1 player without the item is blocked', await isBlocked());
  await stop(bob);

  // ---- A stray } and a restart: the file stays, the rules stay ----
  const good = read();
  const broken = `${good}}\n`;
  const brokenLine = broken.split('\n').length - 1;
  write(broken);
  bob = await start();
  out = await rules();
  check('C2 Forge did not rename the file', errored().length === 0, errored().join(','));
  check('C2 broken file is untouched', read() === broken);
  check('C2 rule still active', /-1 \(Nether\): minecraft:diamond/.test(out));
  check('C2 the mistake is reported with its line', new RegExp(`platí poslední dobrá verze: řádek ${brokenLine}: } navíc`).test(out), out.split('\n').filter((l) => /Config má chybu/.test(l)).join(' | ').slice(0, 200));
  check('C2 player without the item is still blocked', await isBlocked());
  await stop(bob);
  bob = await start();
  out = await rules();
  check('C3 second restart: same', /-1 \(Nether\)/.test(out) && /řádek \d+: } navíc/.test(out) && errored().length === 0 && read() === broken);

  // ---- Fixing the file and reloading clears it ----
  write(good);
  out = await reload();
  check('C4 fixed file: no problem left', !/Config má chybu/.test(out), out.split('\n').filter((l) => /chyb/.test(l)).join(' | ').slice(0, 200));

  // ---- Mistakes Forge would swallow or turn into defaults ----
  const expectRefused = async (name, text, re) => {
    write(text);
    const o = await reload();
    const r = await rules();
    check(`${name}: refused with a clear message`, re.test(o), o.split('\n').filter((l) => /Config má chybu/.test(l)).join(' | ').slice(0, 220));
    check(`${name}: file not rewritten, rule stays`, read() === text && /-1 \(Nether\): minecraft:diamond/.test(r));
  };
  await expectRefused('C5 number with a space', good.replace('I:dimensionId=-1', 'I:dimensionId= -1'), /řádek \d+: 'I:dimensionId= -1' není celé číslo/);
  await expectRefused('C6 boolean with trailing space', good.replace('B:consumeItem=false', 'B:consumeItem=true '), /řádek \d+: 'B:consumeItem=true ' musí být true nebo false/);
  await expectRefused('C7 copied block not renamed', good.replace(/\n}\s*\n/, '\n\n    nether {\n        I:dimensionId=1\n        S:item=minecraft:ender_eye\n    }\n}\n'), /blok 'nether' je v souboru dvakrát/);
  await expectRefused('C8 missing }', good.replace(/}\s*$/, '').replace(/}\s*$/, ''), /chybí } u bloku/);
  await expectRefused('C9 { on its own line', good.replace(/^( *)nether \{$/m, '$1nether\n$1{'), /{ bez názvu bloku/);
  await expectRefused('C10 note with an exclamation mark', good.replace(/^( *)nether \{$/m, '$1nether {\n        quest u kovare!'), /Forge soubor nepřečetl/);
  check('C5-10 Forge renamed nothing', errored().length === 0);
  write(good);
  await reload();

  // ---- Reload follows the file ----
  write(good.replace(/\n}\s*\n/, '\n\n    extra {\n        I:dimensionId=1\n        S:dimensionName=The End\n        S:item=minecraft:ender_eye\n    }\n}\n'));
  await reload();
  out = await rules();
  check('C11 added block works after reload', /1 \(The End\): minecraft:ender_eye/.test(out));
  const withExtra = read();
  write(withExtra.replace(/\s*extra \{[\s\S]*?\n {4}}\n/, '\n'));
  await reload();
  out = await rules();
  check('C11 deleted block is gone after reload', !/\(The End\)/.test(out) && !/extra/.test(read()), out.split('\n').filter((l) => /End/.test(l)).join(' | '));
  write(good.replace(/\n}\s*\n/, '\n\n    aaa {\n        S:item=minecraft:ender_eye\n    }\n}\n'));
  out = await reload();
  check('C12 a rule without dimensionId is reported', /Pravidlo 'aaa': dimenze 0 je Overworld/.test(out));
  write(good);
  await reload();

  // ---- The hand-edited config Reven really used loads ----
  write(fs.readFileSync(process.env.REVEN_CFG || path.join(__dirname, '..', 'reven-config', 'dimensioncontrol.cfg'), 'utf8'));
  out = await reload();
  check('C13 Reven\'s real file is accepted', !/Config má chybu/.test(out) && /pravidel: 4/.test(out), (out.match(/pravidel: \d+/) || [''])[0]);
  write(good);
  await reload();

  // ---- Commands run the way CustomNPCs runs them: through a command block ----
  await console_('dctest cmdblock dimaccess grant Bob -1', /DCTEST cmdblock ran/);
  await sleep(300);
  out = await console_('dimaccess list Bob', /má odemčeno/);
  check('C14 with enable-command-block=false the grant silently does nothing', /má odemčeno: nic/.test(out));
  await stop(bob);
  const props = fs.readFileSync(PROPS, 'utf8');
  fs.writeFileSync(PROPS, `${props.replace(/^enable-command-block=.*\n/m, '')}enable-command-block=true\n`);
  bob = await start();
  await console_('dctest cmdblock dimaccess grant Bob -1', /DCTEST cmdblock ran/);
  await sleep(300);
  out = await console_('dimaccess list Bob', /má odemčeno/);
  check('C14 with enable-command-block=true the grant works', /má odemčeno: -1 \(Nether\)/.test(out));
  check('C14 and the player gets in without the item', !(await isBlocked()));
  await console_('dimaccess revoke Bob all', /odebráno/);
  await stop(bob);
  fs.writeFileSync(PROPS, props);

  // ---- No good copy to fall back on: nothing is overwritten and the problem is reported ----
  fs.unlinkSync(LASTGOOD);
  write(broken);
  bob = await start();
  out = await rules();
  check('C15 no good copy: reported, file left alone', /dobrá verze není, takže žádná dimenze není omezená/.test(out) && read() === broken && errored().length === 0);
  await stop(bob);
  write(good);

  const failed = results.filter((r) => !r.ok);
  console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
  process.exit(failed.length ? 1 : 0);
}

main().catch((e) => {
  console.log('ERROR', e.stack || e);
  try {
    server('stop');
  } catch (e2) {
    // already stopped
  }
  process.exit(2);
});
