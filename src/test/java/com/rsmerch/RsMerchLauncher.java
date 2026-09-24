package com.rsmerch;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public final class RsMerchLauncher {
    public static void main(String[] args) throws Exception {
        ExternalPluginManager.loadBuiltin(RsMerchPlugin.class);
        RuneLite.main(args);
    }
}
