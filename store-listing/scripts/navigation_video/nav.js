// Navigation walkthrough for the Play Console "App access" video field.
// render.js loads ../store_assets/store.html (which defines the app screens, device frame,
// palette and icons) and injects this script, then calls window.render(t) once per frame.
//
// The screens follow the current app: tab order and icons from MainTab.kt, titles from
// res/values/strings.xml, and the FAB only on Messages, Automate and Schedule (MainActivity's
// showFab). Every name, number and message shown is fictional.

// ---------------- extra styles ----------------
document.head.insertAdjacentHTML("beforeend", `<style>
.layer { position: absolute; inset: 0; will-change: transform, opacity; }
.tap { position: absolute; width: 56px; height: 56px; margin: -28px 0 0 -28px; border-radius: 50%; z-index: 40; pointer-events: none;
  background: rgba(26,115,232,.28); border: 3px solid rgba(26,115,232,.9); box-shadow: 0 0 0 6px rgba(255,255,255,.55); }
.ring { position: absolute; z-index: 39; border-radius: 22px; border: 3px solid #FBBC05; box-shadow: 0 0 0 6px rgba(251,188,5,.28); pointer-events: none; }
.seg { display: flex; margin-top: 8px; border: 1px solid var(--outline); border-radius: 20px; overflow: hidden; height: 40px; }
.seg div { flex: 1; display: flex; align-items: center; justify-content: center; gap: 8px; font-size: 14px; font-weight: 500; }
.seg div + div { border-left: 1px solid var(--outline); }
.seg div.on { background: var(--secondary-c); color: var(--on-secondary-c); }
.seg svg { width: 18px; height: 18px; }
.obtn { height: 40px; border-radius: 20px; border: 1px solid var(--outline); display: flex; align-items: center; justify-content: center; font-size: 14px; font-weight: 500; color: var(--primary); }
#copy { position: absolute; left: 130px; width: 760px; top: 0; bottom: 0; }
#copy .blk { position: absolute; left: 0; right: 0; top: 50%; }
.step { font-family: Inter, sans-serif; font-weight: 700; font-size: 22px; letter-spacing: 2px; text-transform: uppercase; color: rgba(255,255,255,.7); margin-bottom: 22px; }
.map { position: absolute; left: 130px; bottom: 92px; display: flex; gap: 14px; align-items: center; }
.map .t { display: flex; align-items: center; gap: 10px; height: 52px; padding: 0 18px; border-radius: 26px; background: rgba(255,255,255,.10); color: rgba(255,255,255,.75);
  font-family: Inter, sans-serif; font-weight: 700; font-size: 19px; border: 1px solid rgba(255,255,255,.14); }
.map .t svg { width: 24px; height: 24px; fill: currentColor; }
.map .t.on { background: #C4EED0; color: #072711; border-color: #C4EED0; }
.brand { position: absolute; left: 130px; top: 74px; display: flex; align-items: center; gap: 16px; color: #fff; font-family: Inter, sans-serif; font-weight: 700; font-size: 26px; }
</style>`);

// ---------------- extra / updated screens ----------------
// RuleScreen, in its order: type selector, name, category, sender, then Target (Redirect) or
// the phone-action section, then Save Rule.
const ruleTypeBlock = (phone) => `
    <div class="body" style="margin-top:4px;font-weight:500">What should happen?</div>
    <div class="seg"><div class="${phone ? "" : "on"}">${phone ? "" : ic("check")}Redirect</div><div class="${phone ? "on" : ""}">${phone ? ic("check") : ""}Phone action</div></div>`;
const categoryField = (v) => tf("Category", v, { trail: ic("arrow_drop_down", "", "color:var(--on-variant)") });
const senderField = (label, v) => `<div class="row">${tf(label, v).replace('class="tf', 'style="flex:1" class="tf')}<div class="ib" style="margin-top:14px;color:var(--primary)">${ic("contact_phone")}</div></div>`;

const RULE_FORM_REDIRECT = () => app(`
    ${ruleTypeBlock(false)}
    ${tf("Rule Name", "Optional - auto-generated if empty", { ph: true })}
    ${categoryField("Work")}
    ${senderField("Sender", "WorkBank")}
    ${senderField("Target Number", "+351 912 345 678")}
    <div class="btn big" style="margin-top:24px">Save Rule</div>`,
  { title: "Create Rule", back: true });

