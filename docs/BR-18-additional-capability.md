# BR-18: the additional capability — client watchlist

BR-18 asks the delivery team to build one capability beyond the core platform and to say why it was
chosen over the alternatives. Ours is the **client watchlist**: a client saves the instruments they
follow and sees each one's live price and day move on their dashboard, one click from the trade ticket.

## Why the watchlist

The BRD's client is Joanna (section 6): she checks positions in the evening and around news events,
wants to place a trade in under a minute, and has no patience for jargon or multi-step flows.

- **It shortens the path to a trade.** Without it, every trade starts with a symbol search. With it,
  the instruments Joanna cares about are already on her dashboard, priced, and one click from the
  ticket. That is the "trade in under a minute" objective (section 3) made concrete.
- **It covers what she does between trades.** Most visits are checking, not trading. The watchlist
  makes those visits useful ("how is the thing I'm thinking about buying doing?") and brings her
  back to the platform rather than to a third-party price site.
- **It uses only what the platform already trusts.** Prices come from the same quote service that
  prices orders (BR-08/BR-13), so what she watches is what she would pay. No new data source, no
  new regulatory surface.
- **It is private by construction.** A watchlist is per client and covered by the same ownership
  rules as positions and orders (BR-02, tested in `TradingIntegrationIT.clientsKeepAPrivateWatchlist`).
- **It is small and extendable.** One table (V8), one controller, one dashboard panel. Price alerts
  and news would build directly on it (section 3: "a platform the firm can extend").

## Options considered and not chosen

| Option | Why not, for this phase |
| --- | --- |
| Price alerts (email/push) | Needs a notification channel the BRD puts out of scope (no mobile apps; no messaging integration). A natural next step that builds on the watchlist. |
| Recurring investments | Unattended trades on a schedule: needs standing instructions, reservation of cash and a cancel flow. Too much new risk surface for one capability. |
| Portfolio performance analytics | Valuable, but time-weighted returns need cash-flow history the platform does not model yet (payments are out of scope). |
| Paper-trading mode | Duplicates the practice-cash model the platform already uses in this phase. |
| Leveraged perpetual futures | Margin, funding and liquidation are the jargon Joanna wants to avoid; leveraged derivatives also raise suitability questions for Risk and Compliance before retail launch. |

## Note on perpetual futures

`main` also has an isolated-margin perpetual futures page (`/perps`). It is not our BR-18 capability.
Section 8.4 lists equities, FX and crypto as the launch instrument classes; leveraged crypto
derivatives are outside that list. We suggest keeping perps behind an internal flag until the
Programme Sponsor and Risk and Compliance agree they belong in scope (section 10: "flag explicitly
where it needs more detail").
