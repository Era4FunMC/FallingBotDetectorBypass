package co.earthme.wafkbot.core;

import co.earthme.wafkbot.math.Vec3D;
import com.github.steveice10.mc.protocol.data.game.ClientCommand;
import com.github.steveice10.mc.protocol.data.game.entity.player.HandPreference;
import com.github.steveice10.mc.protocol.data.game.entity.player.PositionElement;
import com.github.steveice10.mc.protocol.data.game.setting.ChatVisibility;
import com.github.steveice10.mc.protocol.data.game.setting.SkinPart;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerCombatKillPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundClientCommandPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundClientInformationPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import com.github.steveice10.packetlib.Session;
import com.github.steveice10.packetlib.event.session.*;
import com.github.steveice10.packetlib.packet.Packet;
import java.util.Arrays;
import java.util.BitSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MoveControl implements SessionListener {
    private final Executor delayedExecutor = CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS, Executors.newSingleThreadExecutor());
    private volatile boolean loggedIn;
    //Current entityId,Used to auto respawn
    private int entityId;
    private int tickCounter = 0;
    private Session currentSession;
    private final Vec3D currentPositionVector = new Vec3D(0,0,0,0,0);

    //Use to calculate the fall speed and when should we fall
    private volatile boolean falling = false;
    private volatile boolean corrected = false;
    private volatile boolean firstPosPacket = false;
    private final Vec3D fallSpeedVector = new Vec3D(0,0,0,0,0);
    private volatile boolean onGround = false;

    private final Vec3D moveVec = new Vec3D(0,0,0,0,0);

    private int viewDistance;

    //Move relative
    public void move(double x,double y,double z){
        this.moveVec.setX(x);
        this.moveVec.setY(y);
        this.moveVec.setZ(z);
    }

    //Get the position we are
    public Vec3D getCurrentPosition(){
        return this.currentPositionVector.copy();
    }

    //Get the falling speed(If on the ground it would always be 0.08)
    public double getCurrentFallingSpeed(){
        return this.fallSpeedVector.getY();
    }

    @Override
    public void packetReceived(Session session, Packet packet) {
        if (packet instanceof ClientboundLoginPacket loginPacket){
            this.entityId = loginPacket.getEntityId();
            this.currentSession = session;
            this.loggedIn = true;
            this.viewDistance = loginPacket.getViewDistance();
            CompletableFuture.delayedExecutor(1,TimeUnit.SECONDS).execute(()->{
                //Send the client info packet to bypass the anti bot
                session.send(new ServerboundClientInformationPacket(
                        "en_GB",
                        this.viewDistance,
                        ChatVisibility.FULL,
                        true,
                        Arrays.stream(SkinPart.values()).toList(),
                        HandPreference.RIGHT_HAND,
                        false,
                        true
                ));
                //Start tick loop
                this.doTick();
            });
        }

        if (packet instanceof ClientboundPlayerCombatKillPacket combatKillPacket){
            if (combatKillPacket.getPlayerId() == this.entityId){
                session.send(new ServerboundClientCommandPacket(ClientCommand.RESPAWN));
            }
        }

        if (packet instanceof ClientboundPlayerPositionPacket playerPositionPacket){
            //Accept the server teleport request
            session.send(new ServerboundAcceptTeleportationPacket(playerPositionPacket.getTeleportId()));

            if (!this.firstPosPacket){
                this.corrected = true;
            }

            if (!this.firstPosPacket){
                this.firstPosPacket = true;
            }

            double lastY = this.currentPositionVector.getY();

            //Update our position
            double x = playerPositionPacket.getRelative().contains(PositionElement.X) ? playerPositionPacket.getX() + this.currentPositionVector.getX() : playerPositionPacket.getX();
            double y = playerPositionPacket.getRelative().contains(PositionElement.Y) ? playerPositionPacket.getY() + this.currentPositionVector.getY() : playerPositionPacket.getY();
            double z = playerPositionPacket.getRelative().contains(PositionElement.Z) ? playerPositionPacket.getZ() + this.currentPositionVector.getZ() : playerPositionPacket.getZ();
            double yaw = playerPositionPacket.getRelative().contains(PositionElement.YAW) ? playerPositionPacket.getYaw() + this.currentPositionVector.getYaw() : playerPositionPacket.getYaw();
            double pitch = playerPositionPacket.getRelative().contains(PositionElement.PITCH) ? playerPositionPacket.getPitch() + this.currentPositionVector.getPitch() : playerPositionPacket.getPitch();

            this.currentPositionVector.setX(x);
            this.currentPositionVector.setY(y);
            this.currentPositionVector.setZ(z);
            this.currentPositionVector.setYaw(yaw);
            this.currentPositionVector.setPitch(pitch);

            //If server corrected our position and the difference from the last Y coordinate > 0.We should stop falling
            if (this.currentPositionVector.getY() - lastY > 0){
                //Check falling vector is smaller than 0
                if (this.fallSpeedVector.getY() < 0){
                    //Set on ground and set falling vector to 0
                    this.onGround = true;
                    this.fallSpeedVector.setY(0);
                }
            }
        }
    }

    private boolean fellSucceed(){
        if (!this.firstPosPacket){
            return false;
        }
        try {
            return this.falling && !this.corrected;
        }finally {
            this.falling = false;
            this.corrected = false;
        }
    }

    private void doTick(){
        if (this.loggedIn){
            //Execute tick if we loggined after 50ms
            this.delayedExecutor.execute(this::doTick);
        }else {
            return;
        }

        this.tickCounter++;

        //Try fall or fall
        if (this.tickCounter % 5 == 0 || this.fallSpeedVector.getY() < 0) {
            this.falling = true;
            if (this.fallSpeedVector.getY() > -3.9200038147008747){
                this.fallSpeedVector.setRelatively(-0.08, Vec3D.Relative.Y);
                double originalMonitorY = this.fallSpeedVector.getY();
                this.fallSpeedVector.setY(originalMonitorY * 0.98);
            }
        }

        final double currY = this.currentPositionVector.getY();
        if (currY - Math.floor(currY) > 0 && !this.falling){
            this.currentPositionVector.setRelatively(Math.floor(currY) - currY, Vec3D.Relative.Y);
        }

        //Fall
        this.currentPositionVector.setRelatively(this.fallSpeedVector.getY(), Vec3D.Relative.Y);

        //Update monitor
        this.currentPositionVector.setRelatively(this.moveVec.getX(), Vec3D.Relative.X);
        this.currentPositionVector.setRelatively(this.moveVec.getY(), Vec3D.Relative.Y);
        this.currentPositionVector.setRelatively(this.moveVec.getZ(), Vec3D.Relative.Z);

        //Sync our position
        this.currentSession.send(new ServerboundMovePlayerPosPacket(
                this.onGround,
                this.currentPositionVector.getX(),
                this.currentPositionVector.getY(),
                this.currentPositionVector.getZ()
        ));

        this.onGround = !this.fellSucceed();
    }

    @Override
    public void packetSending(PacketSendingEvent packetSendingEvent) {}

    @Override
    public void packetSent(Session session, Packet packet) {}

    @Override
    public void packetError(PacketErrorEvent packetErrorEvent) {}

    @Override
    public void connected(ConnectedEvent connectedEvent) {}

    @Override
    public void disconnecting(DisconnectingEvent disconnectingEvent) {
        //Stop the tick loop
        this.loggedIn = false;
    }

    @Override
    public void disconnected(DisconnectedEvent disconnectedEvent) {}
}