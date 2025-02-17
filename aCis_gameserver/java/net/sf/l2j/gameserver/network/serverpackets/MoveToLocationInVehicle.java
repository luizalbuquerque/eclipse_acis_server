package net.sf.l2j.gameserver.network.serverpackets;

import net.sf.l2j.gameserver.model.actor.Boat;
import net.sf.l2j.gameserver.model.actor.Player;

public class MoveToLocationInVehicle extends L2GameServerPacket
{
	private final int _objectId;
	private final int _boatId;
	private final int _targetX;
	private final int _targetY;
	private final int _targetZ;
	private final int _originX;
	private final int _originY;
	private final int _originZ;
	
	public MoveToLocationInVehicle(Player player, Boat boat, int targetX, int targetY, int targetZ, int originX, int originY, int originZ)
	{
		_objectId = player.getObjectId();
		_boatId = boat.getObjectId();
		_targetX = targetX;
		_targetY = targetY;
		_targetZ = targetZ;
		_originX = originX;
		_originY = originY;
		_originZ = originZ;
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0x71);
		writeD(_objectId);
		writeD(_boatId);
		writeD(_targetX);
		writeD(_targetY);
		writeD(_targetZ);
		writeD(_originX);
		writeD(_originY);
		writeD(_originZ);
	}
}