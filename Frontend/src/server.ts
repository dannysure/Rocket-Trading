import {
  AngularNodeAppEngine,
  createNodeRequestHandler,
  isMainModule,
  writeResponseToNodeResponse,
} from '@angular/ssr/node';
import express from 'express';
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

const browserDistFolder = join(import.meta.dirname, '../browser');
const quoteApiBaseUrl =
  'https://y4t9nq2bqf.execute-api.eu-west-2.amazonaws.com/v1/quotes';
const analyticsApiBaseUrl =
  process.env['ROCKET_TRADING_ANALYTICS_API_BASE_URL'] ??
  process.env['ANALYTICS_API_BASE_URL'] ??
  'http://127.0.0.1:8000';
const envPath = join(process.cwd(), '.env');

const app = express();
const angularApp = new AngularNodeAppEngine();
app.use(express.json());

function readEnvValue(name: string): string | undefined {
  if (!existsSync(envPath)) {
    return undefined;
  }

  const match = readFileSync(envPath, 'utf8').match(
    new RegExp(`^\\s*${name}\\s*=\\s*(.*)\\s*$`, 'm'),
  );

  if (!match) {
    return undefined;
  }

  const value = match[1].trim();
  return value.replace(/^["']|["']$/g, '');
}

app.get('/api/quotes/:symbol', async (req, res, next) => {
  const apiKey =
    process.env['MARKET_DATA_API_KEY'] ??
    process.env['ROCKET_TRADING_QUOTES_API_KEY'] ??
    process.env['X_API_KEY'] ??
    readEnvValue('MARKET_DATA_API_KEY') ??
    readEnvValue('ROCKET_TRADING_QUOTES_API_KEY') ??
    readEnvValue('X_API_KEY');

  if (!apiKey) {
    res.status(500).json({
      message: 'Missing market data API key.',
    });
    return;
  }

  try {
    const upstreamResponse = await fetch(
      `${quoteApiBaseUrl}/${encodeURIComponent(req.params.symbol)}`,
      {
        headers: {
          'X-Api-Key': apiKey,
        },
      },
    );

    const body = await upstreamResponse.text();

    res
      .status(upstreamResponse.status)
      .type(upstreamResponse.headers.get('content-type') ?? 'application/json')
      .send(body);
  } catch (error) {
    next(error);
  }
});

async function proxyAnalyticsRequest(
  req: express.Request,
  res: express.Response,
  next: express.NextFunction,
) {
  try {
    const baseUrl = analyticsApiBaseUrl.endsWith('/')
      ? analyticsApiBaseUrl
      : `${analyticsApiBaseUrl}/`;
    const upstreamUrl = new URL(req.originalUrl.replace(/^\/+/, ''), baseUrl);
    const hasBody = req.method !== 'GET' && req.method !== 'HEAD';

    const upstreamResponse = await fetch(upstreamUrl, {
      method: req.method,
      headers: hasBody ? { 'Content-Type': 'application/json' } : undefined,
      body: hasBody ? JSON.stringify(req.body) : undefined,
    });

    const body = await upstreamResponse.text();
    res
      .status(upstreamResponse.status)
      .type(upstreamResponse.headers.get('content-type') ?? 'application/json')
      .send(body);
  } catch (error) {
    next(error);
  }
}

app.get('/api/perpetual-futures/accounts', proxyAnalyticsRequest);
app.get('/api/perpetual-futures/markets/:marketSymbol', proxyAnalyticsRequest);
app.post('/api/perpetual-futures/orders', proxyAnalyticsRequest);

/**
 * Example Express Rest API endpoints can be defined here.
 * Uncomment and define endpoints as necessary.
 *
 * Example:
 * ```ts
 * app.get('/api/{*splat}', (req, res) => {
 *   // Handle API request
 * });
 * ```
 */

/**
 * Serve static files from /browser
 */
app.use(
  express.static(browserDistFolder, {
    maxAge: '1y',
    index: false,
    redirect: false,
  }),
);

/**
 * Handle all other requests by rendering the Angular application.
 */
app.use((req, res, next) => {
  angularApp
    .handle(req)
    .then((response) =>
      response ? writeResponseToNodeResponse(response, res) : next(),
    )
    .catch(next);
});

/**
 * Start the server if this module is the main entry point, or it is ran via PM2.
 * The server listens on the port defined by the `PORT` environment variable, or defaults to 4000.
 */
if (isMainModule(import.meta.url) || process.env['pm_id']) {
  const port = process.env['PORT'] || 4000;
  app.listen(port, (error) => {
    if (error) {
      throw error;
    }

    console.log(`Node Express server listening on http://localhost:${port}`);
  });
}

/**
 * Request handler used by the Angular CLI (for dev-server and during build) or Firebase Cloud Functions.
 */
export const reqHandler = createNodeRequestHandler(app);
