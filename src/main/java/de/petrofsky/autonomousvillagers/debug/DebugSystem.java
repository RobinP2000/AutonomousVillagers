package de.petrofsky.autonomousvillagers.debug;

import de.petrofsky.autonomousvillagers.AutonomousVillagers;
import de.petrofsky.autonomousvillagers.utils.InventoryUtils;
import de.petrofsky.autonomousvillagers.villagers.goals.MoveToGoal;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = AutonomousVillagers.MODID)
public class DebugSystem {

    private static final Map<UUID, DebugSession> SESSIONS = new HashMap<>();

    public static class DebugSession {
        public BlockPos pos1 = null;
        public BlockPos pos2 = null;
        public Villager testEntity = null;
        public boolean particlesActive = false;
        public boolean actionActive = false;
        public MoveToGoal currentGoal = null;
    }

    private static DebugSession getSession(ServerPlayer player) {
        return SESSIONS.computeIfAbsent(player.getUUID(), k -> new DebugSession());
    }


    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("debug")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();


                    ItemStack markerItem = new ItemStack(Items.DIAMOND_AXE);
                    markerItem.set(DataComponents.CUSTOM_NAME, Component.literal("[Debug] Marker (Pos 1 & 2)").withStyle(ChatFormatting.GOLD));

                    ItemStack spawnerItem = new ItemStack(Items.SPAWNER);
                    spawnerItem.set(DataComponents.CUSTOM_NAME, Component.literal("[Debug] Spawner / Despawner").withStyle(ChatFormatting.GREEN));

                    ItemStack particleItem = new ItemStack(Items.BLAZE_ROD);
                    particleItem.set(DataComponents.CUSTOM_NAME, Component.literal("[Debug] Partikel Toggle").withStyle(ChatFormatting.AQUA));

                    ItemStack actionItem = new ItemStack(Items.CLOCK);
                    actionItem.set(DataComponents.CUSTOM_NAME, Component.literal("[Debug] MoveToGoal Start/Stop").withStyle(ChatFormatting.LIGHT_PURPLE));

                    player.getInventory().add(markerItem);
                    player.getInventory().add(spawnerItem);
                    player.getInventory().add(particleItem);
                    player.getInventory().add(actionItem);

                    player.sendSystemMessage(Component.literal("§aDebug-Modus aktiviert! 4 beschriftete Items erhalten."));
                    return 1;
                }));
    }


    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide || event.getHand() != InteractionHand.MAIN_HAND) return;

        ServerPlayer player = (ServerPlayer) event.getEntity();
        if (player.getMainHandItem().is(Items.DIAMOND_AXE)) {
            DebugSession session = getSession(player);
            session.pos2 = event.getPos().above();
            player.sendSystemMessage(Component.literal("§ePosition 2 gesetzt: " + session.pos2.toShortString()));
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide || event.getHand() != InteractionHand.MAIN_HAND) return;

        ServerPlayer player = (ServerPlayer) event.getEntity();
        if (player.getMainHandItem().is(Items.DIAMOND_AXE)) {
            DebugSession session = getSession(player);
            session.pos1 = event.getPos().above();
            player.sendSystemMessage(Component.literal("§aPosition 1 gesetzt: " + session.pos1.toShortString()));
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide || event.getHand() != InteractionHand.MAIN_HAND) return;

        ServerPlayer player = (ServerPlayer) event.getEntity();
        ServerLevel level = (ServerLevel) event.getLevel();
        Item item = player.getMainHandItem().getItem();
        DebugSession session = getSession(player);

        if (item == Items.SPAWNER) {
            if (player.isShiftKeyDown()) {
                if (session.testEntity != null && !session.testEntity.isRemoved()) {
                    session.testEntity.discard();
                    session.testEntity = null;
                    if (session.currentGoal != null) session.currentGoal.stop();
                    player.sendSystemMessage(Component.literal("§cEntität entfernt."));
                }
            } else {
                if (session.pos1 == null) {
                    player.sendSystemMessage(Component.literal("§cBitte setze zuerst Position 1 mit dem Eichenstamm!"));
                    return;
                }
                if (session.testEntity != null && !session.testEntity.isRemoved()) {
                    session.testEntity.discard();
                }
                session.testEntity = EntityType.VILLAGER.create(level);
                if (session.testEntity != null) {
                    session.testEntity.setPos(session.pos1.getX() + 0.5, session.pos1.getY(), session.pos1.getZ() + 0.5);
                    session.testEntity.setCustomName(Component.literal("Dummy"));
                    session.testEntity.setCustomNameVisible(true);
                    session.testEntity.setInvulnerable(true);
                    session.testEntity.setCanPickUpLoot(false);
                    session.testEntity.setSilent(true);
                    InventoryUtils.increase(session.testEntity, ItemTags.DIRT, 64);

                    session.testEntity.getBrain().removeAllBehaviors();
                    level.addFreshEntity(session.testEntity);
                    player.sendSystemMessage(Component.literal("§aVillager an Position 1 gespawnt."));
                }
            }
        }


        else if (item == Items.BLAZE_ROD) {
            session.particlesActive = !session.particlesActive;
            player.sendSystemMessage(Component.literal("§bPartikel: " + (session.particlesActive ? "AN" : "AUS")));
        }

        else if (item == Items.CLOCK) {
            if (!session.actionActive) {
                if (session.testEntity != null && session.pos2 != null) {
                    session.currentGoal = new MoveToGoal(session.testEntity, session.pos2);
                    session.currentGoal.start();
                    session.actionActive = true;
                    player.sendSystemMessage(Component.literal("§dAction-Modus: AN (MoveToGoal gestartet)"));
                } else {
                    player.sendSystemMessage(Component.literal("§cFehler: Villager oder Position 2 fehlt!"));
                }
            } else {
                if (session.currentGoal != null) {
                    session.currentGoal.stop();
                }
                session.actionActive = false;
                player.sendSystemMessage(Component.literal("§dAction-Modus: AUS (MoveToGoal gestoppt)"));
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (DebugSession session : SESSIONS.values()) {
            if (session.particlesActive) {
                if (session.pos1 != null && session.testEntity != null && session.testEntity.level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, session.pos1.getX() + 0.5, session.pos1.getY() + 0.5, session.pos1.getZ() + 0.5, 1, 0, 0, 0, 0);
                }
                if (session.pos2 != null && session.testEntity != null && session.testEntity.level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.FLAME, session.pos2.getX() + 0.5, session.pos2.getY() + 0.5, session.pos2.getZ() + 0.5, 1, 0, 0, 0, 0);
                }


                if (session.currentGoal != null && session.currentGoal.getShortPath() != null) {
                    var path = session.currentGoal.getShortPath().getPath();
                    if (path != null && session.testEntity != null && session.testEntity.level() instanceof ServerLevel sl) {
                        for (de.petrofsky.autonomousvillagers.utils.Node node : path) {
                            BlockPos pathPos = node.getPos();

                            sl.sendParticles(ParticleTypes.END_ROD, pathPos.getX() + 0.5, pathPos.getY() + 0.5, pathPos.getZ() + 0.5, 1, 0, 0, 0, 0);
                        }
                    }
                }
            }


            if (session.actionActive && session.currentGoal != null && session.testEntity != null && session.testEntity.isAlive()) {
                if (session.currentGoal.isInProgress()) {
                    session.currentGoal.executeTick();
                } else {
                    session.actionActive = false;
                }
            }
        }
    }
}