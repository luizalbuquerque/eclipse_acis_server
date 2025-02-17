package net.sf.l2j.gameserver.model.actor.instance;

import net.sf.l2j.gameserver.enums.IntentionType;
import net.sf.l2j.gameserver.enums.SiegeSide;
import net.sf.l2j.gameserver.model.actor.Attackable;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.ai.type.CreatureAI;
import net.sf.l2j.gameserver.model.actor.ai.type.SiegeGuardAI;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.network.serverpackets.ActionFailed;
import net.sf.l2j.gameserver.network.serverpackets.MoveToPawn;

/**
 * This class represents all guards in the world.
 */
public final class SiegeGuard extends Attackable
{
	public SiegeGuard(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public CreatureAI getAI()
	{
		CreatureAI ai = _ai;
		if (ai == null)
		{
			synchronized (this)
			{
				ai = _ai;
				if (ai == null)
					_ai = ai = new SiegeGuardAI(this);
			}
		}
		return ai;
	}
	
	@Override
	public boolean isAutoAttackable(Creature attacker)
	{
		// Attackable during siege by all, except defenders.
		return (attacker != null && attacker.getActingPlayer() != null && getCastle() != null && getCastle().getSiege().isInProgress() && !getCastle().getSiege().checkSides(attacker.getActingPlayer().getClan(), SiegeSide.DEFENDER, SiegeSide.OWNER));
	}
	
	@Override
	public boolean hasRandomAnimation()
	{
		return false;
	}
	
	/**
	 * Note that super() is not called because guards need extra check to see if a player should interact or ATTACK them when clicked.
	 */
	@Override
	public void onAction(Player player)
	{
		// Set the target of the player
		if (player.getTarget() != this)
			player.setTarget(this);
		else
		{
			if (isAutoAttackable(player))
			{
				if (!isAlikeDead() && (Math.abs(player.getZ() - getZ()) < 600)) // this max heigth difference might need some tweaking
					player.getAI().setIntention(IntentionType.ATTACK, this);
			}
			else
			{
				// Notify the Player AI with INTERACT
				if (!canInteract(player))
					player.getAI().setIntention(IntentionType.INTERACT, this);
				else
				{
					// Stop moving if we're already in interact range.
					if (player.isMoving() || player.isInCombat())
						player.getAI().setIntention(IntentionType.IDLE);
					
					// Rotate the player to face the instance
					player.sendPacket(new MoveToPawn(player, this, Npc.INTERACTION_DISTANCE));
					
					// Send ActionFailed to the player in order to avoid he stucks
					player.sendPacket(ActionFailed.STATIC_PACKET);
				}
			}
		}
	}
	
	@Override
	public void addDamageHate(Creature attacker, int damage, int aggro)
	{
		// Can't add friendly Guard as attacker.
		if (attacker instanceof SiegeGuard)
			return;
		
		super.addDamageHate(attacker, damage, aggro);
	}
	
	@Override
	public void reduceHate(Creature target, int amount)
	{
		stopHating(target);
		setTarget(null);
		getAI().setIntention(IntentionType.ACTIVE);
	}
	
	@Override
	public boolean isGuard()
	{
		return true;
	}
	
	@Override
	public int getDriftRange()
	{
		return 20;
	}
}