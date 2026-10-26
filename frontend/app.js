const state = { summaries: [], intervals: new Map(), events: new Map(), artifacts: [] };
const $ = (id) => document.getElementById(id);

function parseCsv(text) {
  const lines = text.trim().split(/\r?\n/).filter(Boolean);
  if (!lines.length) return [];
  const headers = lines[0].split(',');
  return lines.slice(1).map(line => {
    const values = line.split(',');
    return Object.fromEntries(headers.map((header, index) => [header, values[index] ?? '']));
  });
}

function parseProperties(text) {
  return Object.fromEntries(text.split(/\r?\n/).filter(line => line.includes('=')).map(line => {
    const index = line.indexOf('=');
    return [line.slice(0, index), line.slice(index + 1)];
  }));
}

function number(value) { return Number(value || 0); }
function formatRate(value) { return `${number(value).toLocaleString(undefined, { maximumFractionDigits: 1 })} B/s`; }
function formatMs(value) { return `${number(value).toFixed(2)} ms`; }

function resetState() {
  state.summaries = [];
  state.intervals.clear();
  state.events.clear();
  state.artifacts = [];
}

function ingestArtifact(name, text) {
  state.artifacts.push(name);
  if (name.endsWith('.properties')) return;
  const rows = parseCsv(text);
  if (!rows.length) return;
    if ('scenario' in rows[0] && 'goodput_bytes_per_sec' in rows[0]) state.summaries.push(...rows);
    if ('interval_start_nanos' in rows[0]) state.intervals.set(name, rows);
    if ('event_type' in rows[0]) state.events.set(name, rows);
}

async function loadFiles(fileList) {
  resetState();
  for (const file of fileList) ingestArtifact(file.name, await file.text());
  render();
}

async function loadBundled(names) {
  resetState();
  for (const name of names) {
    const response = await fetch(`demo-results/${name}`);
    if (response.ok) ingestArtifact(name, await response.text());
  }
  render();
}

async function loadFromBackend() {
  const response = await fetch('/api/results');
  if (!response.ok) throw new Error(`Backend returned ${response.status}`);
  const entries = await response.json();
  resetState();
  for (const entry of entries) {
    const artifact = await fetch(`/api/results/${encodeURIComponent(entry.name)}`);
    if (artifact.ok) ingestArtifact(entry.name, await artifact.text());
  }
  render();
}

function render() {
  $('empty-state').classList.toggle('hidden', state.summaries.length > 0 || state.intervals.size > 0);
  $('dashboard').classList.toggle('hidden', state.summaries.length === 0 && state.intervals.size === 0);
  renderCards(); renderSummary(); renderIntervalSelector(); renderEventSelector(); renderArtifacts();
  drawBarChart($('goodput-chart'), state.summaries, 'goodput_bytes_per_sec', formatRate);
  drawBarChart($('rtt-chart'), state.summaries, 'average_rtt_ms', formatMs);
  drawBarChart($('ack-chart'), state.summaries, 'acknowledgement_rate', value => `${number(value).toFixed(1)}%`);
  drawBarChart($('retransmission-chart'), state.summaries, 'retransmission_rate', value => `${number(value).toFixed(1)}%`);
  drawBarChart($('loss-chart'), state.summaries, 'packet_loss_rate', value => `${number(value).toFixed(1)}%`);
  drawIntervals();
  drawEvents();
}

function renderCards() {
  const acknowledged = state.summaries.reduce((sum, row) => sum + number(row.acknowledged), 0);
  const packets = state.summaries.reduce((sum, row) => sum + number(row.packets), 0);
  $('metadata').innerHTML = [
    ['Summaries', state.summaries.length], ['Interval files', state.intervals.size],
    ['Event files', state.events.size],
    ['Acknowledged', `${acknowledged}/${packets}`], ['Artifacts', state.artifacts.length]
  ].map(([label, value]) => `<div class="card"><span>${label}</span><strong>${value}</strong></div>`).join('');
}

function renderSummary() {
  $('summary-count').textContent = `${state.summaries.length} row(s)`;
  const columns = ['scenario', 'acknowledged', 'goodput_bytes_per_sec', 'average_rtt_ms', 'retransmissions'];
  $('summary-table').innerHTML = `<thead><tr>${columns.map(column => `<th>${column.replaceAll('_', ' ')}</th>`).join('')}</tr></thead><tbody>${state.summaries.map(row => `<tr>${columns.map(column => `<td>${column.includes('goodput') ? formatRate(row[column]) : column.includes('rtt') ? formatMs(row[column]) : row[column]}</td>`).join('')}</tr>`).join('')}</tbody>`;
}

