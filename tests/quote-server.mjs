// Deterministic HTTP quote fixture used ONLY by compose.e2e.yaml.
import { createServer } from 'node:http';
createServer((request, response) => {
  const symbol = decodeURIComponent(new URL(request.url, 'http://localhost').pathname.split('/').pop());
  response.writeHead(200, { 'Content-Type': 'application/json' });
  response.end(JSON.stringify({ data: { symbol, bid: 100, ask: 101, price: 100.5, timestamp: new Date().toISOString() } }));
}).listen(8099, '0.0.0.0');
