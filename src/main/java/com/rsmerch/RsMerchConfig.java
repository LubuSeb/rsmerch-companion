package com.rsmerch;

import net.runelite.client.config.*;

@ConfigGroup("rsmerch")
public interface RsMerchConfig extends Config {
    @ConfigItem(keyName="scannerFile", name="Scanner report", description="Local file for saved public market research", position=1, hidden=true)
    default String scannerFile() { return net.runelite.client.RuneLite.RUNELITE_DIR.toPath().resolve("rsmerch").resolve("market-scan-v1.json").toString(); }

    @ConfigItem(keyName="historyFile", name="Imported history", description="Full path to the normalized JSON produced by import_history.py", position=2)
    default String historyFile() { return ""; }

    @ConfigItem(keyName="planningCash", name="Planning cash (GP)", description="Assumption of free GP, excluding existing offers and stock. Not a bank balance.", position=3)
    @Range(min=0, max=Integer.MAX_VALUE)
    default int planningCash() { return 1_000_000; }

    @ConfigItem(keyName="maxStockDays", name="Stock review threshold", description="Flag recorded inventory above this many days of observed sales", position=4)
    @Range(min=1, max=90)
    default int maxStockDays() { return 7; }

    @ConfigItem(keyName="membersItems", name="Members items", description="Include members items in user-triggered market scans", position=5)
    default boolean membersItems() { return true; }

    @ConfigItem(keyName="wikiEnabled", name="Wiki market requests", description="Refresh and Scan send public price requests to prices.runescape.wiki. Scans request item IDs; no account identity, offers, history or holdings are uploaded.", position=6)
    default boolean wikiEnabled() { return true; }
}