const RULE_FORM_PHONE = () => app(`
    ${ruleTypeBlock(true)}
    ${tf("Rule Name", "Mom calling for help")}
    ${categoryField("Family")}
    ${senderField("Sender (optional)", "Mom")}
    ${tf("Message contains", "urgent")}
    <p class="small muted" style="margin:4px 16px 0">Not case-sensitive. Leave empty to match every message from the sender.</p>
    ${tf("Action", "Ring at maximum volume", { trail: ic("arrow_drop_down", "", "color:var(--on-variant)") })}
    <p class="small muted" style="margin-top:8px">Rings at full volume for up to a minute, even when the phone is on silent. Stop it from the notification.</p>
    <div style="font-size:16px;margin-top:8px">Sound: Default alarm sound</div>
    <div class="row" style="gap:8px;margin-top:4px"><div class="obtn" style="padding:0 20px">System sounds</div><div class="obtn" style="padding:0 20px">Song from files</div></div>
    <div class="obtn" style="margin-top:12px">Try it now</div>
    <div class="btn big" style="margin-top:16px">Save Rule</div>`,
  { title: "Create Rule", back: true });

const NEW_MESSAGE = () => app(`
    ${tf("To", "Phone number or email", { ph: true, focus: true, caret: true, trail: `<div class="ib" style="margin-right:-8px">${ic("contacts")}</div>` })}
    <p class="small muted" style="margin:8px 4px 0">Messages to more than one person are sent as a group message (MMS).</p>
    <div class="btn" style="margin-top:20px;opacity:.45">Next</div>`,
  { title: "New message", back: true });

const SCHEDULE_FORM = () => app(`
    ${tf("Scheduled SMS Name", "Ana's birthday")}
    ${tf("Category", "Birthdays", { trail: ic("arrow_drop_down", "", "color:var(--on-variant)") })}
    <div class="row">${tf("Contact", "Ana Silva").replace('class="tf', 'style="flex:1" class="tf')}<div class="ib" style="margin-top:14px;color:var(--primary)">${ic("contact_phone")}</div></div>
    ${tf("Message", "Happy birthday, Ana! 🎂 Have the best day.", { multi: true })}
    <div class="row">${tf("Date", "Sep 3, 2027", { lead: "calendar_month" }).replace('class="tf', 'style="flex:1.2" class="tf')}${tf("Time", "08:00", { lead: "access_time" }).replace('class="tf', 'style="flex:1" class="tf')}</div>
    ${tf("Repeat", "Yearly", { trail: ic("arrow_drop_down", "", "color:var(--on-variant)") })}
    <div class="btn big" style="margin-top:24px">Save</div>`,
  { title: "Create Schedule SMS", back: true });

// ScheduleHistoryScreen: one card per sent message, with the Send icon.
const SCHEDULE_HISTORY = () => app(`
    ${histCard("Ana's birthday → Ana Silva", "Sep 3, 2026, 08:00", "Ana's birthday", "Happy birthday, Ana! 🎂 Have the best day.")}
    ${histCard("Pay the rent → Rita", "Nov 1, 09:00", "Pay the rent", "Rent reminder: due today 🏠")}
    ${histCard("Gym tonight? → Rui", "Yesterday, 18:00", "Gym tonight?", "Gym tonight? 💪")}`.replaceAll(ICONS.forward, ICONS.send),
  { title: "Scheduled SMS History", back: true, actions: ["delete_sweep"] });

const RESTORE_IDLE = () => app(`
    <div style="margin:16px;padding:16px;border-radius:12px;background:var(--s-v);display:flex;flex-direction:column;gap:12px">
      <div class="row" style="gap:12px;font-size:16px;font-weight:500">${ic("restore", "", "color:var(--primary)")}Restore from Gmail</div>
      <div class="body">Copy the messages backed up under the Gmail label “SMS” back to this phone. Messages already on the phone are skipped, so it is safe to run more than once.</div>
      <div class="btn" style="margin-top:4px">${ic("cloud_download")}Restore messages</div>
    </div>`,
  { nav: TAB.restore, title: "Restore from Gmail", actions: ["settings"], flush: true });

const BACKUP_IDLE = () => SCREENS.backup().replace(/<div style="padding:16px"><div class="body">Backing up[\s\S]*?<\/i><\/div><\/div>/, "");

