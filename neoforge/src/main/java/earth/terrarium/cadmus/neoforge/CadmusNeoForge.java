package earth.terrarium.cadmus.neoforge;

import earth.terrarium.cadmus.Cadmus;
import earth.terrarium.cadmus.api.claims.ClaimApi;
import earth.terrarium.cadmus.api.claims.InteractionType;
import earth.terrarium.cadmus.common.claims.AdminClaimHandler;
import earth.terrarium.cadmus.common.claims.ClaimHandler;
import earth.terrarium.cadmus.common.claims.admin.ModFlags;
import earth.terrarium.cadmus.common.commands.ModCommands;
import earth.terrarium.cadmus.common.util.ModUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingAttackEvent;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.EntityItemPickupEvent;
import net.neoforged.neoforge.event.entity.player.FillBucketEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@Mod(Cadmus.MOD_ID)
public class CadmusNeoForge {
    public CadmusNeoForge() {
        Cadmus.init();

        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener(CadmusNeoForge::onRegisterCommands);
        bus.addListener(CadmusNeoForge::onServerStarted);
        bus.addListener(CadmusNeoForge::onEnterSection);
        bus.addListener(CadmusNeoForge::onRightClick);
        registerChunkProtectionEvents(bus);
    }

    private static void registerChunkProtectionEvents(IEventBus bus) {
        bus.addListener(EventPriority.LOWEST, CadmusNeoForge::onBlockPlace);
        bus.addListener(EventPriority.LOWEST, CadmusNeoForge::onBlockBreak);
        bus.addListener(EventPriority.LOWEST, CadmusNeoForge::onBlockInteract);
        bus.addListener(EventPriority.LOWEST, CadmusNeoForge::onEntityInteract);
        bus.addListener(EventPriority.LOWEST, CadmusNeoForge::onAttackBlock);
        bus.addListener(EventPriority.LOWEST, CadmusNeoForge::onAttackEntity);
        bus.addListener(EventPriority.LOWEST, CadmusNeoForge::onFillBucket);
        bus.addListener(EventPriority.LOWEST, CadmusNeoForge::onExplode);
        bus.addListener(EventPriority.LOWEST, CadmusNeoForge::onFarmLandTrample);
        bus.addListener(EventPriority.LOWEST, CadmusNeoForge::onEntityMobGriefing);
        bus.addListener(EventPriority.LOWEST, CadmusNeoForge::onLivingDestroyBlock);
        bus.addListener(EventPriority.LOWEST, CadmusNeoForge::onItemPickup);
        bus.addListener(EventPriority.LOWEST, CadmusNeoForge::onEntityStruckByLightning);
        bus.addListener(EventPriority.LOWEST, CadmusNeoForge::onProjectileImpact);
        bus.addListener(EventPriority.LOWEST, CadmusNeoForge::onLivingAttack);
        bus.addListener(EventPriority.LOWEST, CadmusNeoForge::onPistonPush);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher(), event.getBuildContext(), event.getCommandSelection());
    }

    private static void onServerStarted(ServerStartedEvent event) {
        Cadmus.serverStarted(event.getServer());
    }

    private static void onEnterSection(EntityEvent.EnteringSection event) {
        if (event.getEntity() instanceof Player player) {
            Cadmus.enterChunkSection(player, event.getOldPos().chunk());
        }
    }

    private static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!ClaimApi.API.canBreakBlock(event.getPlayer().level(), event.getPos(), event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    private static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (!ClaimApi.API.canPlaceBlock(player.level(), event.getPos(), player)) {
                event.setCanceled(true);
            }
        } else if (event.getEntity() != null && !ClaimApi.API.canEntityGrief(event.getEntity().level(), event.getPos(), event.getEntity())) {
            event.setCanceled(true);
        }
    }

    private static void onBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        if (!ClaimApi.API.canInteractWithBlock(event.getEntity().level(), event.getPos(), InteractionType.USE, event.getEntity())) {
            event.setCanceled(true);
        }
    }

    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!ClaimApi.API.canInteractWithEntity(event.getEntity().level(), event.getTarget(), event.getEntity())) {
            event.setCanceled(true);
        }
    }

    private static void onAttackBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!ClaimApi.API.canInteractWithBlock(event.getEntity().level(), event.getPos(), InteractionType.ATTACK, event.getEntity())) {
            event.setCanceled(true);
        }
    }

    private static void onAttackEntity(AttackEntityEvent event) {
        if (!ClaimApi.API.canDamageEntity(event.getEntity().level(), event.getTarget(), event.getEntity())) {
            event.setCanceled(true);
        }
    }

    // Prevent players from using buckets in protected chunks
    private static void onFillBucket(FillBucketEvent event) {
        var target = event.getTarget();
        if (target != null && !ClaimApi.API.canBreakBlock(event.getLevel(), BlockPos.containing(target.getLocation()), event.getEntity())) {
            event.setResult(Event.Result.DENY);
            event.setCanceled(true);
        }
    }

    // Prevent explosions from destroying blocks in protected chunks
    private static void onExplode(ExplosionEvent.Detonate event) {
        Player player = event.getExplosion().getIndirectSourceEntity() instanceof Player p ? p : null;
        event.getAffectedBlocks().removeIf(next -> (ClaimApi.API.canExplodeBlock(event.getLevel(), new ChunkPos(next)) && (player == null || !ClaimApi.API.canExplodeBlock(event.getLevel(), next, event.getExplosion(), player))));
        event.getAffectedEntities().removeIf(next -> (ClaimApi.API.canExplodeBlock(event.getLevel(), next.chunkPosition()) && (player == null || !ClaimApi.API.canDamageEntity(event.getLevel(), next, player))));
    }

    // Prevent players from trampling crops in protected chunks
    private static void onFarmLandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (!ClaimApi.API.canBreakBlock(player.level(), event.getPos(), player)) {
                event.setCanceled(true);
            }
        } else if (!ClaimApi.API.canEntityGrief(event.getEntity().level(), event.getPos(), event.getEntity())) {
            event.setCanceled(true);
        }
    }

    private static void onEntityMobGriefing(EntityMobGriefingEvent event) {
        if (!ClaimApi.API.canEntityGrief(event.getEntity().level(), event.getEntity())) {
            event.setResult(Event.Result.DENY);
        }
    }

    // Prevent mobs destroying blocks in protected chunks
    private static void onLivingDestroyBlock(LivingDestroyBlockEvent event) {
        if (!ClaimApi.API.canEntityGrief(event.getEntity().level(), event.getPos(), event.getEntity())) {
            event.setCanceled(true);
        }
    }

    // Prevent players from picking up items in protected chunks unless they dropped them
    private static void onItemPickup(EntityItemPickupEvent event) {
        if (!ClaimApi.API.canPickupItem(event.getItem().level(), event.getItem().blockPosition(), event.getItem(), event.getEntity())) {
            event.setResult(Event.Result.DENY);
            event.setCanceled(true);
        }
    }

    // Prevent entities from being affected by lightning in protected chunks
    private static void onEntityStruckByLightning(EntityStruckByLightningEvent event) {
        if (!event.getEntity().level().isClientSide()) {
            var claim = ClaimHandler.getClaim((ServerLevel) event.getEntity().level(), event.getEntity().chunkPosition());
            if (claim != null && ModUtils.isAdmin(claim.getFirst())) {
                event.setCanceled(!AdminClaimHandler.getBooleanFlag(event.getEntity().level().getServer(), claim.getFirst(), ModFlags.LIGHTNING));
            }
            if (event.getLightning().getCause() != null) {
                if (!ClaimApi.API.canDamageEntity(event.getEntity().level(), event.getEntity(), event.getLightning().getCause())) {
                    event.setCanceled(true);
                }
            } else if (!ClaimApi.API.canEntityGrief(event.getLightning().level(), event.getLightning())) {
                event.setCanceled(true);
            }
        }
    }

    private static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getProjectile().getOwner() instanceof Player player) {
            if (!ClaimApi.API.canDamageEntity(event.getEntity().level(), event.getEntity(), player)) {
                event.setCanceled(true);
            }
        }
    }

    // Prevent mobs from taking damage from players in protected chunks
    private static void onLivingAttack(LivingAttackEvent event) {
        if (event.getSource().getEntity() instanceof Player player) {
            if (!ClaimApi.API.canDamageEntity(event.getEntity().level(), event.getEntity(), player)) {
                event.setCanceled(true);
            }
        }
    }

    private static void onPistonPush(PistonEvent.Pre event) {
        // TODO
    }

    private static void onRightClick(PlayerInteractEvent.RightClickItem event) {
        if (!event.getLevel().isClientSide()) {
            var id = ClaimHandler.getClaim((ServerLevel) event.getLevel(), event.getEntity().chunkPosition());
            if (id == null) return;
            if (!AdminClaimHandler.getBooleanFlag(event.getLevel().getServer(), id.getFirst(), ModFlags.USE)) {
                event.setCanceled(true);
            }
        }
    }
}