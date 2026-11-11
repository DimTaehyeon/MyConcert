const fs = require('node:fs');
const path = require('node:path');
const sharp = require('sharp');
const source = path.resolve(__dirname, '../design/assets');
const target = path.resolve(__dirname, '../app/src/main/res/drawable-nodpi');
fs.mkdirSync(target, { recursive: true });
Promise.all(fs.readdirSync(source).map(async name => {
  const input = path.join(source, name);
  const output = path.join(target, name.replace(/\.svg$/, '.png'));
  if (name.endsWith('.svg')) await sharp(input, { density: 288 }).png().toFile(output);
  else fs.copyFileSync(input, output);
})).then(async () => {
  for (const [density, size] of Object.entries({mdpi:48, hdpi:72, xhdpi:96, xxhdpi:144, xxxhdpi:192})) {
    const folder = path.resolve(__dirname, `../app/src/main/res/mipmap-${density}`);
    await sharp(path.join(source, 'brand_logo.png')).resize(size,size).flatten({background:'#fff'}).webp({quality:95}).toFile(path.join(folder,'ic_launcher.webp'));
    await sharp(path.join(source, 'brand_logo.png')).resize(size,size).flatten({background:'#fff'}).webp({quality:95}).toFile(path.join(folder,'ic_launcher_round.webp'));
  }
}).catch(e => { console.error(e); process.exitCode = 1; });