const SETTINGS_TOP = () => app(`
    <div class="sline" style="margin-top:4px">${ic("language")}<span class="grow">Language<div class="small muted">English</div></span>${ic("chevron_right")}</div>
    ${tf("Landing screen", "Messages", { trail: ic("arrow_drop_down", "", "color:var(--on-variant)") })}
    <div class="divider"></div>
    <div class="section-t" style="margin-top:4px">Message receipts</div>
    <div class="sline"><span class="grow">SMS delivery reports</span><div class="sw"></div></div>
    <div class="sline"><span class="grow">MMS delivery reports</span><div class="sw off"></div></div>
    <div class="sline"><span class="grow">MMS read reports</span><div class="sw off"></div></div>
    <div class="divider"></div>
    <div class="sline"><span class="grow">Send while roaming</span><div class="sw off"></div></div>
    <div class="sline"><span class="grow">Show notifications</span><div class="sw"></div></div>
    <div class="divider"></div>
    <div class="section-t" style="margin-top:4px">Gmail backup</div>
    ${tf("Gmail address", "you@gmail.com", { lead: "email" })}`,
  { title: "Settings", back: true });

// ---------------- timeline ----------------
// Each step: dur (s), screen, enter transition (push = slide in from the right, pop = slide back,
// tab = cross-fade, none = instant), caption, mapTab (which tab chip is lit), and optionally
// tap: a selector inside the screen, tapped 0.65 s before the step ends (it causes the next step),
// rings: selectors to highlight for the whole step.
const TAB_SEL = i => `.fnav .tab:nth-child(${i + 1})`;
const FAB = ".fnav .fabb";
const BACK = ".topbar .ib:first-child";
const ACTION = i => `.topbar .ib:nth-of-type(${i + 1})`;

