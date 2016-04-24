package com.zyin.zyinhud.keyhandlers;

import net.minecraftforge.fml.common.gameevent.InputEvent.KeyInputEvent;

import org.lwjgl.input.Keyboard;

import com.zyin.zyinhud.ZyinHUDRenderer;
import com.zyin.zyinhud.ZyinHUDSound;
import com.zyin.zyinhud.mods.SafeOverlay;
import com.zyin.zyinhud.mods.SafeOverlay.Modes;
import com.zyin.zyinhud.util.Localization;

/**
 * The type Safe overlay key handler.
 */
public class SafeOverlayKeyHandler implements ZyinHUDKeyHandlerBase
{
    /**
     * The constant HotkeyDescription.
     */
    public static final String HotkeyDescription = "key.zyinhud.safeoverlay";

    /**
     * Pressed.
     *
     * @param event the event
     */
    public static void Pressed(KeyInputEvent event) {
        if (mc.currentScreen != null)
        {
            return;    //don't activate if the user is looking at a GUI
        }
        
        if(!SafeOverlay.Enabled)
        	return;

        //if "+" is pressed, increase the draw distance
        if (Keyboard.isKeyDown(Keyboard.KEY_EQUALS) || 	//keyboard "+" ("=")
                Keyboard.isKeyDown(Keyboard.KEY_ADD))	//numpad "+"
        {
            int drawDistance = SafeOverlay.instance.IncreaseDrawDistance();

            if (drawDistance == SafeOverlay.maxDrawDistance)
            {
            	ZyinHUDRenderer.DisplayNotification(Localization.get("safeoverlay.distance") + " " + drawDistance + " ("+Localization.get("safeoverlay.distance.max")+")");
            }
            else
            {
            	ZyinHUDRenderer.DisplayNotification(Localization.get("safeoverlay.distance") + " " + drawDistance);
            }

            return;
        }

        //if "-" is pressed, decrease the draw distance
        if (Keyboard.isKeyDown(Keyboard.KEY_MINUS))
        {
            int drawDistance = SafeOverlay.instance.DecreaseDrawDistance();
            ZyinHUDRenderer.DisplayNotification(Localization.get("safeoverlay.distance") + " " + drawDistance);
            
            return;
        }

        //if "0" is pressed, set to the default draw distance
        if (Keyboard.isKeyDown(Keyboard.KEY_0))
        {
            int drawDistance = SafeOverlay.instance.SetDrawDistance(SafeOverlay.defaultDrawDistance);
            SafeOverlay.instance.SetSeeUnsafePositionsThroughWalls(false);
            ZyinHUDRenderer.DisplayNotification(Localization.get("safeoverlay.distance") + " " + Localization.get("safeoverlay.distance.default") + " (" + drawDistance + ")");
            
        	ZyinHUDSound.PlayButtonPress();
            return;
        }
        
        //if Control is pressed, enable see through mode
        if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)
                || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL))
        {
            boolean seeThroughWalls = SafeOverlay.instance.ToggleSeeUnsafePositionsThroughWalls();

            if (seeThroughWalls)
            {
            	ZyinHUDRenderer.DisplayNotification(Localization.get("safeoverlay.seethroughwallsenabled"));
            }
            else
            {
            	ZyinHUDRenderer.DisplayNotification(Localization.get("safeoverlay.seethroughwallsdisabled"));
            }

        	ZyinHUDSound.PlayButtonPress();
            return;
        }
        
        //if nothing is pressed, do the default behavior
        
        SafeOverlay.Modes.ToggleMode();
    	ZyinHUDSound.PlayButtonPress();
	}
	
}