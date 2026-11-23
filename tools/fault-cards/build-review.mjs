// Writes docs/fault-cards-review.html: every card in out/cards.json, searchable, low-confidence first.
import { readFileSync, writeFileSync } from "node:fs";

const cards = JSON.parse(readFileSync("out/cards.json", "utf8"));
const rank = { low: 0, medium: 1, high: 2 };
cards.sort((a, b) => rank[a.confidence] - rank[b.confidence] || (a.brand ?? "").localeCompare(b.brand ?? "") || a.code.localeCompare(b.code));
const data = JSON.stringify(cards).replace(/</g, "\\u003c");

writeFileSync("../../docs/fault-cards-review.html", `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Fault Cards Review</title>
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;600;700&family=Noto+Sans+Devanagari:wght@400;600;700&display=swap" rel="stylesheet">
<style>
:root{--bg:#FAF7F2;--card:#141414;--card2:#1F1F1F;--text:#F5F5F5;--muted:#A8A8A8;--ink:#1A1A1A;--ink-muted:#6B6B6B;--orange:#F26B1D;--line:#2C2C2C}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);font-family:Inter,"Noto Sans Devanagari",system-ui,sans-serif;line-height:1.5}
body.hi{font-family:"Noto Sans Devanagari",Inter,system-ui,sans-serif}
header{position:sticky;top:0;background:var(--bg);z-index:2;max-width:1100px;margin:0 auto;padding:20px 16px 10px;border-bottom:1px solid #e6e0d6}
h1{margin:0 0 4px;font-size:24px}h1 span{color:var(--orange)}.sub{color:var(--ink-muted);font-size:13px;margin:0 0 10px}
.bar{display:flex;flex-wrap:wrap;gap:8px;align-items:center}
input,select{font:inherit;padding:7px 10px;border:1.5px solid var(--ink);border-radius:10px;background:#fff;color:var(--ink)}
input{flex:1;min-width:180px}
.toggle button{border:1.5px solid var(--ink);background:transparent;color:var(--ink);padding:6px 14px;font:inherit;font-weight:600;cursor:pointer}
.toggle button:first-child{border-radius:999px 0 0 999px}.toggle button:last-child{border-radius:0 999px 999px 0;border-left:none}.toggle button.on{background:var(--ink);color:#fff}
.count{font-size:13px;color:var(--ink-muted)}
.grid{max-width:1100px;margin:0 auto;padding:14px 16px 20px;display:grid;grid-template-columns:repeat(auto-fill,minmax(min(100%,480px),1fr));gap:16px}
.card{background:var(--card);color:var(--text);border-radius:18px;padding:18px;display:flex;flex-direction:column;gap:11px}
.top{display:flex;align-items:center;gap:8px;flex-wrap:wrap}
.code{background:var(--orange);color:#fff;font-weight:700;border-radius:8px;padding:2px 10px;font-size:14px}
.pill{border-radius:999px;padding:2px 10px;font-size:12px;font-weight:700;color:#111}
.chip{border:1px solid var(--line);color:var(--muted);border-radius:999px;padding:1px 8px;font-size:11px}
.conf-low{border-color:#E5484D;color:#FF8A8D}.conf-medium{border-color:#E6B422;color:#F2CF6B}
.title{font-size:18px;font-weight:700;margin:0}.tech{color:var(--muted);font-size:12px;margin-top:-8px}
h3{margin:0 0 4px;font-size:11px;text-transform:uppercase;letter-spacing:1px;color:var(--orange)}body.hi h3{text-transform:none;letter-spacing:0;font-size:13px}
ul,ol{margin:0;padding-left:20px;font-size:14px}li{margin:2px 0}p{margin:0;font-size:14.5px}
.tag{background:var(--orange);color:#fff;border-radius:6px;font-size:10.5px;font-weight:700;padding:0 6px;margin-left:4px}
.box{background:var(--card2);border-radius:12px;padding:10px 12px;font-size:14px}.box.drive{border-left:3px solid var(--orange)}
table{width:100%;border-collapse:collapse;font-size:13.5px}td{padding:5px 0;border-bottom:1px solid var(--line);vertical-align:top}
td.r{text-align:right;white-space:nowrap;font-weight:600;padding-left:12px}tr:last-child td{border-bottom:none}
.india{font-size:13.5px;color:#FFD9C2}.india b{color:var(--orange)}
.more{display:block;margin:0 auto 40px;font:inherit;font-weight:600;padding:10px 22px;border-radius:999px;border:1.5px solid var(--ink);background:transparent;cursor:pointer}
</style></head><body>
<header>
<h1>Fault-code cards: <span>review</span></h1>
<p class="sub">${cards.length} cards made by Claude Sonnet. Low-confidence cards come first; they need a mechanic's check most. Costs are tentative ranges.</p>
<div class="bar">
<input id="q" placeholder="Search code or words, e.g. P0300, battery, Tata">
<select id="conf"><option value="">All confidence</option><option value="low">Low</option><option value="medium">Medium</option><option value="high">High</option></select>
<select id="urg"><option value="">All urgency</option><option value="STOP">Stop driving</option><option value="NOW">Fix right away</option><option value="SOON">Within 2 weeks</option><option value="WAIT">Can wait</option></select>
<select id="brand"><option value="">All makers</option></select>
<div class="toggle"><button id="b-en" class="on">English</button><button id="b-hi">हिंदी</button></div>
<span class="count" id="count"></span>
</div></header>
<main class="grid" id="grid"></main><button class="more" id="more">Show more</button>
<script>
const CARDS=${data};
const URG={STOP:["#E5484D","Stop driving","गाड़ी रोकें"],NOW:["#F26B1D","Fix right away","तुरंत ठीक कराएं"],SOON:["#E6B422","Fix within 2 weeks","2 हफ़्ते में ठीक कराएं"],WAIT:["#3FB27F","Can wait","रुक सकते हैं"]};
const L={notice:["You may notice","आपको ये दिख सकता है"],causes:["Likely causes","संभावित कारण"],most:["Most common","सबसे आम"],drive:["Can I keep driving?","क्या गाड़ी चला सकते हैं?"],first:["Cheapest first check","सबसे सस्ती पहली जांच"],cost:["Tentative cost","अनुमानित ख़र्च"],india:["India note","भारत के लिए ख़ास"]};
let lang="en",shown=30,list=CARDS;
const i=()=>lang==="en"?0:1,esc=s=>String(s).replace(/[&<>]/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;"}[c])),t=o=>esc(o[lang]);
const inr=n=>"₹"+n.toLocaleString("en-IN");
for(const b of [...new Set(CARDS.map(c=>c.brand||"Generic"))].sort())document.getElementById("brand").insertAdjacentHTML("beforeend",'<option>'+b+'</option>');
function card(c){const u=URG[c.urgency];return '<article class="card"><div class="top"><span class="code">'+c.code+'</span>'+
'<span class="pill" style="background:'+u[0]+'">'+u[1+i()]+'</span>'+(c.brand?'<span class="chip">'+c.brand+'</span>':'')+
c.fuel.map(f=>'<span class="chip">'+f+'</span>').join("")+'<span class="chip conf-'+c.confidence+'">'+c.confidence+' confidence</span></div>'+
'<p class="title">'+t(c.title)+'</p><div class="tech">'+esc(c.tech)+'</div><p>'+t(c.meaning)+'</p>'+
'<div><h3>'+L.notice[i()]+'</h3><ul>'+c.notice.map(n=>'<li>'+t(n)+'</li>').join("")+'</ul></div>'+
'<div><h3>'+L.causes[i()]+'</h3><ol>'+c.causes.map((x,k)=>'<li>'+t(x)+(k?'':'<span class="tag">'+L.most[i()]+'</span>')+'</li>').join("")+'</ol></div>'+
'<div class="box drive"><h3>'+L.drive[i()]+'</h3>'+t(c.drive)+'</div><div class="box"><h3>'+L.first[i()]+'</h3>'+t(c.first_check)+'</div>'+
'<div><h3>'+L.cost[i()]+'</h3><table>'+c.costs.map(x=>'<tr><td>'+t(x.part)+'</td><td class="r">'+inr(x.min_inr)+'–'+inr(x.max_inr)+'</td></tr>').join("")+'</table></div>'+
(c.india_note.en.trim()?'<div class="india"><b>'+L.india[i()]+':</b> '+t(c.india_note)+'</div>':'')+'</article>';}
function filter(){const q=document.getElementById("q").value.toLowerCase().trim(),cf=document.getElementById("conf").value,ug=document.getElementById("urg").value,br=document.getElementById("brand").value;
list=CARDS.filter(c=>(!cf||c.confidence===cf)&&(!ug||c.urgency===ug)&&(!br||(c.brand||"Generic")===br)&&(!q||JSON.stringify(c).toLowerCase().includes(q)));shown=30;render();}
function render(){document.body.classList.toggle("hi",lang==="hi");document.getElementById("grid").innerHTML=list.slice(0,shown).map(card).join("");
document.getElementById("count").textContent=list.length+" cards";document.getElementById("more").style.display=shown<list.length?"block":"none";
document.getElementById("b-en").classList.toggle("on",lang==="en");document.getElementById("b-hi").classList.toggle("on",lang==="hi");}
["q","conf","urg","brand"].forEach(id=>document.getElementById(id).addEventListener("input",filter));
document.getElementById("more").onclick=()=>{shown+=30;render();};
document.getElementById("b-en").onclick=()=>{lang="en";render();};document.getElementById("b-hi").onclick=()=>{lang="hi";render();};
render();
</script></body></html>`);
console.log(`Wrote docs/fault-cards-review.html with ${cards.length} cards.`);