const STEPS = [
  { dur: 4.0, kind: "title" },
  { dur: 6.0, screen: SCREENS.messages, enter: "tab", mapTab: -1, rings: [".fnav .bar"],
    step: "The basics", head: "Five tabs <em>at the bottom</em>", sub: "Messages, Automate, Schedule, Backup and Restore. Tap a tab to switch." },
  { dur: 4.5, screen: SCREENS.messages, enter: "none", mapTab: 0, rings: [".topbar .ib", FAB],
    step: "The basics", head: "Settings <em>top right</em>, create with <em>+</em>", sub: "The ⚙ icon opens Settings from any tab. The + button adds something new on the tab you are on." },
  { dur: 4.0, screen: SCREENS.messages, enter: "none", mapTab: 0, tap: ".conv:nth-child(5)",
    step: "1 · Messages", head: "Messages is <em>your inbox</em>", sub: "The app opens here. Tap a conversation to read it." },
  { dur: 4.5, screen: SCREENS.conversation, enter: "push", mapTab: 0, tap: BACK,
    step: "1 · Messages", head: "Read and <em>reply</em>", sub: "Type at the bottom and tap send. Attach photos for a picture message. ← goes back." },
  { dur: 3.0, screen: SCREENS.messages, enter: "pop", mapTab: 0, tap: FAB,
    step: "1 · Messages", head: "Start a <em>new message</em>", sub: "Tap + on the Messages tab." },
  { dur: 4.0, screen: NEW_MESSAGE, enter: "push", mapTab: 0, tap: BACK,
    step: "1 · Messages", head: "Pick who <em>to text</em>", sub: "Type a number or pick contacts, then Next. Several people make a group message." },
  { dur: 3.0, screen: SCREENS.messages, enter: "pop", mapTab: 0, tap: TAB_SEL(1),
    step: "2 · Automate", head: "Next tab: <em>Automate</em>", sub: "Tap the second tab." },
  { dur: 4.0, screen: SCREENS.rules, enter: "tab", mapTab: 1, tap: FAB,
    step: "2 · Automate", head: "Rules for <em>incoming texts</em>", sub: "Rules are grouped by category. Drag ≡ to reorder. Tap + to add one." },
  { dur: 4.0, screen: RULE_FORM_REDIRECT, enter: "push", mapTab: 1, tap: ".seg div:nth-child(2)",
    step: "2 · Automate", head: "<em>Redirect</em> a text…", sub: "Forward messages from a sender to another phone number." },
  { dur: 4.5, screen: RULE_FORM_PHONE, enter: "none", mapTab: 1, tap: BACK,
    step: "2 · Automate", head: "…or run a <em>phone action</em>", sub: "Ring at full volume, vibrate or change the sound mode when a matching text arrives." },
  { dur: 2.6, screen: SCREENS.rules, enter: "pop", mapTab: 1, tap: ACTION(0),
    step: "2 · Automate", head: "See what <em>was forwarded</em>", sub: "Tap the history icon." },
  { dur: 3.6, screen: SCREENS.history, enter: "push", mapTab: 1, tap: BACK,
    step: "2 · Automate", head: "Redirect <em>History</em>", sub: "Every forwarded message, newest first." },
  { dur: 2.6, screen: SCREENS.rules, enter: "pop", mapTab: 1, tap: TAB_SEL(2),
    step: "3 · Schedule", head: "Next tab: <em>Schedule</em>", sub: "Tap the clock." },
  { dur: 4.0, screen: SCREENS.scheduleList, enter: "tab", mapTab: 2, tap: FAB,
    step: "3 · Schedule", head: "Texts that <em>send later</em>", sub: "Each card shows the contact, next send time and how often it repeats. Tap + to add one." },
  { dur: 4.5, screen: SCHEDULE_FORM, enter: "push", mapTab: 2, tap: BACK,
    step: "3 · Schedule", head: "Write it now, <em>send it later</em>", sub: "Pick a contact, message, date and time. Repeat daily, on work days, monthly or yearly." },
  { dur: 2.6, screen: SCREENS.scheduleList, enter: "pop", mapTab: 2, tap: ACTION(0),
    step: "3 · Schedule", head: "See what <em>was sent</em>", sub: "Tap the history icon." },
  { dur: 3.4, screen: SCHEDULE_HISTORY, enter: "push", mapTab: 2, tap: BACK,
    step: "3 · Schedule", head: "Scheduled SMS <em>History</em>", sub: "Every scheduled text that went out." },
  { dur: 2.6, screen: SCREENS.scheduleList, enter: "pop", mapTab: 2, tap: TAB_SEL(3),
    step: "4 · Backup", head: "Next tab: <em>Backup</em>", sub: "Tap the cloud." },
  { dur: 5.0, screen: BACKUP_IDLE, enter: "tab", mapTab: 3, tap: TAB_SEL(4),
    step: "4 · Backup", head: "Back up to <em>your Gmail</em>", sub: "Choose the email subject and Gmail label, then turn on backup. New texts are saved as they arrive." },
  { dur: 4.5, screen: RESTORE_IDLE, enter: "tab", mapTab: 4, tap: ".topbar .ib",
    step: "5 · Restore", head: "Bring texts back <em>from Gmail</em>", sub: "On a new phone, tap Restore messages. Texts already on the phone are skipped." },
  { dur: 4.8, screen: SETTINGS_TOP, enter: "push", mapTab: -1, tap: BACK,
    step: "Settings", head: "Everything else, <em>in Settings</em>", sub: "Language, landing tab, delivery reports and the Gmail account used by Backup and Restore." },
  { dur: 2.4, screen: RESTORE_IDLE, enter: "pop", mapTab: 4, tap: TAB_SEL(0),
    step: "Back home", head: "Back to <em>Messages</em>", sub: "Tap the first tab any time." },
  { dur: 2.2, screen: SCREENS.messages, enter: "tab", mapTab: 0,
    step: "Back home", head: "Back to <em>Messages</em>", sub: "Tap the first tab any time." },
  { dur: 5.0, kind: "outro" },
];
let acc = 0;
for (const s of STEPS) { s.start = acc; acc += s.dur; }
window.DURATION = acc;

// ---------------- stage ----------------
const W = 1920, H = 1080, TR = 0.42, TAP_LEAD = 0.65;
const MAP = [["chat", "Messages"], ["auto_mode", "Automate"], ["schedule", "Schedule"], ["backup", "Backup"], ["restore", "Restore"]];
const stage = document.getElementById("stage");
stage.style.cssText = `width:${W}px;height:${H}px`;
stage.innerHTML = `<div id="bgwrap">${background(W, H, ["#1A73E8", "#34A853"])}</div>
  <div class="brand" id="brand">${appIcon(52)}SMS Toolbox</div>
  <div id="copy"></div>
  <div class="map" id="map">${MAP.map(([i, l]) => `<div class="t">${ic(i)}${l}</div>`).join("")}</div>
  ${device("", { x: 1000, y: 68, scale: 0.997 })}
  <div id="card" style="position:absolute;inset:0;z-index:80"></div>`;
const screenEl = stage.querySelector(".screen");
const glare = screenEl.querySelector(".glare");
const copy = document.getElementById("copy");
const card = document.getElementById("card");
const deviceWrap = stage.querySelector(".device-wrap");
const ease = x => x < 0 ? 0 : x > 1 ? 1 : x < .5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
const clamp01 = x => Math.max(0, Math.min(1, x));

