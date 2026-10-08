// #AI-CODE-FILE-dev_release/v0.0.1-20260924: SVG-filter displacement engine for the login card. The WebGL engine
// (displacement-engine.js) can only show the effect where the experimental HTML-in-Canvas API exists
// (chrome://flags/#canvas-draw-element, or an origin trial in production); this engine reuses the same
// pointer-driven field but distorts the *live* DOM through feDisplacementMap, so every modern browser shows the
// effect. Because the DOM is never captured, inputs keep their focus, caret and hit testing for free.
//
// Calibration mirrors the shader: `push = field * 0.02 * shift` in UV space, i.e. `field * 0.02 * shift * box` CSS
// pixels. The filter runs with primitiveUnits="objectBoundingBox", so map geometry and feDisplacementMap `scale`
// are fractions of the border box and a single `scale` covers both axes exactly like the UV shader does:
// feDisplacementMap gathers from `p + scale * (channel / 255 - 0.5)`, hence `scale = 255 * push / gain`.
// Keep both engines in sync if the shader constant ever changes.
import { DEFAULTS } from "./displacement-engine.js";
import { createRectCache } from "./rect-cache.js";

const SVG_NS = "http://www.w3.org/2000/svg";
const XLINK_NS = "http://www.w3.org/1999/xlink";

const SHADER_PUSH = 0.02; // displacement per field unit, in UV space (same as the GLSL `uShift` factor)
const MAP_MID = 128; // "no displacement" channel value
// Field magnitude that saturates a channel. The shader left this unbounded, which tears the card apart on a hard
// sweep; saturating at ~18% of the border box keeps a fast sweep readable as a smear instead of a blank card.
const MAP_HEADROOM = 8;
const MAP_INTERVAL = 1000 / 30; // PNG map refresh budget, independent of the rAF simulation rate
const SCRAMBLE_UNITS = 3; // bounded entry ripple (~26px on a 400px card) that decays in under a second
const IDLE_EPSILON = 0.03; // field magnitude below which the surface counts as at rest
const GRAIN_SCALE = 0.3; // grain amplitude, matching the shader's `(gn - 0.5) * 0.3 * uGrain`
const GRAIN_CELL = 1.5; // grain cell size in CSS px, matching the shader's `uGrainPx`
const MAX_SEED = 997;

// Channel isolation for chromatic aberration: keep one colour channel, carry the source alpha through untouched.
const CHANNEL_MATRIX = {
  r: "1 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 0 1 0",
  g: "0 0 0 0 0  0 1 0 0 0  0 0 0 0 0  0 0 0 1 0",
  b: "0 0 0 0 0  0 0 0 0 0  0 0 1 0 0  0 0 0 1 0",
};

const clamp = (value, min, max) => Math.min(Math.max(value, min), max);

const smoothstep = (edge0, edge1, value) => {
  const t = clamp((value - edge0) / (edge1 - edge0), 0, 1);
  return t * t * (3 - 2 * t);
};

function svgNode(name, attrs = {}) {
  const node = document.createElementNS(SVG_NS, name);
  for (const [key, value] of Object.entries(attrs)) {
    node.setAttribute(key, String(value));
  }
  return node;
}

let sequence = 0;

/**
 * Distort one element with a pointer-driven SVG displacement filter.
 *
 * @param {{ content: Element, container?: Element }} elements - element to distort, and where the hidden <svg>
 *   holding the filter definition is mounted (defaults to document.body).
 * @param {object} options - DEFAULTS-compatible options, mirrored onto the props of the Vue wrapper.
 * @returns {{ setOptions: Function, resize: Function, destroy: Function } | null} null when unavailable, in which
 *   case the caller keeps the plain, undistorted DOM.
 */
