// Uses Node's built-in WebSocket and Chrome's DevTools protocol, with no packages.
import { spawn } from "node:child_process";
import { once } from "node:events";
import { existsSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const browser = [
  process.env.ProgramFiles && path.join(process.env.ProgramFiles, "Google", "Chrome", "Application", "chrome.exe"),
  process.env["ProgramFiles(x86)"] && path.join(process.env["ProgramFiles(x86)"], "Microsoft", "Edge", "Application", "msedge.exe")
].find(file => file && existsSync(file));
if (!browser) throw new Error("Chrome or Edge is required.");
const profile = mkdtempSync(path.join(tmpdir(), "vibe-manager-browser-"));
const fixture = spawn("java", ["-cp", path.join(root, "out"), "LocalManagerTests", "browser"], { cwd: root });
let fixtureOutput = "", fixtureErrors = "", chrome, socket, toolboxServer;
fixture.stdout.on("data", bytes => { fixtureOutput += bytes; });
fixture.stderr.on("data", bytes => { fixtureErrors += bytes; });
const fixtureExit = once(fixture, "exit");
const errors = [];
let checks = 0;
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
function expect(condition, label) {
  if (!condition) throw new Error(label);
  checks++;
}
async function until(get, label) {
  for (let i = 0; i < 200; i++) {
    const result = await get();
    if (result) return result;
    await sleep(50);
  }
  throw new Error(`Timed out: ${label}\n${fixtureErrors}`);
}

try {
  const url = await until(() => fixtureOutput.match(/^http:\/\/127\.0\.0\.1:\d+\//m)?.[0], "fixture server");
  expect((await fetch(url)).ok, "server responsive");
  chrome = spawn(browser, [
    "--headless=new", "--disable-gpu", "--no-first-run", "--no-default-browser-check",
    "--remote-debugging-port=0", `--user-data-dir=${profile}`, "about:blank"
  ], { stdio: ["ignore", "ignore", "pipe"] });
  let chromeOutput = "";
  chrome.stderr.on("data", bytes => { chromeOutput += bytes; });
  const endpoint = await until(() => chromeOutput.match(/DevTools listening on (ws:\/\/[^\s]+)/)?.[1], "Chrome DevTools");
  socket = new WebSocket(endpoint);
  await once(socket, "open");
  let sequence = 0;
  const pending = new Map();
  socket.addEventListener("message", event => {
    const message = JSON.parse(event.data);
    if (message.id) {
      const request = pending.get(message.id);
      if (!request) return;
      pending.delete(message.id);
      if (message.error) request.reject(new Error(JSON.stringify(message.error)));
      else request.resolve(message.result);
    } else if (message.method === "Runtime.exceptionThrown") {
      errors.push(message.params.exceptionDetails.exception?.description || message.params.exceptionDetails.text);
    } else if (message.method === "Log.entryAdded" && message.params.entry.level === "error") {
      // The collision test deliberately returns HTTP 400; keep all console evidence.
      errors.push(message.params.entry.text);
    }
  });
  function send(method, params = {}, sessionId) {
    return new Promise((resolve, reject) => {
      const id = ++sequence;
      const timeout = setTimeout(() => { pending.delete(id); reject(new Error(`CDP timeout: ${method}`)); }, 15000);
      pending.set(id, {
        resolve: value => { clearTimeout(timeout); resolve(value); },
        reject: error => { clearTimeout(timeout); reject(error); }
      });
      socket.send(JSON.stringify({ id, method, params, ...(sessionId ? { sessionId } : {}) }));
    });
  }
  const { targetId } = await send("Target.createTarget", { url: "about:blank" });
  const { sessionId } = await send("Target.attachToTarget", { targetId, flatten: true });
  await send("Page.enable", {}, sessionId);
  await send("Runtime.enable", {}, sessionId);
  await send("Log.enable", {}, sessionId);
  async function evaluate(expression, session = sessionId) {
    const result = await send("Runtime.evaluate", { expression, returnByValue: true, awaitPromise: true }, session);
    if (result.exceptionDetails) throw new Error(JSON.stringify(result.exceptionDetails));
    return result.result.value;
  }
  async function navigate(location) {
    await send("Page.navigate", { url: location }, sessionId);
    await until(() => evaluate(`location.href === ${JSON.stringify(location)} && document.readyState === "complete"`), "navigation");
  }
  async function visible(selector, text) {
    return until(() => evaluate(`(() => {
      const element = document.querySelector(${JSON.stringify(selector)});
      return !!element && element.getBoundingClientRect().height > 0
        && element.textContent.includes(${JSON.stringify(text)});
    })()`), `${selector}: ${text}`);
  }
  async function click(selector) {
    const point = await evaluate(`(() => {
      const element = document.querySelector(${JSON.stringify(selector)});
      element.scrollIntoView({block: "center"});
      const box = element.getBoundingClientRect();
      return {x: box.x + box.width / 2, y: box.y + box.height / 2};
    })()`);
    await send("Input.dispatchMouseEvent", { type: "mousePressed", button: "left", clickCount: 1, ...point }, sessionId);
    await send("Input.dispatchMouseEvent", { type: "mouseReleased", button: "left", clickCount: 1, ...point }, sessionId);
  }
  async function addName(name, enter = false) {
    await evaluate('document.querySelector("#newName").value = ""');
    await click("#newName");
    await send("Input.insertText", { text: name }, sessionId);
    if (enter) {
      await send("Input.dispatchKeyEvent", { type: "keyDown", key: "Enter", code: "Enter", windowsVirtualKeyCode: 13 }, sessionId);
      await send("Input.dispatchKeyEvent", { type: "keyUp", key: "Enter", code: "Enter", windowsVirtualKeyCode: 13 }, sessionId);
    } else {
      await click("#addName");
    }
  }
  async function spin(winner) {
    await click("#spin");
    await until(() => evaluate(`document.querySelector("#result").textContent === ${JSON.stringify(`${winner} goes next!`)}`),
      `spin completes for ${winner}`);
    expect(await evaluate(`(() => {
      const wheel = document.querySelector("#wheel"), box = wheel.getBoundingClientRect();
      const radius = (box.width - 56) / 2;
      wheel.dispatchEvent(new PointerEvent("pointermove", {
        clientX: box.left + box.width / 2, clientY: box.top + box.height / 2 - radius * .8
      }));
      return wheel.title === ${JSON.stringify(winner)};
    })()`), `${winner} aligns with the wheel pointer`);
  }
  async function fill(slug, name, main) {
    await evaluate(`(() => {
      document.querySelector('[name="slug"]').value = ${JSON.stringify(slug)};
      document.querySelector('[name="name"]').value = ${JSON.stringify(name)};
      document.querySelector('[name="description"]').value = "Reviewed in actual browser workflow";
      document.querySelector('[name="main"]').value = ${JSON.stringify(main)};
    })()`);
  }
  await navigate(url);
  expect(await visible("#choose", "Add project"), "catalog presents Add project");
  await click("#choose");
  expect(await visible("h2", "Selection cancelled"), "chooser cancellation shown");
  expect(await evaluate('location.pathname === "/"'), "native cancellation returns to catalog URL");
  await click("#choose");
  expect(await visible("h2", "Review import"), "review shown before confirmation");
  expect(await visible("#source", "sample_app"), "selected folder visible");
  expect(await visible("#included", "prompts.md"), "available history included");
  expect(await visible("#excluded", ".env"), "secret exclusion visible before confirmation");
  expect(await visible("#excluded", "Version-control metadata"), "Git exclusion explained");
  expect(await visible("#excluded", "IDE settings"), "IDE exclusion explained");
  expect(await visible("#excluded", "Build output"), "build exclusions explained");
  await click("#cancel");
  expect(await visible("h2", "Import cancelled"), "review cancellation shown");
  expect(await evaluate('location.pathname === "/"'), "review cancellation returns to catalog URL");
  await click("#choose");
  await visible("h2", "Review import");
  await fill("browser_html", "Browser HTML", "app.html");
  await click("#confirm");
  expect(await visible("#success", "Import successful"), "actual form import succeeds");
  expect(await visible("main", "catalog regenerated"), "success confirms catalog update");
  expect(await visible("article", "Browser HTML"), "imported project appears immediately on home");
  expect(await visible("#choose", "Add project"), "Add project stays available after import");
  expect(await evaluate('location.pathname === "/"'), "import returns to catalog URL");
  await click("#details");
  expect(await visible("#projectTitle", "Browser HTML"), "success links to project details");
  expect(await visible("#readme", "Reviewed in actual browser workflow"), "description persisted in details");
  expect(await visible("#readme", "app.html"), "selected main file persisted");
  expect(await visible("#openApp", "Open HTML app"), "HTML launch link visible");
  const appUrl = await evaluate('document.querySelector("#openApp").href');
  expect(new URL(appUrl).origin !== new URL(url).origin, "app isolated from management origin");
  const { targetId: appTarget } = await send("Target.createTarget", { url: appUrl });
  const { sessionId: appSession } = await send("Target.attachToTarget", { targetId: appTarget, flatten: true });
  await until(() => evaluate('document.querySelector("#fixture")?.textContent === "Original HTML works"', appSession), "HTML app visible");
  checks++;
  await send("Target.closeTarget", { targetId: appTarget });
  await navigate(url);
  await click("#choose");
  await visible("h2", "Review import");
  await fill("browser_html", "Collision must not overwrite", "app.html");
  await click("#confirm");
  expect(await visible("main", "Destination already exists"), "collision visibly reported");
  await navigate(url);
  await click("#choose");
  await visible("h2", "Review import");
  await fill("browser_java", "Browser Java", "Example.java");
  await click("#confirm");
  await visible("#success", "Import successful");
  await click("#details");
  expect(await visible("main", "Java apps are not executed"), "Java shows run instructions, not execution");
  expect(await visible("#readme", "javac --release 21"), "Java compile guidance visible");
  expect(await evaluate('document.querySelector("#openApp") === null'), "no HTML action for Java");
  const catalogUrl = await evaluate(`(() => {
    const base = ${JSON.stringify(appUrl)};
    return new URL("/index.html", base).href;
  })()`);
  await navigate(catalogUrl);
  expect(await visible("main", "Browser HTML"), "updated catalog contains HTML import");
  expect(await visible("main", "Browser Java"), "updated catalog contains Java import");
  const screenshot = await send("Page.captureScreenshot", { format: "png" }, sessionId);
  writeFileSync(path.join(root, "out", "management-catalog-browser.png"), Buffer.from(screenshot.data, "base64"));
  expect(errors.length === 1 && errors[0].includes("400 (Bad Request)"),
    `expected only the deliberate collision HTTP 400; browser console: ${errors.join("\n")}`);
  fixture.stdin.end("\n");
  const [code] = await fixtureExit;
  expect(code === 0 && fixtureOutput.includes("PASS 6 browser persistence checks"),
    `fixture persistence failed:\n${fixtureOutput}\n${fixtureErrors}`);
  toolboxServer = spawn("java", ["-cp", path.join(root, "out"), "Main", "serve"], { cwd: root });
  let serverOutput = "", serverErrors = "";
  toolboxServer.stdout.on("data", bytes => { serverOutput += bytes; });
  toolboxServer.stderr.on("data", bytes => { serverErrors += bytes; });
  const printedUrl = await until(() => serverOutput.match(/http:\/\/127\.0\.0\.1:\d+\//)?.[0],
    "actual Toolbox server printed URL");
  await navigate(printedUrl);
  expect(await visible("#choose", "Add project"), "printed server URL shows Add project");
  for (const title of ["Presentation Timer", "Student Spin Wheel", "CSV Quick Check"]) {
    expect(await evaluate(`Array.from(document.querySelectorAll("article")).some(card =>
      card.querySelector("h2")?.textContent === ${JSON.stringify(title)}
      && card.querySelector(".action")?.textContent === "Open app" && card.querySelector("a.details")?.textContent === "Details"
      && !!card.querySelector("p")?.textContent.trim())`), `${title} card has description, action and Details`);
  }
  const wheelUrl = await evaluate(`(() => {
    const card = Array.from(document.querySelectorAll("article"))
      .find(item => item.querySelector("h2")?.textContent === "Student Spin Wheel");
    return card.querySelector(".action").href;
  })()`);
  expect(new URL(wheelUrl).pathname === "/projects/student_spin_wheel/student_spin_wheel.html",
    "live catalog Open app targets the browser wheel");
  const wheelDetails = await fetch(new URL("/project/student_spin_wheel", printedUrl));
  const wheelDetailsHtml = await wheelDetails.text();
  expect(wheelDetails.ok && wheelDetailsHtml.includes('id="openApp"')
    && wheelDetailsHtml.includes("projects/student_spin_wheel/student_spin_wheel.html"),
  "live project details link to the browser wheel");
  expect(await evaluate('document.querySelector("#choose").getBoundingClientRect().bottom <= document.querySelector(".cards").getBoundingClientRect().top'),
    "Add project is above cards");
  expect(await evaluate('!document.querySelector("main").textContent.includes("**Dates:**")'),
    "technical metadata stays off catalog home");
  await navigate(new URL("/index.html", wheelUrl).href);
  const offlineWheelUrl = await evaluate(`(() => {
    const card = Array.from(document.querySelectorAll("article"))
      .find(item => item.querySelector("h2")?.textContent === "Student Spin Wheel");
    return card.querySelector(".actions a").href;
  })()`);
  expect(new URL(offlineWheelUrl).pathname === "/projects/student_spin_wheel/student_spin_wheel.html",
    "offline catalog Open app targets the browser wheel");
  await navigate(offlineWheelUrl);
  expect(await visible("h1", "Student Spin Wheel"), "offline catalog link opens the browser app");
  await send("Emulation.setDeviceMetricsOverride", {
    width: 1280, height: 900, deviceScaleFactor: 1, mobile: false
  }, sessionId);
  await navigate(wheelUrl);
  expect(await evaluate('document.querySelector("#remaining").textContent === "Remaining students: 10"'),
    "browser app starts with the original roster");
  expect(await evaluate('document.querySelector("#removeWinner").checked'),
    "winner removal is enabled by default");
  await addName("   ");
  expect(await evaluate('document.querySelector("#status").textContent === "Please enter a new student name."'
    + ' && document.querySelector("#remaining").textContent === "Remaining students: 10"'),
    "blank names are rejected inline");
  await addName(" Zoe ");
  expect(await evaluate('document.querySelector("#remaining").textContent === "Remaining students: 11"'),
    "adding a trimmed name by click updates the active roster");
  expect(await evaluate('document.querySelector("#newName").value === ""'),
    "successful addition clears the editor");
  await addName("zoe", true);
  expect(await evaluate('document.querySelector("#status").textContent.startsWith("Duplicate name:")'),
    "duplicate names are rejected case-insensitively");
  await evaluate(`(() => {
    document.querySelector("#spin").addEventListener("click", () => {
      window.__spinStarted = performance.now();
    }, { capture: true, once: true });
    Math.random = () => 0.999999;
  })()`);
  await click("#spin");
  expect(await evaluate('document.querySelector("#status").textContent === "Spinning - good luck!"'
    + ' && ["spin", "newName", "addName", "removeWinner", "reset"].every(id => document.getElementById(id).disabled)'),
    "spin disables state-changing controls");
  await until(() => evaluate('document.querySelector("#result").textContent === "Zoe goes next!"'),
    "five-second spin completes");
  expect(await evaluate('performance.now() - window.__spinStarted >= 5000'),
    "spin animation lasts five seconds");
  expect(await evaluate('document.querySelector("#remaining").textContent === "Remaining students: 10"'
    + ' && document.querySelector("#history").textContent === "Zoe"'),
    "selected winner is removed and recorded in history");
  expect(await evaluate(`(() => {
    const wheel = document.querySelector("#wheel"), box = wheel.getBoundingClientRect();
    const radius = (box.width - 56) / 2;
    wheel.dispatchEvent(new PointerEvent("pointermove", {
      clientX: box.left + box.width / 2, clientY: box.top + box.height / 2 - radius * .8
    }));
    return wheel.title === "Zoe";
  })()`), "winner aligns with the wheel pointer");
  const wheelScreenshot = await send("Page.captureScreenshot", { format: "png", captureBeyondViewport: true }, sessionId);
  writeFileSync(path.join(root, "out", "student-wheel-winner.png"), Buffer.from(wheelScreenshot.data, "base64"));
  await addName("ZOE", true);
  expect(await evaluate('document.querySelector("#status").textContent.startsWith("Duplicate name:")'
    + ' && document.querySelector("#remaining").textContent === "Remaining students: 10"'),
    "removed winners remain in duplicate-name validation");
  await click("#removeWinner");
  await evaluate('Math.random = () => 0');
  await spin("Scott");
  await spin("Scott");
  expect(await evaluate('document.querySelector("#remaining").textContent === "Remaining students: 10"'
    + ' && JSON.stringify(Array.from(document.querySelectorAll("#history li"), item => item.textContent))'
    + ' === JSON.stringify(["Zoe", "Scott", "Scott"])'),
    "removal off allows repeat winners, preserves ordered history, and does not restore removed names");
  await addName("Alex", true);
  expect(await evaluate('document.querySelector("#remaining").textContent === "Remaining students: 11"'
    + ' && document.querySelector("#history").children.length === 3'
    + ' && document.querySelector("#result").textContent === "Scott goes next!"'),
    "adding by Enter preserves history and result without restoring removed names");
  await evaluate(`(() => {
    document.querySelector("#newName").value = "Draft text";
  })()`);
  await click("#reset");
  expect(await evaluate('document.querySelector("#remaining").textContent === "Remaining students: 10"'
    + ' && document.querySelector("#history").children.length === 0'
    + ' && document.querySelector("#result").textContent === "Who goes next?"'
    + ' && !document.querySelector("#removeWinner").checked'
    + ' && document.querySelector("#newName").value === "Draft text"'),
    "reset restores only defaults, clears history, and preserves editor and removal setting");
  await click("#removeWinner");
  const defaults = ["Scott", "Emma", "Liam", "Olivia", "Noah", "Ava", "James", "Sophia", "Lucas", "Mia"];
  for (const [index, winner] of defaults.entries()) {
    await spin(winner);
    expect(await evaluate(`document.querySelector("#remaining").textContent === "Remaining students: ${9 - index}"`),
      `removal leaves ${9 - index} students`);
  }
  expect(await evaluate('document.querySelector("#spin").disabled'
    + ' && document.querySelector("#status").textContent.includes("Reset to start again.")'
    + ' && JSON.stringify(Array.from(document.querySelectorAll("#history li"), item => item.textContent))'
    + ` === ${JSON.stringify(JSON.stringify(defaults))}`),
    "exhaustion disables Spin and displays reset instructions");
  await addName("Zoe", true);
  expect(await evaluate('!document.querySelector("#spin").disabled'
    + ' && document.querySelector("#remaining").textContent === "Remaining students: 1"'),
    "reset discarded old temporary names and adding after exhaustion enables Spin");
  await spin("Zoe");
  await click("#reset");
  expect(await evaluate('!document.querySelector("#spin").disabled'
    + ' && document.querySelector("#remaining").textContent === "Remaining students: 10"'
    + ' && document.querySelector("#history").children.length === 0'),
    "reset recovers from exhaustion");
  await send("Emulation.setDeviceMetricsOverride", {
    width: 390, height: 844, deviceScaleFactor: 2, mobile: true
  }, sessionId);
  expect(await evaluate('document.documentElement.scrollWidth <= innerWidth'
    + ' && Math.abs(document.querySelector("#wheel").getBoundingClientRect().width'
    + ' - document.querySelector("#wheel").getBoundingClientRect().height) < 1'),
    "mobile layout fits the viewport and keeps the wheel circular");
  const mobileScreenshot = await send("Page.captureScreenshot", { format: "png", captureBeyondViewport: true }, sessionId);
  writeFileSync(path.join(root, "out", "student-wheel-mobile.png"), Buffer.from(mobileScreenshot.data, "base64"));
  await send("Emulation.clearDeviceMetricsOverride", {}, sessionId);
  await navigate(pathToFileURL(path.join(root, "index.html")).href);
  const directWheelUrl = await evaluate(`Array.from(document.querySelectorAll("article"))
    .find(item => item.querySelector("h2")?.textContent === "Student Spin Wheel").querySelector(".actions a").href`);
  expect(directWheelUrl === pathToFileURL(path.join(root, "projects", "student_spin_wheel", "student_spin_wheel.html")).href,
    "local-file offline catalog targets the same browser app");
  await navigate(directWheelUrl);
  await addName("Offline student", true);
  expect(await evaluate('document.querySelector("#remaining").textContent === "Remaining students: 11"'),
    "direct-file app accepts names without a server");
  await evaluate('Math.random = () => 0.999999');
  await spin("Offline student");
  await click("#reset");
  expect(await evaluate('document.querySelector("#remaining").textContent === "Remaining students: 10"'
    + ' && document.querySelector("#history").children.length === 0'),
    "direct-file spin, removal, history, and reset work");
  expect(errors.length === 1 && errors[0].includes("400 (Bad Request)"),
    `no wheel or catalog browser errors; expected only deliberate collision: ${errors.join("\n")}`);
  expect(!serverErrors, `actual server errors: ${serverErrors}`);
  console.log(`PASS ${checks} browser workflow checks; wheel behavior and both catalog links verified`);
  console.log(fixtureOutput.trim().split("\n").slice(1).join("\n"));
} finally {
  writeFileSync(path.join(root, "out", "management-browser-console.log"), errors.join("\n") || "No browser errors.\n");
  if (toolboxServer && toolboxServer.exitCode === null) {
    const exited = once(toolboxServer, "exit");
    toolboxServer.kill();
    await exited;
  }
  if (socket) socket.close();
  if (chrome && chrome.exitCode === null) {
    const exited = once(chrome, "exit");
    chrome.kill();
    await exited;
  }
  if (fixture.exitCode === null) {
    fixture.stdin.end("\n");
    // Let fixture assertions and scoped cleanup finish even after a browser failure.
    await fixtureExit;
  }
  await sleep(250);
  rmSync(profile, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 });
}
