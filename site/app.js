// BopDop site: the first-visit loader, scroll reveals, the pointer ring, the hero phone's tilt,
// the lyrics clip, the pet deck and the seek bar it drives (ported from the app's SeekBar.kt and
// Mascot.kt), the pinned screens story, the odometer, the dock, and the newest release's APKs.
(() => {
  "use strict";
  const reduce = matchMedia("(prefers-reduced-motion: reduce)").matches;
  const REPO = "Harsx404/BopDop";

  // --- Reveal on scroll -------------------------------------------------------------------
  const io = new IntersectionObserver((entries) => {
    for (const e of entries) if (e.isIntersecting) { e.target.classList.add("in"); io.unobserve(e.target); }
  }, { threshold: 0.15 });
  // --- Loader: count to 100, then lift; reveals start once the page is showing ------------
  const root = document.documentElement;
  const reveals = () => document.querySelectorAll(".reveal").forEach((el) => io.observe(el));
  if (root.classList.contains("loading")) {
    const n = document.getElementById("loaderN"), bar = document.getElementById("loaderBar");
    const t0 = performance.now(), DUR = 1300;
    const count = (now) => {
      const t = Math.min(1, (now - t0) / DUR), v = Math.round(100 * (1 - Math.pow(1 - t, 3)));
      n.textContent = String(v).padStart(2, "0");
      bar.style.width = v + "%";
      if (t < 1) return requestAnimationFrame(count);
      root.classList.add("loaded");
      setTimeout(reveals, 250);
      setTimeout(() => root.classList.remove("loading", "loaded"), 1100);
    };
    requestAnimationFrame(count);
  } else reveals();

  const fine = matchMedia("(pointer: fine)").matches;

  // --- Nav picks up a backdrop once the page moves -----------------------------------------
  const nav = document.querySelector(".nav");
  const onScroll = () => nav.classList.toggle("scrolled", scrollY > 20);
  addEventListener("scroll", onScroll, { passive: true }); onScroll();

  // --- A ring that trails the pointer and swells over things you can press -----------------
  const ring = document.querySelector(".cursor");
  if (ring && fine && !reduce) {
    let x = -100, y = -100, rx = x, ry = y;
    addEventListener("pointermove", (e) => {
      // Show it from the first real move, starting under the pointer instead of sliding in.
      if (!document.body.classList.contains("has-cursor")) { document.body.classList.add("has-cursor"); rx = e.clientX; ry = e.clientY; }
      x = e.clientX; y = e.clientY;
      ring.classList.toggle("hot", !!e.target.closest("a, button, summary, .card, .index-list li, canvas"));
    }, { passive: true });
    const follow = () => {
      rx += (x - rx) * 0.2; ry += (y - ry) * 0.2;
      ring.style.transform = `translate3d(${rx}px, ${ry}px, 0)`;
      requestAnimationFrame(follow);
    };
    follow();
  }

  // --- The hero phone leans toward the pointer ---------------------------------------------
  const heroPhone = document.querySelector(".hero-phone");
  if (heroPhone && fine && !reduce) {
    let raf = 0, tx = 0, ty = 0;
    addEventListener("pointermove", (e) => {
      tx = (e.clientX / innerWidth - 0.5) * 16;
      ty = (e.clientY / innerHeight - 0.5) * -10;
      if (!raf) raf = requestAnimationFrame(() => {
        raf = 0;
        heroPhone.style.transform = `perspective(1200px) rotateY(${tx.toFixed(2)}deg) rotateX(${ty.toFixed(2)}deg)`;
      });
    }, { passive: true });
  }

  // --- Lyrics clip: a still frame for reduced motion, and no playing while off screen -------
  const clip = document.querySelector(".lyrics-shot video");
  if (clip) {
    if (reduce) { clip.removeAttribute("autoplay"); clip.pause(); }
    else new IntersectionObserver(([e]) => { e.isIntersecting ? clip.play().catch(() => {}) : clip.pause(); }).observe(clip);
  }

  // --- Seek bar + pets ------------------------------------------------------------------------
  const canvas = document.getElementById("seek");
  if (canvas) {
    const ctx = canvas.getContext("2d");
    const petEl = document.getElementById("pet");
    const playBtn = document.getElementById("playBtn");
    const tNow = document.getElementById("tNow");
    const DUR = 204; // seconds
    let style = "wave", playing = true, pos = 38, phase = 0, amp = 1, last = performance.now();
    let pets = [], pet = null, petT = 0, sleepT = 0, dragging = false;
    // Strokes follow the page's text colour, so the bar reads on the dark stage and on pearl.
    let INK = "#fff", REST = "rgba(255,255,255,0.28)";
    const tint = () => {
      const m = getComputedStyle(document.body).color.match(/\d+/g) || [255, 255, 255];
      INK = `rgb(${m[0]},${m[1]},${m[2]})`; REST = `rgba(${m[0]},${m[1]},${m[2]},0.28)`;
    };
    tint();
    matchMedia("(prefers-color-scheme: dark)").addEventListener("change", tint);

    const smooth = (t) => t * t * (3 - 2 * t);
    const barShape = (seed, i) => {
      const n = (k) => {
        let h = (seed * 31 + k * 0x9e3779b1) | 0;
        h = Math.imul(h ^ (h >>> 15), 0x2c1b3c6d);
        h = Math.imul(h ^ (h >>> 12), 0x297a2d39);
        h ^= h >>> 15;
        return (h & 0xffff) / 65535;
      };
      return 0.22 + 0.78 * (n(i - 1) + 2 * n(i) + n(i + 1)) / 4;
    };

    const size = () => {
      const r = canvas.getBoundingClientRect(), dpr = Math.min(devicePixelRatio || 1, 2);
      canvas.width = Math.round(r.width * dpr); canvas.height = Math.round(64 * dpr);
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    };
    // Re-measure whenever the card's width changes (first layout, reveal, resizing).
    new ResizeObserver(size).observe(canvas);

    const pill = (x, cy) => {
      const th = 22, tw = 6;
      ctx.fillStyle = INK;
      ctx.beginPath(); ctx.roundRect(x - tw / 2, cy - th / 2, tw, th, tw / 2); ctx.fill();
    };
    const draw = () => {
      const w = canvas.clientWidth, pad = 10, cy = 32, span = w - 2 * pad;
      const x = pad + span * (pos / DUR);
      ctx.clearRect(0, 0, w, 64);
      ctx.lineCap = "round";
      if (style === "wave") {
        const gap = 7, lambda = 30, wave = 3.4 * amp;
        ctx.strokeStyle = REST; ctx.lineWidth = 4;
        if (x + gap < pad + span) { ctx.beginPath(); ctx.moveTo(x + gap, cy); ctx.lineTo(pad + span, cy); ctx.stroke(); }
        const end = Math.max(pad, x - gap);
        if (end > pad) {
          ctx.strokeStyle = INK; ctx.beginPath(); ctx.moveTo(pad, cy);
          const room = Math.min(1, Math.max(0, (end - pad) / (lambda * 1.6)));
          for (let px = pad; px <= end; px += 1.5) {
            const inT = Math.min(1, (px - pad) / (lambda * 0.5)), outT = Math.min(1, (end - px) / (lambda * 0.5));
            const taper = room * smooth(Math.max(0, inT)) * smooth(Math.max(0, outT));
            ctx.lineTo(px, cy + wave * taper * Math.sin(((px - pad) / lambda) * 2 * Math.PI - phase));
          }
          ctx.stroke();
        }
        pill(x, cy);
      } else if (style === "line") {
        ctx.lineWidth = 5;
        ctx.strokeStyle = REST; ctx.beginPath(); ctx.moveTo(pad, cy); ctx.lineTo(pad + span, cy); ctx.stroke();
        ctx.strokeStyle = INK; ctx.beginPath(); ctx.moveTo(pad, cy); ctx.lineTo(x, cy); ctx.stroke();
        const r = dragging ? 9 : 7;
        if (dragging) { ctx.fillStyle = REST; ctx.beginPath(); ctx.arc(x, cy, r + 7, 0, 7); ctx.fill(); }
        ctx.fillStyle = INK; ctx.beginPath(); ctx.arc(x, cy, r, 0, 7); ctx.fill();
      } else if (style === "bars") {
        const bw = 3, gap = 2.2, maxH = 24, n = Math.max(1, Math.floor(span / (bw + gap)));
        for (let i = 0; i < n; i++) {
          const bx = pad + i * (bw + gap) + bw / 2, played = bx <= x;
          const breathe = played ? 1 - 0.22 * amp + 0.22 * amp * Math.sin(phase * 1.4 + i * 0.55) : 1;
          const h = Math.max(bw, maxH * barShape(7, i) * breathe);
          ctx.fillStyle = played ? INK : REST;
          ctx.beginPath(); ctx.roundRect(bx - bw / 2, cy - h / 2, bw, h, bw / 2); ctx.fill();
        }
        pill(x, cy);
      } else {
        const step = 9, n = Math.floor(span / step);
        for (let i = 0; i <= n; i++) {
          const dx = pad + i * step;
          ctx.fillStyle = dx <= x ? INK : REST;
          ctx.beginPath(); ctx.arc(dx, cy, dx <= x ? 2.6 : 1.8, 0, 7); ctx.fill();
        }
        const pulse = 0.5 + 0.5 * Math.sin(phase * 1.2) * amp, r = 7;
        ctx.globalAlpha = 0.5 + 0.5 * pulse; ctx.fillStyle = REST;
        ctx.beginPath(); ctx.arc(x, cy, r + 4 + 3 * pulse, 0, 7); ctx.fill();
        ctx.globalAlpha = 1; ctx.fillStyle = INK; ctx.beginPath(); ctx.arc(x, cy, r, 0, 7); ctx.fill();
      }
      return x;
    };

    // The pet: walking frames while playing; when paused, its settle frames once, then a loop.
    const PET_H = 80;
    const placePet = (x, dt) => {
      if (!pet) return;
      const set = playing ? pet.walk : pet.sleep;
      const fw = (set.w * PET_H) / set.h;
      let frame;
      if (playing) { petT += dt; frame = Math.floor(petT / pet.step) % set.n; }
      else {
        sleepT += dt;
        const f = Math.floor(sleepT / (pet.step * 1.6));
        const s = pet.settle;
        frame = s > 0 ? (f < s ? f : s + ((f - s) % Math.max(1, set.n - s))) : f % set.n;
      }
      if (petEl.dataset.src !== set.src) {
        petEl.dataset.src = set.src;
        petEl.style.backgroundImage = `url(${set.src})`;
        petEl.style.width = fw + "px";
        petEl.style.backgroundSize = `${fw * set.n}px ${PET_H}px`;
      }
      petEl.style.backgroundPosition = `${-frame * fw}px 0`;
      // Feet just above the bar, centred on the playhead.
      petEl.style.transform = `translate3d(${x - fw / 2}px, ${76 + 32 - 16 - PET_H}px, 0)`;
    };

    const loop = (now) => {
      const dt = Math.min(0.05, (now - last) / 1000); last = now;
      if (playing && !dragging) pos = (pos + dt) % DUR;
      phase += dt * (playing ? 5 : 0);
      amp += ((playing ? 1 : 0) - amp) * Math.min(1, dt * 6);
      const x = draw();
      placePet(x, reduce ? 0 : dt);
      const s = Math.floor(pos);
      tNow.textContent = `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`;
      requestAnimationFrame(loop);
    };

    const setPlaying = (p) => {
      playing = p; sleepT = 0; petT = 0;
      playBtn.innerHTML = p ? '<i class="ph-fill ph-pause"></i>' : '<i class="ph-fill ph-play"></i>';
      playBtn.setAttribute("aria-label", p ? "Pause" : "Play");
    };
    playBtn.addEventListener("click", () => setPlaying(!playing));

    document.querySelectorAll("#styleSeg button").forEach((b) => b.addEventListener("click", () => {
      style = b.dataset.style;
      document.querySelectorAll("#styleSeg button").forEach((o) => o.classList.toggle("on", o === b));
    }));

    // Scrub by dragging, like the app.
    const seekTo = (e) => {
      const r = canvas.getBoundingClientRect();
      pos = Math.min(1, Math.max(0, (e.clientX - r.left - 10) / (r.width - 20))) * DUR;
    };
    canvas.addEventListener("pointerdown", (e) => { dragging = true; canvas.setPointerCapture(e.pointerId); seekTo(e); });
    canvas.addEventListener("pointermove", (e) => { if (dragging) seekTo(e); });
    canvas.addEventListener("pointerup", () => { dragging = false; });

    // The deck: each pet as a card from its own little world. The card in front walks the bar.
    const WORLDS = {
      bunny: ["linear-gradient(165deg, #8cc27a, #2f6a3a 58%, #10261a)", "Soft", "Hops", "Naps"],
      girl1: ["linear-gradient(165deg, #f4b3cc, #b0507a 58%, #34122a)", "Pixel", "Sakura", "Naps"],
      girl2: ["linear-gradient(165deg, #b6a0f4, #5a3fb0 58%, #170f38)", "Pixel", "Dusk", "Naps"],
      girl3: ["linear-gradient(165deg, #7ed8d4, #1f7f8f 58%, #082833)", "Pixel", "Seaside", "Naps"],
      fighter: ["linear-gradient(165deg, #f4c66a, #b0662a 58%, #33170a)", "Pixel", "Brawler", "Rests"],
      samurai: ["linear-gradient(165deg, #ff8a62, #b3261e 58%, #330a0a)", "Pixel", "Blade", "Rests"],
      shinobi: ["linear-gradient(165deg, #7b8ae0, #26306e 58%, #090c22)", "Pixel", "Stealth", "Rests"],
    };
    const deck = document.getElementById("deck");
    const count = document.getElementById("deckCount");
    let cards = [], active = 0;
    const choose = (i) => {
      active = (i + cards.length) % cards.length;
      const spread = Math.min(250, innerWidth * 0.22);
      const n = cards.length, half = Math.floor(n / 2);
      cards.forEach((c, j) => {
        // The deck wraps, so there are always cards fanned out on both sides.
        const o = ((j - active + n + half) % n) - half, a = Math.abs(o);
        c.style.setProperty("--tx", `${o * spread}px`);
        c.style.setProperty("--ry", `${Math.max(-50, Math.min(50, -o * 24))}deg`);
        c.style.setProperty("--s", `${1 - Math.min(a, 3) * 0.1}`);
        c.style.zIndex = 10 - a;
        c.style.opacity = a > 2 ? 0 : 1;
        c.classList.toggle("on", o === 0);
        c.setAttribute("aria-selected", o === 0);
      });
      count.textContent = `${String(active + 1).padStart(2, "0")} / ${String(cards.length).padStart(2, "0")}`;
      pet = pets[active]; petT = 0; sleepT = 0;
      petEl.classList.toggle("pixel", pet.pixel);
    };

    // Swipe between cards; a short tap still picks the card under it.
    let downX = null, moved = false;
    deck.addEventListener("pointerdown", (e) => { downX = e.clientX; moved = false; });
    deck.addEventListener("pointermove", (e) => { if (downX !== null && Math.abs(e.clientX - downX) > 8) moved = true; });
    deck.addEventListener("pointerup", (e) => {
      if (downX !== null && Math.abs(e.clientX - downX) > 40) choose(active + (e.clientX < downX ? 1 : -1));
      downX = null;
    });

    fetch("assets/pets/pets.json").then((r) => r.json()).then((list) => {
      pets = list;
      cards = list.map((p, i) => {
        const [world, ...tags] = WORLDS[p.id] || WORLDS.bunny;
        const c = document.createElement("figure");
        c.className = "card";
        c.setAttribute("role", "option");
        c.setAttribute("aria-label", p.name);
        c.style.setProperty("--world", world);
        c.innerHTML = `
          <div class="card-top">
            <p class="mono card-id">Pet.${String(i + 1).padStart(2, "0")}</p>
            <p class="card-name">${p.name}</p>
            <p class="card-tags mono">${tags.map((t) => `<span>${t}</span>`).join("")}</p>
          </div>
          <div class="card-stage"><i class="card-sprite${p.pixel ? " pixel" : ""}" style="--ar:${p.walk.w}/${p.walk.h};--n:${p.walk.n};--dur:${(p.step * p.walk.n).toFixed(2)}s;background-image:url(${p.walk.src})"></i></div>
          <span class="card-ground"></span>
          <p class="card-foot mono"><span>BopDop</span><span>Seek bar</span></p>
          <span class="glare"></span>`;
        c.addEventListener("click", () => { if (!moved) choose(i); });
        deck.appendChild(c);
        return c;
      });
      choose(0);
    });

    document.getElementById("deckPrev").addEventListener("click", () => choose(active - 1));
    document.getElementById("deckNext").addEventListener("click", () => choose(active + 1));
    deck.addEventListener("keydown", (e) => {
      if (e.key === "ArrowLeft") { e.preventDefault(); choose(active - 1); }
      if (e.key === "ArrowRight") { e.preventDefault(); choose(active + 1); }
    });
    addEventListener("resize", () => cards.length && choose(active));

    // The front card tilts under the pointer, with a glare that follows it.
    const settle = (c) => { c.classList.remove("tilting"); c.style.setProperty("--rx", "0deg"); c.style.setProperty("--ty", "0deg"); };
    if (fine && !reduce) {
      deck.addEventListener("pointermove", (e) => {
        const c = cards[active];
        if (!c || downX !== null) return;
        const r = c.getBoundingClientRect();
        const px = (e.clientX - r.left) / r.width, py = (e.clientY - r.top) / r.height;
        if (px < 0 || px > 1 || py < 0 || py > 1) return settle(c);
        c.classList.add("tilting");
        c.style.setProperty("--rx", `${(0.5 - py) * 16}deg`);
        c.style.setProperty("--ty", `${(px - 0.5) * 20}deg`);
        c.style.setProperty("--gx", `${px * 100}%`);
        c.style.setProperty("--gy", `${py * 100}%`);
      });
      deck.addEventListener("pointerleave", () => cards[active] && settle(cards[active]));
    }

    size();
    if (reduce) setPlaying(true);
    requestAnimationFrame(loop);
  }

  // --- Screens: pinned on wide screens, the phone turns over to each screen as you scroll ------
  const rows = [...document.querySelectorAll("#indexList li")];
  const story = document.getElementById("story");
  const flip = document.getElementById("flip");
  const stepLabel = document.getElementById("indexStep");
  const wide = matchMedia("(min-width: 901px)");
  let step = 0, turns = 0;
  const faces = flip ? [flip.querySelector(".front img"), flip.querySelector(".back img")] : [];
  const pick = (i) => {
    if (i === step) return;
    step = i;
    rows.forEach((r, j) => r.classList.toggle("on", j === i));
    if (stepLabel) stepLabel.textContent = `${String(i + 1).padStart(2, "0")} / ${String(rows.length).padStart(2, "0")}`;
    if (!flip) return;
    // Put the new screen on the face that's turned away, then turn the phone over to it.
    turns++;
    faces[turns % 2].src = rows[i].dataset.shot;
    tilt();
  };
  let lean = 0;
  const tilt = () => { if (flip) flip.style.transform = `rotateY(${turns * 180 + lean}deg) rotateZ(-3deg)`; };
  const onStory = () => {
    if (!story || !wide.matches) return;
    const r = story.getBoundingClientRect();
    const span = r.height - innerHeight;
    const p = Math.min(0.9999, Math.max(0, -r.top / span));
    const pos = p * rows.length;
    pick(Math.floor(pos));
    // Between steps the phone leans a little with the scroll, so it feels held, not swapped.
    if (!reduce) { lean = (pos % 1 - 0.5) * -14; tilt(); }
  };
  addEventListener("scroll", onStory, { passive: true });
  wide.addEventListener("change", onStory);
  onStory(); tilt();
  rows.forEach((li, i) => {
    li.tabIndex = 0;
    const go = () => {
      if (story && wide.matches) {
        // Scroll to the middle of that step; the scroll handler does the rest.
        const top = story.getBoundingClientRect().top + scrollY;
        scrollTo({ top: top + ((i + 0.5) / rows.length) * (story.offsetHeight - innerHeight), behavior: reduce ? "auto" : "smooth" });
      } else pick(i);
    };
    li.addEventListener("click", go);
    li.addEventListener("keydown", (e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); go(); } });
  });

  // --- Odometer: each digit rolls to its value when the stats come into view ----------------
  document.querySelectorAll(".odo").forEach((el) => {
    const digits = el.dataset.to.split("");
    el.textContent = "";
    el.setAttribute("aria-label", el.dataset.to);
    const cols = digits.map((d, k) => {
      const col = document.createElement("i");
      col.setAttribute("aria-hidden", "true");
      // Two runs of 0-9 so a digit can spin past itself before landing.
      col.innerHTML = Array.from({ length: 20 }, (_, n) => `<span>${n % 10}</span>`).join("");
      col.style.transitionDelay = `${k * 0.12}s`;
      el.appendChild(col);
      return [col, +d];
    });
    const roll = () => cols.forEach(([col, d]) => (col.style.transform = `translateY(${-(10 + d)}em)`));
    if (reduce) return roll();
    new IntersectionObserver(([e], o) => { if (e.isIntersecting) { roll(); o.disconnect(); } }, { threshold: 0.6 }).observe(el);
  });

  // --- Dock: light up the link for the section on screen -----------------------------------
  const dockLinks = [...document.querySelectorAll(".dock-links a")];
  const spy = new IntersectionObserver((entries) => {
    for (const e of entries) if (e.isIntersecting) {
      dockLinks.forEach((a) => a.classList.toggle("on", a.getAttribute("href") === `#${e.target.id}`));
    }
  }, { rootMargin: "-45% 0px -50% 0px" });
  // Every section is watched, so a part with no link of its own (the spec sheet) clears the light.
  document.querySelectorAll("main > section").forEach((s) => spy.observe(s));

  // --- Download links: the newest release's APKs ---------------------------------------------
  const fmtSize = (b) => (b / 1048576).toFixed(1) + " MB";
  const KEY = "bopdop-release";
  const apply = (rel) => {
    const assets = rel.assets || [];
    const arm = assets.find((a) => /arm64-v8a\.apk$/.test(a.name));
    const uni = assets.find((a) => /\.apk$/.test(a.name) && !/(arm64-v8a|armeabi-v7a|x86_64|x86)\.apk$/.test(a.name));
    const main = arm || uni;
    if (main) document.querySelectorAll(".js-apk").forEach((a) => (a.href = main.browser_download_url));
    if (uni) document.querySelectorAll(".js-universal").forEach((a) => (a.href = uni.browser_download_url));
    const meta = document.getElementById("relMeta");
    if (meta && main) {
      const v = rel.tag_name.replace(/^v/, "");
      const when = new Date(rel.published_at).toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" });
      meta.textContent = `Version ${v} / ${fmtSize(main.size)} / ${when} / Android 8.0+`;
    }
    const hero = document.getElementById("heroMeta");
    if (hero && main) hero.textContent = `v${rel.tag_name.replace(/^v/, "")} / ${fmtSize(main.size)} / Android 8.0+`;
  };
  try {
    const cached = JSON.parse(sessionStorage.getItem(KEY) || "null");
    if (cached) apply(cached);
  } catch (_) { /* storage blocked: fetch below still works */ }
  fetch(`https://api.github.com/repos/${REPO}/releases/latest`, { headers: { Accept: "application/vnd.github+json" } })
    .then((r) => (r.ok ? r.json() : Promise.reject(r.status)))
    .then((rel) => {
      apply(rel);
      try { sessionStorage.setItem(KEY, JSON.stringify({ tag_name: rel.tag_name, published_at: rel.published_at, assets: rel.assets.map(({ name, size, browser_download_url }) => ({ name, size, browser_download_url })) })); } catch (_) {}
    })
    .catch(() => { /* links keep pointing at the latest release page */ });
})();
