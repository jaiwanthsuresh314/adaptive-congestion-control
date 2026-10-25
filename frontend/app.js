const state = { summaries: [], intervals: new Map(), artifacts: [] };
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

async function loadFiles(fileList) {
  state.summaries = [];
  state.intervals.clear();
  state.artifacts = [...fileList].map(file => file.name);
  for (const file of fileList) {
    const text = await file.text();
    if (file.name.endsWith('.properties')) continue;
    const rows = parseCsv(text);
    if (!rows.length) continue;
    if ('scenario' in rows[0] && 'goodput_bytes_per_sec' in rows[0]) state.summaries.push(...rows);
    if ('interval_start_nanos' in rows[0]) state.intervals.set(file.name, rows);
  }
  render();
}

function render() {
  $('empty-state').classList.toggle('hidden', state.summaries.length > 0 || state.intervals.size > 0);
  $('dashboard').classList.toggle('hidden', state.summaries.length === 0 && state.intervals.size === 0);
  renderCards(); renderSummary(); renderIntervalSelector(); renderArtifacts();
  drawBarChart($('goodput-chart'), state.summaries, 'goodput_bytes_per_sec', formatRate);
  drawBarChart($('rtt-chart'), state.summaries, 'average_rtt_ms', formatMs);
  drawIntervals();
}

function renderCards() {
  const acknowledged = state.summaries.reduce((sum, row) => sum + number(row.acknowledged), 0);
  const packets = state.summaries.reduce((sum, row) => sum + number(row.packets), 0);
  $('metadata').innerHTML = [
    ['Summaries', state.summaries.length], ['Interval files', state.intervals.size],
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

function renderArtifacts() {
  $('artifact-count').textContent = `${state.artifacts.length} file(s)`;
  $('artifacts').innerHTML = state.artifacts.map(name => `<li>${name}</li>`).join('');
}

function drawBarChart(canvas, rows, field, formatter) {
  const context = canvas.getContext('2d');
  const width = canvas.clientWidth * devicePixelRatio; const height = 270 * devicePixelRatio;
  canvas.width = width; canvas.height = height; context.clearRect(0, 0, width, height);
  if (!rows.length) return;
  const max = Math.max(...rows.map(row => number(row[field])), 1); const gap = width / rows.length;
  rows.forEach((row, index) => {
    const value = number(row[field]); const barHeight = (value / max) * (height - 50);
    context.fillStyle = index % 2 ? '#8ea8ff' : '#62d6c7'; context.fillRect(index * gap + gap * .18, height - barHeight - 25, gap * .64, barHeight);
    context.fillStyle = '#9aa8bb'; context.font = `${12 * devicePixelRatio}px system-ui`; context.fillText(row.scenario, index * gap + gap * .12, height - 8);
  });
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

$('files').addEventListener('change', event => loadFiles(event.target.files));