export function createSvgDisplacement(elements, options = {}) {
  if (typeof document === "undefined") return null;

  const { content, container } = elements;
  if (!content) return null;

  const config = { ...DEFAULTS, ...options };
  const filterId = `aw-displacement-${(sequence += 1)}`;
  const motionQuery = window.matchMedia("(prefers-reduced-motion: reduce)");

  let host = null;
  let filter = null;
  let mapNode = null;
  let graph = [];
  let scaleTargets = [];
  let grain = null;

  try {
    host = svgNode("svg", { "aria-hidden": "true", focusable: "false", width: "0", height: "0" });
    // A zero-sized <svg> is the standard filter-definition host: referenced filters are resources, not painted
    // content. It must not be display:none - some engines then silently refuse to load the feImage.
    host.setAttribute(
      "style",
      "position: absolute; width: 0; height: 0; overflow: hidden; pointer-events: none",
    );
    filter = svgNode("filter", {
      id: filterId,
      // The filter region is the border box, the same rectangle the WebGL engine clips to with `inset: 0`, so
      // displaced ink cannot spill over the glass shell.
      x: "0%",
      y: "0%",
      width: "100%",
      height: "100%",
      filterUnits: "objectBoundingBox",
      // Bounding-box primitives keep the map geometry aspect-independent and make `scale` a fraction of the box.
      primitiveUnits: "objectBoundingBox",
      // The filter pipeline is linearRGB by default, which would bend the "128 = no displacement" encoding.
      "color-interpolation-filters": "sRGB",
    });
    // feImage is the only way to hand a per-frame field to a filter, and a primitive may only read results declared
    // before it, so the map stays the first node of every graph.
    mapNode = svgNode("feImage", {
      x: 0,
      y: 0,
      width: 1,
      height: 1,
      preserveAspectRatio: "none",
      result: "map",
    });
    host.appendChild(filter);
    (container || document.body).appendChild(host);
  } catch (error) {
    console.warn("[canvasui] SVG displacement is unavailable, keeping the plain card:", error);
    host?.remove();
    return null;
  }

  const state = {
    cols: 0,
    rows: 0,
    gain: (255 - MAP_MID) / MAP_HEADROOM,
    scale: 0,
    push: SHADER_PUSH,
    boxW: 1,
    boxH: 1,
    rowScale: 1,
  };

  const mouse = { x: 0, y: 0, prevX: 0, prevY: 0, vX: 0, vY: 0, speed: 0, gate: 0, lastT: 0 };

  let field = new Float32Array(0);
  let scratch = null; // { canvas, ctx, image } - the tiny PNG handed to feImage
  let seeded = false;
  let tracking = false;
  let active = false;
  let reducedMotion = motionQuery.matches;
  let destroyed = false;
  let running = false;
  let visible = true;
  let raf = 0;
  let lastTime = performance.now();
  let lastMapAt = 0;
  let lastMaxAbs = 0;
  let mapReadyAt = 0; // when the first PNG reached the feImage, see the decode guard in `frame`

  // ---- filter graph ---------------------------------------------------------------------------------------

  function rebuildGraph() {
    for (const node of graph) node.remove();
    graph = [];
    scaleTargets = [];
    grain = null;

    const spread = clamp(config.aberration, 0, 3) * 0.08; // same ratio as the shader's `ab`
    const channels = spread > 0
      ? [["r", 1 + spread], ["g", 1], ["b", 1 - spread]]
      : [[null, 1]];

    let tail = null;
    if (channels.length === 1) {
      const displace = svgNode("feDisplacementMap", {
        in: "SourceGraphic",
        in2: "map",
        xChannelSelector: "R",
        yChannelSelector: "G",
        result: "displaced",
      });
      graph.push(displace);
      scaleTargets.push({ node: displace, factor: 1 });
      tail = "displaced";
    } else {
      // Red and blue travel further and shorter than green, which is what produces the colour fringing on the
      // moving edges. The channels are isolated before displacing so each one is only ever warped once.
      let merged = null;
      for (const [channel, factor] of channels) {
        const isolate = svgNode("feColorMatrix", {
          in: "SourceGraphic",
          type: "matrix",
          values: CHANNEL_MATRIX[channel],
          result: `src-${channel}`,
        });
        const displace = svgNode("feDisplacementMap", {
          in: `src-${channel}`,
          in2: "map",
          xChannelSelector: "R",
          yChannelSelector: "G",
          result: `disp-${channel}`,
        });
        graph.push(isolate, displace);
        scaleTargets.push({ node: displace, factor });
        if (merged === null) {
          merged = `disp-${channel}`;
        } else {
          // screen, not arithmetic: isolated channels add exactly, while the alpha stays close to the source
          const blend = svgNode("feBlend", {
            in: merged,
            in2: `disp-${channel}`,
            mode: "screen",
            result: `mix-${channel}`,
          });
          graph.push(blend);
          merged = `mix-${channel}`;
        }
      }
      tail = merged;
    }

    if (clamp(config.grain, 0, 1) > 0) {
      // Film grain on the moving surface: noise masked by the content alpha, added on top of the displaced result.
      const turbulence = svgNode("feTurbulence", {
        type: "fractalNoise",
        baseFrequency: state.boxW / (clamp(config.grainSize, 0.5, 4) * GRAIN_CELL),
        numOctaves: 1,
        seed: 1,
        stitchTiles: "stitch",
        result: "grain-noise",
      });
      const gray = svgNode("feColorMatrix", {
        in: "grain-noise",
        type: "saturate",
        values: 0,
        result: "grain-gray",
      });
      const opacity = svgNode("feComponentTransfer", { in: "grain-gray", result: "grain-faded" });
      const alpha = svgNode("feFuncA", { type: "linear", slope: 0, intercept: 0 });
      opacity.appendChild(alpha);
      const shaped = svgNode("feComposite", {
        in: "grain-faded",
        in2: tail,
        operator: "in",
        result: "grain-masked",
      });
      const mixed = svgNode("feComposite", {
        in: "grain-masked",
        in2: tail,
        operator: "arithmetic",
        k1: 0,
        k2: 1,
        k3: 1,
        k4: 0,
      });
      graph.push(turbulence, gray, opacity, shaped, mixed);
      grain = { turbulence, alpha, seed: 1 };
    }

    // The last primitive in document order is the filter result, so no feMerge is needed.
    filter.replaceChildren(mapNode, ...graph);
  }

  // ---- displacement map -----------------------------------------------------------------------------------

  function createScratch(cols, rows) {
    const canvas = document.createElement("canvas");
    canvas.width = cols;
    canvas.height = rows;
    const ctx = canvas.getContext("2d");
    if (!ctx) return null;
    return { canvas, ctx, image: ctx.createImageData(cols, rows) };
  }

  function updateGrain(maxAbs) {
    if (!grain) return;
    const pushPx = maxAbs * state.push * state.boxW;
    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260924: same gate as the shader's smoothstep(1.5, 18.0, pushPx), so
    // grain only appears while the surface is actually moving
    const gate = smoothstep(1.5, 18, pushPx);
    grain.alpha.setAttribute("slope", String(clamp(config.grain, 0, 1) * GRAIN_SCALE * gate));
    if (clamp(config.grainSpeed, 0, 4) > 0) {
      grain.seed = (grain.seed % MAX_SEED) + 1;
      grain.turbulence.setAttribute("seed", String(grain.seed));
    }
  }

  function drawMap(maxAbs) {
    if (!scratch) return;
    const { ctx, image } = scratch;
    const data = image.data;
    const cells = state.cols * state.rows;
    for (let cell = 0; cell < cells; cell += 1) {
      const offset = cell * 4;
      // Negated against the shader: the GLSL samples `cuv - push`, while feDisplacementMap gathers from
      // `p + scale * (channel / 255 - 0.5)`. Uint8ClampedArray clamps to 0..255, the saturation we want.
      data[offset] = MAP_MID - field[cell * 2] * state.gain;
      data[offset + 1] = MAP_MID - field[cell * 2 + 1] * state.gain;
      data[offset + 2] = 0;
      data[offset + 3] = 255;
    }
    ctx.putImageData(image, 0, 0);
    const url = scratch.canvas.toDataURL("image/png");
    if (!url) return;
    // Both spellings: `href` for modern engines, `xlink:href` for older WebKit.
    mapNode.setAttribute("href", url);
    mapNode.setAttributeNS(XLINK_NS, "xlink:href", url);
    if (!mapReadyAt) mapReadyAt = performance.now();
    updateGrain(maxAbs);
  }

  // ---- geometry -------------------------------------------------------------------------------------------

  function seedField() {
    if (seeded) return;
    seeded = true;
    const amplitude = clamp(config.scramble, 0, 3) * SCRAMBLE_UNITS;
    if (reducedMotion || amplitude <= 0) return;
    for (let i = 0; i < field.length; i += 1) {
      field[i] = (Math.random() * 2 - 1) * amplitude;
    }
  }

  function syncGeometry() {
    const boxW = Math.max(1, Math.round(content.clientWidth));
    const boxH = Math.max(1, Math.round(content.clientHeight));
    const cols = Math.round(clamp(config.grid, 4, 100));
    const aspect = clamp(config.cellAspect, 0.25, 4);
    const rows = Math.round(clamp((cols * boxH * aspect) / boxW, 2, 200));
    const push = SHADER_PUSH * clamp(config.shift, 0, 4);
    const gain = state.gain;
    const scale = (255 * push) / gain;

    state.boxW = boxW;
    state.boxH = boxH;
    state.push = push;
    state.scale = scale;
    state.rowScale = (boxH * cols) / (boxW * rows);

    if (cols !== state.cols || rows !== state.rows) {
      state.cols = cols;
      state.rows = rows;
      field = new Float32Array(cols * rows * 2);
      scratch = createScratch(cols, rows);
      seedField();
      // Pre-warm the feImage resource before the filter is ever applied: an feImage whose PNG has not decoded yet
      // would make feDisplacementMap produce nothing, i.e. hide the whole card for a frame.
      drawMap(0);
    }

    for (const target of scaleTargets) {
      target.node.setAttribute("scale", String(state.scale * target.factor));
    }
    if (grain) {
      // baseFrequency lives in the same bounding-box units as the rest of the primitive, so it follows the width.
      grain.turbulence.setAttribute(
        "baseFrequency",
        String(boxW / (clamp(config.grainSize, 0.5, 4) * GRAIN_CELL)),
      );
    }
  }

  // ---- simulation -----------------------------------------------------------------------------------------

  function stepSimulation(delta) {
    const relaxation = clamp(config.relaxation, 0.5, 0.995);
    const decay = Math.pow(relaxation, delta * 60);
    let maxAbs = 0;
    for (let i = 0; i < field.length; i += 1) {
      const value = field[i] * decay;
      field[i] = value;
      const abs = Math.abs(value);
      if (abs > maxAbs) maxAbs = abs;
    }

    const injecting = tracking && (mouse.vX !== 0 || mouse.vY !== 0);
    if (injecting) {
      const { cols, rows, rowScale } = state;
      const gridX = mouse.x * cols;
      const gridY = mouse.y * rows;
      const maxDist = cols * clamp(config.radius, 0.02, 1);
      const maxSq = maxDist * maxDist;
      const gain = clamp(config.strength, 0, 1) * 100 * mouse.gate;
      for (let j = 0; j < rows; j += 1) {
        const dy = (gridY - j) * rowScale;
        for (let i = 0; i < cols; i += 1) {
          const dx = gridX - i;
          const distSq = dx * dx + dy * dy;
          if (distSq < maxSq) {
            const power = Math.min(maxDist / Math.sqrt(distSq), 10);
            const idx = 2 * (i + cols * j);
            field[idx] += gain * mouse.vX * power;
            field[idx + 1] += gain * mouse.vY * power;
          }
        }
      }
    }

    const velocityDecay = Math.pow(0.9, delta * 60);
    mouse.vX *= velocityDecay;
    mouse.vY *= velocityDecay;
    if (Math.abs(mouse.vX) < 0.0001) mouse.vX = 0;
    if (Math.abs(mouse.vY) < 0.0001) mouse.vY = 0;

    const alive = injecting || mouse.vX !== 0 || mouse.vY !== 0 || maxAbs > IDLE_EPSILON;
    if (!alive && maxAbs > 0) field.fill(0);
    return { alive, maxAbs };
  }

  // ---- filter activation ----------------------------------------------------------------------------------

  function activate() {
    if (active || destroyed) return;
    active = true;
    content.style.filter = `url(#${filterId})`;
  }

  function deactivate() {
    if (!active) return;
    active = false;
    content.style.filter = "";
  }

  // ---- frame loop -----------------------------------------------------------------------------------------

  function frame(now) {
    if (destroyed) return;
    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260924: pause while the tab is hidden, matching the page-hidden
    // convention in theme.css and displacement-engine.js - and drop the filter, so a frozen field can never leave
    // the card warped for as long as the tab stays in the background
    if (document.hidden || !visible) {
      running = false;
      field.fill(0);
      onPointerLeave();
      deactivate();
      return;
    }
    const delta = Math.min((now - lastTime) / 1000, 1 / 30);
    lastTime = now;

    const { alive, maxAbs } = reducedMotion ? { alive: false, maxAbs: 0 } : stepSimulation(delta);
    lastMaxAbs = maxAbs;
    if (!alive) {
      running = false;
      deactivate();
      return;
    }
    // Refresh the map first and only then apply the filter, so the surface is never distorted by a stale - or by
    // an empty - displacement field.
    if (now - lastMapAt >= MAP_INTERVAL) {
      lastMapAt = now;
      drawMap(maxAbs);
    }
    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260924: an feImage whose PNG has not decoded yet paints nothing at
    // all, which would blank the whole card - so the very first map gets one extra frame of slack before the
    // filter is applied. Later swaps keep the previous image until the new one is ready.
    if (!mapReadyAt || now - mapReadyAt < 40) {
      raf = requestAnimationFrame(frame);
      return;
    }
    activate();
    raf = requestAnimationFrame(frame);
  }

  function start() {
    if (destroyed || running || !visible || reducedMotion) return;
    running = true;
    lastTime = performance.now();
    raf = requestAnimationFrame(frame);
  }

  // ---- pointer --------------------------------------------------------------------------------------------

  const pointerHost = content.parentElement ?? content;
  const rectCache = createRectCache(content);

  function onPointerMove(event) {
    if (reducedMotion) return;
    const box = rectCache.current;
    if (box.width < 1 || box.height < 1) return;
    const x = (event.clientX - box.left) / box.width;
    const y = (event.clientY - box.top) / box.height;
    const now = performance.now();
    if (!tracking) {
      tracking = true;
      mouse.prevX = x;
      mouse.prevY = y;
      mouse.speed = 0;
      mouse.gate = 0;
      mouse.lastT = now;
    }
    mouse.vX = x - mouse.prevX;
    mouse.vY = y - mouse.prevY;
    const dt = Math.max((now - mouse.lastT) / 1000, 0.001);
    mouse.lastT = now;
    const distPx = Math.hypot(mouse.vX * box.width, mouse.vY * box.height);
    mouse.speed += (distPx / dt - mouse.speed) * Math.min(dt * 25, 1);
    const threshold = Math.max(config.threshold, 0);
    if (threshold <= 0) {
      mouse.gate = 1;
    } else {
      const ramp = clamp((mouse.speed - threshold) / threshold, 0, 1);
      mouse.gate = ramp * ramp * (3 - 2 * ramp);
    }
    mouse.prevX = x;
    mouse.prevY = y;
    mouse.x = x;
    mouse.y = y;
    start();
  }

  function onPointerLeave() {
    tracking = false;
    mouse.vX = 0;
    mouse.vY = 0;
    mouse.speed = 0;
    mouse.gate = 0;
  }

  function onMotionChange() {
    reducedMotion = motionQuery.matches;
    if (reducedMotion) {
      field.fill(0);
      onPointerLeave();
      deactivate();
    } else {
      start();
    }
  }

  function onVisibilityChange() {
    if (!document.hidden) start();
  }

  const observer = new ResizeObserver(() => {
    syncGeometry();
    if (active) drawMap(lastMaxAbs);
    start();
  });
  observer.observe(content);

  const intersection = new IntersectionObserver((entries) => {
    visible = entries[entries.length - 1]?.isIntersecting ?? true;
    if (visible) start();
  });
  intersection.observe(content);

  pointerHost.addEventListener("pointermove", onPointerMove, { passive: true });
  pointerHost.addEventListener("pointerleave", onPointerLeave, { passive: true });
  pointerHost.addEventListener("pointercancel", onPointerLeave, { passive: true });
  motionQuery.addEventListener("change", onMotionChange);
  document.addEventListener("visibilitychange", onVisibilityChange);

  rebuildGraph();
  syncGeometry();
  // A scrambled field needs frames even before the first pointer move; with scramble 0 this single frame finds the
  // surface at rest and stops, leaving the card as plain HTML until someone sweeps across it.
  start();

  return {
    filterId,
    setOptions(next) {
      let structural = false;
      for (const key of ["grid", "cellAspect", "aberration", "grain", "grainSize"]) {
        if (next[key] !== undefined && next[key] !== config[key]) structural = true;
      }
      Object.assign(config, next);
      if (structural) rebuildGraph();
      syncGeometry();
      start();
    },
    resize() {
      syncGeometry();
      if (active) drawMap(lastMaxAbs);
      start();
    },
    destroy() {
      destroyed = true;
      cancelAnimationFrame(raf);
      rectCache.destroy();
      observer.disconnect();
      intersection.disconnect();
      motionQuery.removeEventListener("change", onMotionChange);
      document.removeEventListener("visibilitychange", onVisibilityChange);
      pointerHost.removeEventListener("pointermove", onPointerMove);
      pointerHost.removeEventListener("pointerleave", onPointerLeave);
      pointerHost.removeEventListener("pointercancel", onPointerLeave);
      deactivate();
      host?.remove();
      host = null;
      filter = null;
      mapNode = null;
      graph = [];
      scaleTargets = [];
      grain = null;
      scratch = null;
      field = new Float32Array(0);
    },
  };
}
