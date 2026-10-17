package net.machinemuse.powersuits.client;

import net.minecraft.network.INetworkManager;
import net.minecraft.network.NetLoginHandler;
import net.minecraft.network.packet.NetHandler;
import net.minecraft.network.packet.Packet1Login;
import net.minecraft.server.MinecraftServer;
import cpw.mods.fml.common.network.IConnectionHandler;
import cpw.mods.fml.common.network.Player;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Loads the custom keybinds when the client logs into a world. There is no FML
 * lifecycle event after options.txt has been applied to the mod keybindings,
 * so this is the first safe point that is still before the player can use them.
 *
 * In singleplayer the integrated server shares the NetworkRegistry, so the
 * server-side callbacks reach this handler too; they must stay no-ops.
 */
@SideOnly(Side.CLIENT)
public class KeybindConnectionHandler implements IConnectionHandler {

	@Override
	public void clientLoggedIn(NetHandler clientHandler, INetworkManager manager, Packet1Login login) {
		KeybindManager.loadKeybindsOnce();
	}

	@Override
	public void playerLoggedIn(Player player, NetHandler netHandler, INetworkManager manager) {
	}

	@Override
	public String connectionReceived(NetLoginHandler netHandler, INetworkManager manager) {
		return null;
	}

	@Override
	public void connectionOpened(NetHandler netClientHandler, String server, int port, INetworkManager manager) {
	}

	@Override
	public void connectionOpened(NetHandler netClientHandler, MinecraftServer server, INetworkManager manager) {
	}

	@Override
	public void connectionClosed(INetworkManager manager) {
	}

}
