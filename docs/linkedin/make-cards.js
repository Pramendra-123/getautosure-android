// Renders the LinkedIn image cards (1080x1350) from SVG with sharp (borrowed from report-app).
const path = require('path');
const sharp = require(path.join('C:/Users/hp/Desktop/Code/report-app/node_modules/sharp'));
const fs = require('fs');
const OUT = path.join(__dirname, 'images');
const ORANGE = '#FF6B2C', INK = '#12151A', BG = '#FBF7F2', MUTED = '#6B6460';
const F = "font-family=\"Segoe UI, Arial, sans-serif\"";
const logo = (x, y, s) => `
  <g transform="translate(${x},${y}) scale(${s / 64})">
    <rect x="4" y="4" width="56" height="56" rx="16" fill="${ORANGE}"/>
    <path d="M14 34 A18 18 0 0 1 50 34" fill="none" stroke="#fff" stroke-width="5" stroke-linecap="round"/>
    <path d="M24 30 L30 37 L42 20" fill="none" stroke="#fff" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/>
    <circle cx="14" cy="46" r="6.5" fill="${INK}"/><circle cx="50" cy="46" r="6.5" fill="${INK}"/>
  </g>`;
const footer = `
  ${logo(80, 1218, 64)}
  <text x="162" y="1262" ${F} font-size="34" font-weight="700" fill="${INK}">GetAutoSure</text>
  <text x="1000" y="1262" ${F} font-size="30" fill="${MUTED}" text-anchor="end">getautosure.com</text>`;
// Simplified check-engine symbol (engine outline), drawn in the given colour.
const engine = (x, y, c) => `
  <g transform="translate(${x},${y})" fill="none" stroke="${c}" stroke-width="9" stroke-linejoin="round" stroke-linecap="round">
    <path d="M40 30 h50 v-14 h40 v14 h26 l18 22 h22 v-16 h14 v76 h-14 v-16 h-22 l-18 30 h-86 l-20 -24 h-20 v-34 h20 z"/>
    <path d="M4 58 v40"/>
  </g>`;

const card2 = `<svg xmlns="http://www.w3.org/2000/svg" width="1080" height="1350" viewBox="0 0 1080 1350">
  <rect width="1080" height="1350" fill="${BG}"/>
  <text x="80" y="170" ${F} font-size="74" font-weight="800" fill="${INK}">Check-engine light on?</text>
  <text x="80" y="240" ${F} font-size="38" fill="${MUTED}">Look closely. It matters which one you see.</text>

  <rect x="80" y="310" width="920" height="400" rx="36" fill="${INK}"/>
  ${engine(130, 400, '#FFB020')}
  <text x="400" y="430" ${F} font-size="34" font-weight="700" fill="#FFB020" letter-spacing="4">STEADY</text>
  <text x="400" y="500" ${F} font-size="48" font-weight="800" fill="#fff">Get it checked soon</text>
  <text x="400" y="565" ${F} font-size="32" fill="#D9D3CC">A fault is stored. Drive gently</text>
  <text x="400" y="607" ${F} font-size="32" fill="#D9D3CC">and see a workshop within</text>
  <text x="400" y="649" ${F} font-size="32" fill="#D9D3CC">a few days.</text>

  <rect x="80" y="750" width="920" height="400" rx="36" fill="${INK}"/>
  <rect x="80" y="750" width="920" height="400" rx="36" fill="none" stroke="#FF3B30" stroke-width="6"/>
  ${engine(130, 840, '#FF3B30')}
  <path d="M150 820 l-22 -22 M290 820 l22 -22 M220 812 v-30" stroke="#FF3B30" stroke-width="8" stroke-linecap="round"/>
  <text x="400" y="870" ${F} font-size="34" font-weight="700" fill="#FF3B30" letter-spacing="4">FLASHING</text>
  <text x="400" y="940" ${F} font-size="48" font-weight="800" fill="#fff">Slow down, get help now</text>
  <text x="400" y="1005" ${F} font-size="32" fill="#D9D3CC">Active misfire. Unburnt fuel can</text>
  <text x="400" y="1047" ${F} font-size="32" fill="#D9D3CC">ruin the catalytic converter</text>
  <text x="400" y="1089" ${F} font-size="32" fill="#D9D3CC">(₹8,000–₹35,000+).</text>
  ${footer}
</svg>`;

const card3 = `<svg xmlns="http://www.w3.org/2000/svg" width="1080" height="1350" viewBox="0 0 1080 1350">
  <rect width="1080" height="1350" fill="${BG}"/>
  <rect x="80" y="120" width="190" height="70" rx="35" fill="${INK}"/>
  <text x="175" y="167" ${F} font-size="34" font-weight="800" fill="#fff" text-anchor="middle" letter-spacing="4">MYTH</text>
  <text x="80" y="300" ${F} font-size="76" font-weight="800" fill="${INK}">“Clearing the fault</text>
  <text x="80" y="390" ${F} font-size="76" font-weight="800" fill="${INK}">code fixes the car.”</text>
  <g transform="translate(860,250)">
    <circle cx="60" cy="60" r="60" fill="#FF3B30"/>
    <path d="M36 36 L84 84 M84 36 L36 84" stroke="#fff" stroke-width="14" stroke-linecap="round"/>
  </g>

  <rect x="80" y="490" width="920" height="600" rx="36" fill="${INK}"/>
  <rect x="130" y="540" width="170" height="64" rx="32" fill="${ORANGE}"/>
  <text x="215" y="584" ${F} font-size="32" font-weight="800" fill="#fff" text-anchor="middle" letter-spacing="4">FACT</text>
  <text x="130" y="690" ${F} font-size="50" font-weight="800" fill="#fff">It only turns the light off.</text>
  <text x="130" y="780" ${F} font-size="36" fill="#D9D3CC">The car re-checks itself over the next</text>
  <text x="130" y="828" ${F} font-size="36" fill="#D9D3CC">few drives. If the fault is still there,</text>
  <text x="130" y="876" ${F} font-size="36" fill="#D9D3CC">the code comes back.</text>
  <text x="130" y="975" ${F} font-size="36" font-weight="700" fill="${ORANGE}">Buying used? Ask the seller:</text>
  <text x="130" y="1027" ${F} font-size="36" font-weight="700" fill="#fff">“When were the codes last cleared?”</text>
  ${footer}
</svg>`;

(async () => {
  for (const [name, svg] of [['post2-check-engine-light.png', card2], ['post3-myth-clearing-codes.png', card3]]) {
    await sharp(Buffer.from(svg)).png().toFile(path.join(OUT, name));
    console.log('wrote', name);
  }
})();
