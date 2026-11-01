package net.machinemuse.powersuits.client;

import java.util.EnumSet;

import net.machinemuse.powersuits.common.ModularPowersuits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.world.World;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import cpw.mods.fml.client.registry.KeyBindingRegistry.KeyHandler;
import cpw.mods.fml.common.TickType;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class KeybindKeyHandler extends KeyHandler {
	public static KeyBinding openKeybindGUI = new KeyBinding("Open Muse Keybind GUI", Keyboard.KEY_K);
	public static KeyBinding flightControlDown = new KeyBinding("Flight Control Down", Keyboard.KEY_Z);

	public KeybindKeyHandler() {
		super(new KeyBinding[] { openKeybindGUI, flightControlDown }, new boolean[] { false, false });
	}

	/**
	 * Polled rather than read from KeyBinding.pressed, which only tracks one
	 * binding per key code and would go dead if another mod shares the key.
	 * Negative key codes are mouse buttons, same as in FML's KeyHandler.
	 */
	public static boolean isFlightControlDownHeld() {
		int keyCode = flightControlDown.keyCode;
		return keyCode < 0 ? Mouse.isButtonDown(keyCode + 100) : Keyboard.isKeyDown(keyCode);
	}

	@Override
	public String getLabel() {
		return "machineMuseKeybinds";
	}

	@Override
	public void keyDown(EnumSet<TickType> types, KeyBinding kb, boolean tickEnd, boolean isRepeat) {
		if (kb.equals(openKeybindGUI)) {
			EntityClientPlayerMP player = Minecraft.getMinecraft().thePlayer;
			World world = Minecraft.getMinecraft().theWorld;
			if (Minecraft.getMinecraft().inGameHasFocus) {
				player.openGui(ModularPowersuits.instance, 1, world, 0, 0, 0);
			}
		}
	}

	@Override
	public void keyUp(EnumSet<TickType> types, KeyBinding kb, boolean tickEnd) {
		// TODO Auto-generated method stub

	}

	public void addKeybind(KeyBinding kb) {

	}

	@Override
	public EnumSet<TickType> ticks() {
		return EnumSet.of(TickType.CLIENT);
	}

}
