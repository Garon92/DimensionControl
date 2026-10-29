// Shared helpers for the DimensionControl end-to-end tests: console control of the server, log reading,
// and a headless 1.7.10 player with the client side of the FML handshake.

const mc = require('minecraft-protocol');
const fs = require('fs');
const path = require('path');

let PORT, CMDS, LOG, CONFIG;

function init(serverDir, port) {
  PORT = port;
  CMDS = path.join(serverDir, 'cmds.txt');
  LOG = path.join(serverDir, 'server.out');
  CONFIG = path.join(serverDir, 'config', 'dimensioncontrol.cfg');
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const results = [];

function check(name, ok, detail) {
  results.push({ name, ok: !!ok });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  -- ' + detail : ''}`);
}

function cmd(line) {
  fs.appendFileSync(CMDS, line + '\n');
}

function logSize() {
  return fs.statSync(LOG).size;
}

function logSince(mark) {
  const fd = fs.openSync(LOG, 'r');
  const size = fs.fstatSync(fd).size;
  const buf = Buffer.alloc(Math.max(0, size - mark));
  fs.readSync(fd, buf, 0, buf.length, mark);
  fs.closeSync(fd);
  return buf.toString('utf8').replace(/\u00a7./g, ''); // drop chat formatting codes
}

async function waitFor(fn, ms, what) {
  const end = Date.now() + ms;
  while (Date.now() < end) {
    const v = fn();
    if (v) return v;
    await sleep(100);
  }
  if (what) throw new Error('timeout waiting for ' + what);
  return null;
}

async function console_(line, expect, ms = 5000) {
  const mark = logSize();
  cmd(line);
  if (!expect) {
    await sleep(300);
    return logSince(mark);
  }
  return waitFor(() => {
    const out = logSince(mark);
    return expect.test(out) ? out : null;
  }, ms, `console output ${expect} after "${line}"`);
}

async function where(name, ms = 5000) {
  const mark = logSize();
  cmd(`dctest where ${name}`);
  const re = new RegExp(`DCTEST where ${name} dim=(-?\\d+) x=(-?[\\d.]+) y=(-?[\\d.]+) z=(-?[\\d.]+) hp=([\\d.]+)`);
  const m = await waitFor(() => re.exec(logSince(mark)), ms, `where ${name}`);
  return { dim: +m[1], x: +m[2], y: +m[3], z: +m[4], hp: +m[5] };
}

function flatten(c) {
  if (typeof c === 'string') return c;
  let s = c.text || '';
  if (c.translate) s += `{${c.translate}}`;
  if (c.extra) s += c.extra.map(flatten).join('');
  return s;
}

function varint(n) {
  const out = [];
  do {
    let b = n & 0x7f;
    n >>>= 7;
    if (n) b |= 0x80;
    out.push(b);
  } while (n);
  return out;
}

function utf8(s) {
  const b = Buffer.from(s, 'utf8');
  return [...varint(b.length), ...b];
}

// FML|HS ModList: discriminator 2, varint count, (modid, version) strings
function modList(mods) {
  const bytes = [2, ...varint(mods.length)];
  for (const [id, version] of mods) bytes.push(...utf8(id), ...utf8(version));
  return bytes;
}

class Bot {
  // mods: [[modid, version], ...] to claim in the FML handshake (needed when server mods require the client side)
  constructor(name, mods = []) {
    this.name = name;
    this.mods = mods;
    this.dim = null;
    this.pos = null;
    this.chat = [];
    this.respawns = [];
    this.teleports = 0;
    this.kicked = null;
    // Mod dimensions generate terrain on the first visit and can stall the server for longer than the default 30 s
    this.client = mc.createClient({
      host: '127.0.0.1',
      port: PORT,
      username: name,
      version: '1.7.10',
      checkTimeoutInterval: 5 * 60 * 1000,
    });
    this.client.on('login', (p) => (this.dim = p.dimension));
    this.client.on('respawn', (p) => {
      this.dim = p.dimension;
      this.respawns.push(p.dimension);
    });
    this.client.on('position', (p) => {
      // 1.7: server sends eye height, client answers with feet + stance
      this.pos = { x: p.x, y: p.y - 1.62, z: p.z, yaw: p.yaw, pitch: p.pitch };
      this.teleports++;
      this.sendPosition();
    });
    this.inv = {};
    this.client.on('set_slot', (p) => {
      if (p.windowId === 0) this.inv[p.slot] = p.item.blockId;
    });
    this.client.on('window_items', (p) => {
      if (p.windowId === 0) p.items.forEach((it, i) => (this.inv[i] = it.blockId));
    });
    this.client.on('chat', (p) => {
      try {
        this.chat.push(flatten(JSON.parse(p.message)));
      } catch (e) {
        this.chat.push(p.message);
      }
    });
    this.client.on('update_health', (p) => {
      this.hp = p.health;
      if (p.health <= 0) setTimeout(() => this.client.write('client_command', { payload: 0 }), 300);
    });
    // Forge 1.7.10 servers wait for the FML handshake (vanilla detection has a 10 hour timeout)
    this.client.on('custom_payload', (p) => this.onPayload(p));
    this.client.on('kick_disconnect', (p) => (this.kicked = p.reason));
    this.client.on('error', (e) => console.log(`[${name}] error`, e.message));
    this.timer = setInterval(() => this.sendPosition(), 50);
  }

  // Client side of FML|HS: ClientHello(1) + ModList(2) on ServerHello(0), then acks with the client state ordinals
  onPayload(p) {
    if (p.channel !== 'FML|HS') return;
    const hs = (bytes) => this.client.write('custom_payload', { channel: 'FML|HS', data: Buffer.from(bytes) });
    const discriminator = p.data.readInt8(0);
    if (discriminator === 0) {
      hs([1, 2]); // ClientHello, protocol 2
      hs(modList(this.mods));
    } else if (discriminator === 2) {
      hs([0xff, 2]); // WAITINGSERVERDATA
    } else if (discriminator === 3) {
      hs([0xff, 3]); // WAITINGSERVERCOMPLETE
    } else if (discriminator === -1) {
      const phase = p.data.readInt8(1);
      if (phase === 2) hs([0xff, 4]); // PENDINGCOMPLETE
      if (phase === 3) hs([0xff, 5]); // COMPLETE
    }
  }

  sendPosition() {
    if (!this.pos || this.kicked) return;
    this.client.write('position_look', {
      x: this.pos.x,
      stance: this.pos.y, // 2nd field = feet y in 1.7.10
      y: this.pos.y + 1.62, // 3rd field = head y
      z: this.pos.z,
      yaw: this.pos.yaw,
      pitch: this.pos.pitch,
      onGround: true,
    });
  }

  messagesSince(i, re) {
    return this.chat.slice(i).filter((m) => re.test(m));
  }

  async ready() {
    await waitFor(() => this.pos && this.dim !== null, 15000, `${this.name} spawn`);
  }

  quit() {
    clearInterval(this.timer);
    this.client.end();
  }
}

const DENY = /se dostaneš jen s předmětem/;
const UNLOCK = /je pro tebe nyní trvale odemčena/;

// /tp adds 0.5 to whole numbers, so always pass a decimal point
const num = (n) => (Number.isInteger(n) ? n.toFixed(1) : String(n));

// Teleport a bot with the console and wait until it confirmed the new position
async function tp(bot, x, y, z) {
  const before = bot.teleports;
  cmd(`tp ${bot.name} ${num(x)} ${num(y)} ${num(z)}`);
  await waitFor(() => bot.teleports > before, 5000, `tp ${bot.name}`);
  await sleep(300);
}

const ITEM_IDS = { 'minecraft:flint_and_steel': 259, 'minecraft:ender_eye': 381, 'minecraft:diamond': 264 };
const HELD_SLOT = 36; // hotbar slot 0 in the player inventory window

// 1.7.10 /give drops the item at the player's feet; wait until it was picked up into the hotbar, then hold it
async function give(bot, item) {
  cmd(`give ${bot.name} ${item}`);
  const id = ITEM_IDS[item];
  const slot = await waitFor(() => {
    for (let s = HELD_SLOT; s < HELD_SLOT + 9; s++) if (bot.inv[s] === id) return s;
    return null;
  }, 5000);
  if (!slot) throw new Error(`${bot.name} did not get ${item}, hotbar: ${JSON.stringify(bot.inv)}`);
  bot.client.write('held_item_slot', { slotId: slot - HELD_SLOT });
  await sleep(300);
}

async function clear(bot) {
  cmd(`clear ${bot.name}`);
  await sleep(300);
}

async function backToOverworld(bot) {
  if (bot.dim === 0) return;
  // Leaving through a hand-written transfer avoids the vanilla End exit quirks; the Overworld is never restricted
  cmd(`dctest manual ${bot.name} 0`);
  await waitFor(() => bot.dim === 0, 5000, `${bot.name} back in overworld`);
  await sleep(500);
}

function setGeneral(key, value) {
  let cfg = fs.readFileSync(CONFIG, 'utf8');
  cfg = cfg.replace(new RegExp(`(${key}=)\\S*`), `$1${value}`);
  fs.writeFileSync(CONFIG, cfg);
}


module.exports = {
  init, check, results, cmd, logSize, logSince, waitFor, console_, where, sleep, Bot, DENY, UNLOCK, tp, give, clear,
  backToOverworld, setGeneral, num, CONFIG: () => CONFIG,
};
