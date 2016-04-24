package com.zyin.zyinhud.keyhandlers;

import org.lwjgl.input.Keyboard;

import com.zyin.zyinhud.ZyinHUDSound;
import com.zyin.zyinhud.gui.GuiZyinHUDOptions;

import net.minecraftforge.fml.common.gameevent.InputEvent.KeyInputEvent;

/**
 * The type Zyin hud options key handler.
 */
public class ZyinHUDOptionsKeyHandler implements ZyinHUDKeyHandlerBase
{
    /**
     * The constant HotkeyDescription.
     */
    public static final String HotkeyDescription = "key.zyinhud.zyinhudoptions";

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
        

        //if "Ctrl" and "Alt" is pressed
        if ((Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) &&
            (Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU)) )
        {
            //display the GUI
            mc.displayGuiScreen(new GuiZyinHUDOptions(null));
        	ZyinHUDSound.PlayButtonPress();
        }
	}
}