function renderIntervalSelector() {
  $('interval-select').innerHTML = [...state.intervals.keys()].map(name => `<option>${name}</option>`).join('');
  $('interval-select').onchange = drawIntervals;
}

function renderEventSelector() {
  $('event-select').innerHTML = [...state.events.keys()].map(name => `<option>${name}</option>`).join('');
  $('event-select').onchange = drawEvents;
}

function renderArtifacts() {
  $('artifact-count').textContent = `${state.artifacts.length} file(s)`;
  $('artifacts').innerHTML = state.artifacts.map(name => `<li>${name}</li>`).join('');
}

function shortLabel(scenario) {
  const words = { aimd: 'AIMD', adaptive: 'Adaptive', fixed: 'Fixed', loss: 'Loss', delay: 'Delay' };
  const label = String(scenario || '')
    .replace(/^localhost-/, '')
    .replace(/fixed-window-(\d+)/, 'Fixed $1')
    .split('-')
    .map(word => words[word] || word)
    .join(' ');
  return label || 'run';
}

function fitLabel(context, text, maxWidth) {
  if (context.measureText(text).width <= maxWidth) return [text];
  const words = text.split(' ');
  if (words.length > 1) {
    for (let split = words.length - 1; split > 0; split--) {
      const first = words.slice(0, split).join(' '); const second = words.slice(split).join(' ');
      if (context.measureText(first).width <= maxWidth && context.measureText(second).width <= maxWidth) return [first, second];
    }
  }
  let cut = text;
  while (cut.length > 1 && context.measureText(`${cut}…`).width > maxWidth) cut = cut.slice(0, -1);
  return [`${cut}…`];
}

function compactNumber(value) {
  return number(value).toLocaleString(undefined, { notation: 'compact', maximumFractionDigits: 1 });
}

function metricValue(row, field) {
  if (field === 'acknowledgement_rate') return number(row.packets) ? number(row.acknowledged) / number(row.packets) * 100 : 0;
  if (field === 'retransmission_rate') return number(row.retransmission_rate) * 100;
  if (field === 'packet_loss_rate') return number(row.packets) ? Math.max(0, number(row.packets) - number(row.acknowledged)) / number(row.packets) * 100 : 0;
  return number(row[field]);
}

function drawBarChart(canvas, rows, field, formatter) {
  const context = canvas.getContext('2d'); const scale = devicePixelRatio;
  const width = canvas.clientWidth * scale; const height = 270 * scale;
  canvas.width = width; canvas.height = height; context.clearRect(0, 0, width, height);
  if (!rows.length) return;
  const top = 26 * scale; const bottom = 44 * scale; const plot = height - top - bottom;
  const max = Math.max(...rows.map(row => metricValue(row, field)), 1); const gap = width / rows.length;
  const fontSize = 12 * scale; const lineHeight = 15 * scale;
  context.textAlign = 'center';
  rows.forEach((row, index) => {
    const value = metricValue(row, field); const barHeight = (value / max) * plot;
    const centre = index * gap + gap / 2; const barTop = top + plot - barHeight;
    context.fillStyle = index % 2 ? '#8ea8ff' : '#62d6c7';
    context.fillRect(centre - gap * .32, barTop, gap * .64, barHeight);
    context.font = `600 ${fontSize}px system-ui`; context.fillStyle = '#e6ebf2';
    const valueText = field.includes('rtt') ? number(value).toFixed(2) : compactNumber(value);
    context.fillText(valueText, centre, barTop - 6 * scale);
    context.font = `${fontSize}px system-ui`; context.fillStyle = '#9aa8bb';
    fitLabel(context, shortLabel(row.scenario), gap * .92).forEach((line, lineIndex) => {
      context.fillText(line, centre, top + plot + 16 * scale + lineIndex * lineHeight);
    });
  });
  context.textAlign = 'start';
}

function drawIntervals() {
  const rows = state.intervals.get($('interval-select').value) || [];
  const canvas = $('interval-chart'); const context = canvas.getContext('2d');
  const width = canvas.clientWidth * devicePixelRatio; const height = 300 * devicePixelRatio;
  canvas.width = width; canvas.height = height; context.clearRect(0, 0, width, height);
  if (!rows.length) return;
  const max = Math.max(...rows.map(row => Math.max(number(row.sending_rate_bytes_per_sec), number(row.goodput_bytes_per_sec))), 1);
  const x = index => index * width / Math.max(rows.length - 1, 1);
  for (const [field, color] of [['sending_rate_bytes_per_sec', '#62d6c7'], ['goodput_bytes_per_sec', '#8ea8ff']]) {
    context.beginPath(); context.strokeStyle = color; context.lineWidth = 2 * devicePixelRatio;
    rows.forEach((row, index) => { const y = height - 30 - number(row[field]) / max * (height - 50); index ? context.lineTo(x(index), y) : context.moveTo(x(index), y); });
    context.stroke();
  }
}

