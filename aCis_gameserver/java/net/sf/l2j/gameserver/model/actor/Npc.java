package net.sf.l2j.gameserver.model.actor;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.sf.l2j.commons.concurrent.ThreadPool;
import net.sf.l2j.commons.lang.StringUtil;
import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.data.SkillTable.FrequentSkill;
import net.sf.l2j.gameserver.data.cache.HtmCache;
import net.sf.l2j.gameserver.data.manager.DimensionalRiftManager;
import net.sf.l2j.gameserver.data.manager.LotteryManager;
import net.sf.l2j.gameserver.data.sql.ClanTable;
import net.sf.l2j.gameserver.data.xml.ItemData;
import net.sf.l2j.gameserver.data.xml.MultisellData;
import net.sf.l2j.gameserver.data.xml.NewbieBuffData;
import net.sf.l2j.gameserver.data.xml.NpcData;
import net.sf.l2j.gameserver.data.xml.PolymorphData;
import net.sf.l2j.gameserver.data.xml.PolymorphData.Polymorph;
import net.sf.l2j.gameserver.data.xml.ScriptData;
import net.sf.l2j.gameserver.enums.IntentionType;
import net.sf.l2j.gameserver.enums.SayType;
import net.sf.l2j.gameserver.enums.ScriptEventType;
import net.sf.l2j.gameserver.enums.items.ShotType;
import net.sf.l2j.gameserver.enums.skills.L2SkillType;
import net.sf.l2j.gameserver.geoengine.GeoEngine;
import net.sf.l2j.gameserver.handler.admincommandhandlers.AdminNpc;
import net.sf.l2j.gameserver.idfactory.IdFactory;
import net.sf.l2j.gameserver.model.L2Skill;
import net.sf.l2j.gameserver.model.WorldObject;
import net.sf.l2j.gameserver.model.actor.instance.Chest;
import net.sf.l2j.gameserver.model.actor.instance.GrandBoss;
import net.sf.l2j.gameserver.model.actor.instance.Monster;
import net.sf.l2j.gameserver.model.actor.instance.PartyFarm;
import net.sf.l2j.gameserver.model.actor.instance.RaidBoss;
import net.sf.l2j.gameserver.model.actor.stat.NpcStat;
import net.sf.l2j.gameserver.model.actor.status.NpcStatus;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate.AIType;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate.Race;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate.SkillType;
import net.sf.l2j.gameserver.model.clanhall.ClanHall;
import net.sf.l2j.gameserver.model.clanhall.SiegableHall;
import net.sf.l2j.gameserver.model.entity.Castle;
import net.sf.l2j.gameserver.model.holder.NewbieBuffHolder;
import net.sf.l2j.gameserver.model.item.DropCategory;
import net.sf.l2j.gameserver.model.item.DropData;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.model.item.kind.Item;
import net.sf.l2j.gameserver.model.item.kind.Weapon;
import net.sf.l2j.gameserver.model.pledge.Clan;
import net.sf.l2j.gameserver.model.spawn.Spawn;
import net.sf.l2j.gameserver.network.NpcStringId;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.AbstractNpcInfo.NpcInfo;
import net.sf.l2j.gameserver.network.serverpackets.ActionFailed;
import net.sf.l2j.gameserver.network.serverpackets.ExShowVariationCancelWindow;
import net.sf.l2j.gameserver.network.serverpackets.ExShowVariationMakeWindow;
import net.sf.l2j.gameserver.network.serverpackets.MagicSkillUse;
import net.sf.l2j.gameserver.network.serverpackets.MoveToPawn;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.network.serverpackets.NpcSay;
import net.sf.l2j.gameserver.network.serverpackets.ServerObjectInfo;
import net.sf.l2j.gameserver.network.serverpackets.SocialAction;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;
import net.sf.l2j.gameserver.scripting.Quest;
import net.sf.l2j.gameserver.scripting.QuestState;
import net.sf.l2j.gameserver.taskmanager.DecayTaskManager;
import net.sf.l2j.gameserver.taskmanager.RandomAnimationTaskManager;

/**
 * An instance type extending {@link Creature}, which represents a Non Playable Character (or NPC) in the world.
 */
public class Npc extends Creature
{
	public static final int INTERACTION_DISTANCE = 150;
	private static final int SOCIAL_INTERVAL = 12000;
	
	private Spawn _spawn;
	private Polymorph _fakePc;
	
	volatile boolean _isDecayed = false;
	
	private long _lastSocialBroadcast = 0;
	
	private int _leftHandItemId;
	private int _rightHandItemId;
	private int _enchantEffect;
	
	private double _currentCollisionHeight; // used for npc grow effect skills
	private double _currentCollisionRadius; // used for npc grow effect skills
	
	private int _currentSsCount = 0;
	private int _currentSpsCount = 0;
	private int _shotsMask = 0;
	
	private int _scriptValue = 0;
	
	private Castle _castle;
	private ClanHall _clanHall;
	private SiegableHall _siegableHall;
	
	public Npc(int objectId, NpcTemplate template)
	{
		super(objectId, template);
		
		for (L2Skill skill : template.getSkills(SkillType.PASSIVE))
			addStatFuncs(skill.getStatFuncs(this));
		
		initCharStatusUpdateValues();
		
		// initialize the "current" equipment
		_leftHandItemId = template.getLeftHand();
		_rightHandItemId = template.getRightHand();
		
		_enchantEffect = template.getEnchantEffect();
		
		// initialize the "current" collisions
		_currentCollisionHeight = template.getCollisionHeight();
		_currentCollisionRadius = template.getCollisionRadius();

		_fakePc = PolymorphData.getInstance().getFakePc(template.getNpcId());
		
		// Set the name of the Creature
		setName(template.getName());
		setTitle(template.getTitle());
		
		_castle = template.getCastle();
		_clanHall = template.getClanHall();
		_siegableHall = template.getSiegableHall();
	}
	
	@Override
	public void initCharStat()
	{
		setStat(new NpcStat(this));
	}
	
	@Override
	public NpcStat getStat()
	{
		return (NpcStat) super.getStat();
	}
	
