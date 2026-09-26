const fs = require('fs');
const path = require('path');
const { pathToFileURL } = require('url');
const { chromium } = require('C:/Users/冠儒/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');

const outputDir = __dirname;
const editorPath = path.join(outputDir, 'RX400h-UI-Layout-Editor.html');
const screenshotPath = path.join(outputDir, 'editor-smoke.png');
const workbenchScreenshotPath = path.join(outputDir, 'editor-workbench.png');
const downloadDir = path.join(outputDir, 'smoke-downloads');

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

(async () => {
  fs.mkdirSync(downloadDir, { recursive: true });
  const browser = await chromium.launch({
    headless: true,
    executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe'
  });
  const context = await browser.newContext({
    viewport: { width: 1366, height: 768 },
    acceptDownloads: true
  });
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', error => errors.push(`pageerror: ${error.message}`));
  page.on('console', message => {
    if (message.type() === 'error') errors.push(`console: ${message.text()}`);
  });

  await page.goto(pathToFileURL(editorPath).href);
  await page.waitForSelector('[data-id="brand"]');
  await page.waitForTimeout(100);

  const geometry = await page.evaluate(() => {
    const canvas = document.getElementById('designCanvas').getBoundingClientRect();
    const workspace = document.getElementById('workspace').getBoundingClientRect();
    return {
      canvas: { left: canvas.left, top: canvas.top, right: canvas.right, bottom: canvas.bottom, width: canvas.width, height: canvas.height },
      workspace: { left: workspace.left, top: workspace.top, right: workspace.right, bottom: workspace.bottom, width: workspace.width, height: workspace.height },
      zoom: document.getElementById('zoomText').textContent,
      itemCount: document.querySelectorAll('.design-item').length
    };
  });
  assert(geometry.canvas.left >= geometry.workspace.left - 1, 'Canvas is clipped on the left');
  assert(geometry.canvas.right <= geometry.workspace.right + 1, 'Canvas is clipped on the right');
  assert(geometry.canvas.top >= geometry.workspace.top - 1, 'Canvas is clipped on the top');
  assert(geometry.canvas.bottom <= geometry.workspace.bottom + 1, 'Canvas is clipped on the bottom');
  assert(geometry.itemCount >= 30, 'Initial layout items did not render');

  const invalidStatePath = path.join(downloadDir, 'invalid-layout.json');
  const invalidState = {
    version: 1,
    canvas: { width: 1280, height: 720 },
    globals: {
      background: '#000704', phosphor: '#76ff96', scanlineOpacity: 0.06,
      showGrid: true, showSafe: true, showScanlines: true, snap: 2
    },
    items: [{
      id: 'broken', name: 'broken', type: 'text', x: 0, y: 0, w: 100, h: 30,
      parentId: null, z: 1, visible: true, locked: false, rotation: 0,
      text: 'broken', bindingKey: ''
    }]
  };
  fs.writeFileSync(invalidStatePath, JSON.stringify(invalidState), 'utf8');
  await page.locator('#importFile').setInputFiles(invalidStatePath);
  await page.waitForFunction(() => document.getElementById('statusText').textContent.includes('导入失败'));
  assert(await page.locator('.design-item').count() === 37, 'Invalid JSON damaged the current layout');

  const groupBefore = await page.locator('.design-item').count();
  await page.locator('[data-id="battery-panel"]').click({ position: { x: 6, y: 150 } });
  await page.locator('#duplicateButton').click();
  const groupAfter = await page.locator('.design-item').count();
  assert(groupAfter - groupBefore === 8, `Group duplication should include 7 children (added ${groupAfter - groupBefore} items)`);
  await page.locator('#undoButton').click();
  assert(await page.locator('.design-item').count() === 37, 'Undo after group duplication failed');

  await page.locator('[data-id="brand"]').dblclick();
  assert(await page.locator('[data-id="brand"]').getAttribute('contenteditable') === 'true', 'Double-click did not enter inline text editing');
  await page.keyboard.press('Control+A');
  await page.keyboard.type('LINE 1');
  await page.keyboard.press('Enter');
  await page.keyboard.type('LINE 2');
  await page.locator('#fitButton').click();
  const multilineText = await page.locator('[data-id="brand"]').textContent();
  assert(multilineText === 'LINE 1\nLINE 2', `Inline editing lost a newline (got ${JSON.stringify(multilineText)})`);
  await page.locator('#undoButton').click();
  assert(await page.locator('[data-id="brand"]').textContent() === 'RX400h', 'Undo after inline editing failed');

  const emptyStatePath = path.join(downloadDir, 'empty-layout.json');
  fs.writeFileSync(emptyStatePath, JSON.stringify({
    version: 1,
    canvas: { width: 1280, height: 720 },
    globals: {
      background: '#000704', phosphor: '#76ff96', scanlineOpacity: 0.06,
      showGrid: true, showSafe: true, showScanlines: true, snap: 2
    },
    items: []
  }), 'utf8');
  page.once('dialog', dialog => dialog.accept());
  await page.locator('#importFile').setInputFiles(emptyStatePath);
  await page.waitForFunction(() => document.getElementById('statusText').textContent.includes('导入成功'));
  assert(await page.locator('.design-item').count() === 0, 'Empty layout import did not produce an empty canvas');
  await page.locator('#addTextButton').click();
  assert(await page.locator('.design-item').count() === 1, 'Could not add an item to an empty canvas');
  assert(await page.locator('.design-item').evaluate(element => element.style.zIndex) === '1', 'First item on an empty canvas did not get z=1');
  await page.locator('#undoButton').click();
  await page.locator('#undoButton').click();
  assert(await page.locator('.design-item').count() === 37, 'Undo did not restore the initial layout after empty-canvas test');

  await page.locator('[data-id="brand"]').click();
  const xInput = page.locator('#inspector [data-key="x"]');
  assert(await xInput.count() === 1, 'Inspector X input missing');
  await xInput.fill('60');
  await page.waitForTimeout(450);
  const changedX = await page.locator('[data-id="brand"]').evaluate(element => Number.parseInt(element.style.left, 10));
  assert(changedX === 60, `Inspector edit did not update X (got ${changedX})`);

  await page.locator('#undoButton').click();
  const undoX = await page.locator('[data-id="brand"]').evaluate(element => Number.parseInt(element.style.left, 10));
  assert(undoX === 30, `Undo did not restore X (got ${undoX})`);
  await page.locator('#redoButton').click();
  const redoX = await page.locator('[data-id="brand"]').evaluate(element => Number.parseInt(element.style.left, 10));
  assert(redoX === 60, `Redo did not restore X (got ${redoX})`);

  const brand = page.locator('[data-id="brand"]');
  const beforeDrag = await brand.boundingBox();
  await page.mouse.move(beforeDrag.x + beforeDrag.width / 2, beforeDrag.y + beforeDrag.height / 2);
  await page.mouse.down();
  await page.mouse.move(beforeDrag.x + beforeDrag.width / 2 + 24, beforeDrag.y + beforeDrag.height / 2 + 12, { steps: 4 });
  await page.mouse.up();
  const draggedX = await brand.evaluate(element => Number.parseInt(element.style.left, 10));
  assert(draggedX > redoX, 'Pointer drag did not move selected item');

  const jsonDownload = page.waitForEvent('download');
  await page.locator('#exportJsonButton').click();
  const jsonArtifact = await jsonDownload;
  const jsonPath = path.join(downloadDir, 'layout.json');
  await jsonArtifact.saveAs(jsonPath);
  const parsed = JSON.parse(fs.readFileSync(jsonPath, 'utf8'));
  assert(parsed.canvas.width === 1280 && parsed.canvas.height === 720, 'JSON export canvas size is incorrect');
  assert(parsed.items.some(item => item.id === 'brand'), 'JSON export is missing layout items');
  page.once('dialog', dialog => dialog.accept());
  await page.locator('#importFile').setInputFiles(jsonPath);
  await page.waitForFunction(() => document.getElementById('statusText').textContent.includes('导入成功'));
  assert(await page.locator('.design-item').count() === 37, 'Valid JSON round-trip changed the item count');

  const svgDownload = page.waitForEvent('download');
  await page.locator('#exportSvgButton').click();
  const svgArtifact = await svgDownload;
  const svgPath = path.join(downloadDir, 'layout.svg');
  await svgArtifact.saveAs(svgPath);
  const svg = fs.readFileSync(svgPath, 'utf8');
  assert(svg.includes('width="1280" height="720"'), 'SVG export size is incorrect');
  assert(svg.includes('RX400h'), 'SVG export is missing visible text');
  const secondaryOpacity = svg.match(/opacity="([0-9.]+)"[^>]*><tspan[^>]*>VEHICLE MONITOR<\/tspan>/);
  assert(secondaryOpacity && Math.abs(Number(secondaryOpacity[1]) - 0.72) < 0.001, 'SVG text brightness does not match the editor');

  const pngDownload = page.waitForEvent('download', { timeout: 7000 }).catch(() => null);
  await page.locator('#exportPngButton').click();
  const pngArtifact = await pngDownload;
  if (!pngArtifact) {
    const pngStatus = await page.locator('#statusText').textContent();
    throw new Error(`PNG download did not start. Status: ${pngStatus}. Browser errors: ${errors.join(' | ') || 'none'}`);
  }
  const pngPath = path.join(downloadDir, 'layout.png');
  await pngArtifact.saveAs(pngPath);
  const png = fs.readFileSync(pngPath);
  assert(png.readUInt32BE(16) === 1280 && png.readUInt32BE(20) === 720, 'PNG export size is incorrect');

  await page.locator('#undoButton').click();
  await page.locator('#undoButton').click();
  const restoredBrand = await page.locator('[data-id="brand"]').evaluate(element => ({
    x: Number.parseInt(element.style.left, 10),
    y: Number.parseInt(element.style.top, 10)
  }));
  assert(restoredBrand.x === 30 && restoredBrand.y === 22, 'Layout did not return to its initial position before preview');

  await page.screenshot({ path: workbenchScreenshotPath, fullPage: true });

  await page.locator('#previewButton').click();
  const preview = await page.evaluate(() => ({
    active: document.body.classList.contains('preview-mode'),
    toolbar: getComputedStyle(document.getElementById('editorToolbar')).display,
    safeGuide: getComputedStyle(document.getElementById('safeGuide')).display,
    renderedItems: document.querySelectorAll('.design-item').length,
    visibleItems: [...document.querySelectorAll('.design-item')].filter(element => {
      const box = element.getBoundingClientRect();
      const style = getComputedStyle(element);
      return style.display !== 'none' && style.visibility !== 'hidden' && Number(style.opacity) > 0 && box.width > 0 && box.height > 0;
    }).length,
    canvas: document.getElementById('designCanvas').getBoundingClientRect().toJSON()
  }));
  assert(preview.active && preview.toolbar === 'none' && preview.safeGuide === 'none', 'Clean preview did not hide editor chrome and guides');
  assert(preview.renderedItems === 37 && preview.visibleItems >= 36, 'Clean preview lost layout elements');
  await page.waitForTimeout(150);
  await page.screenshot({ path: screenshotPath, fullPage: true });

  assert(errors.length === 0, errors.join('\n'));
  console.log(JSON.stringify({
    ok: true,
    geometry,
    inspectorEdit: { changedX, undoX, redoX, draggedX },
    exports: { json: jsonPath, svg: svgPath, png: pngPath },
    screenshots: { workbench: workbenchScreenshotPath, preview: screenshotPath }
  }, null, 2));
  await browser.close();
})().catch(async error => {
  console.error(error.stack || error.message);
  process.exitCode = 1;
});
