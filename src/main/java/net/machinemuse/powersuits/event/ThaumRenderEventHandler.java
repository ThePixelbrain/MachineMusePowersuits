package net.machinemuse.powersuits.event;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumSet;

import net.machinemuse.api.IModularItem;
import net.machinemuse.api.MuseCommonStrings;
import net.machinemuse.api.MuseItemUtils;
import net.machinemuse.powersuits.common.MuseLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet250CustomPayload;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.ForgeDirection;
import net.minecraftforge.event.ForgeSubscribe;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.TickType;
import cpw.mods.fml.common.network.IPacketHandler;
import cpw.mods.fml.common.network.Player;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Makes the Maigcal Sense module show everything the Thaumcraft goggles show. Each
 * piece mirrors where and how Thaumcraft does it for its own goggles:
 * <ul>
 * <li>aura nodes: drawn in the world (RenderWorldLastEvent)</li>
 * <li>aura HUD: drawn as a screen overlay after the frame (RENDER tick). The
 * HUD code clears the depth buffer and replaces the projection matrix without
 * restoring it, so running it during world rendering breaks every
 * RenderWorldLastEvent listener after ours (e.g. NEI's F7 overlay).</li>
 * <li>essentia labels on jars, alembics and crucibles (DrawBlockHighlightEvent)</li>
 * <li>sparkles for aura transfers between nodes (Thaumcraft's "TCF" packet 21,
 * which Thaumcraft's own client handler ignores unless goggles are worn)</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public class ThaumRenderEventHandler implements ITickHandler, IPacketHandler {
	public static final String THAUMCRAFT_FX_CHANNEL = "TCF";
	private static final byte AURA_TRANSFER_FX_PACKET = 21;
	// Width/brightness argument Thaumcraft passes to drawTagOnJar
	private static final int TAG_BRIGHTNESS = 220;

	// Aura nodes and HUD
	private Method thaumGogglesHUDRenderer;
	private Method thaumAuraWorldRenderer;
	private Object thaumcraftRenderEventHandler;
	private Object thaumcraftGUITicker;
	private boolean reflectionFailed = false;

	// Essentia labels and aura transfer sparkles
	private Method drawTagOnJar;
	private Field tagScale;
	private Class tileJarFillable;
	private Field jarAmount;
	private Field jarTag;
	private Class tileAlembic;
	private Field alembicAmount;
	private Field alembicTag;
	private Class tileCrucible;
	private Field crucibleTags;
	private Constructor objectTagsConstructor;
	private Method objectTagsAdd;
	private Method objectTagsSize;
	private Constructor sparkleConstructor;
	private Field sparkleSlowdown;
	private Field sparkleLeyLineEffect;
	private Field sparkleShrink;
	private boolean extrasLoaded = false;
	private boolean extrasReflectionFailed = false;

	@ForgeSubscribe
	public void renderLast(RenderWorldLastEvent event) {
		EntityPlayer player = Minecraft.getMinecraft().thePlayer;
		if (hasMagicalSense(player) && loadThaumcraftRenderers()) {
			try {
				// Same GL state handling as Thaumcraft's own RenderEventHandler.renderLast
				GL11.glPushMatrix();
				GL11.glDepthMask(false);
				GL11.glEnable(GL11.GL_BLEND);
				GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
				GL11.glDisable(GL11.GL_CULL_FACE);
				GL11.glDisable(GL11.GL_LIGHTING);
				thaumAuraWorldRenderer.invoke(thaumcraftRenderEventHandler, event, event.partialTicks, player,
						Minecraft.getMinecraft().theWorld.getWorldTime());
				GL11.glDisable(GL11.GL_BLEND);
				GL11.glEnable(GL11.GL_LIGHTING);
				GL11.glDepthMask(true);
				GL11.glPopMatrix();
			} catch (Exception e) {
				MuseLogger.logError("Problem rendering Thaumcraft aura nodes");
				e.printStackTrace();
			}
		}
	}

	/**
	 * Mirrors the goggles branch of Thaumcraft's RenderEventHandler.blockHighlight.
	 * Thaumcraft's own renderLast fades tagscale back out every frame, so only
	 * the fade-in has to be done here.
	 */
	@ForgeSubscribe
	public void blockHighlight(DrawBlockHighlightEvent event) {
		if (!hasMagicalSense(event.player) || !loadThaumcraftRenderers() || !loadThaumcraftExtras()) {
			return;
		}
		try {
			MovingObjectPosition target = event.target;
			TileEntity te = event.player.worldObj.getBlockTileEntity(target.blockX, target.blockY, target.blockZ);
			if (te == null) {
				return;
			}
			Object tags;
			double y;
			ForgeDirection side;
			if (tileJarFillable.isInstance(te) && jarAmount.getInt(te) > 0) {
				tags = newObjectTags(jarTag.get(te), jarAmount.getInt(te));
				y = target.blockY;
				side = ForgeDirection.getOrientation(target.sideHit);
			} else if (tileAlembic.isInstance(te) && alembicAmount.getInt(te) > 0) {
				tags = newObjectTags(alembicTag.get(te), alembicAmount.getInt(te));
				y = target.blockY + 0.4F;
				side = ForgeDirection.UP;
			} else if (tileCrucible.isInstance(te) && (Integer) objectTagsSize.invoke(crucibleTags.get(te)) > 0) {
				tags = crucibleTags.get(te);
				y = target.blockY + 0.4F;
				side = ForgeDirection.UP;
			} else {
				return;
			}
			float scale = tagScale.getFloat(null);
			if (scale < 0.3F) {
				tagScale.setFloat(null, scale + (0.031F - scale / 10.0F));
			}
			drawTagOnJar.invoke(thaumcraftRenderEventHandler, (double) target.blockX, y, (double) target.blockZ, tags, TAG_BRIGHTNESS, side,
					event.partialTicks);
		} catch (Exception e) {
			MuseLogger.logError("Problem rendering Thaumcraft essentia tags");
			e.printStackTrace();
		}
	}

	private Object newObjectTags(Object enumTag, int amount) throws Exception {
		Object tags = objectTagsConstructor.newInstance();
		objectTagsAdd.invoke(tags, enumTag, amount);
		return tags;
	}

	/**
	 * Mirrors Thaumcraft's PacketHandler.handleAuraTransferFXPacket, which only
	 * spawns the sparkle when the player wears goggles.
	 */
	@Override
	public void onPacketData(INetworkManager manager, Packet250CustomPayload packet, Player player) {
		if (packet.data == null || packet.data.length < 25 || packet.data[0] != AURA_TRANSFER_FX_PACKET) {
			return;
		}
		Minecraft mc = Minecraft.getMinecraft();
		if (!hasMagicalSense(mc.thePlayer) || mc.theWorld == null || !loadThaumcraftExtras()) {
			return;
		}
		try {
			DataInputStream data = new DataInputStream(new ByteArrayInputStream(packet.data, 1, packet.data.length - 1));
			float x = data.readFloat();
			float y = data.readFloat();
			float z = data.readFloat();
			float targetX = data.readFloat();
			float targetY = data.readFloat();
			float targetZ = data.readFloat();
			float dx = x - targetX;
			float dy = y - targetY;
			float dz = z - targetZ;
			int distance = (int) MathHelper.sqrt_double(dx * dx + dy * dy + dz * dz);
			EntityFX sparkle = (EntityFX) sparkleConstructor.newInstance(mc.theWorld, (double) x, (double) y, (double) z,
					(double) targetX, (double) targetY, (double) targetZ, 2.5F, 0, distance / 2);
			sparkleSlowdown.setBoolean(sparkle, false);
			sparkle.noClip = true;
			sparkleLeyLineEffect.setBoolean(sparkle, true);
			sparkleShrink.setBoolean(sparkle, false);
			mc.effectRenderer.addEffect(sparkle);
		} catch (Exception e) {
			MuseLogger.logError("Problem spawning Thaumcraft aura transfer effect");
			e.printStackTrace();
		}
	}

	@Override
	public void tickStart(EnumSet<TickType> type, Object... tickData) {
	}

	@Override
	public void tickEnd(EnumSet<TickType> type, Object... tickData) {
		Minecraft mc = Minecraft.getMinecraft();
		EntityPlayer player = mc.thePlayer;
		// Same conditions as Thaumcraft's GUITicker uses for its goggles HUD
		if (mc.inGameHasFocus && Minecraft.isGuiEnabled() && hasMagicalSense(player) && loadThaumcraftRenderers()) {
			try {
				thaumGogglesHUDRenderer.invoke(thaumcraftGUITicker, (Float) tickData[0], player, System.currentTimeMillis());
			} catch (Exception e) {
				MuseLogger.logError("Problem rendering Thaumcraft goggles HUD");
				e.printStackTrace();
			}
		}
	}

	@Override
	public EnumSet<TickType> ticks() {
		return EnumSet.of(TickType.RENDER);
	}

	@Override
	public String getLabel() {
		return "MMMPS: Thaumcraft Goggles Render";
	}

	private boolean hasMagicalSense(EntityPlayer player) {
		if (player == null) {
			return false;
		}
		ItemStack helmet = player.inventory.armorItemInSlot(3);
		return helmet != null && helmet.getItem() instanceof IModularItem && MuseItemUtils.itemHasModule(helmet, MuseCommonStrings.MODULE_MAGICAL_SENSE);
	}

	private boolean loadThaumcraftRenderers() {
		if (thaumcraftRenderEventHandler != null) {
			return true;
		}
		if (reflectionFailed) {
			return false;
		}
		try {
			Class eventHandler = Class.forName("thaumcraft.client.RenderEventHandler");
			Class guiTicker = Class.forName("thaumcraft.client.GUITicker");
			thaumAuraWorldRenderer = eventHandler.getMethod("renderAuraNodes", RenderWorldLastEvent.class, float.class, EntityPlayer.class,
					long.class);
			thaumGogglesHUDRenderer = guiTicker.getMethod("renderGogglesHUD", float.class, EntityPlayer.class, long.class);
			thaumcraftGUITicker = guiTicker.getConstructor().newInstance();
			thaumcraftRenderEventHandler = eventHandler.getConstructor().newInstance();
			return true;
		} catch (Exception e) {
			reflectionFailed = true;
			MuseLogger.logError("Problem loading Thaumcraft classes to display HUD");
			e.printStackTrace();
			return false;
		}
	}

	private boolean loadThaumcraftExtras() {
		if (extrasLoaded) {
			return true;
		}
		if (extrasReflectionFailed) {
			return false;
		}
		try {
			Class eventHandler = Class.forName("thaumcraft.client.RenderEventHandler");
			Class objectTags = Class.forName("thaumcraft.api.ObjectTags");
			Class enumTag = Class.forName("thaumcraft.api.EnumTag");
			drawTagOnJar = eventHandler.getMethod("drawTagOnJar", double.class, double.class, double.class, objectTags, int.class,
					ForgeDirection.class, float.class);
			tagScale = eventHandler.getField("tagscale");
			tileJarFillable = Class.forName("thaumcraft.common.blocks.jars.TileJarFillable");
			jarAmount = tileJarFillable.getField("amount");
			jarTag = tileJarFillable.getField("tag");
			tileAlembic = Class.forName("thaumcraft.common.blocks.TileAlembic");
			alembicAmount = tileAlembic.getField("amount");
			alembicTag = tileAlembic.getField("tag");
			tileCrucible = Class.forName("thaumcraft.common.blocks.TileCrucible");
			crucibleTags = tileCrucible.getField("tags");
			objectTagsConstructor = objectTags.getConstructor();
			objectTagsAdd = objectTags.getMethod("add", enumTag, int.class);
			objectTagsSize = objectTags.getMethod("size");
			Class sparkle = Class.forName("thaumcraft.client.fx.FXSparkle");
			sparkleConstructor = sparkle.getConstructor(World.class, double.class, double.class, double.class, double.class, double.class,
					double.class, float.class, int.class, int.class);
			sparkleSlowdown = sparkle.getField("slowdown");
			sparkleLeyLineEffect = sparkle.getField("leyLineEffect");
			sparkleShrink = sparkle.getField("shrink");
			extrasLoaded = true;
			return true;
		} catch (Exception e) {
			extrasReflectionFailed = true;
			MuseLogger.logError("Problem loading Thaumcraft classes to display essentia tags and aura effects");
			e.printStackTrace();
			return false;
		}
	}
}