function drawEvents() {
  const rows = state.events.get($('event-select').value) || [];
  const canvas = $('event-chart'); const context = canvas.getContext('2d');
  const scale = devicePixelRatio; const width = canvas.clientWidth * scale; const height = 300 * scale;
  canvas.width = width; canvas.height = height; context.clearRect(0, 0, width, height);
  const windows = rows.filter(row => row.event_type === 'WINDOW_CHANGED');
  if (!windows.length) return;
  const max = Math.max(...windows.map(row => number(row.packet_id)), 1);
  const first = number(windows[0].timestamp_nanos); const last = number(windows[windows.length - 1].timestamp_nanos);
  const span = Math.max(last - first, 1);
  const x = row => (number(row.timestamp_nanos) - first) / span * width;
  const y = row => height - 36 * scale - number(row.packet_id) / max * (height - 70 * scale);
  context.strokeStyle = '#f5bb65'; context.lineWidth = 2 * scale; context.beginPath();
  windows.forEach((row, index) => { index ? context.lineTo(x(row), y(row)) : context.moveTo(x(row), y(row)); });
  context.stroke();
  context.fillStyle = '#f5bb65'; windows.forEach(row => context.fillRect(x(row) - 2 * scale, y(row) - 2 * scale, 4 * scale, 4 * scale));
  context.font = `${12 * scale}px system-ui`; context.fillText('window', 8 * scale, 18 * scale);
}

async function runPreset(preset) {
  const status = $('run-status');
  for (const button of [$('run-quick'), $('run-loss'), $('run-delay')]) button.disabled = true;
  status.textContent = `starting ${preset} test…`;
  try {
    const response = await fetch(`/api/runs/${preset}`, { method: 'POST' });
    if (!response.ok) throw new Error(await response.text());
    const run = await response.json();
    let current;
    do {
      await new Promise(resolve => setTimeout(resolve, 500));
      const poll = await fetch(`/api/run-status/${encodeURIComponent(run.id)}`);
      if (!poll.ok) throw new Error(`status request returned ${poll.status}`);
      current = await poll.json();
      status.textContent = `${preset} test: ${current.status}`;
    } while (current.status === 'queued' || current.status === 'running');
    if (current.status !== 'completed') throw new Error(current.error || 'benchmark failed');
    await loadFromBackend();
    status.textContent = `${preset} test completed`;
  } catch (error) {
    status.textContent = 'server unavailable';
    window.alert(`Could not run the test. Start DashboardServer first.\n${error.message}`);
  } finally {
    for (const button of [$('run-quick'), $('run-loss'), $('run-delay')]) button.disabled = false;
  }
}

$('files').addEventListener('change', event => loadFiles(event.target.files));
$('load-demo').addEventListener('click', () => loadBundled([
  'fixed.csv', 'aimd.csv', 'adaptive.csv',
  'fixed-localhost-fixed-window-16-intervals.csv',
  'aimd-localhost-aimd-intervals.csv',
  'adaptive-localhost-adaptive-intervals.csv',
  'adaptive-localhost-adaptive-events.csv'
]));
$('load-impairment').addEventListener('click', () => loadBundled([
  'loss-adaptive.csv', 'loss-adaptive-loss-adaptive-intervals.csv',
  'loss-adaptive-loss-adaptive-events.csv'
]));
$('load-backend').addEventListener('click', async () => {
  try { await loadFromBackend(); }
  catch (error) { window.alert(`Backend unavailable. Start DashboardServer first.\n${error.message}`); }
});
$('clear').addEventListener('click', () => { resetState(); render(); });
$('run-quick').addEventListener('click', () => runPreset('quick'));
$('run-loss').addEventListener('click', () => runPreset('loss'));
$('run-delay').addEventListener('click', () => runPreset('delay'));
$('copy-command').addEventListener('click', async () => {
  await navigator.clipboard.writeText($('command').textContent);
  $('copy-command').textContent = 'Copied';
  setTimeout(() => { $('copy-command').textContent = 'Copy command'; }, 1200);
});
