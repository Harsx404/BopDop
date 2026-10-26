// BopDop site: scroll reveals, hero parallax, the lyrics clip, the seek bar and pet player
// (ported from the app's SeekBar.kt and Mascot.kt), and links to the newest release's APKs.
(() => {
  "use strict";
  const reduce = matchMedia("(prefers-reduced-motion: reduce)").matches;
  const REPO = "Harsx404/BopDop";

  // --- Reveal on scroll -------------------------------------------------------------------
  const io = new IntersectionObserver((entries) => {
    for (const e of entries) if (e.isIntersecting) { e.target.classList.add("in"); io.unobserve(e.target); }
  }, { threshold: 0.15 });
  document.querySelectorAll(".reveal").forEach((el) => io.observe(el));

  // --- Hero phones follow the pointer a little ---------------------------------------------
  const phones = document.querySelector(".hero-phones");
  if (phones && !reduce && matchMedia("(pointer: fine)").matches) {
    let raf = 0, tx = 0, ty = 0;
    addEventListener("pointermove", (e) => {
      tx = (e.clientX / innerWidth - 0.5) * 18;
      ty = (e.clientY / innerHeight - 0.5) * 14;
      if (!raf) raf = requestAnimationFrame(() => {
        raf = 0;
        phones.style.setProperty("--px", tx.toFixed(1) + "px");
        phones.style.setProperty("--py", ty.toFixed(1) + "px");
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
    const REST = "rgba(255,255,255,0.32)";

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
      ctx.fillStyle = "#fff";
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
          ctx.strokeStyle = "#fff"; ctx.beginPath(); ctx.moveTo(pad, cy);
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
        ctx.strokeStyle = "#fff"; ctx.beginPath(); ctx.moveTo(pad, cy); ctx.lineTo(x, cy); ctx.stroke();
        const r = dragging ? 9 : 7;
        if (dragging) { ctx.fillStyle = "rgba(255,255,255,0.25)"; ctx.beginPath(); ctx.arc(x, cy, r + 7, 0, 7); ctx.fill(); }
        ctx.fillStyle = "#fff"; ctx.beginPath(); ctx.arc(x, cy, r, 0, 7); ctx.fill();
      } else if (style === "bars") {
        const bw = 3, gap = 2.2, maxH = 24, n = Math.max(1, Math.floor(span / (bw + gap)));
        for (let i = 0; i < n; i++) {
          const bx = pad + i * (bw + gap) + bw / 2, played = bx <= x;
          const breathe = played ? 1 - 0.22 * amp + 0.22 * amp * Math.sin(phase * 1.4 + i * 0.55) : 1;
          const h = Math.max(bw, maxH * barShape(7, i) * breathe);
          ctx.fillStyle = played ? "#fff" : REST;
          ctx.beginPath(); ctx.roundRect(bx - bw / 2, cy - h / 2, bw, h, bw / 2); ctx.fill();
        }
        pill(x, cy);
      } else {
        const step = 9, n = Math.floor(span / step);
        for (let i = 0; i <= n; i++) {
          const dx = pad + i * step;
          ctx.fillStyle = dx <= x ? "#fff" : REST;
          ctx.beginPath(); ctx.arc(dx, cy, dx <= x ? 2.6 : 1.8, 0, 7); ctx.fill();
        }
        const pulse = 0.5 + 0.5 * Math.sin(phase * 1.2) * amp, r = 7;
        ctx.fillStyle = `rgba(255,255,255,${0.18 + 0.14 * pulse})`;
        ctx.beginPath(); ctx.arc(x, cy, r + 4 + 3 * pulse, 0, 7); ctx.fill();
        ctx.fillStyle = "#fff"; ctx.beginPath(); ctx.arc(x, cy, r, 0, 7); ctx.fill();
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

    fetch("assets/pets/pets.json").then((r) => r.json()).then((list) => {
      pets = list;
      const picker = document.getElementById("petPicker");
      list.forEach((p, i) => {
        const b = document.createElement("button");
        b.type = "button";
        b.className = (i === 0 ? "on " : "") + (p.pixel ? "pixel" : "");
        const fw = (p.walk.w * 27) / p.walk.h;
        b.innerHTML = `<span class="thumb"><i style="width:${fw}px;background-image:url(${p.walk.src});background-size:${fw * p.walk.n}px 27px"></i></span>${p.name}`;
        b.addEventListener("click", () => {
          pet = p; petT = 0; sleepT = 0;
          petEl.classList.toggle("pixel", p.pixel);
          picker.querySelectorAll("button").forEach((o) => o.classList.toggle("on", o === b));
        });
        picker.appendChild(b);
      });
      pet = list[0];
      petEl.classList.toggle("pixel", pet.pixel);
    });

    size();
    if (reduce) setPlaying(true);
    requestAnimationFrame(loop);
  }

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
      meta.textContent = `Version ${v}, ${fmtSize(main.size)}. Released ${when}. Android 8.0 and newer.`;
    }
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
