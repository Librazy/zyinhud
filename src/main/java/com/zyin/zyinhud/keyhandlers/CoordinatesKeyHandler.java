package com.zyin.zyinhud.keyhandlers;

import net.minecraft.client.gui.GuiChat;

import org.lwjgl.input.Keyboard;

import com.zyin.zyinhud.ZyinHUDKeyHandlers;
import com.zyin.zyinhud.mods.Coordinates;

import net.minecraftforge.fml.common.gameevent.InputEvent.KeyInputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;

/**
 * The type Coordinates key handler.
 */
public class CoordinatesKeyHandler implements ZyinHUDKeyHandlerBase
{
	/**
	 * The constant HotkeyDescription.
	 */
	public static final String HotkeyDescription = "key.zyinhud.coordinates";

	/**
	 * Pressed.
	 *
	 * @param event the event
	 */
	public static void Pressed(KeyInputEvent event)
	{
		Coordinates.PasteCoordinatesIntoChat();
	}
	

    
    private static boolean keyDown = false;

	/**
	 * Client tick event.
	 *
	 * @param event the event
	 */
	public static void ClientTickEvent(ClientTickEvent event)
    {
		if(mc.currentScreen != null && mc.currentScreen instanceof GuiChat)
    	{
			if(Keyboard.getEventKey() == ZyinHUDKeyHandlers.KEY_BINDINGS[1].getKeyCode())
    		{
    			if(Keyboard.getEventKeyState())
    			{
    				if(keyDown == false)
    					OnKeyDown();
    	            keyDown = true;
    	        }
    	        else
    	        {
    				//if(keyDown == true)
    					//OnKeyUp();
    	            keyDown = false;
    	        }
    		}
    		
    	}
    }

	private static void OnKeyDown()
	{
        Pressed(null);
	}
}