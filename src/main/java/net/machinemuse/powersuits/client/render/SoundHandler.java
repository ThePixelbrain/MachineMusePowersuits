package net.machinemuse.powersuits.client.render;

import net.machinemuse.powersuits.common.Config;
import net.minecraft.client.audio.SoundManager;
import net.minecraft.entity.Entity;

public class SoundHandler {
	private SoundManager sndManager;

	public SoundHandler(SoundManager sndManager) {
		this.sndManager = sndManager;
	}

	public void playSound(String soundName, float x, float y, float z, float volume, float pitch) {
		if (Config.isSoundEnabled()) {
			sndManager.playSound(soundName, x, y, z, volume, pitch);
		}
	}

	public void playSoundFX(String soundName, float volume, float pitch) {
		if (Config.isSoundEnabled()) {
			sndManager.playSoundFX(soundName, volume, pitch);
		}
	}

	public void playEntitySound(String soundName, Entity entity, float volume, float pitch, boolean repeat) {
		if (Config.isSoundEnabled()) {
			sndManager.playEntitySound(soundName, entity, volume, pitch, repeat);
		}
	}

	public void stopEntitySound(Entity entity) {
		sndManager.stopEntitySound(entity);
	}
}