// Two screen layers: the previous step's screen and the current one, rebuilt only when the step changes.
const layers = [document.createElement("div"), document.createElement("div")];
layers.forEach(l => { l.className = "layer"; screenEl.insertBefore(l, glare); });
const tapEl = document.createElement("div"); tapEl.className = "tap"; screenEl.insertBefore(tapEl, glare);
const ringEls = [];
let shownStep = -1, copyStep = -1, copyPrev = "";

function screenIndexBefore(i) { for (let k = i - 1; k >= 0; k--) if (STEPS[k].screen) return k; return -1; }
function rectIn(layer, sel) {
  const el = layer.querySelector(sel);
  if (!el) throw new Error("selector not found: " + sel);
  const s = screenEl.getBoundingClientRect(), r = el.getBoundingClientRect(), k = s.width / 414;
  return { x: (r.left - s.left) / k, y: (r.top - s.top) / k, w: r.width / k, h: r.height / k };
}
function captionHtml(s) {
  return `<div class="step">${s.step}</div><div class="headline" style="font-size:86px;font-family:Inter,sans-serif;font-weight:800;color:#fff">${s.head}</div>
    <div class="sub" style="font-size:34px;margin-top:26px;font-family:Inter,sans-serif">${s.sub}</div>`;
}

function titleCard(o) {
  return `<div style="position:absolute;inset:0;opacity:${o};background:radial-gradient(1400px 1000px at 25% 0%, #2B7FF0 0%, #1554C4 40%, #0B2E7A 80%, #071C4E 100%);
    display:flex;flex-direction:column;align-items:center;justify-content:center;color:#fff;font-family:Inter,sans-serif;text-align:center">
    ${appIcon(170)}
    <div class="headline" style="font-size:104px;margin-top:46px">SMS Toolbox</div>
    <div class="sub" style="font-size:44px;margin-top:20px">How to navigate the app</div></div>`;
}
function outroCard(o) {
  const rows = [["chat", "Messages", "Read, reply and start texts"], ["auto_mode", "Automate", "Redirect texts or run phone actions"], ["schedule", "Schedule", "Texts that send later"],
    ["backup", "Backup", "Save texts to your Gmail"], ["restore", "Restore", "Bring them back from Gmail"], ["settings", "Settings", "⚙ top right on every tab"]];
  return `<div style="position:absolute;inset:0;opacity:${o};background:radial-gradient(1400px 1000px at 25% 0%, #2B7FF0 0%, #1554C4 40%, #0B2E7A 80%, #071C4E 100%);
    display:flex;align-items:center;justify-content:center;gap:110px;color:#fff;font-family:Inter,sans-serif">
    <div style="display:flex;flex-direction:column;align-items:flex-start">${appIcon(130)}
      <div class="headline" style="font-size:88px;margin-top:36px">SMS Toolbox</div>
      <div class="sub" style="font-size:36px;margin-top:16px">Every screen, one tap away</div></div>
    <div style="display:flex;flex-direction:column;gap:16px">${rows.map(([i, n, d]) => `<div style="display:flex;align-items:center;gap:22px;background:rgba(255,255,255,.10);border:1px solid rgba(255,255,255,.16);border-radius:22px;padding:16px 28px 16px 18px;width:720px">
      <span style="width:58px;height:58px;border-radius:50%;background:#C4EED0;color:#072711;display:grid;place-items:center">${ic(i, "", "width:30px;height:30px")}</span>
      <span style="font-size:32px;font-weight:700;width:200px">${n}</span><span style="font-size:27px;color:rgba(255,255,255,.82)">${d}</span></div>`).join("")}</div></div>`;
}

