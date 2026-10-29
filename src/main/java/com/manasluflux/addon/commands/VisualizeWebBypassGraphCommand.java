package com.manasluflux.addon.commands;

import com.manasluflux.addon.modules.CobwebBypass;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.command.CommandSource;

import java.awt.Desktop;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

/**
 * .mfvisualizewebbypassgraph - writes the Cobweb Bypass timer curve to an HTML file and opens it in
 * the default browser, so the formula can be eyeballed instead of guessed at.
 *
 * <p>The samples are taken from {@link CobwebBypass#timerSpeedAt(double)}, the same call the running
 * timer makes every tick, so the page always shows the module as it is configured right now - not a
 * second implementation of the maths that could drift.
 */
public class VisualizeWebBypassGraphCommand extends Command {
    /** How far into a web the chart looks. Nobody stands in one longer than this on purpose. */
    private static final double MAX_SECONDS = 20.0;
    /** Resolution of the curve; 400 points is smooth at any chart width. */
    private static final int STEPS = 400;

    public VisualizeWebBypassGraphCommand() {
        super("mfvisualizewebbypassgraph", "Opens a browser page charting the Cobweb Bypass timer formula and scale.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            CobwebBypass cobweb = Modules.get().get(CobwebBypass.class);
            if (cobweb == null) {
                Msg.error("Cobweb Bypass is not loaded.");
                return SINGLE_SUCCESS;
            }

            String formula = cobweb.graphFormulaValue();
            double scale = cobweb.graphScaleValue();
            boolean graph = cobweb.timerModeValue() == CobwebBypass.TimerMode.Graph;

            // One pass over the curve, in the same units the module uses (seconds in the web).
            double[] values = new double[STEPS + 1];
            double peak = 1.0;
            boolean broken = false;

            for (int i = 0; i <= STEPS; i++) {
                double t = MAX_SECONDS * i / STEPS;
                double speed = cobweb.timerSpeedAt(t);

                if (Double.isNaN(speed)) {
                    broken = true;
                    break;
                }

                values[i] = speed;
                peak = Math.max(peak, speed);
            }

            String error = broken ? "\"" + formula + "\" does not parse, so the timer is sitting at 1x" : "";

            try {
                File file = new File(MeteorClient.FOLDER, "webbypass-graph.html");
                File parent = file.getParentFile();
                if (parent != null) parent.mkdirs();

                String html = page(cobweb, formula, scale, graph, values, peak, broken, error);
                Files.writeString(file.toPath(), html, StandardCharsets.UTF_8);

                Msg.info("Timer graph written to %s", file.getAbsolutePath());

                if (broken) {
                    Msg.warning("Graph formula does not work (%s) - the page shows the error instead of a curve.", error);
                } else {
                    Msg.info("Peak timer speed on that chart: %.2fx at %.1fs in a web (scale %.3f).", peak, MAX_SECONDS, scale);
                }

                open(file);
            } catch (Exception e) {
                Msg.error("Could not write the graph page: %s", e.getMessage());
            }

            return SINGLE_SUCCESS;
        });
    }

    /** Hands the file to whatever the desktop registered for .html, and says so if that fails. */
    private void open(File file) {
        try {
            if (!Desktop.isDesktopSupported()) {
                Msg.warning("No desktop integration here - open %s yourself.", file.getAbsolutePath());
                return;
            }

            Desktop.getDesktop().browse(file.toURI());
        } catch (Exception e) {
            Msg.warning("Could not open the browser automatically (%s) - the file is at %s", e.getMessage(), file.getAbsolutePath());
        }
    }

    private static String esc(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String page(CobwebBypass cobweb, String formula, double scale, boolean graph,
                               double[] values, double peak, boolean broken, String error) {
        StringBuilder samples = new StringBuilder("[");

        for (int i = 0; i < values.length; i++) {
            if (i > 0) samples.append(',');
            samples.append('[')
                .append(String.format(Locale.ROOT, "%.3f", MAX_SECONDS * i / STEPS))
                .append(',')
                .append(String.format(Locale.ROOT, "%.4f", values[i]))
                .append(']');
        }
        samples.append(']');

        return TEMPLATE
            .replace("__MODE__", graph ? "Graph" : "Constant")
            .replace("__MODE_NOTE__", graph
                ? "The timer follows the formula below, evaluated with t = seconds spent in the current web."
                : "The timer uses one fixed multiplier while you are in a web; switch timer-mode to Graph for a curve.")
            .replace("__FORMULA__", esc(formula))
            .replace("__SCALE__", String.format(Locale.ROOT, "%.3f", scale))
            .replace("__WEBTIMER__", String.format(Locale.ROOT, "%.3f", cobweb.webTimerValue()))
            .replace("__CONSTANT__", String.format(Locale.ROOT, "%.3f", cobweb.timerMultiplierValue()))
            .replace("__PEAK__", String.format(Locale.ROOT, "%.3f", peak))
            .replace("__CAP__", String.format(Locale.ROOT, "%.3f", cobweb.graphMaxValue()))
            .replace("__MAXT__", String.format(Locale.ROOT, "%.1f", MAX_SECONDS))
            .replace("__SAMPLES__", samples.toString())
            .replace("__ERROR__", broken ? esc(error) : "")
            .replace("__BROKEN__", broken ? "true" : "false");
    }

    private static final String TEMPLATE = """
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Cobweb Bypass - timer graph</title>
<style>
  :root {
    --bg: #0a0a0d;
    --panel: #121217;
    --panel-2: #17171e;
    --line: #23232c;
    --txt: #eceaf3;
    --dim: #94919f;
    --accent: #c274f0;
    --good: #4ad6a8;
  }
  * { box-sizing: border-box; }
  body {
    margin: 0;
    padding: 40px 24px 64px;
    background: radial-gradient(120% 80% at 50% -10%, #1a1524 0%, var(--bg) 55%);
    color: var(--txt);
    font: 15px/1.55 -apple-system, "Segoe UI Variable", "Segoe UI", Inter, system-ui, sans-serif;
    -webkit-font-smoothing: antialiased;
    min-height: 100vh;
  }
  .wrap { max-width: 940px; margin: 0 auto; }
  .eyebrow {
    font-size: 11px; letter-spacing: .16em; text-transform: uppercase;
    color: var(--dim); margin: 0 0 10px;
  }
  h1 { font-size: 30px; line-height: 1.15; letter-spacing: -.02em; margin: 0 0 6px; font-weight: 650; }
  .sub { color: var(--dim); margin: 0 0 26px; max-width: 62ch; }
  .chips { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 22px; }
  .chip {
    display: inline-flex; align-items: center; gap: 8px;
    padding: 7px 12px; border-radius: 999px;
    background: var(--panel); border: 1px solid var(--line);
    font-size: 12.5px; color: var(--dim);
  }
  .chip b { color: var(--txt); font-weight: 600; }
  .chip .dot { width: 7px; height: 7px; border-radius: 50%; background: var(--good); }
  .card {
    background: var(--panel); border: 1px solid var(--line); border-radius: 18px;
    padding: 22px 22px 18px; margin-bottom: 18px;
  }
  .card.err { border-color: #7a2f38; background: #1b1216; }
  .card h2 { font-size: 13px; letter-spacing: .1em; text-transform: uppercase; color: var(--dim); margin: 0 0 14px; font-weight: 600; }
  .readout { display: flex; align-items: baseline; gap: 14px; flex-wrap: wrap; margin-bottom: 4px; }
  .big { font-size: 46px; font-weight: 650; letter-spacing: -.03em; font-variant-numeric: tabular-nums; }
  .big small { font-size: 18px; color: var(--dim); font-weight: 500; margin-left: 4px; }
  .readout .at { color: var(--dim); font-size: 13.5px; }
  svg { display: block; width: 100%; height: auto; touch-action: none; }
  .axis { fill: var(--dim); font-size: 11px; }
  .grid { stroke: var(--line); stroke-width: 1; }
  .base { stroke: #3a3a46; stroke-width: 1; stroke-dasharray: 4 5; }
  .curve { fill: none; stroke: var(--accent); stroke-width: 2.5; stroke-linejoin: round; stroke-linecap: round; }
  .fill { fill: url(#g); opacity: .85; }
  .marker { stroke: #6d6a7c; stroke-width: 1; stroke-dasharray: 3 4; }
  .cap { stroke: var(--good); stroke-width: 1.5; stroke-dasharray: 7 5; opacity: .75; }
  .caplabel { fill: var(--good); font-size: 11px; }
  .knob { fill: var(--txt); stroke: var(--accent); stroke-width: 2.5; }
  .controls { display: flex; align-items: center; gap: 14px; margin-top: 6px; }
  input[type=range] { flex: 1; accent-color: var(--accent); }
  .tval { font-variant-numeric: tabular-nums; color: var(--dim); font-size: 13px; min-width: 84px; text-align: right; }
  table { width: 100%; border-collapse: collapse; font-size: 13.5px; }
  th, td { text-align: left; padding: 7px 0; border-bottom: 1px solid var(--line); }
  th { color: var(--dim); font-weight: 500; }
  td:last-child, th:last-child { text-align: right; font-variant-numeric: tabular-nums; }
  code { font-family: ui-monospace, "Cascadia Code", Consolas, monospace; background: var(--panel-2); padding: 2px 7px; border-radius: 6px; font-size: 13px; }
  .note { color: var(--dim); font-size: 12.5px; margin: 14px 0 0; }
  .errmsg { color: #ff9aa6; }
  @media (prefers-reduced-motion: reduce) { * { transition: none !important; } }
</style>
</head>
<body>
<div class="wrap">
  <p class="eyebrow">ManasluFlux &middot; Cobweb Bypass</p>
  <h1>Timer graph</h1>
  <p class="sub">__MODE_NOTE__</p>

  <div class="chips">
    <span class="chip"><span class="dot"></span>Mode <b>__MODE__</b></span>
    <span class="chip">Formula <b>__FORMULA__</b></span>
    <span class="chip">Scale <b>__SCALE__</b></span>
    <span class="chip">Constant <b>__CONSTANT__x</b></span>
    <span class="chip">Web timer <b>__WEBTIMER__x</b></span>
    <span class="chip">Peak <b>__PEAK__x</b></span>
    <span class="chip">Max timer <b>__CAP__x</b></span>
  </div>

  <div class="card" id="err" hidden>
    <h2>The formula does not work</h2>
    <p class="errmsg" id="errmsg">__ERROR__</p>
    <p class="note">Until it is fixed the module leaves Meteor's Timer at 1x, so nothing is being sped up.</p>
  </div>

  <div class="card" id="chart-card">
    <div class="readout">
      <div class="big"><span id="speed">1.00</span><small>x timer</small></div>
      <div class="at" id="when">at 0.0 s in the web</div>
    </div>
    <svg id="chart" viewBox="0 0 900 340" role="img" aria-label="Timer multiplier over time spent in a cobweb">
      <defs>
        <linearGradient id="g" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#c274f0" stop-opacity=".34"/>
          <stop offset="100%" stop-color="#c274f0" stop-opacity="0"/>
        </linearGradient>
      </defs>
      <g id="grid"></g>
      <path id="area" class="fill" d=""></path>
      <path id="curve" class="curve" d=""></path>
      <line id="cap" class="cap" x1="0" y1="0" x2="0" y2="0" hidden></line>
      <text id="caplabel" class="caplabel" hidden></text>
      <line id="marker" class="marker" x1="0" y1="0" x2="0" y2="0"></line>
      <circle id="knob" class="knob" r="5"></circle>
      <g id="labels"></g>
    </svg>
    <div class="controls">
      <input id="slider" type="range" min="0" max="__MAXT__" step="0.1" value="0" aria-label="Seconds spent in the web">
      <span class="tval" id="tval">0.0 s</span>
    </div>
  </div>

  <div class="card">
    <h2>What the curve means</h2>
    <table>
      <tbody>
        <tr><th>1.00x</th><td>normal game speed (a formula value of 0)</td></tr>
        <tr><th>1 + scale</th><td>the plateau, reached once the formula sits at 1</td></tr>
        <tr><th>max-timer</th><td>a hard ceiling on the graph speed (0 = no cap); the green dashed line when it is in view</td></tr>
        <tr><th>t</th><td>seconds spent in the current web; it resets the moment you leave</td></tr>
      </tbody>
    </table>
    <p class="note">Sampled with the module's own evaluator, once per 0.05 s. The timer only runs while you actually stand in a cobweb.</p>
  </div>
</div>
<script>
const BROKEN = __BROKEN__;
const SAMPLES = __SAMPLES__;
const MAXT = __MAXT__;
const CAP = __CAP__;

const W = 900, H = 340, PAD_L = 46, PAD_R = 16, PAD_T = 14, PAD_B = 30;
const svg = document.getElementById('chart');
const grid = document.getElementById('grid');
const labels = document.getElementById('labels');
const slider = document.getElementById('slider');

function fmt(n, d) { return n.toFixed(d === undefined ? 2 : d); }

if (BROKEN) {
  document.getElementById('err').hidden = false;
  document.getElementById('chart-card').hidden = true;
}

if (!BROKEN && SAMPLES.length > 1) {
  const peak = Math.max(...SAMPLES.map(p => p[1]), 1.1);
  const yMax = Math.ceil(peak * 1.06 * 10) / 10;
  const yMin = 0.9;

  const xs = t => PAD_L + (t / MAXT) * (W - PAD_L - PAD_R);
  const ys = v => H - PAD_B - ((v - yMin) / (yMax - yMin)) * (H - PAD_T - PAD_B);

  // Horizontal grid + value labels
  const rows = 5;
  for (let i = 0; i <= rows; i++) {
    const v = yMin + (yMax - yMin) * (i / rows);
    const y = ys(v);
    const l = document.createElementNS('http://www.w3.org/2000/svg', 'line');
    l.setAttribute('x1', PAD_L); l.setAttribute('x2', W - PAD_R);
    l.setAttribute('y1', y); l.setAttribute('y2', y);
    l.setAttribute('class', 'grid');
    grid.appendChild(l);

    const t = document.createElementNS('http://www.w3.org/2000/svg', 'text');
    t.setAttribute('x', PAD_L - 10); t.setAttribute('y', y + 4);
    t.setAttribute('text-anchor', 'end'); t.setAttribute('class', 'axis');
    t.textContent = fmt(v, 1) + 'x';
    labels.appendChild(t);
  }

  // Vertical grid + seconds
  for (let s = 0; s <= MAXT; s += MAXT / 4) {
    const x = xs(s);
    const l = document.createElementNS('http://www.w3.org/2000/svg', 'line');
    l.setAttribute('x1', x); l.setAttribute('x2', x);
    l.setAttribute('y1', PAD_T); l.setAttribute('y2', H - PAD_B);
    l.setAttribute('class', 'grid');
    grid.appendChild(l);

    const t = document.createElementNS('http://www.w3.org/2000/svg', 'text');
    t.setAttribute('x', x); t.setAttribute('y', H - PAD_B + 18);
    t.setAttribute('text-anchor', 'middle'); t.setAttribute('class', 'axis');
    t.textContent = Math.round(s) + 's';
    labels.appendChild(t);
  }

  // Normal-speed baseline
  const base = document.createElementNS('http://www.w3.org/2000/svg', 'line');
  base.setAttribute('x1', PAD_L); base.setAttribute('x2', W - PAD_R);
  base.setAttribute('y1', ys(1)); base.setAttribute('y2', ys(1));
  base.setAttribute('class', 'base');
  grid.appendChild(base);

  // The max-timer ceiling, when it is actually within view.
  if (CAP > 0 && CAP >= yMin && CAP <= yMax) {
    const cap = document.getElementById('cap');
    cap.hidden = false;
    cap.setAttribute('x1', PAD_L); cap.setAttribute('x2', W - PAD_R);
    cap.setAttribute('y1', ys(CAP)); cap.setAttribute('y2', ys(CAP));

    const label = document.getElementById('caplabel');
    label.hidden = false;
    label.setAttribute('x', W - PAD_R - 6);
    label.setAttribute('y', ys(CAP) - 7);
    label.setAttribute('text-anchor', 'end');
    label.textContent = 'max ' + fmt(CAP, 2) + 'x';
  }

  let d = '';
  SAMPLES.forEach((p, i) => { d += (i ? ' L' : 'M') + xs(p[0]).toFixed(1) + ' ' + ys(p[1]).toFixed(1); });

  document.getElementById('curve').setAttribute('d', d);
  document.getElementById('area').setAttribute('d',
    d + ' L' + xs(SAMPLES[SAMPLES.length - 1][0]).toFixed(1) + ' ' + (H - PAD_B) +
    ' L' + xs(0).toFixed(1) + ' ' + (H - PAD_B) + ' Z');

  const marker = document.getElementById('marker');
  const knob = document.getElementById('knob');

  function sampleAt(t) {
    if (t <= 0) return SAMPLES[0][1];
    if (t >= MAXT) return SAMPLES[SAMPLES.length - 1][1];

    const idx = t / MAXT * (SAMPLES.length - 1);
    const lo = Math.floor(idx), hi = Math.min(lo + 1, SAMPLES.length - 1);
    const f = idx - lo;
    return SAMPLES[lo][1] * (1 - f) + SAMPLES[hi][1] * f;
  }

  function mark(t) {
    const v = sampleAt(t);
    const x = xs(t), y = ys(v);

    marker.setAttribute('x1', x); marker.setAttribute('x2', x);
    marker.setAttribute('y1', PAD_T); marker.setAttribute('y2', H - PAD_B);
    knob.setAttribute('cx', x); knob.setAttribute('cy', y);

    document.getElementById('speed').textContent = fmt(v);
    document.getElementById('when').textContent = 'at ' + fmt(t, 1) + ' s in the web';
    document.getElementById('tval').textContent = fmt(t, 1) + ' s';
  }

  slider.addEventListener('input', () => mark(parseFloat(slider.value)));

  svg.addEventListener('pointermove', (e) => {
    const r = svg.getBoundingClientRect();
    const px = (e.clientX - r.left) / r.width * W;
    let t = (px - PAD_L) / (W - PAD_L - PAD_R) * MAXT;
    t = Math.max(0, Math.min(MAXT, t));
    slider.value = t.toFixed(1);
    mark(t);
  });

  mark(parseFloat(slider.value) || 0);
}
</script>
</body>
</html>
""";
}
