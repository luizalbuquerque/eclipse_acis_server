package net.sf.l2j.gameserver.taskmanager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.sf.l2j.commons.concurrent.ThreadPool;

import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Player;

import Dev.StartPlayer.StartupManager;

/**
 * @author Baggos
 */
public final class ArmorTaskManager implements Runnable
{
	private final Map<Player, Long> _players = new ConcurrentHashMap<>();
	
	protected ArmorTaskManager()
	{
		// Run task each 10 second.
		ThreadPool.scheduleAtFixedRate(this, 1000, 1000);
	}
	
	public final void add(Player player)
	{
		_players.put(player, System.currentTimeMillis());
	}
	
	public final void remove(Creature player)
	{
		_players.remove(player);
	}
	
	@Override
	public final void run()
	{
		if (_players.isEmpty())
			return;
		
		for (Map.Entry<Player, Long> entry : _players.entrySet())
		{
			final Player player = entry.getKey();
			
			if (player.getMemos().getLong("equipEndTime") < System.currentTimeMillis())
			{
				StartupManager.removeEquip(player);
				remove(player);
			}
		}
	}
	
	public static final ArmorTaskManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private static class SingletonHolder
	{
		protected static final ArmorTaskManager _instance = new ArmorTaskManager();
	}
}