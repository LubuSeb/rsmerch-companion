# RSMerch Companion

A local Grand Exchange journal and public-market research sidebar for RuneLite.

**Plugin Hub status: not yet approved.** Publication of this repository is not an endorsement by RuneLite or Jagex. The plugin does not place, edit, cancel or collect offers, click game interfaces, move the player or automate gameplay.

## Features

- **Results:** profit leaderboard and progress, defaulting to recovered history when available. Use the period selector and **Filters** for source and sort; **Details** opens each item's buys, sells and returns. The **Trading value** view uses current recorded holdings.
- **Stock:** quantity, known cost and break-even price, with editing and gathered-stock entry.
- **Offers:** your price and fills, Wiki instant-buy/sell prices, and a suggested range after manual **Analyse**. Stale data and below-cost ranges are flagged; evidence, volume and copyable guide prices are in **Details**.
- **Finds:** manual market scan, after-tax margins, copyable prices, reported volume and test-size ceilings. Search/sort controls are under **Filters**, and charts and limits under **Details**.
- **History:** observed fills, recovered RuneLite records and GE History captures. Includes item search, archive paging, notes and separate CSV exports. Totals, tax and source information are in **Details**.

The sidebar keeps primary figures visible and puts explanations behind explicit detail controls. All requests and offer decisions are manual; no background scans run.

## History and accounting

The plugin records new observed changes while connected. Fills already present at login are saved as baselines and are not counted as new executions. Offline fills, bank transfers, gifts and consumption need reconciliation.

Recovered history is evidence, not an opening inventory balance. RuneLite records have rounded-down gross average prices and observation timestamps. The in-game history shows totals and sometimes tax but no trade dates. Its snapshots can overlap. Recovered records do not alter live stock or profit; possible-overlap labels are heuristic and not exhaustive.

Results can estimate FIFO outcomes within the deduplicated RuneLite archive, using recorded order and estimated historical tax. Purchases at the exact same recorded timestamp as a sale are not assumed to precede it. Missing trades, rounded prices and observation times can change this estimate. GE screen captures are excluded from performance aggregation because they can overlap and lack dates. Neither source is presented as lifetime profit. A period filter uses prior purchase costs when matching sales inside the selected period.

**Trading value** values recorded units at fresh Wiki instant-sell references after estimated tax. Missing-price units are excluded explicitly; unknown-cost units do not create unrealised profit. To save total trading value, first reconcile all holdings, then enter coins outside the GE and confirm the stock balance. The snapshot adds that cash to recorded stock value and coins still inside observed GE offers. It is a hypothetical valuation, not guaranteed liquidation proceeds. The scanner budget is never used as cash. Changes between saved snapshots include deposits, withdrawals and reconciliation, not only trading returns. Value snapshots are compatible journal notes and export to a separate CSV.

Use **Add stock** to record current total holdings and their known average purchase cost. Leave cost blank when unknown. Unknown cost is never treated as zero profit cost. **Collected** explicitly adds gathered stock with zero GP acquisition cost; time and travel costs are not included.

Public Wiki prints are completed reported transactions, not an order book, executable offers or guaranteed fills. Your own trades may be in that data. Scanner quantities are independent ceilings based on a planning budget, item limits and a hypothetical share of reported flow; they are not a basket allocation or a fill-time forecast. Tax is estimated where exact execution details are unavailable.

## Price-guidance method

The patient sell range is the median to upper quartile of active completed hourly buyer prices in the last seven days, with equal weight per hour. It requires at least 12 active hours across three UTC dates and activity in the last day. If six or more recent active hours show a median decline exceeding 15%, the range switches to the last day's distribution. The passive bid range uses the lower quartile to median of recent seller prices when there are enough observations. The faster-exit reference is the latest fresh instant-sell print.

Ranges are experiments, not confidence intervals or fill-time forecasts. Stale quotes/cache and request errors disable ask-price copying. A conservative spread that is non-positive after tax warns against new buys. Personal fills and known costs provide context; public history may include those same fills, so it is not independent confirmation. The model does not infer market control or other players' inventories.

## Privacy and network access

Local account journals and archives are stored under `.runelite/rsmerch/<derived-account-key>/`. The account key is derived from the client account hash. The plugin reads matching RuneLite profile identity/trade-history settings through ConfigManager and already-loaded GE History widgets. It does not read login credentials.

**Refresh Wiki prices**, **Analyse** and **Scan market** request public data from `https://prices.runescape.wiki/api/v1/osrs/` using an identifying User-Agent. Refresh uses bulk prices. Analysis and scanning also send public item IDs in history requests. The Wiki receives ordinary connection information such as your IP address. Account identity, offers, holdings and personal history are not uploaded. Disable **Wiki market requests** in plugin settings to prevent these requests. Wiki chart buttons open the selected public item page in your browser.

Network requests run off the game and Swing threads, have timeouts, and are cached and spaced. Scans retain the last usable report on failure. The plugin runs no external program, downloads no executable code, and contains no AI service integration.

The append-only journal has an exclusive writer lock. Malformed records stop recording rather than being silently replaced. History snapshots are content-addressed and account-validated. Preserve backups before a data migration. CSV history exports can include overlapping observations and must not be summed as a unique execution ledger.

## Development

Requires Java 11. This version was tested against RuneLite 1.12.39.

```sh
./gradlew test
./gradlew run
./gradlew preview
```

Jagex-account development login is described in [RuneLite's own guide](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts). Never commit or share login credentials or personal RuneLite profile files.

`preview` renders real Swing components with synthetic data. `scanSmoke`, `wikiSmoke` and `priceSmoke` are optional, explicit live-network checks and write public-market output only beneath `build/`. Unit tests require no game login or live Wiki requests. Tests run locally; this repository has no GitHub Actions workflow. RuneLite's required Plugin Hub checks run on its own repository.

## Limitations and review

The price guidance is an explainable historical model with personal cost/fill context. It cannot determine queue position, true available supply, remaining account buy allowance, or a guaranteed optimal price. Recovered and live performance remain separate; unknown costs and observation gaps prevent an exact lifetime-profit claim.

All gameplay decisions remain with the player. Rule compliance and acceptance remain subject to [Jagex's guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1) and [RuneLite's review](https://github.com/runelite/runelite/wiki/Plugin-Hub-Review).

Licensed under BSD-2-Clause. See [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md).
