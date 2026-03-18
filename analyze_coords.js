const fs = require('fs');
const path = require('path');

const coords = JSON.parse(fs.readFileSync(path.join(__dirname, 'assets/coords.json'), 'utf8'));

let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
coords.forEach(coord => {
    if (coord.x < minX) minX = coord.x;
    if (coord.x > maxX) maxX = coord.x;
    if (coord.y < minY) minY = coord.y;
    if (coord.y > maxY) maxY = coord.y;
});

console.log(`Total LEDs: ${coords.length}`);
console.log(`X range: ${minX} - ${maxX}`);
console.log(`Y range: ${minY} - ${maxY}`);
console.log(`Width: ${maxX - minX}, Height: ${maxY - minY}`);