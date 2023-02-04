package co.earthme.wafkbot.core;

import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundCustomPayloadPacket;
import com.github.steveice10.packetlib.Session;
import com.github.steveice10.packetlib.event.session.*;
import com.github.steveice10.packetlib.packet.Packet;

public class BrandSpoof implements SessionListener {

    @Override
    public void packetReceived(Session session, Packet packet) {
        if (packet instanceof ClientboundLoginPacket){
            session.send(new ServerboundCustomPayloadPacket("minecraft:brand","vanilla".getBytes()));
        }
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
    public void disconnecting(DisconnectingEvent disconnectingEvent) {}

    @Override
    public void disconnected(DisconnectedEvent disconnectedEvent) {}
}
