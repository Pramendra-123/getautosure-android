// Shared GetAutoSure print artwork. All units are millimetres.
window.ART = (function () {
  const ORANGE = "#FF6B2C", INK = "#14171C", WHITE = "#FFFFFF", GREY = "#B9BDC4";

  // Brand mark (gauge arc + check + wheels), drawn into a box of size s at (x, y).
  function mark(x, y, s, wheel = WHITE, stroke = ORANGE) {
    const k = s / 64;
    return `<g transform="translate(${x} ${y}) scale(${k})">
      <path d="M10 32 A22 22 0 0 1 54 32" fill="none" stroke="${stroke}" stroke-width="5" stroke-linecap="round"/>
      <path d="M22 28 L29 36 L44 16" fill="none" stroke="${stroke}" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/>
      <circle cx="10" cy="46" r="8" fill="${wheel}"/><circle cx="54" cy="46" r="8" fill="${wheel}"/></g>`;
  }

  // App icon: orange rounded square with the dark mark.
  function icon(x, y, s) {
    return `<rect x="${x}" y="${y}" width="${s}" height="${s}" rx="${s * 0.24}" fill="${ORANGE}"/>` +
      mark(x + s * 0.06, y + s * 0.02, s * 0.88, INK, INK);
  }

  function wordmark(x, y, size, anchor = "middle", light = WHITE) {
    return `<text x="${x}" y="${y}" text-anchor="${anchor}" font-family="Sora" font-weight="700" font-size="${size}" letter-spacing="-0.02em"><tspan fill="${light}">GetAuto</tspan><tspan fill="${ORANGE}">Sure</tspan></text>`;
  }

  function qr(url, x, y, size, fill = INK) {
    const q = qrcode(0, "M"); q.addData(url); q.make();
    const n = q.getModuleCount(), s = size / n; let d = "";
    for (let r = 0; r < n; r++) for (let c = 0; c < n; c++) if (q.isDark(r, c))
      d += `M${(x + c * s).toFixed(3)} ${(y + r * s).toFixed(3)}h${s.toFixed(3)}v${s.toFixed(3)}h-${s.toFixed(3)}z`;
    return `<path d="${d}" fill="${fill}"/>`;
  }

  // Dongle front label, 42 x 50.6 mm (same proportions as the vendor's current label).
  // LED window and the three LED circles sit exactly where the vendor's are, so the light holes line up.
  function label() {
    const W = 42, H = 50.6;
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${W} ${H}" width="${W}mm" height="${H}mm">
      <rect width="${W}" height="${H}" rx="1" fill="#111316"/>
      ${wordmark(W / 2, 8.2, 5.6)}
      <rect x="2.4" y="11.2" width="37.2" height="11.4" rx="1.6" fill="#0A0B0D" stroke="#D5D8DD" stroke-width="0.5"/>
      <g fill="#F2F3F5"><circle cx="12.35" cy="15.6" r="1.6"/><circle cx="20.96" cy="15.6" r="1.6"/><circle cx="29.65" cy="15.6" r="1.6"/></g>
      <g font-family="IBM Plex Sans" font-size="2.2" fill="#D5D8DD" text-anchor="middle">
        <text x="12.35" y="20.6">Power</text><text x="20.96" y="20.6">PC</text><text x="29.65" y="20.6">OBD</text></g>
      ${icon(16.5, 26, 9)}
      <text x="21" y="41" text-anchor="middle" font-family="Sora" font-weight="700" font-size="3.1" fill="#F1F2F4">OBD-II Car Scanner</text>
      <text x="21" y="45.3" text-anchor="middle" font-family="IBM Plex Sans" font-size="2.15" fill="${GREY}">Bluetooth · getautosure.com</text>
    </svg>`;
  }

  const band = (w, y) => `<rect x="-3" y="${y}" width="${w + 6}" height="3" fill="${ORANGE}"/>`;
  const sans = (x, y, size, fill, text, anchor = "start", extra = "") =>
    `<text x="${x}" y="${y}" font-family="IBM Plex Sans" font-size="${size}" fill="${fill}" text-anchor="${anchor}" ${extra}>${text}</text>`;

  // Box panels for the vendor's 100 x 63 x 25.5 mm upright tuck-end carton.
  // Each returns SVG content in the panel's own coordinates (0,0 = top-left of the panel).
  const panels = {
    front() { // 63 x 100
      return mark(19, 14, 25) +
        wordmark(31.5, 53, 8.2) +
        sans(31.5, 60, 2.9, GREY, "OBD-II CAR SCANNER", "middle", 'letter-spacing="0.6"') +
        `<text x="31.5" y="82" text-anchor="middle" font-family="Sora" font-weight="700" font-size="4.2" fill="${WHITE}">Don't guess. Be sure.</text>` +
        band(63, 91);
    },
    top() { // 63 x 25.5, read from the front
      return wordmark(31.5, 15, 6.2);
    },
    sideLeft() { // 25.5 x 100, text runs bottom to top
      return `<g transform="translate(12.75 50) rotate(-90)">
        <text x="0" y="-1" text-anchor="middle" font-family="Sora" font-weight="700" font-size="4.6" fill="${WHITE}">Don't guess. Be sure.</text>
        ${sans(0, 5.2, 2.6, ORANGE, "getautosure.com", "middle", 'font-family="IBM Plex Mono"')}</g>` + band(25.5, 91);
    },
    sideRight() { // 25.5 x 100
      return `<g transform="translate(12.75 46) rotate(-90)">
        ${sans(0, -3.4, 2.6, WHITE, "Works with Android phones", "middle", 'font-weight="600"')}
        ${sans(0, 0.6, 2.3, GREY, "Bluetooth · cars with an OBD-II port", "middle")}
        ${sans(0, 4.6, 2.3, GREY, "Check your car: getautosure.com/app", "middle")}</g>
        <g transform="translate(9.5 78)" stroke="${GREY}" stroke-width="0.4" fill="none">
          <path d="M1 1.8 h5 l-.6 6.2 h-3.8 z"/><path d="M.4 1.8 h6.2"/><path d="M-.3 0 l7.6 9 M7.3 0 l-7.6 9"/></g>` +
        band(25.5, 91);
    },
    back() { // 63 x 100
      return `<text x="5" y="11" font-family="Sora" font-weight="700" font-size="4" fill="${WHITE}">Start in 3 steps</text>` +
        sans(5, 18.5, 2.4, "#D5D8DD", `<tspan font-family="IBM Plex Mono" fill="${ORANGE}">01 </tspan>Scan the QR, get the app`) +
        sans(5, 23.5, 2.4, "#D5D8DD", `<tspan font-family="IBM Plex Mono" fill="${ORANGE}">02 </tspan>Plug into the OBD port`) +
        sans(5, 28.5, 2.4, "#D5D8DD", `<tspan font-family="IBM Plex Mono" fill="${ORANGE}">03 </tspan>Ignition on, tap Scan`) +
        `<rect x="41" y="6" width="17.5" height="17.5" rx="1.2" fill="${WHITE}"/>` + qr("https://getautosure.com/app", 42.25, 7.25, 15) +
        sans(49.75, 27, 1.8, GREY, "getautosure.com/app", "middle", 'font-family="IBM Plex Mono"') +
        `<line x1="5" y1="33" x2="58" y2="33" stroke="#2E333B" stroke-width="0.3"/>` +
        sans(5, 39, 2.05, GREY, "Contents: 1 OBD-II scanner, 1 card, 1 sticker") +
        `<text x="5" y="46" font-family="Sora" font-weight="700" font-size="2.9" fill="${WHITE}">MRP ₹1,499 <tspan font-family="IBM Plex Sans" font-weight="400" font-size="2.1" fill="${GREY}">(incl. of all taxes)</tspan></text>` +
        sans(5, 51.5, 2.05, GREY, "Net quantity: 1 N · Mfd: MM/YYYY") +
        sans(5, 55.5, 2.05, GREY, "Marketed by: [name, full address]") +
        sans(5, 59.5, 2.05, GREY, "Mfd / imported by: [vendor, address]") +
        sans(5, 63.5, 2.05, GREY, "Country of origin: [country]") +
        sans(5, 67.5, 2.05, GREY, "Customer care: [phone] · [email]") +
        `<rect x="5" y="73" width="32" height="14" rx="0.8" fill="${WHITE}"/>` +
        sans(21, 81, 2, "#8A9099", "EAN barcode", "middle") +
        band(63, 91);
    },
    bottom() { // 63 x 25.5
      return sans(31.5, 14.5, 2.2, GREY, "getautosure.com", "middle", 'font-family="IBM Plex Mono"');
    },
  };

  // Upgrade card front, 95 x 58.
  function cardFront() {
    return `<rect width="95" height="58" rx="2.5" fill="${INK}"/>` +
      `<text x="6" y="9" font-family="IBM Plex Mono" font-size="2.3" fill="${ORANGE}" letter-spacing="0.3">GETAUTOSURE PRO</text>` +
      `<text x="6" y="17" font-family="Sora" font-weight="700" font-size="4.4" fill="${WHITE}">Buying a used car?</text>` +
      `<text x="6" y="22.5" font-family="Sora" font-weight="700" font-size="4.4" fill="${WHITE}">Check it properly.</text>` +
      sans(6, 30, 2.4, "#D5D8DD", `<tspan fill="${ORANGE}">✓ </tspan>Pre-purchase report as PDF`) +
      sans(6, 34.5, 2.4, "#D5D8DD", `<tspan fill="${ORANGE}">✓ </tspan>Tamper and odometer checks`) +
      sans(6, 39, 2.4, "#D5D8DD", `<tspan fill="${ORANGE}">✓ </tspan>RTO record check`) +
      `<rect x="67" y="11" width="22" height="22" rx="1.5" fill="${WHITE}"/>` + qr("https://getautosure.com/pro", 68.5, 12.5, 19) +
      sans(78, 37, 1.9, GREY, "getautosure.com/pro", "middle", 'font-family="IBM Plex Mono"') +
      `<line x1="6" y1="45" x2="89" y2="45" stroke="#2E333B" stroke-width="0.3"/>` +
      sans(6, 51, 2.2, "#D5D8DD", `<tspan font-family="Sora" font-weight="700" fill="${WHITE}">Car dealer? </tspan>Dealer Sathi puts your name on every report.`);
  }

  // Windscreen sticker, 50 mm round.
  function sticker() {
    return `<circle cx="25" cy="25" r="25" fill="${INK}"/><circle cx="25" cy="25" r="23" fill="none" stroke="${ORANGE}" stroke-width="1"/>` +
      mark(17.5, 6.5, 15) +
      `<text x="25" y="27" text-anchor="middle" font-family="IBM Plex Mono" font-size="2.6" fill="${GREY}" letter-spacing="0.5">CHECKED BY</text>` +
      wordmark(25, 33.5, 5.4) + sans(25, 39, 2.2, GREY, "getautosure.com", "middle");
  }

  // Flat flip-lid box, 104 x 80 x 32 mm outside (Carly-style). The dongle lies face up in the insert.
  const flat = {
    lidTop() { // 104 x 80, the closed lid seen from above
      return mark(39, 14, 26) +
        wordmark(52, 56, 9) +
        sans(52, 63.5, 3, GREY, "OBD-II CAR SCANNER", "middle", 'letter-spacing="0.6"');
    },
    lidInside() { // 104 x 80, printed inside the lid
      const step = (x, n, title, sub, glyph) => `<g transform="translate(${x} 36)">
        <circle r="7" fill="none" stroke="${ORANGE}" stroke-width="0.55"/>${glyph}
        <text y="-9.5" text-anchor="middle" font-family="IBM Plex Mono" font-size="2.3" fill="${ORANGE}">${n}</text>
        <text y="12.5" text-anchor="middle" font-family="Sora" font-weight="700" font-size="3" fill="${WHITE}">${title}</text>
        <text y="16.5" text-anchor="middle" font-family="IBM Plex Sans" font-size="2.5" fill="#C9CDD3">${sub}</text></g>`;
      return `<text x="52" y="15" text-anchor="middle" font-family="Sora" font-weight="700" font-size="6" fill="${WHITE}">Don't guess. Be sure.</text>` +
        step(17, "01", "Get the app", "Scan the QR",
          `<rect x="-2.8" y="-4.2" width="5.6" height="8.4" rx="0.9" fill="none" stroke="${WHITE}" stroke-width="0.5"/>`) +
        step(41, "02", "Plug it in", "Under the steering",
          `<path d="M-3.4 -1.8 h6.8 l-1 4.2 h-4.8 z M-1.6 -1.8 v-2 M1.6 -1.8 v-2" fill="none" stroke="${WHITE}" stroke-width="0.5" stroke-linejoin="round"/>`) +
        step(65, "03", "Ignition on", "Tap Scan",
          `<path d="M-2.6 0 l1.8 2 l3.6 -4.4" fill="none" stroke="${WHITE}" stroke-width="0.65" stroke-linecap="round" stroke-linejoin="round"/>`) +
        `<rect x="79" y="25" width="21" height="21" rx="1.5" fill="${WHITE}"/>` + qr("https://getautosure.com/app", 80.5, 26.5, 18) +
        sans(89.5, 50, 2.1, GREY, "getautosure.com/app", "middle", 'font-family="IBM Plex Mono"') +
        `<line x1="10" y1="62" x2="94" y2="62" stroke="#2E333B" stroke-width="0.3"/>` +
        wordmark(52, 71, 4);
    },
    frontWall() { // 104 x 32, clean: wordmark only, no marketing
      return wordmark(52, 17.5, 4.6) + `<rect x="-3" y="26" width="110" height="3" fill="${ORANGE}"/>`;
    },
    sideWall() { // 80 x 32
      return `<rect x="-3" y="26" width="86" height="3" fill="${ORANGE}"/>`;
    },
    leftWall() { // 80 x 32
      return `<text x="40" y="17.5" text-anchor="middle" font-family="Sora" font-weight="700" font-size="4.4" fill="${WHITE}">Don't guess. Be sure.</text>` +
        `<rect x="-3" y="26" width="86" height="3" fill="${ORANGE}"/>`;
    },
    rightWall() { // 80 x 32, kept plain for the Amazon FNSKU sticker
      return `<rect x="-3" y="26" width="86" height="3" fill="${ORANGE}"/>`;
    },
    backWall() { // 104 x 32, four features
      const S = `fill="none" stroke="${ORANGE}" stroke-width="0.5" stroke-linecap="round" stroke-linejoin="round"`;
      const feat = (x, glyph, l1, l2) => `<g transform="translate(${x} 0)">${glyph}
        <text y="17.5" text-anchor="middle" font-family="Sora" font-weight="700" font-size="2.4" fill="${WHITE}">${l1}</text>
        <text y="21.3" text-anchor="middle" font-family="IBM Plex Sans" font-size="2.3" fill="#C9CDD3">${l2}</text></g>`;
      return feat(13,  // chat bubble with spark
          `<path d="M-4 5 h8 a1.2 1.2 0 0 1 1.2 1.2 v4.6 a1.2 1.2 0 0 1 -1.2 1.2 h-4.4 l-2.4 2 v-2 h-1.2 a1.2 1.2 0 0 1 -1.2 -1.2 v-4.6 a1.2 1.2 0 0 1 1.2 -1.2 z" ${S}/><path d="M0 6.6 l.6 1.4 l1.4 .6 l-1.4 .6 l-.6 1.4 l-.6 -1.4 l-1.4 -.6 l1.4 -.6 z" fill="${ORANGE}"/>`,
          "Your own", "AI Mechanic") +
        feat(39,  // car with check
          `<path d="M-5 11 v-2.6 l1.4 -3.2 h7.2 l1.4 3.2 v2.6 z M-5 8.4 h10" ${S}/><circle cx="-2.8" cy="11.6" r="1" ${S}/><circle cx="2.8" cy="11.6" r="1" ${S}/><path d="M-1.4 6.8 l1 1 l2 -2" ${S}/>`,
          "Used Car", "Check") +
        feat(65,  // document
          `<path d="M-3.2 4.4 h4.4 l2 2 v7.2 h-6.4 z M1.2 4.4 v2 h2" ${S}/><path d="M-1.8 9 h3.6 M-1.8 11 h3.6" ${S}/>`,
          "PDF car", "health report") +
        feat(91,  // phone
          `<rect x="-2.4" y="4.2" width="4.8" height="9" rx="0.9" ${S}/><path d="M-0.6 11.8 h1.2" ${S}/>`,
          "Android", "&amp; iPhone") +
        `<rect x="-3" y="26" width="110" height="3" fill="${ORANGE}"/>`;
    },
    bottom() { // 104 x 80, regulatory declarations, read with the box tipped back
      const L = 3.75; let y = 15.5; const rows = [];
      const row = (k, v) => { rows.push(`<text x="7" y="${y.toFixed(2)}" font-family="IBM Plex Sans" font-size="2.1" fill="#C9CDD3"><tspan font-weight="600" fill="${WHITE}">${k}</tspan> ${v}</text>`); y += L; };
      row("Product:", "Bluetooth OBD-II Automotive Diagnostic Scanner");
      row("Model No.:", "V02H4");
      row("Net Quantity:", "1 Unit");
      row("Country of Origin:", "China");
      row("Manufactured by:", "[manufacturer legal name, full address, China]");
      row("Imported &amp; Marketed by:", "[business name, address, PIN]");
      row("Month &amp; Year of Manufacture:", "[MM/YYYY]");
      rows.push(`<text x="7" y="${(y + 0.6).toFixed(2)}" font-family="Sora" font-weight="700" font-size="3.4" fill="${WHITE}">MRP ₹1,499 <tspan font-family="IBM Plex Sans" font-weight="400" font-size="2.1" fill="#C9CDD3">(Inclusive of all taxes)</tspan></text>`); y += L + 1.4;
      row("Consumer Care:", "[name, postal address]");
      row("Phone:", `[number] · <tspan font-weight="600" fill="${WHITE}">Email:</tspan> [email]`);
      row("Contents:", "1 × OBD-II Scanner · 1 × Info card · 1 × Windscreen sticker");
      row("Compatibility:", "For vehicles with an OBD-II port. Supported functions");
      rows.push(`<text x="7" y="${y.toFixed(2)}" font-family="IBM Plex Sans" font-size="2.1" fill="#C9CDD3">vary by vehicle make, model, year and protocol.</text>`); y += L;
      row("Website:", "getautosure.com");
      return `<text x="7" y="10" font-family="Sora" font-weight="700" font-size="3.2" fill="${WHITE}">GetAutoSure OBD-II Diagnostic Scanner</text>` +
        rows.join("") +
        `<g transform="translate(82 60)" stroke="${GREY}" stroke-width="0.4" fill="none"><path d="M1 1.8 h5 l-.6 6.2 h-3.8 z"/><path d="M.4 1.8 h6.2"/><path d="M-.3 0 l7.6 9 M7.3 0 l-7.6 9"/></g>` +
        sans(86, 73, 1.7, GREY, "Do not dispose with", "middle") + sans(86, 75.6, 1.7, GREY, "household waste", "middle");
    },
  };

  // Quiet premium direction (v2): monochrome outside, orange only inside as the reveal.
  const MUTE = "#8C9199";
  const monoMark = (x, y, s) => mark(x, y, s);   // brand colours: orange arc + check, white wheels
  const monoWord = (x, y, size) => wordmark(x, y, size);   // "GetAuto" white, "Sure" orange

  // Dongle face, minimal: LED dots stay exactly where the vendor's LED holes are.
  function labelMinimal() {
    const W = 42, H = 50.6;
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${W} ${H}" width="${W}mm" height="${H}mm">
      <rect width="${W}" height="${H}" rx="1" fill="#111316"/>
      <g fill="#E9EBEE"><circle cx="12.35" cy="15.6" r="1.1"/><circle cx="20.96" cy="15.6" r="1.1"/><circle cx="29.65" cy="15.6" r="1.1"/></g>
      <g font-family="IBM Plex Mono" font-size="1.7" fill="${MUTE}" text-anchor="middle" letter-spacing="0.2">
        <text x="12.35" y="20">PWR</text><text x="20.96" y="20">PC</text><text x="29.65" y="20">OBD</text></g>
      ${monoMark(16, 25.5, 10)}
      ${monoWord(21, 41.5, 4.6)}
    </svg>`;
  }

  // Same face turned landscape (plug on the left), for the dongle lying sideways like Carly's.
  // Point (x, y) on the portrait label maps to (y, 42 - x), so the LED dots form a column by the plug.
  function labelLandscape() {
    const W = 50.6, H = 42;
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${W} ${H}" width="${W}mm" height="${H}mm">
      <rect width="${W}" height="${H}" rx="1" fill="#111316"/>
      <g fill="#E9EBEE"><circle cx="15.6" cy="12.35" r="1.1"/><circle cx="15.6" cy="21.04" r="1.1"/><circle cx="15.6" cy="29.65" r="1.1"/></g>
      <g font-family="IBM Plex Mono" font-size="1.6" fill="${MUTE}" text-anchor="end" letter-spacing="0.2">
        <text x="13.4" y="12.9">PWR</text><text x="13.4" y="21.6">PC</text><text x="13.4" y="30.2">OBD</text></g>
      ${monoMark(29.5, 7.5, 11)}
      ${monoWord(35, 27.5, 4.4)}
    </svg>`;
  }

  const quiet = {
    lidTop() { // 104 x 80: one small white logo
      return monoMark(44, 23, 16) + monoWord(52, 48, 6) +
        `<text x="52" y="54" text-anchor="middle" font-family="IBM Plex Sans" font-size="2" fill="${MUTE}" letter-spacing="0.8">OBD-II SCANNER</text>`;
    },
    lidInside() { // 104 x 80: small type, small icons, lots of black
      const S = `fill="none" stroke="${WHITE}" stroke-width="0.4" stroke-linecap="round" stroke-linejoin="round"`;
      const step = (x, title, sub1, sub2, glyph) => `<g transform="translate(${x} 0)">${glyph}
        <text y="40" text-anchor="middle" font-family="Sora" font-weight="600" font-size="2.3" fill="${ORANGE}" letter-spacing="0.2">${title}</text>
        <text y="43.6" text-anchor="middle" font-family="IBM Plex Sans" font-size="2" fill="${MUTE}">${sub1}</text>
        <text y="46.6" text-anchor="middle" font-family="IBM Plex Sans" font-size="2" fill="${MUTE}">${sub2}</text></g>`;
      const arrow = x => `<path d="M${x - 1} 30 l1.6 1.6 l-1.6 1.6" fill="none" stroke="${ORANGE}" stroke-width="0.45" stroke-linecap="round" stroke-linejoin="round"/>`;
      return `<text x="52" y="16" text-anchor="middle" font-family="Sora" font-weight="700" font-size="4" fill="${WHITE}">Start in three steps</text>` +
        step(26, "GET THE APP", "Scan the code", "below",
          `<rect x="-2.6" y="26" width="5.2" height="9" rx="0.9" ${S}/><path d="M-0.6 33.6 h1.2" ${S}/>`) + arrow(39) +
        step(52, "PLUG IN", "OBD port under", "the steering",
          `<path d="M-4 29 h8 l-1.2 4.4 h-5.6 z M-2 29 v-2 M2 29 v-2" ${S}/>`) + arrow(65) +
        step(78, "SCAN", "Ignition on,", "tap Scan",
          `<path d="M-2.6 31 l1.8 2 l3.8 -4.6" ${S} stroke-width="0.55"/><circle cx="0" cy="31" r="4.6" ${S}/>`) +
        `<rect x="45.5" y="53" width="13" height="13" rx="1" fill="${WHITE}"/>` + qr("https://getautosure.com/app", 46.4, 53.9, 11.2) +
        `<text x="52" y="72" text-anchor="middle" font-family="IBM Plex Sans" font-size="2.2" fill="${WHITE}">Don't guess. Be sure.</text>`;
    },
    frontWall() { return ""; },            // 104 x 30: plain black
    rightWall() { return ""; },            // 80 x 30: plain, for the Amazon FNSKU sticker (Amazon-only launch, no EAN)
    leftWall() {                           // 80 x 30
      return `<text x="40" y="16.5" text-anchor="middle" font-family="IBM Plex Sans" font-size="3" fill="${MUTE}" letter-spacing="0.2">Don't guess. Be sure.</text>`;
    },
    backWall() {                           // 104 x 30: four features, white line icons
      const S = `fill="none" stroke="${ORANGE}" stroke-width="0.4" stroke-linecap="round" stroke-linejoin="round"`;
      const feat = (x, glyph, l1, l2) => `<g transform="translate(${x} 0)">${glyph}
        <text y="19" text-anchor="middle" font-family="IBM Plex Sans" font-weight="600" font-size="2.2" fill="${WHITE}">${l1}</text>
        <text y="22.4" text-anchor="middle" font-family="IBM Plex Sans" font-size="2.1" fill="${MUTE}">${l2}</text></g>`;
      return feat(13, `<path d="M-3.4 7 h6.8 a1 1 0 0 1 1 1 v4 a1 1 0 0 1 -1 1 h-3.8 l-2 1.7 v-1.7 h-1 a1 1 0 0 1 -1 -1 v-4 a1 1 0 0 1 1 -1 z" ${S}/>`, "Your own", "AI Mechanic") +
        feat(39, `<path d="M-4.4 12.6 v-2.2 l1.2 -2.8 h6.4 l1.2 2.8 v2.2 z M-4.4 10.4 h8.8" ${S}/><circle cx="-2.4" cy="13.2" r=".8" ${S}/><circle cx="2.4" cy="13.2" r=".8" ${S}/>`, "Used Car", "Check") +
        feat(65, `<path d="M-2.8 6.6 h3.8 l1.8 1.8 v6.4 h-5.6 z M1 6.6 v1.8 h1.8 M-1.4 10.8 h2.8 M-1.4 12.6 h2.8" ${S}/>`, "PDF car", "health report") +
        feat(91, `<rect x="-2.1" y="6.4" width="4.2" height="8" rx="0.8" ${S}/>`, "App for", "Android &amp; iPhone") +
        `<text x="52" y="27.4" text-anchor="middle" font-family="IBM Plex Sans" font-size="2.1" fill="${MUTE}">Universal OBD-II fit for your family car · Check yours: getautosure.com/app</text>`;
    },
  };

  return { ORANGE, INK, WHITE, GREY, mark, icon, wordmark, qr, label, labelMinimal, labelLandscape, panels, cardFront, sticker, flat, quiet };
})();
