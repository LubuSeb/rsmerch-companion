package com.rsmerch;

import java.lang.reflect.Field;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import org.junit.Test;
import static org.junit.Assert.*;

public class LifecycleTest {
    @Test public void ordinaryRegionLoadingDoesNotBreakCaptureButLogoutDoes() throws Exception {
        RsMerchPlugin p=new RsMerchPlugin();
        Field key=RsMerchPlugin.class.getDeclaredField("activeKey"); key.setAccessible(true); key.set(p,"account");
        GameStateChanged loading=new GameStateChanged(); loading.setGameState(GameState.LOADING);
        p.onGameStateChanged(loading); assertEquals("account",key.get(p));
        GameStateChanged logout=new GameStateChanged(); logout.setGameState(GameState.LOGIN_SCREEN);
        p.onGameStateChanged(logout); assertNull(key.get(p));
    }
}