	@Override
	public void initCharStatus()
	{
		setStatus(new NpcStatus(this));
	}
	
	@Override
	public NpcStatus getStatus()
	{
		return (NpcStatus) super.getStatus();
	}
	
	@Override
	public final NpcTemplate getTemplate()
	{
		return (NpcTemplate) super.getTemplate();
	}
	
	@Override
	public boolean isAttackable()
	{
		return true;
	}
	
	@Override
	public final int getLevel()
	{
		return getTemplate().getLevel();
	}
	
	@Override
	public boolean isUndead()
	{
		return getTemplate().getRace() == Race.UNDEAD;
	}
	
	@Override
	public void updateAbnormalEffect()
	{
		for (Player player : getKnownType(Player.class))
		{
			if (getMoveSpeed() == 0)
				player.sendPacket(new ServerObjectInfo(this, player));
			else
				player.sendPacket(new NpcInfo(this, player));
		}
	}
	
	@Override
	public final void setTitle(String value)
	{
		_title = (value == null) ? "" : value;
	}
	
	@Override
	public boolean isAutoAttackable(Creature attacker)
	{
		return false;
	}
	
	@Override
	public void onAction(Player player)
	{
		  
		// Set the target of the player
		if (player.getTarget() != this)
			player.setTarget(this);
		else
		{
			// Check if the player is attackable (without a forced attack).
			if (isAutoAttackable(player))
				player.getAI().setIntention(IntentionType.ATTACK, this);
			else
			{
				// Calculate the distance between the Player and this instance.
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
					
					if (hasRandomAnimation())
						onRandomAnimation(Rnd.get(8));
					
					List<Quest> scripts = getTemplate().getEventQuests(ScriptEventType.QUEST_START);
					if (scripts != null && !scripts.isEmpty())
						player.setLastQuestNpcObject(getObjectId());
					
					scripts = getTemplate().getEventQuests(ScriptEventType.ON_FIRST_TALK);
					if (scripts != null && scripts.size() == 1)
						scripts.get(0).notifyFirstTalk(this, player);
					else
						showChatWindow(player);
				}
			}
		}
	}
	
	@Override
	public void onActionShift(Player player)
	{
		// Check if the Player is a GM ; send him NPC infos if true.
		if (player.isGM())
			AdminNpc.sendGeneralInfos(player, this);
		
	      else if (this instanceof Monster || this instanceof RaidBoss || this instanceof PartyFarm || this instanceof GrandBoss || this instanceof Chest)
	           sendNpcDrop(player, getTemplate().getNpcId(), 1);
		
		if (player.getTarget() != this)
			player.setTarget(this);
		else
		{
			if (isAutoAttackable(player))
			{
				if (player.isInsideRadius(this, player.getPhysicalAttackRange(), false, false) && GeoEngine.getInstance().canSeeTarget(player, this))
					player.getAI().setIntention(IntentionType.ATTACK, this);
				else
					player.sendPacket(ActionFailed.STATIC_PACKET);
			}
			else if (canInteract(player))
			{
				// Rotate the player to face the instance
				player.sendPacket(new MoveToPawn(player, this, Npc.INTERACTION_DISTANCE));
				
				// Send ActionFailed to the player in order to avoid he stucks
				player.sendPacket(ActionFailed.STATIC_PACKET);
				
				if (hasRandomAnimation())
					onRandomAnimation(Rnd.get(8));
				
				List<Quest> scripts = getTemplate().getEventQuests(ScriptEventType.QUEST_START);
				if (scripts != null && !scripts.isEmpty())
					player.setLastQuestNpcObject(getObjectId());
				
				scripts = getTemplate().getEventQuests(ScriptEventType.ON_FIRST_TALK);
				if (scripts != null && scripts.size() == 1)
					scripts.get(0).notifyFirstTalk(this, player);
				else
					showChatWindow(player);
			}
			else
				player.sendPacket(ActionFailed.STATIC_PACKET);
		}
	}
	
	   public static void sendNpcDrop(Player player, int npcId, int page)
	   {
	       final int ITEMS_PER_LIST = 7;
	       final NpcTemplate npc = NpcData.getInstance().getTemplate(npcId);
	       if (npc == null)
	           return;
	      
	       if (npc.getDropData().isEmpty())
	       {
	           player.sendMessage("This target have not drop info.");
	           return;
	       }
	      
	       final List<DropCategory> list = new ArrayList<>();
	       npc.getDropData().forEach(c -> list.add(c));
	       Collections.reverse(list);
	      
	       int myPage = 1;
	       int i = 0;
	       int shown = 0;
	       boolean hasMore = false;
	      
	       final StringBuilder sb = new StringBuilder();
	       for (DropCategory cat : list)
	       {
	           if (shown == ITEMS_PER_LIST)
	           {
	               hasMore = true;
	               break;
	           }
	          
	           for (DropData drop : cat.getAllDrops())
	           {
	               double chance = (drop.getItemId() == 57 ? drop.getChance() * Config.RATE_DROP_ADENA : drop.getChance() * Config.RATE_DROP_ITEMS) / 10000;
	               chance = chance > 100 ? 100 : chance;
	              
	               String percent = null;
	               if (chance <= 0.001)
	               {
	                   DecimalFormat df = new DecimalFormat("#.####");
	                   percent = df.format(chance);
	               }
	               else if (chance <= 0.01)
	               {
	                   DecimalFormat df = new DecimalFormat("#.###");
	                   percent = df.format(chance);
	               }
	               else
	               {
	                   DecimalFormat df = new DecimalFormat("##.##");
	                   percent = df.format(chance);
	               }
	              
	               Item item = ItemData.getInstance().getTemplate(drop.getItemId());
	               String name = item.getName();
	              
	               if (name.startsWith("Recipe: "))
	                   name = "R: " + name.substring(8);
	              
	               if (name.length() >= 40)
	                   name = name.substring(0, 37) + "...";
	              
	               if (myPage != page)
	               {
	                   i++;
	                   if (i == ITEMS_PER_LIST)
	                   {
	                       myPage++;
	                       i = 0;
	                   }
	                   continue;
	               }
	              
	               if (shown == ITEMS_PER_LIST)
	               {
	                   hasMore = true;
	                   break;
	               }
	              
	               String check = player.ignoredDropContain(item.getItemId()) ? "L2UI.CheckBox" : "L2UI.CheckBox_checked";
	               sb.append("<table width=280 bgcolor=000000><tr>");
	               sb.append("<td width=44 height=41 align=center><table bgcolor=" + (cat.isSweep() ? "FF00FF" : "FFFFFF") + " cellpadding=6 cellspacing=\"-5\"><tr><td><button width=32 height=32 back=" + item.getIcon() + " fore=" + item.getIcon() + "></td></tr></table></td>");
	               sb.append("<td width=240>" + (cat.isSweep() ? "<font color=ff00ff>" + name + "</font>" : name) + "<br1><font color=B09878>" + (cat.isSweep() ? "Spoil" : "Drop") + " Chance : " + percent + "%</font></td>");
	               sb.append("<td width=20><button action=\"bypass droplist " + npcId + " " + page + " " + item.getItemId() + "\" width=12 height=12 back=\"" + check + "\" fore=\"" + check + "\"/></td>");
	               sb.append("</tr></table><img src=L2UI.SquareGray width=280 height=1>");
	               shown++;
	               }
	       }
	       sb.append("<img height=" + (294 - (shown * 42)) + ">");
	       sb.append("<img height=8><img src=L2UI.SquareGray width=280 height=1>");
	       sb.append("<table width=280 bgcolor=000000><tr>");
	       sb.append("<td align=center width=70>" + (page > 1 ? "<button value=\"< PREV\" action=\"bypass droplist " + npcId + " " + (page - 1) + "\" width=65 height=19 back=L2UI_ch3.smallbutton2_over fore=L2UI_ch3.smallbutton2>" : "") + "</td>");
	       sb.append("<td align=center width=140>Page " + page + "</td>");
	       sb.append("<td align=center width=70>" + (hasMore ? "<button value=\"NEXT >\" action=\"bypass droplist " + npcId + " " + (page + 1) + "\" width=65 height=19 back=L2UI_ch3.smallbutton2_over fore=L2UI_ch3.smallbutton2>" : "") + "</td>");
	       sb.append("</tr></table><img src=L2UI.SquareGray width=280 height=1>");
	      
	       final NpcHtmlMessage html = new NpcHtmlMessage(200);
	       html.setFile("data/html/mods/droplist.htm");
	       html.replace("%list%", sb.toString());
	       html.replace("%name%", npc.getName());
	       player.sendPacket(html);
	   }
	
	@Override
	protected final void notifyQuestEventSkillFinished(L2Skill skill, WorldObject target)
	{
		final List<Quest> scripts = getTemplate().getEventQuests(ScriptEventType.ON_SPELL_FINISHED);
		if (scripts != null)
		{
			final Player player = (target == null) ? null : target.getActingPlayer();
			
			for (Quest quest : scripts)
				quest.notifySpellFinished(this, player, skill);
		}
	}
	
	@Override
	public boolean isMovementDisabled()
	{
		return super.isMovementDisabled() || !getTemplate().canMove() || getTemplate().getAiType().equals(AIType.CORPSE);
	}
	
	@Override
	public boolean isCoreAIDisabled()
	{
		return super.isCoreAIDisabled() || getTemplate().getAiType().equals(AIType.CORPSE);
	}
	
	@Override
	public void sendInfo(Player player)
	{
		if (getMoveSpeed() == 0)
			player.sendPacket(new ServerObjectInfo(this, player));
		else
			player.sendPacket(new NpcInfo(this, player));
	}
	
	@Override
	public boolean isChargedShot(ShotType type)
	{
		return (_shotsMask & type.getMask()) == type.getMask();
	}
	
	@Override
	public void setChargedShot(ShotType type, boolean charged)
	{
		if (charged)
			_shotsMask |= type.getMask();
		else
			_shotsMask &= ~type.getMask();
	}
	
	@Override
	public void rechargeShots(boolean physical, boolean magic)
	{
		if (physical)
		{
			// No more ss for this instance, already charged or the activation chance didn't trigger.
			if (_currentSsCount <= 0 || isChargedShot(ShotType.SOULSHOT) || Rnd.get(100) > getTemplate().getSsRate())
				return;
			
			// Reduce the amount of ss for this instance.
			_currentSsCount--;
			
			broadcastPacketInRadius(new MagicSkillUse(this, this, 2154, 1, 0, 0), 600);
			setChargedShot(ShotType.SOULSHOT, true);
		}
		
		if (magic)
		{
			// No more sps for this instance, already charged or the activation chance didn't trigger.
			if (_currentSpsCount <= 0 || isChargedShot(ShotType.SPIRITSHOT) || Rnd.get(100) > getTemplate().getSpsRate())
				return;
			
			// Reduce the amount of sps for this instance.
			_currentSpsCount--;
			
			broadcastPacketInRadius(new MagicSkillUse(this, this, 2061, 1, 0, 0), 600);
			setChargedShot(ShotType.SPIRITSHOT, true);
		}
	}
	
	@Override
	public int getSkillLevel(int skillId)
	{
		for (List<L2Skill> list : getTemplate().getSkills().values())
		{
			for (L2Skill skill : list)
				if (skill.getId() == skillId)
					return skill.getLevel();
		}
		return 0;
	}
	
	@Override
	public L2Skill getSkill(int skillId)
	{
		for (List<L2Skill> list : getTemplate().getSkills().values())
		{
			for (L2Skill skill : list)
				if (skill.getId() == skillId)
					return skill;
		}
		return null;
	}
	
	@Override
	public ItemInstance getActiveWeaponInstance()
	{
		return null;
	}
	
	@Override
	public Weapon getActiveWeaponItem()
	{
		final int weaponId = getTemplate().getRightHand();
		if (weaponId <= 0)
			return null;
		
		final Item item = ItemData.getInstance().getTemplate(weaponId);
		if (!(item instanceof Weapon))
			return null;
		
		return (Weapon) item;
	}
	
	@Override
	public ItemInstance getSecondaryWeaponInstance()
	{
		return null;
	}
	
	@Override
	public Item getSecondaryWeaponItem()
	{
		final int itemId = getTemplate().getLeftHand();
		if (itemId <= 0)
			return null;
		
		return ItemData.getInstance().getTemplate(itemId);
	}
	
	@Override
	public boolean doDie(Creature killer)
	{
		if (!super.doDie(killer))
			return false;
		
		_leftHandItemId = getTemplate().getLeftHand();
		_rightHandItemId = getTemplate().getRightHand();
		
		_enchantEffect = getTemplate().getEnchantEffect();
		
		_currentCollisionHeight = getTemplate().getCollisionHeight();
		_currentCollisionRadius = getTemplate().getCollisionRadius();
		
		DecayTaskManager.getInstance().add(this, getTemplate().getCorpseTime());
		return true;
	}
	
	@Override
	public void onSpawn()
	{
		super.onSpawn();
		
		// initialize ss/sps counts.
		_currentSsCount = getTemplate().getSsCount();
		_currentSpsCount = getTemplate().getSpsCount();
		
		final List<Quest> scripts = getTemplate().getEventQuests(ScriptEventType.ON_SPAWN);
		if (scripts != null)
			for (Quest quest : scripts)
				quest.notifySpawn(this);
	}
	
	@Override
	public void onDecay()
	{
		if (isDecayed())
			return;
		
		setDecayed(true);
		
		final List<Quest> scripts = getTemplate().getEventQuests(ScriptEventType.ON_DECAY);
		if (scripts != null)
			for (Quest quest : scripts)
				quest.notifyDecay(this);
			
		// Remove the Npc from the world when the decay task is launched.
		super.onDecay();
		
		// Respawn it, if possible.
		if (_spawn != null)
			_spawn.doRespawn();
	}
	
	@Override
	public void deleteMe()
	{
		// Decay
		onDecay();
		
		super.deleteMe();
	}
	
	@Override
	public double getCollisionHeight()
	{
		return _currentCollisionHeight;
	}
	
	@Override
	public double getCollisionRadius()
	{
		return _currentCollisionRadius;
	}
	
	@Override
	public String toString()
	{
		return getName() + " [npcId=" + getNpcId() + " objId=" + getObjectId() + "]";
	}
	
	public int getCurrentSsCount()
	{
		return _currentSsCount;
	}
	
	public int getCurrentSpsCount()
	{
		return _currentSpsCount;
	}
	
	/**
	 * @return the {@link Spawn} associated to this {@link Npc}.
	 */
	public Spawn getSpawn()
	{
		return _spawn;
	}
	
	/**
	 * Set the {@link Spawn} of this {@link Npc}.
	 * @param spawn : The Spawn to set.
	 */
	public void setSpawn(Spawn spawn)
	{
		_spawn = spawn;
	}
	
	public Npc scheduleDespawn(long delay)
	{
		ThreadPool.schedule(() ->
		{
			if (!isDecayed())
				deleteMe();
		}, delay);
		
		return this;
	}
	
	public boolean isDecayed()
	{
		return _isDecayed;
	}
	
	public void setDecayed(boolean decayed)
	{
		_isDecayed = decayed;
	}
	
	public void endDecayTask()
	{
		if (!isDecayed())
		{
			DecayTaskManager.getInstance().cancel(this);
			onDecay();
		}
	}
	
	/**
	 * Broadcast a {@link SocialAction} packet with a specific id. It refreshs the timer.
	 * @param id : The animation id to broadcast.
	 */
	public void onRandomAnimation(int id)
	{
		final long now = System.currentTimeMillis();
		if (now - _lastSocialBroadcast > SOCIAL_INTERVAL)
		{
			_lastSocialBroadcast = now;
			broadcastPacket(new SocialAction(this, id));
		}
	}
	
	/**
	 * Add this {@link Npc} on {@link RandomAnimationTaskManager}. The task will be fired after a calculated delay.
	 */
	public void startRandomAnimationTimer()
	{
		if (!hasRandomAnimation())
			return;
		
		RandomAnimationTaskManager.getInstance().add(this, calculateRandomAnimationTimer());
	}
	
	public int calculateRandomAnimationTimer()
	{
		return Rnd.get(Config.MIN_NPC_ANIMATION, Config.MAX_NPC_ANIMATION);
	}
	
	/**
	 * @return true if {@link Config} allows Random Animation, false if not or if the {@link AIType} is a corpse.
	 */
	public boolean hasRandomAnimation()
	{
		return (Config.MAX_NPC_ANIMATION > 0 && !getTemplate().getAiType().equals(AIType.CORPSE));
	}
	
	/**
	 * @return the id of this {@link Npc} contained in the {@link NpcTemplate}.
	 */
	public int getNpcId()
	{
		return getTemplate().getNpcId();
	}
	
	/**
	 * @return true if this {@link Npc} is agressive.
	 */
	public boolean isAggressive()
	{
		return false;
	}
	
	/**
	 * @return the id of the item in the left hand of this {@link Npc}.
	 */
	public int getLeftHandItemId()
	{
		return _leftHandItemId;
	}
	
	public void setLeftHandItemId(int itemId)
	{
		_leftHandItemId = itemId;
	}
	
	/**
	 * @return the id of the item in the right hand of this {@link Npc}.
	 */
	public int getRightHandItemId()
	{
		return _rightHandItemId;
	}
	
	public void setRightHandItemId(int id)
	{
		_rightHandItemId = id;
	}
	
	public int getEnchantEffect()
	{
		return _enchantEffect;
	}
	
	public void setEnchantEffect(int enchant)
	{
		_enchantEffect = enchant;
	}
	
	public void setCollisionHeight(double height)
	{
		_currentCollisionHeight = height;
	}
	
	public void setCollisionRadius(double radius)
	{
		_currentCollisionRadius = radius;
	}
	
	public int getScriptValue()
	{
		return _scriptValue;
	}
	
	public void setScriptValue(int val)
	{
		_scriptValue = val;
	}
	
	public boolean isScriptValue(int val)
	{
		return _scriptValue == val;
	}
	
	/**
	 * @return true if this {@link Npc} can be a warehouse manager.
	 */
	public boolean isWarehouse()
	{
		return false;
	}
	
	/**
	 * @return the {@link Castle} this {@link Npc} belongs to.
	 */
	public final Castle getCastle()
	{
		return _castle;
	}
	
	public void setCastle(Castle castle)
	{
		_castle = castle;
	}

	public Polymorph getFakePc()
	{
		return _fakePc;
	}
	
	/**
	 * @return the {@link ClanHall} this {@link Npc} belongs to.
	 */
	public final ClanHall getClanHall()
	{
		return _clanHall;
	}
	
	/**
	 * @return the {@link SiegableHall} this {@link Npc} belongs to.
	 */
	public final SiegableHall getSiegableHall()
	{
		return _siegableHall;
	}
	
	/**
	 * @param player : The Player used as reference.
	 * @return true if the {@link Player} set as parameter is the clan leader owning this {@link Npc} (being a Castle, ClanHall or SiegableHall).
	 */
	public boolean isLordOwner(Player player)
	{
		// The player isn't a Clan leader, return.
		if (!player.isClanLeader())
			return false;
		
		// Test Castle ownership.
		if (_castle != null && _castle.getOwnerId() == player.getClanId())
			return true;
		
		// Test ClanHall / SiegableHall ownership.
		if (_clanHall != null && _clanHall.getOwnerId() == player.getClanId())
			return true;
		
		return false;
	}
	
	/**
	 * @return the Exp reward of this {@link Npc} based on its {@link NpcTemplate} and modified by {@link Config#RATE_XP}.
	 */
	public int getExpReward()
	{
		return (int) (getTemplate().getRewardExp() * Config.RATE_XP);
	}
	
	/**
	 * @return the SP reward of this {@link Npc} based on its {@link NpcTemplate} and modified by {@link Config#RATE_SP}.
	 */
	public int getSpReward()
	{
		return (int) (getTemplate().getRewardSp() * Config.RATE_SP);
	}
	
	/**
	 * Open a quest or chat window on client with the text of this {@link Npc} in function of the command.
	 * @param player :The player to test.
	 * @param command : The String received from client, used as command bypass.
	 */
	public void onBypassFeedback(Player player, String command)
	{
		if (command.equalsIgnoreCase("TerritoryStatus"))
		{
			final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
			
			if (getCastle().getOwnerId() > 0)
			{
				html.setFile("data/html/territorystatus.htm");
				Clan clan = ClanTable.getInstance().getClan(getCastle().getOwnerId());
				html.replace("%clanname%", clan.getName());
				html.replace("%clanleadername%", clan.getLeaderName());
			}
			else
				html.setFile("data/html/territorynoclan.htm");
			
			html.replace("%castlename%", getCastle().getName());
			html.replace("%taxpercent%", getCastle().getTaxPercent());
			html.replace("%objectId%", getObjectId());
			
			if (getCastle().getCastleId() > 6)
				html.replace("%territory%", "The Kingdom of Elmore");
			else
				html.replace("%territory%", "The Kingdom of Aden");
			
			player.sendPacket(html);
		}
		else if (command.startsWith("Quest"))
		{
			String quest = "";
			try
			{
				quest = command.substring(5).trim();
			}
			catch (IndexOutOfBoundsException ioobe)
			{
			}
			
			if (quest.isEmpty())
				showQuestWindowGeneral(player, this);
			else
				showQuestWindowSingle(player, this, ScriptData.getInstance().getQuest(quest));
		}
		else if (command.startsWith("Chat"))
		{
			int val = 0;
			try
			{
				val = Integer.parseInt(command.substring(5));
			}
			catch (IndexOutOfBoundsException ioobe)
			{
			}
			catch (NumberFormatException nfe)
			{
			}
			
			showChatWindow(player, val);
		}
		else if (command.startsWith("Link"))
		{
			String path = command.substring(5).trim();
			if (path.indexOf("..") != -1)
				return;
			
			final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
			html.setFile("data/html/" + path);
			html.replace("%objectId%", getObjectId());
			player.sendPacket(html);
		}
		else if (command.startsWith("Loto"))
		{
			int val = 0;
			try
			{
				val = Integer.parseInt(command.substring(5));
			}
			catch (IndexOutOfBoundsException ioobe)
			{
			}
			catch (NumberFormatException nfe)
			{
			}
			
			if (val == 0)
			{
				// new loto ticket
				for (int i = 0; i < 5; i++)
					player.setLoto(i, 0);
			}
			showLotoWindow(player, val);
		}
		else if (command.startsWith("CPRecovery"))
		{
			if (getNpcId() != 31225 && getNpcId() != 31226)
				return;
			
			if (player.isCursedWeaponEquipped())
			{
				player.sendMessage("Go away, you're not welcome here.");
				return;
			}
			
			// Consume 100 adenas
			if (player.reduceAdena("RestoreCP", 100, player.getCurrentFolk(), true))
			{
				setTarget(player);
				doCast(FrequentSkill.ARENA_CP_RECOVERY.getSkill());
				player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_CP_WILL_BE_RESTORED).addCharName(player));
			}
		}
		else if (command.startsWith("SupportMagic"))
		{
			// Prevent a cursed weapon wielder of being buffed.
			if (player.isCursedWeaponEquipped())
				return;
			
			int playerLevel = player.getLevel();
			int lowestLevel = 0;
			int higestLevel = 0;
			
			// Select the player.
			setTarget(player);
			
			// Calculate the min and max level between which the player must be to obtain buff.
			if (player.isMageClass())
			{
				lowestLevel = NewbieBuffData.getInstance().getMagicLowestLevel();
				higestLevel = NewbieBuffData.getInstance().getMagicHighestLevel();
			}
			else
			{
				lowestLevel = NewbieBuffData.getInstance().getPhysicLowestLevel();
				higestLevel = NewbieBuffData.getInstance().getPhysicHighestLevel();
			}
			
			// If the player is too high level, display a message and return.
			if (playerLevel > higestLevel || !player.isNewbie())
			{
				final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
				html.setHtml("<html><body>Newbie Guide:<br>Only a <font color=\"LEVEL\">novice character of level " + higestLevel + " or less</font> can receive my support magic.<br>Your novice character is the first one that you created and raised in this world.</body></html>");
				html.replace("%objectId%", getObjectId());
				player.sendPacket(html);
				return;
			}
			
			// If the player is too low level, display a message and return.
			if (playerLevel < lowestLevel)
			{
				final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
				html.setHtml("<html><body>Come back here when you have reached level " + lowestLevel + ". I will give you support magic then.</body></html>");
				html.replace("%objectId%", getObjectId());
				player.sendPacket(html);
				return;
			}
			
			// Go through the NewbieBuff List and cast skills.
			for (NewbieBuffHolder buff : NewbieBuffData.getInstance().getBuffs())
			{
				if (buff.isMagicClassBuff() == player.isMageClass() && playerLevel >= buff.getLowerLevel() && playerLevel <= buff.getUpperLevel())
				{
					final L2Skill skill = buff.getSkill();
					if (skill.getSkillType() == L2SkillType.SUMMON)
						player.doCast(skill);
					else
						doCast(skill);
				}
			}
		}
		else if (command.startsWith("multisell"))
		{
			MultisellData.getInstance().separateAndSend(command.substring(9).trim(), player, this, false);
		}
		else if (command.startsWith("exc_multisell"))
		{
			MultisellData.getInstance().separateAndSend(command.substring(13).trim(), player, this, true);
		}
		else if (command.startsWith("Augment"))
		{
			int cmdChoice = Integer.parseInt(command.substring(8, 9).trim());
			switch (cmdChoice)
			{
				case 1:
					player.sendPacket(SystemMessageId.SELECT_THE_ITEM_TO_BE_AUGMENTED);
					player.sendPacket(ExShowVariationMakeWindow.STATIC_PACKET);
					break;
				case 2:
					player.sendPacket(SystemMessageId.SELECT_THE_ITEM_FROM_WHICH_YOU_WISH_TO_REMOVE_AUGMENTATION);
					player.sendPacket(ExShowVariationCancelWindow.STATIC_PACKET);
					break;
			}
		}
		else if (command.startsWith("EnterRift"))
		{
			try
			{
				Byte b1 = Byte.parseByte(command.substring(10)); // Selected Area: Recruit, Soldier etc
				DimensionalRiftManager.getInstance().start(player, b1, this);
			}
			catch (Exception e)
			{
			}
		}
	}
	
	/**
	 * Collect quests in progress and possible quests and show proper quest window to a {@link Player}.
	 * @param player : The player that talk with the Npc.
	 * @param npc : The Npc instance.
	 */
	public static void showQuestWindowGeneral(Player player, Npc npc)
	{
		final List<Quest> quests = new ArrayList<>();
		
		List<Quest> scripts = npc.getTemplate().getEventQuests(ScriptEventType.ON_TALK);
		if (scripts != null)
		{
			for (Quest quest : scripts)
			{
				if (quest == null || !quest.isRealQuest() || quests.contains(quest))
					continue;
				
				QuestState qs = player.getQuestState(quest.getName());
				if (qs == null || qs.isCreated())
					continue;
				
				quests.add(quest);
			}
		}
		
		scripts = npc.getTemplate().getEventQuests(ScriptEventType.QUEST_START);
		if (scripts != null)
		{
			for (Quest quest : scripts)
			{
				if (quest == null || !quest.isRealQuest() || quests.contains(quest))
					continue;
				
				quests.add(quest);
			}
		}
		
		if (quests.isEmpty())
			showQuestWindowSingle(player, npc, null);
		else if (quests.size() == 1)
			showQuestWindowSingle(player, npc, quests.get(0));
		else
			showQuestWindowChoose(player, npc, quests);
	}
	
	/**
	 * Open a quest window on client with the text of this {@link Npc}. Create the {@link QuestState} if not existing.
	 * @param player : The Player that talk with the Npc.
	 * @param npc : The Npc instance.
	 * @param quest : The Quest to check.
	 */
	public static void showQuestWindowSingle(Player player, Npc npc, Quest quest)
	{
		if (quest == null)
		{
			final NpcHtmlMessage html = new NpcHtmlMessage(npc.getObjectId());
			html.setHtml(Quest.getNoQuestMsg());
			player.sendPacket(html);
			
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		if (quest.isRealQuest() && (player.getWeightPenalty() > 2 || player.getInventoryLimit() * 0.8 <= player.getInventory().getSize()))
		{
			player.sendPacket(SystemMessageId.INVENTORY_LESS_THAN_80_PERCENT);
			return;
		}
		
		QuestState qs = player.getQuestState(quest.getName());
		if (qs == null)
		{
			if (quest.isRealQuest() && player.getAllQuests(false).size() >= 25)
			{
				final NpcHtmlMessage html = new NpcHtmlMessage(npc.getObjectId());
				html.setHtml(Quest.getTooMuchQuestsMsg());
				player.sendPacket(html);
				
				player.sendPacket(ActionFailed.STATIC_PACKET);
				return;
			}
			
			final List<Quest> scripts = npc.getTemplate().getEventQuests(ScriptEventType.QUEST_START);
			if (scripts != null && scripts.contains(quest))
				qs = quest.newQuestState(player);
		}
		
		if (qs != null)
			quest.notifyTalk(npc, qs.getPlayer());
	}
	
	/**
	 * Shows the list of available {@link Quest}s for this {@link Npc}.
	 * @param player : The player that talk with the Npc.
	 * @param npc : The Npc instance.
	 * @param quests : The list containing quests of the Npc.
	 */
	public static void showQuestWindowChoose(Player player, Npc npc, List<Quest> quests)
	{
		final StringBuilder sb = new StringBuilder("<html><body>");
		
		for (Quest q : quests)
		{
			StringUtil.append(sb, "<a action=\"bypass -h npc_%objectId%_Quest ", q.getName(), "\">[", q.getDescr());
			
			final QuestState qs = player.getQuestState(q.getName());
			if (qs != null && qs.isStarted())
				sb.append(" (In Progress)]</a><br>");
			else if (qs != null && qs.isCompleted())
				sb.append(" (Done)]</a><br>");
			else
				sb.append("]</a><br>");
		}
		
		sb.append("</body></html>");
		
		final NpcHtmlMessage html = new NpcHtmlMessage(npc.getObjectId());
		html.setHtml(sb.toString());
		html.replace("%objectId%", npc.getObjectId());
		player.sendPacket(html);
		
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	/**
	 * Generate the complete path to retrieve a htm, based on npcId.
	 * <ul>
	 * <li>if the file exists on the server (page number = 0) : <B>data/html/default/12006.htm</B> (npcId-page number)</li>
	 * <li>if the file exists on the server (page number > 0) : <B>data/html/default/12006-1.htm</B> (npcId-page number)</li>
	 * <li>if the file doesn't exist on the server : <B>data/html/npcdefault.htm</B> (message : "I have nothing to say to you")</li>
	 * </ul>
	 * @param npcId : The id of the Npc whose text must be displayed.
	 * @param val : The number of the page to display.
	 * @return the pathfile of the selected HTML file in function of the npcId and of the page number.
	 */
	public String getHtmlPath(int npcId, int val)
	{
		String filename;
		
		if (val == 0)
			filename = "data/html/default/" + npcId + ".htm";
		else
			filename = "data/html/default/" + npcId + "-" + val + ".htm";
		
		if (HtmCache.getInstance().isLoadable(filename))
			return filename;
		
		return "data/html/npcdefault.htm";
	}
	
	/**
	 * Broadcast a {@link String} to the knownlist of this {@link Npc}.
	 * @param message : The String message to send.
	 */
	public void broadcastNpcSay(String message)
	{
		broadcastPacket(new NpcSay(this, SayType.ALL, message));
	}
	
	/**
	 * Broadcast a {@link NpcStringId} to the knownlist of this {@link Npc}.
	 * @param npcStringId : The {@link NpcStringId}.
	 */
	public void broadcastNpcSay(NpcStringId npcStringId)
	{
		broadcastPacket(new NpcSay(this, SayType.ALL, npcStringId.getMessage()));
	}
	
	/**
	 * Broadcast a {@link NpcStringId} to the knownlist of this {@link Npc}.
	 * @param npcStringId : The {@link NpcStringId}.
	 * @param params : Additional parameters {@link NpcStringId}.
	 */
	public void broadcastNpcSay(NpcStringId npcStringId, Object... params)
	{
		broadcastPacket(new NpcSay(this, SayType.ALL, npcStringId.getMessage(params)));
	}
	
	/**
	 * Open a Loto window for the {@link Player} set as parameter.
	 * <ul>
	 * <li>0 - first buy lottery ticket window</li>
	 * <li>1-20 - buttons</li>
	 * <li>21 - second buy lottery ticket window</li>
	 * <li>22 - selected ticket with 5 numbers</li>
	 * <li>23 - current lottery jackpot</li>
	 * <li>24 - Previous winning numbers/Prize claim</li>
	 * <li>>24 - check lottery ticket by item object id</li>
	 * </ul>
	 * @param player : The player that talk with this Npc.
	 * @param val : The number of the page to display.
	 */
	public void showLotoWindow(Player player, int val)
	{
		int npcId = getTemplate().getNpcId();
		
		final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		
		if (val == 0) // 0 - first buy lottery ticket window
		{
			html.setFile(getHtmlPath(npcId, 1));
		}
		else if (val >= 1 && val <= 21) // 1-20 - buttons, 21 - second buy lottery ticket window
		{
			if (!LotteryManager.getInstance().isStarted())
			{
				// tickets can't be sold
				player.sendPacket(SystemMessageId.NO_LOTTERY_TICKETS_CURRENT_SOLD);
				return;
			}
			if (!LotteryManager.getInstance().isSellableTickets())
			{
				// tickets can't be sold
				player.sendPacket(SystemMessageId.NO_LOTTERY_TICKETS_AVAILABLE);
				return;
			}
			
			html.setFile(getHtmlPath(npcId, 5));
			
			int count = 0;
			int found = 0;
			// counting buttons and unsetting button if found
			for (int i = 0; i < 5; i++)
			{
				if (player.getLoto(i) == val)
				{
					// unsetting button
					player.setLoto(i, 0);
					found = 1;
				}
				else if (player.getLoto(i) > 0)
				{
					count++;
				}
			}
			
			// if not rearched limit 5 and not unseted value
			if (count < 5 && found == 0 && val <= 20)
				for (int i = 0; i < 5; i++)
					if (player.getLoto(i) == 0)
					{
						player.setLoto(i, val);
						break;
					}
				
			// setting pusshed buttons
			count = 0;
			for (int i = 0; i < 5; i++)
				if (player.getLoto(i) > 0)
				{
					count++;
					String button = String.valueOf(player.getLoto(i));
					if (player.getLoto(i) < 10)
						button = "0" + button;
					String search = "fore=\"L2UI.lottoNum" + button + "\" back=\"L2UI.lottoNum" + button + "a_check\"";
					String replace = "fore=\"L2UI.lottoNum" + button + "a_check\" back=\"L2UI.lottoNum" + button + "\"";
					html.replace(search, replace);
				}
			
			if (count == 5)
			{
				String search = "0\">Return";
				String replace = "22\">The winner selected the numbers above.";
				html.replace(search, replace);
			}
		}
		else if (val == 22) // 22 - selected ticket with 5 numbers
		{
			if (!LotteryManager.getInstance().isStarted())
			{
				// tickets can't be sold
				player.sendPacket(SystemMessageId.NO_LOTTERY_TICKETS_CURRENT_SOLD);
				return;
			}
			if (!LotteryManager.getInstance().isSellableTickets())
			{
				// tickets can't be sold
				player.sendPacket(SystemMessageId.NO_LOTTERY_TICKETS_AVAILABLE);
				return;
			}
			
			int price = Config.ALT_LOTTERY_TICKET_PRICE;
			int lotonumber = LotteryManager.getInstance().getId();
			int enchant = 0;
			int type2 = 0;
			
			for (int i = 0; i < 5; i++)
			{
				if (player.getLoto(i) == 0)
					return;
				
				if (player.getLoto(i) < 17)
					enchant += Math.pow(2, player.getLoto(i) - 1);
				else
					type2 += Math.pow(2, player.getLoto(i) - 17);
			}
			
			if (!player.reduceAdena("Loto", price, this, true))
				return;
			
			LotteryManager.getInstance().increasePrize(price);
			
			ItemInstance item = new ItemInstance(IdFactory.getInstance().getNextId(), 4442);
			item.setCount(1);
			item.setCustomType1(lotonumber);
			item.setEnchantLevel(enchant);
			item.setCustomType2(type2);
			
			player.addItem("Loto", item, player, false);
			player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.EARNED_ITEM_S1).addItemName(4442));
			
			html.setFile(getHtmlPath(npcId, 3));
		}
		else if (val == 23) // 23 - current lottery jackpot
		{
			html.setFile(getHtmlPath(npcId, 3));
		}
		else if (val == 24) // 24 - Previous winning numbers/Prize claim
		{
			final int lotoNumber = LotteryManager.getInstance().getId();
			
			final StringBuilder sb = new StringBuilder();
			for (ItemInstance item : player.getInventory().getItems())
			{
				if (item == null)
					continue;
				
				if (item.getItemId() == 4442 && item.getCustomType1() < lotoNumber)
				{
					StringUtil.append(sb, "<a action=\"bypass -h npc_%objectId%_Loto ", item.getObjectId(), "\">", item.getCustomType1(), " Event Number ");
					
					int[] numbers = LotteryManager.decodeNumbers(item.getEnchantLevel(), item.getCustomType2());
					for (int i = 0; i < 5; i++)
						StringUtil.append(sb, numbers[i], " ");
					
					int[] check = LotteryManager.checkTicket(item);
					if (check[0] > 0)
					{
						switch (check[0])
						{
							case 1:
								sb.append("- 1st Prize");
								break;
							case 2:
								sb.append("- 2nd Prize");
								break;
							case 3:
								sb.append("- 3th Prize");
								break;
							case 4:
								sb.append("- 4th Prize");
								break;
						}
						StringUtil.append(sb, " ", check[1], "a.");
					}
					sb.append("</a><br>");
				}
			}
			
			if (sb.length() == 0)
				sb.append("There is no winning lottery ticket...<br>");
			
			html.setFile(getHtmlPath(npcId, 4));
			html.replace("%result%", sb.toString());
		}
		else if (val == 25) // 25 - lottery instructions
		{
			html.setFile(getHtmlPath(npcId, 2));
			html.replace("%prize5%", Config.ALT_LOTTERY_5_NUMBER_RATE * 100);
			html.replace("%prize4%", Config.ALT_LOTTERY_4_NUMBER_RATE * 100);
			html.replace("%prize3%", Config.ALT_LOTTERY_3_NUMBER_RATE * 100);
			html.replace("%prize2%", Config.ALT_LOTTERY_2_AND_1_NUMBER_PRIZE);
		}
		else if (val > 25) // >25 - check lottery ticket by item object id
		{
			final ItemInstance item = player.getInventory().getItemByObjectId(val);
			if (item == null || item.getItemId() != 4442 || item.getCustomType1() >= LotteryManager.getInstance().getId())
				return;
			
			if (player.destroyItem("Loto", item, this, true))
			{
				final int adena = LotteryManager.checkTicket(item)[1];
				if (adena > 0)
					player.addAdena("Loto", adena, this, true);
			}
			return;
		}
		html.replace("%objectId%", getObjectId());
		html.replace("%race%", LotteryManager.getInstance().getId());
		html.replace("%adena%", LotteryManager.getInstance().getPrize());
		html.replace("%ticket_price%", Config.ALT_LOTTERY_TICKET_PRICE);
		html.replace("%enddate%", DateFormat.getDateInstance().format(LotteryManager.getInstance().getEndDate()));
		player.sendPacket(html);
		
		// Send a Server->Client ActionFailed to the Player in order to avoid that the client wait another packet
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	/**
	 * Cast a random {@link L2Skill} based on stored {@link NpcTemplate} skills of this {@link Npc}.
	 * @param type : The related {@link SkillType} to get skills from.
	 */
	public void doCast(SkillType type)
	{
		super.doCast(Rnd.get(getTemplate().getSkills(type)));
	}
	
	/**
	 * Research the pk chat window htm related to this {@link Npc}, based on a String folder and npcId.<br>
	 * Send the content to the {@link Player} passed as parameter.
	 * @param player : The player to send the HTM.
	 * @param type : The folder to search on.
	 * @return true if such HTM exists.
	 */
	protected boolean showPkDenyChatWindow(Player player, String type)
	{
		final String content = HtmCache.getInstance().getHtm("data/html/" + type + "/" + getNpcId() + "-pk.htm");
		if (content != null)
		{
			final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
			html.setHtml(content);
			player.sendPacket(html);
			
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return true;
		}
		return false;
	}
	
	/**
	 * Open a chat window on client with the text of the Npc.<br>
	 * Send the content to the {@link Player} passed as parameter.
	 * @param player : The player that talk with the Npc.
	 */
	public void showChatWindow(Player player)
	{
		showChatWindow(player, 0);
	}
	
	/**
	 * Open a chat window on client with the text specified by {@link #getHtmlPath} and val parameter.<br>
	 * Send the content to the {@link Player} passed as parameter.
	 * @param player : The player that talk with the Npc.
	 * @param val : The current htm page to show.
	 */
	public void showChatWindow(Player player, int val)
	{
		showChatWindow(player, getHtmlPath(getNpcId(), val));
	}
	
	/**
	 * Open a chat window on client with the text specified by the given file name and path.<br>
	 * Send the content to the {@link Player} passed as parameter.
	 * @param player : The player that talk with the Npc.
	 * @param filename : The filename that contains the text to send.
	 */
	public void showChatWindow(Player player, String filename)
	{
		final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		html.setFile(filename);
		html.replace("%objectId%", getObjectId());
		player.sendPacket(html);
		
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	@Override
	public void onActiveRegion()
	{
		startRandomAnimationTimer();
	}
}