window.render = function (t) {
  t = Math.min(t, DURATION - 1e-6);
  const i = STEPS.findIndex(s => t >= s.start && t < s.start + s.dur);
  const s = STEPS[i], lt = t - s.start;

  // Title / outro cards fade over the walkthrough.
  if (s.kind === "title") card.innerHTML = titleCard(lt < s.dur - .6 ? 1 : 1 - ease((lt - (s.dur - .6)) / .6));
  else if (s.kind === "outro") card.innerHTML = outroCard(ease(lt / .6));
  else card.innerHTML = "";

  // Which step's screen is current (the title shows the first screen underneath it).
  const si = s.screen ? i : (s.kind === "title" ? 1 : screenIndexBefore(i));
  const cur = STEPS[si];
  if (shownStep !== si) {
    const pi = screenIndexBefore(si);
    layers[0].innerHTML = pi >= 0 ? STEPS[pi].screen() : "";
    layers[1].innerHTML = cur.screen();
    ringEls.forEach(r => r.remove()); ringEls.length = 0;
    (cur.rings || []).forEach(sel => layers[1].querySelectorAll(sel).forEach((_, k) => {
      const r = document.createElement("div"); r.className = "ring"; r.dataset.sel = sel; r.dataset.k = k; screenEl.insertBefore(r, glare); ringEls.push(r);
    }));
    shownStep = si;
  }

  // Screen transition.
  const p = s.screen ? ease(lt / TR) : 1;
  const [a, b] = layers;
  a.style.cssText = b.style.cssText = "";
  const enter = s.screen ? cur.enter : "none";
  if (p >= 1 || enter === "none") { a.style.display = "none"; }
  else if (enter === "push") { a.style.transform = `translateX(${-30 * p}%)`; a.style.filter = `brightness(${1 - .25 * p})`; b.style.transform = `translateX(${100 * (1 - p)}%)`; b.style.boxShadow = "-20px 0 40px rgba(0,0,0,.18)"; }
  else if (enter === "pop") { a.style.zIndex = 2; a.style.transform = `translateX(${100 * p}%)`; a.style.boxShadow = "-20px 0 40px rgba(0,0,0,.18)"; b.style.transform = `translateX(${-30 * (1 - p)}%)`; b.style.filter = `brightness(${.75 + .25 * p})`; }
  else if (enter === "tab") { a.style.opacity = 1 - p; b.style.opacity = p; a.style.zIndex = 2; }

  // Highlight rings (pulse).
  ringEls.forEach(r => {
    const el = layers[1].querySelectorAll(r.dataset.sel)[+r.dataset.k];
    const s2 = screenEl.getBoundingClientRect(), rr = el.getBoundingClientRect(), k = s2.width / 414, pad = 6;
    r.style.left = ((rr.left - s2.left) / k - pad) + "px"; r.style.top = ((rr.top - s2.top) / k - pad) + "px";
    r.style.width = (rr.width / k + 2 * pad) + "px"; r.style.height = (rr.height / k + 2 * pad) + "px";
    r.style.borderRadius = (r.dataset.sel === ".fnav .bar" ? 34 : 24) + "px";
    r.style.opacity = s.screen ? clamp01((lt - .5) / .4) * (.75 + .25 * Math.sin(lt * 5)) : 0;
  });

  // Tap indicator: appears, presses, ripples out.
  tapEl.style.opacity = 0;
  if (s.screen && s.tap) {
    const tt = lt - (s.dur - TAP_LEAD - .45);
    if (tt > 0) {
      const r = rectIn(layers[1], s.tap);
      tapEl.style.left = (r.x + r.w / 2) + "px"; tapEl.style.top = (r.y + r.h / 2) + "px";
      const press = tt < .45 ? 1 - .25 * ease(tt / .45) : .75 + 1.1 * ease((tt - .45) / .5);
      tapEl.style.transform = `scale(${press})`;
      tapEl.style.opacity = tt < .2 ? tt / .2 : tt < .45 ? 1 : 1 - clamp01((tt - .45) / .5);
    }
  }

  // Caption (cross-fades when the text changes) and tab map.
  const capStep = s.screen ? i : -1;
  if (capStep >= 0) {
    const html = captionHtml(s);
    if (copyStep !== capStep) {
      const prevHtml = copyStep >= 0 ? captionHtml(STEPS[copyStep]) : "";
      copyPrev = prevHtml === html ? "" : prevHtml;
      copy.innerHTML = `<div class="blk" id="cOld">${copyPrev}</div><div class="blk" id="cNew">${html}</div>`;
      copyStep = capStep;
    }
    const q = copyPrev ? ease(lt / .5) : 1;
    const o = document.getElementById("cOld"), n = document.getElementById("cNew");
    o.style.opacity = 1 - q; o.style.transform = `translateY(calc(-50% - ${24 * q}px))`;
    n.style.opacity = q; n.style.transform = `translateY(calc(-50% + ${24 * (1 - q)}px))`;
    document.querySelectorAll("#map .t").forEach((el, k) => el.classList.toggle("on", k === s.mapTab));
  }
};
