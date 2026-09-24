# RSMerch Companion

A local Grand Exchange journal and public-market research sidebar for RuneLite.

**Plugin Hub status: not yet approved.** Publication of this repository is not an endorsement by RuneLite or Jagex. The plugin does not place, edit, cancel or collect offers, click game interfaces, move the player or automate gameplay.

## Features

- **Desk:** recorded stock, known acquisition costs, estimated FIFO profit, opening-stock reconciliation and separately recorded gathered items.
- **Offers:** observed GE slots alongside Wiki last instant-buy and instant-sell trades, each with its source timestamp; recent hourly averages, reported volume and chart links. Refresh prices explicitly with the button.
- **Finds:** a user-triggered Java scan of mapped items, 24 complete hourly windows and up to 70 candidate histories. Results show estimated margins after tax, historical price charts and small-test ceilings. No background scans run.
- **History:** recent observed fills, an archive of the connected account's existing RuneLite GE records, and snapshots captured when the user opens the in-game GE History screen. Search, archive paging, notes and separate live/history CSV exports are included.

## History and accounting

The plugin records new observed changes while connected. Fills already present at login are saved as baselines and are not counted as new executions. Offline fills, bank transfers, gifts and consumption need reconciliation.

Recovered history is evidence, not an opening inventory balance. RuneLite records have rounded-down gross average prices and observation timestamps. The in-game history shows totals and sometimes tax but no trade dates. Its snapshots can overlap. Recovered records do not alter live stock or profit; possible-overlap labels are heuristic and not exhaustive.

Use **Add stock** to record current total holdings and their known average purchase cost. Leave cost blank when unknown. Unknown cost is never treated as zero profit cost. **Collected** explicitly adds gathered stock with zero GP acquisition cost; time and travel costs are not included.

Public Wiki prints are completed reported transactions, not an order book, executable offers or guaranteed fills. Your own trades may be in that data. Scanner quantities are independent ceilings based on a planning budget, item limits and a hypothetical share of reported flow; they are not a basket allocation or a fill-time forecast. Tax is estimated where exact execution details are unavailable.

## Privacy and network access

Local account journals and archives are stored under `.runelite/rsmerch/<derived-account-key>/`. The account key is derived from the client account hash. The plugin reads matching RuneLite profile identity/trade-history settings through ConfigManager and already-loaded GE History widgets. It does not read login credentials.

**Refresh Wiki prices** and **Scan market** request public data from `https://prices.runescape.wiki/api/v1/osrs/` using an identifying User-Agent. Refresh uses bulk prices. Scanning also sends public candidate item IDs in history requests. The Wiki receives ordinary connection information such as your IP address. Account identity, offers, holdings and personal history are not uploaded. Disable **Wiki market requests** in plugin settings to prevent these requests. Wiki chart buttons open the selected public item page in your browser.

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

`preview` renders real Swing components with synthetic data. `scanSmoke` and `wikiSmoke` are optional, explicit live-network checks and write public-market output only beneath `build/`. Unit tests require no game login or live Wiki requests.

## Limitations and review

The present UI compares offers with observed public prices. A personalised price recommendation model is not implemented. It cannot determine queue position, true available supply, remaining account buy allowance, or a guaranteed optimal price.

All gameplay decisions remain with the player. Rule compliance and acceptance remain subject to [Jagex's guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1) and [RuneLite's review](https://github.com/runelite/runelite/wiki/Plugin-Hub-Review).

Licensed under BSD-2-Clause. See [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md).
