package net.minecraft.client.network;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.StreamSupport;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.Portal.Effect;
import net.minecraft.block.entity.CommandBlockBlockEntity;
import net.minecraft.block.entity.HangingSignBlockEntity;
import net.minecraft.block.entity.JigsawBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.StructureBlockBlockEntity;
import net.minecraft.block.entity.TestBlockEntity;
import net.minecraft.block.entity.TestInstanceBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.gui.screen.ingame.CommandBlockScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.HangingSignEditScreen;
import net.minecraft.client.gui.screen.ingame.JigsawBlockScreen;
import net.minecraft.client.gui.screen.ingame.MinecartCommandBlockScreen;
import net.minecraft.client.gui.screen.ingame.SignEditScreen;
import net.minecraft.client.gui.screen.ingame.StructureBlockScreen;
import net.minecraft.client.gui.screen.ingame.TestBlockScreen;
import net.minecraft.client.gui.screen.ingame.TestInstanceBlockScreen;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;
import net.minecraft.client.input.Input;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.client.sound.AmbientSoundLoops;
import net.minecraft.client.sound.AmbientSoundPlayer;
import net.minecraft.client.sound.BiomeEffectSoundPlayer;
import net.minecraft.client.sound.BubbleColumnSoundPlayer;
import net.minecraft.client.sound.ElytraSoundInstance;
import net.minecraft.client.sound.HappyGhastRidingSoundInstance;
import net.minecraft.client.sound.MinecartInsideSoundInstance;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.ClientPlayerTickable;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.command.permission.LeveledPermissionPredicate;
import net.minecraft.command.permission.PermissionPredicate;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttackRangeComponent;
import net.minecraft.component.type.UseEffectsComponent;
import net.minecraft.component.type.WritableBookContentComponent;
import net.minecraft.dialog.type.Dialog;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.JumpingMount;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.Entity.RemovalReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.AbstractNautilusEntity;
import net.minecraft.entity.passive.HappyGhastEntity;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.CommandBlockMinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.RecipeBookDataC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdatePlayerAbilitiesC2SPacket;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Full;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.OnGroundOnly;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.recipe.NetworkRecipeId;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.StatHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Arm;
import net.minecraft.util.ClickType;
import net.minecraft.util.Cooldown;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.math.Direction.AxisDirection;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.GameMode;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Represents the client's own player entity.
 */
@Environment(EnvType.CLIENT)
public class ClientPlayerEntity extends AbstractClientPlayerEntity {
	public static final Logger LOGGER = LogUtils.getLogger();
	private static final int field_32671 = 20;
	private static final int field_32672 = 600;
	private static final int field_32673 = 100;
	private static final float field_32674 = 0.6F;
	private static final double field_32675 = 0.35;
	private static final double MAX_SOFT_COLLISION_RADIANS = 0.13962634F;
	public final ClientPlayNetworkHandler networkHandler;
	private final StatHandler statHandler;
	private final ClientRecipeBook recipeBook;
	private final Cooldown itemDropCooldown = new Cooldown(20, 1280);
	private final List<ClientPlayerTickable> tickables = Lists.<ClientPlayerTickable>newArrayList();
	private PermissionPredicate permissions = PermissionPredicate.NONE;
	private double lastXClient;
	private double lastYClient;
	private double lastZClient;
	private float lastYawClient;
	private float lastPitchClient;
	private boolean lastOnGround;
	private boolean lastHorizontalCollision;
	private boolean inSneakingPose;
	private boolean lastSprinting;
	private int ticksSinceLastPositionPacketSent;
	private boolean healthInitialized;
	public Input input = new Input();
	private PlayerInput lastPlayerInput;
	protected final MinecraftClient client;
	protected int ticksLeftToDoubleTapSprint;
	private static final int field_63570 = Integer.MIN_VALUE;
	private static final int field_63571 = -2147483647;
	public int experienceBarDisplayStartTime = Integer.MIN_VALUE;
	public float renderYaw;
	public float renderPitch;
	public float lastRenderYaw;
	public float lastRenderPitch;
	private int mountJumpTicks;
	private float mountJumpStrength;
	public float nauseaIntensity;
	public float lastNauseaIntensity;
	private boolean usingItem;
	@Nullable
	private Hand activeHand;
	private boolean riding;
	private boolean autoJumpEnabled = true;
	private int ticksToNextAutoJump;
	private boolean falling;
	private int underwaterVisibilityTicks;
	private boolean showsDeathScreen = true;
	private boolean limitedCraftingEnabled = false;

	public ClientPlayerEntity(
		MinecraftClient client,
		ClientWorld world,
		ClientPlayNetworkHandler networkHandler,
		StatHandler stats,
		ClientRecipeBook recipeBook,
		PlayerInput lastPlayerInput,
		boolean lastSprinting
	) {
		super(world, networkHandler.getProfile());
		this.client = client;
		this.networkHandler = networkHandler;
		this.statHandler = stats;
		this.recipeBook = recipeBook;
		this.lastPlayerInput = lastPlayerInput;
		this.lastSprinting = lastSprinting;
		this.tickables.add(new AmbientSoundPlayer(this, client.getSoundManager()));
		this.tickables.add(new BubbleColumnSoundPlayer(this));
		this.tickables.add(new BiomeEffectSoundPlayer(this, client.getSoundManager()));
	}

	public void heal(float amount) {
	}

	public boolean startRiding(Entity entity, boolean force, boolean emitEvent) {
		if (!super.startRiding(entity, force, emitEvent)) {
			return false;
		} else {
			if (entity instanceof AbstractMinecartEntity abstractMinecartEntity) {
				this.client
					.getSoundManager()
					.play(new MinecartInsideSoundInstance(this, abstractMinecartEntity, true, SoundEvents.ENTITY_MINECART_INSIDE_UNDERWATER, 0.0F, 0.75F, 1.0F));
				this.client
					.getSoundManager()
					.play(new MinecartInsideSoundInstance(this, abstractMinecartEntity, false, SoundEvents.ENTITY_MINECART_INSIDE, 0.0F, 0.75F, 1.0F));
			} else if (entity instanceof HappyGhastEntity happyGhastEntity) {
				this.client
					.getSoundManager()
					.play(
						new HappyGhastRidingSoundInstance(
							this, happyGhastEntity, false, SoundEvents.ENTITY_HAPPY_GHAST_RIDING, happyGhastEntity.getSoundCategory(), 0.0F, 1.0F, 5.0F
						)
					);
			} else if (entity instanceof AbstractNautilusEntity abstractNautilusEntity) {
				this.client
					.getSoundManager()
					.play(
						new HappyGhastRidingSoundInstance(
							this, abstractNautilusEntity, true, SoundEvents.ENTITY_NAUTILUS_RIDING, abstractNautilusEntity.getSoundCategory(), 0.0F, 1.0F, 5.0F
						)
					);
			}

			return true;
		}
	}

	public void dismountVehicle() {
		super.dismountVehicle();
		this.riding = false;
	}

	public float getPitch(float tickProgress) {
		return this.getPitch();
	}

	public float getYaw(float tickProgress) {
		return this.hasVehicle() ? super.getYaw(tickProgress) : this.getYaw();
	}

	@Override
	public void tick() {
		if (this.networkHandler.method_76760()) {
			this.itemDropCooldown.tick();
			super.tick();
			if (!this.lastPlayerInput.equals(this.input.playerInput)) {
				this.networkHandler.sendPacket(new PlayerInputC2SPacket(this.input.playerInput));
				this.lastPlayerInput = this.input.playerInput;
			}

			if (this.hasVehicle()) {
				this.networkHandler.sendPacket(new LookAndOnGround(this.getYaw(), this.getPitch(), this.isOnGround(), this.horizontalCollision));
				Entity entity = this.getRootVehicle();
				if (entity != this && entity.isLogicalSideForUpdatingMovement()) {
					this.networkHandler.sendPacket(VehicleMoveC2SPacket.fromVehicle(entity));
					this.sendSprintingPacket();
				}
			} else {
				this.sendMovementPackets();
			}

			for (ClientPlayerTickable clientPlayerTickable : this.tickables) {
				clientPlayerTickable.tick();
			}
		}
	}

	/**
	 * {@return the percentage for the biome mood sound for the debug HUD to
	 * display}
	 */
	public float getMoodPercentage() {
		for (ClientPlayerTickable clientPlayerTickable : this.tickables) {
			if (clientPlayerTickable instanceof BiomeEffectSoundPlayer) {
				return ((BiomeEffectSoundPlayer)clientPlayerTickable).getMoodPercentage();
			}
		}

		return 0.0F;
	}

	private void sendMovementPackets() {
		this.sendSprintingPacket();
		if (this.isCamera()) {
			double d = this.getX() - this.lastXClient;
			double e = this.getY() - this.lastYClient;
			double f = this.getZ() - this.lastZClient;
			double g = this.getYaw() - this.lastYawClient;
			double h = this.getPitch() - this.lastPitchClient;
			this.ticksSinceLastPositionPacketSent++;
			boolean bl = MathHelper.squaredMagnitude(d, e, f) > MathHelper.square(2.0E-4) || this.ticksSinceLastPositionPacketSent >= 20;
			boolean bl2 = g != 0.0 || h != 0.0;
			if (bl && bl2) {
				this.networkHandler.sendPacket(new Full(this.getEntityPos(), this.getYaw(), this.getPitch(), this.isOnGround(), this.horizontalCollision));
			} else if (bl) {
				this.networkHandler.sendPacket(new PositionAndOnGround(this.getEntityPos(), this.isOnGround(), this.horizontalCollision));
			} else if (bl2) {
				this.networkHandler.sendPacket(new LookAndOnGround(this.getYaw(), this.getPitch(), this.isOnGround(), this.horizontalCollision));
			} else if (this.lastOnGround != this.isOnGround() || this.lastHorizontalCollision != this.horizontalCollision) {
				this.networkHandler.sendPacket(new OnGroundOnly(this.isOnGround(), this.horizontalCollision));
			}

			if (bl) {
				this.lastXClient = this.getX();
				this.lastYClient = this.getY();
				this.lastZClient = this.getZ();
				this.ticksSinceLastPositionPacketSent = 0;
			}

			if (bl2) {
				this.lastYawClient = this.getYaw();
				this.lastPitchClient = this.getPitch();
			}

			this.lastOnGround = this.isOnGround();
			this.lastHorizontalCollision = this.horizontalCollision;
			this.autoJumpEnabled = this.client.options.getAutoJump().getValue();
		}
	}

	private void sendSprintingPacket() {
		boolean bl = this.isSprinting();
		if (bl != this.lastSprinting) {
			Mode mode = bl ? Mode.START_SPRINTING : Mode.STOP_SPRINTING;
			this.networkHandler.sendPacket(new ClientCommandC2SPacket(this, mode));
			this.lastSprinting = bl;
		}
	}

	public boolean dropSelectedItem(boolean entireStack) {
		Action action = entireStack ? Action.DROP_ALL_ITEMS : Action.DROP_ITEM;
		ItemStack itemStack = this.getInventory().dropSelectedItem(entireStack);
		this.networkHandler.sendPacket(new PlayerActionC2SPacket(action, BlockPos.ORIGIN, Direction.DOWN));
		return !itemStack.isEmpty();
	}

	public void swingHand(Hand hand) {
		super.swingHand(hand);
		this.networkHandler.sendPacket(new HandSwingC2SPacket(hand));
	}

	public void requestRespawn() {
		this.networkHandler.sendPacket(new ClientStatusC2SPacket(net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket.Mode.PERFORM_RESPAWN));
		KeyBinding.untoggleStickyKeys();
	}

	public void closeHandledScreen() {
		this.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(this.currentScreenHandler.syncId));
		this.closeScreen();
	}

	public void closeScreen() {
		super.closeHandledScreen();
		this.client.setScreen(null);
	}

	public void updateHealth(float health) {
		if (this.healthInitialized) {
			float f = this.getHealth() - health;
			if (f <= 0.0F) {
				this.setHealth(health);
				if (f < 0.0F) {
					this.timeUntilRegen = 10;
				}
			} else {
				this.lastDamageTaken = f;
				this.timeUntilRegen = 20;
				this.setHealth(health);
				this.maxHurtTime = 10;
				this.hurtTime = this.maxHurtTime;
			}
		} else {
			this.setHealth(health);
			this.healthInitialized = true;
		}
	}

	public void sendAbilitiesUpdate() {
		this.networkHandler.sendPacket(new UpdatePlayerAbilitiesC2SPacket(this.getAbilities()));
	}

	public void setReducedDebugInfo(boolean reducedDebugInfo) {
		super.setReducedDebugInfo(reducedDebugInfo);
		this.client.debugHudEntryList.updateVisibleEntries();
	}

	public boolean isMainPlayer() {
		return true;
	}

	public boolean isHoldingOntoLadder() {
		return !this.getAbilities().flying && super.isHoldingOntoLadder();
	}

	public boolean shouldSpawnSprintingParticles() {
		return !this.getAbilities().flying && super.shouldSpawnSprintingParticles();
	}

	protected void startRidingJump() {
		this.networkHandler.sendPacket(new ClientCommandC2SPacket(this, Mode.START_RIDING_JUMP, MathHelper.floor(this.getMountJumpStrength() * 100.0F)));
	}

	public void openRidingInventory() {
		this.networkHandler.sendPacket(new ClientCommandC2SPacket(this, Mode.OPEN_INVENTORY));
	}

	public StatHandler getStatHandler() {
		return this.statHandler;
	}

	public ClientRecipeBook getRecipeBook() {
		return this.recipeBook;
	}

	public void onRecipeDisplayed(NetworkRecipeId recipeId) {
		if (this.recipeBook.isHighlighted(recipeId)) {
			this.recipeBook.unmarkHighlighted(recipeId);
			this.networkHandler.sendPacket(new RecipeBookDataC2SPacket(recipeId));
		}
	}

	public PermissionPredicate getPermissions() {
		return this.permissions;
	}

	public void setPermissions(PermissionPredicate permissions) {
		this.permissions = permissions;
	}

	public void sendMessage(Text message, boolean overlay) {
		this.client.getMessageHandler().onGameMessage(message, overlay);
	}

	private void pushOutOfBlocks(double x, double z) {
		BlockPos blockPos = BlockPos.ofFloored(x, this.getY(), z);
		if (this.wouldCollideAt(blockPos)) {
			double d = x - blockPos.getX();
			double e = z - blockPos.getZ();
			Direction direction = null;
			double f = Double.MAX_VALUE;
			Direction[] directions = new Direction[]{Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH};

			for (Direction direction2 : directions) {
				double g = direction2.getAxis().choose(d, 0.0, e);
				double h = direction2.getDirection() == AxisDirection.POSITIVE ? 1.0 - g : g;
				if (h < f && !this.wouldCollideAt(blockPos.offset(direction2))) {
					f = h;
					direction = direction2;
				}
			}

			if (direction != null) {
				Vec3d vec3d = this.getVelocity();
				if (direction.getAxis() == Axis.X) {
					this.setVelocity(0.1 * direction.getOffsetX(), vec3d.y, vec3d.z);
				} else {
					this.setVelocity(vec3d.x, vec3d.y, 0.1 * direction.getOffsetZ());
				}
			}
		}
	}

	private boolean wouldCollideAt(BlockPos pos) {
		Box box = this.getBoundingBox();
		Box box2 = new Box(pos.getX(), box.minY, pos.getZ(), pos.getX() + 1.0, box.maxY, pos.getZ() + 1.0).contract(1.0E-7);
		return this.getEntityWorld().canCollide(this, box2);
	}

	public void setExperience(float progress, int total, int level) {
		if (progress != this.experienceProgress) {
			this.updateExperienceBarDisplayStartTime();
		}

		this.experienceProgress = progress;
		this.totalExperience = total;
		this.experienceLevel = level;
	}

	private void updateExperienceBarDisplayStartTime() {
		if (this.experienceBarDisplayStartTime == Integer.MIN_VALUE) {
			this.experienceBarDisplayStartTime = -2147483647;
		} else {
			this.experienceBarDisplayStartTime = this.age;
		}
	}

	public void handleStatus(byte status) {
		switch (status) {
			case 24:
				this.setPermissions(PermissionPredicate.NONE);
				break;
			case 25:
				this.setPermissions(LeveledPermissionPredicate.MODERATORS);
				break;
			case 26:
				this.setPermissions(LeveledPermissionPredicate.GAMEMASTERS);
				break;
			case 27:
				this.setPermissions(LeveledPermissionPredicate.ADMINS);
				break;
			case 28:
				this.setPermissions(LeveledPermissionPredicate.OWNERS);
				break;
			default:
				super.handleStatus(status);
		}
	}

	public void setShowsDeathScreen(boolean showsDeathScreen) {
		this.showsDeathScreen = showsDeathScreen;
	}

	public boolean showsDeathScreen() {
		return this.showsDeathScreen;
	}

	public void setLimitedCraftingEnabled(boolean limitedCraftingEnabled) {
		this.limitedCraftingEnabled = limitedCraftingEnabled;
	}

	public boolean isLimitedCraftingEnabled() {
		return this.limitedCraftingEnabled;
	}

	public void playSound(SoundEvent sound, float volume, float pitch) {
		this.getEntityWorld().playSoundClient(this.getX(), this.getY(), this.getZ(), sound, this.getSoundCategory(), volume, pitch, false);
	}

	public void setCurrentHand(Hand hand) {
		ItemStack itemStack = this.getStackInHand(hand);
		if (!itemStack.isEmpty() && !this.isUsingItem()) {
			super.setCurrentHand(hand);
			this.usingItem = true;
			this.activeHand = hand;
		}
	}

	public boolean isUsingItem() {
		return this.usingItem;
	}

	private boolean isBlockedFromSprinting() {
		return this.isUsingItem()
			&& !((UseEffectsComponent)this.activeItemStack.getOrDefault(DataComponentTypes.USE_EFFECTS, UseEffectsComponent.DEFAULT)).canSprint();
	}

	private float getActiveItemSpeedMultiplier() {
		return ((UseEffectsComponent)this.activeItemStack.getOrDefault(DataComponentTypes.USE_EFFECTS, UseEffectsComponent.DEFAULT)).speedMultiplier();
	}

	public void clearActiveItem() {
		super.clearActiveItem();
		this.usingItem = false;
	}

	public Hand getActiveHand() {
		return (Hand)Objects.requireNonNullElse(this.activeHand, Hand.MAIN_HAND);
	}

	public void onTrackedDataSet(TrackedData<?> data) {
		super.onTrackedDataSet(data);
		if (LIVING_FLAGS.equals(data)) {
			boolean bl = ((Byte)this.dataTracker.get(LIVING_FLAGS) & 1) > 0;
			Hand hand = (this.dataTracker.get(LIVING_FLAGS) & 2) > 0 ? Hand.OFF_HAND : Hand.MAIN_HAND;
			if (bl && !this.usingItem) {
				this.setCurrentHand(hand);
			} else if (!bl && this.usingItem) {
				this.clearActiveItem();
			}
		}

		if (FLAGS.equals(data) && this.isGliding() && !this.falling) {
			this.client.getSoundManager().play(new ElytraSoundInstance(this));
		}
	}

	@Nullable
	public JumpingMount getJumpingMount() {
		return this.getControllingVehicle() instanceof JumpingMount jumpingMount && jumpingMount.canJump() ? jumpingMount : null;
	}

	public float getMountJumpStrength() {
		return this.mountJumpStrength;
	}

	public boolean shouldFilterText() {
		return this.client.shouldFilterText();
	}

	public void openEditSignScreen(SignBlockEntity sign, boolean front) {
		if (sign instanceof HangingSignBlockEntity hangingSignBlockEntity) {
			this.client.setScreen(new HangingSignEditScreen(hangingSignBlockEntity, front, this.client.shouldFilterText()));
		} else {
			this.client.setScreen(new SignEditScreen(sign, front, this.client.shouldFilterText()));
		}
	}

	public void openCommandBlockMinecartScreen(CommandBlockMinecartEntity minecart) {
		this.client.setScreen(new MinecartCommandBlockScreen(minecart));
	}

	public void openCommandBlockScreen(CommandBlockBlockEntity commandBlock) {
		this.client.setScreen(new CommandBlockScreen(commandBlock));
	}

	public void openStructureBlockScreen(StructureBlockBlockEntity structureBlock) {
		this.client.setScreen(new StructureBlockScreen(structureBlock));
	}

	public void openTestBlockScreen(TestBlockEntity testBlock) {
		this.client.setScreen(new TestBlockScreen(testBlock));
	}

	public void openTestInstanceBlockScreen(TestInstanceBlockEntity testInstanceBlock) {
		this.client.setScreen(new TestInstanceBlockScreen(testInstanceBlock));
	}

	public void openJigsawScreen(JigsawBlockEntity jigsaw) {
		this.client.setScreen(new JigsawBlockScreen(jigsaw));
	}

	public void openDialog(RegistryEntry<Dialog> dialog) {
		this.networkHandler.showDialog(dialog, this.client.currentScreen);
	}

	public void useBook(ItemStack book, Hand hand) {
		WritableBookContentComponent writableBookContentComponent = (WritableBookContentComponent)book.get(DataComponentTypes.WRITABLE_BOOK_CONTENT);
		if (writableBookContentComponent != null) {
			this.client.setScreen(new BookEditScreen(this, book, hand, writableBookContentComponent));
		}
	}

	public void addCritParticles(Entity target) {
		this.client.particleManager.addEmitter(target, ParticleTypes.CRIT);
	}

	public void addEnchantedHitParticles(Entity target) {
		this.client.particleManager.addEmitter(target, ParticleTypes.ENCHANTED_HIT);
	}

	public boolean isSneaking() {
		return this.input.playerInput.sneak();
	}

	public boolean isInSneakingPose() {
		return this.inSneakingPose;
	}

	public boolean shouldSlowDown() {
		return this.isInSneakingPose() || this.isCrawling();
	}

	public void tickMovementInput() {
		if (this.isCamera()) {
			Vec2f vec2f = this.applyMovementSpeedFactors(this.input.getMovementInput());
			this.sidewaysSpeed = vec2f.x;
			this.forwardSpeed = vec2f.y;
			this.jumping = this.input.playerInput.jump();
			this.lastRenderYaw = this.renderYaw;
			this.lastRenderPitch = this.renderPitch;
			this.renderPitch = this.renderPitch + (this.getPitch() - this.renderPitch) * 0.5F;
			this.renderYaw = this.renderYaw + (this.getYaw() - this.renderYaw) * 0.5F;
		} else {
			super.tickMovementInput();
		}
	}

	private Vec2f applyMovementSpeedFactors(Vec2f input) {
		if (input.lengthSquared() == 0.0F) {
			return input;
		} else {
			Vec2f vec2f = input.multiply(0.98F);
			if (this.isUsingItem() && !this.hasVehicle()) {
				vec2f = vec2f.multiply(this.getActiveItemSpeedMultiplier());
			}

			if (this.shouldSlowDown()) {
				float f = (float)this.getAttributeValue(EntityAttributes.SNEAKING_SPEED);
				vec2f = vec2f.multiply(f);
			}

			return applyDirectionalMovementSpeedFactors(vec2f);
		}
	}

	private static Vec2f applyDirectionalMovementSpeedFactors(Vec2f vec) {
		float f = vec.length();
		if (f <= 0.0F) {
			return vec;
		} else {
			Vec2f vec2f = vec.multiply(1.0F / f);
			float g = getDirectionalMovementSpeedMultiplier(vec2f);
			float h = Math.min(f * g, 1.0F);
			return vec2f.multiply(h);
		}
	}

	private static float getDirectionalMovementSpeedMultiplier(Vec2f vec) {
		float f = Math.abs(vec.x);
		float g = Math.abs(vec.y);
		float h = g > f ? f / g : g / f;
		return MathHelper.sqrt(1.0F + MathHelper.square(h));
	}

	protected boolean isCamera() {
		return this.client.getCameraEntity() == this;
	}

	public void init() {
		this.setPose(EntityPose.STANDING);
		if (this.getEntityWorld() != null) {
			for (double d = this.getY(); d > this.getEntityWorld().getBottomY() && d <= this.getEntityWorld().getTopYInclusive(); d++) {
				this.setPosition(this.getX(), d, this.getZ());
				if (this.getEntityWorld().isSpaceEmpty(this)) {
					break;
				}
			}

			this.setVelocity(Vec3d.ZERO);
			this.setPitch(0.0F);
		}

		this.setHealth(this.getMaxHealth());
		this.deathTime = 0;
	}

	@Override
	public void tickMovement() {
		if (this.ticksLeftToDoubleTapSprint > 0) {
			this.ticksLeftToDoubleTapSprint--;
		}

		if (!(this.client.currentScreen instanceof LevelLoadingScreen)) {
			this.tickNausea(this.getCurrentPortalEffect() == Effect.CONFUSION);
			this.tickPortalCooldown();
		}

		boolean bl = this.input.playerInput.jump();
		boolean bl2 = this.input.playerInput.sneak();
		boolean bl3 = this.input.hasForwardMovement();
		PlayerAbilities playerAbilities = this.getAbilities();
		this.inSneakingPose = !playerAbilities.flying
			&& !this.isSwimming()
			&& !this.hasVehicle()
			&& this.canChangeIntoPose(EntityPose.CROUCHING)
			&& (this.isSneaking() || !this.isSleeping() && !this.canChangeIntoPose(EntityPose.STANDING));
		this.input.tick();
		this.client.getTutorialManager().onMovement(this.input);
		boolean bl4 = false;
		if (this.ticksToNextAutoJump > 0) {
			this.ticksToNextAutoJump--;
			bl4 = true;
			this.input.jump();
		}

		if (!this.noClip) {
			this.pushOutOfBlocks(this.getX() - this.getWidth() * 0.35, this.getZ() + this.getWidth() * 0.35);
			this.pushOutOfBlocks(this.getX() - this.getWidth() * 0.35, this.getZ() - this.getWidth() * 0.35);
			this.pushOutOfBlocks(this.getX() + this.getWidth() * 0.35, this.getZ() - this.getWidth() * 0.35);
			this.pushOutOfBlocks(this.getX() + this.getWidth() * 0.35, this.getZ() + this.getWidth() * 0.35);
		}

		if (bl2 || this.isBlockedFromSprinting() && !this.hasVehicle() || this.input.playerInput.backward()) {
			this.ticksLeftToDoubleTapSprint = 0;
		}

		if (this.canStartSprinting()) {
			if (!bl3) {
				if (this.ticksLeftToDoubleTapSprint > 0) {
					this.setSprinting(true);
				} else {
					this.ticksLeftToDoubleTapSprint = this.client.options.getSprintWindow().getValue();
				}
			}

			if (this.input.playerInput.sprint()) {
				this.setSprinting(true);
			}
		}

		if (this.isSprinting()) {
			if (this.isSwimming()) {
				if (this.shouldStopSwimSprinting()) {
					this.setSprinting(false);
				}
			} else if (this.shouldStopSprinting()) {
				this.setSprinting(false);
			}
		}

		boolean bl5 = false;
		if (playerAbilities.allowFlying) {
			if (this.client.interactionManager.isFlyingLocked()) {
				if (!playerAbilities.flying) {
					playerAbilities.flying = true;
					bl5 = true;
					this.sendAbilitiesUpdate();
				}
			} else if (!bl && this.input.playerInput.jump() && !bl4) {
				if (this.abilityResyncCountdown == 0) {
					this.abilityResyncCountdown = 7;
				} else if (!this.isSwimming() && (this.getVehicle() == null || this.getJumpingMount() != null)) {
					playerAbilities.flying = !playerAbilities.flying;
					if (playerAbilities.flying && this.isOnGround()) {
						this.jump();
					}

					bl5 = true;
					this.sendAbilitiesUpdate();
					this.abilityResyncCountdown = 0;
				}
			}
		}

		if (this.input.playerInput.jump() && !bl5 && !bl && !this.isClimbing() && this.checkGliding()) {
			this.networkHandler.sendPacket(new ClientCommandC2SPacket(this, Mode.START_FALL_FLYING));
		}

		this.falling = this.isGliding();
		if (this.isTouchingWater() && this.input.playerInput.sneak() && this.shouldSwimInFluids()) {
			this.knockDownwards();
		}

		if (this.isSubmergedIn(FluidTags.WATER)) {
			int i = this.isSpectator() ? 10 : 1;
			this.underwaterVisibilityTicks = MathHelper.clamp(this.underwaterVisibilityTicks + i, 0, 600);
		} else if (this.underwaterVisibilityTicks > 0) {
			this.isSubmergedIn(FluidTags.WATER);
			this.underwaterVisibilityTicks = MathHelper.clamp(this.underwaterVisibilityTicks - 10, 0, 600);
		}

		if (playerAbilities.flying && this.isCamera()) {
			int i = 0;
			if (this.input.playerInput.sneak()) {
				i--;
			}

			if (this.input.playerInput.jump()) {
				i++;
			}

			if (i != 0) {
				this.setVelocity(this.getVelocity().add(0.0, i * playerAbilities.getFlySpeed() * 3.0F, 0.0));
			}
		}

		JumpingMount jumpingMount = this.getJumpingMount();
		if (jumpingMount != null && jumpingMount.getJumpCooldown() == 0) {
			if (this.mountJumpTicks < 0) {
				this.mountJumpTicks++;
				if (this.mountJumpTicks == 0) {
					this.mountJumpStrength = 0.0F;
				}
			}

			if (bl && !this.input.playerInput.jump()) {
				this.mountJumpTicks = -10;
				jumpingMount.setJumpStrength(MathHelper.floor(this.getMountJumpStrength() * 100.0F));
				this.startRidingJump();
			} else if (!bl && this.input.playerInput.jump()) {
				this.mountJumpTicks = 0;
				this.mountJumpStrength = 0.0F;
			} else if (bl) {
				this.mountJumpTicks++;
				if (this.mountJumpTicks < 10) {
					this.mountJumpStrength = this.mountJumpTicks * 0.1F;
				} else {
					this.mountJumpStrength = 0.8F + 2.0F / (this.mountJumpTicks - 9) * 0.1F;
				}
			}
		} else {
			this.mountJumpStrength = 0.0F;
		}

		super.tickMovement();
		if (this.isOnGround() && playerAbilities.flying && !this.client.interactionManager.isFlyingLocked()) {
			playerAbilities.flying = false;
			this.sendAbilitiesUpdate();
		}
	}

	private boolean shouldStopSprinting() {
		return !this.canSprint(this.getAbilities().flying) || !this.input.hasForwardMovement() || this.horizontalCollision && !this.collidedSoftly;
	}

	private boolean shouldStopSwimSprinting() {
		return !this.canSprint(true) || !this.isTouchingWater() || !this.input.hasForwardMovement() && !this.isOnGround() && !this.input.playerInput.sneak();
	}

	public Effect getCurrentPortalEffect() {
		return this.portalManager == null ? Effect.NONE : this.portalManager.getEffect();
	}

	protected void updatePostDeath() {
		this.deathTime++;
		if (this.deathTime == 20) {
			this.remove(RemovalReason.KILLED);
		}
	}

	private void tickNausea(boolean fromPortalEffect) {
		this.lastNauseaIntensity = this.nauseaIntensity;
		float f = 0.0F;
		if (fromPortalEffect && this.portalManager != null && this.portalManager.isInPortal()) {
			if (this.client.currentScreen != null && !this.client.currentScreen.keepOpenThroughPortal()) {
				if (this.client.currentScreen instanceof HandledScreen) {
					this.closeHandledScreen();
				}

				this.client.setScreen(null);
			}

			if (this.nauseaIntensity == 0.0F) {
				this.client.getSoundManager().play(PositionedSoundInstance.ambient(SoundEvents.BLOCK_PORTAL_TRIGGER, this.random.nextFloat() * 0.4F + 0.8F, 0.25F));
			}

			f = 0.0125F;
			this.portalManager.setInPortal(false);
		} else if (this.nauseaIntensity > 0.0F) {
			f = -0.05F;
		}

		this.nauseaIntensity = MathHelper.clamp(this.nauseaIntensity + f, 0.0F, 1.0F);
	}

	@Override
	public void tickRiding() {
		super.tickRiding();
		this.riding = false;
		if (this.getControllingVehicle() instanceof AbstractBoatEntity abstractBoatEntity) {
			abstractBoatEntity.setInputs(
				this.input.playerInput.left(), this.input.playerInput.right(), this.input.playerInput.forward(), this.input.playerInput.backward()
			);
			this.riding = this.riding
				| (this.input.playerInput.left() || this.input.playerInput.right() || this.input.playerInput.forward() || this.input.playerInput.backward());
		}
	}

	public boolean isRiding() {
		return this.riding;
	}

	public void move(MovementType type, Vec3d movement) {
		double d = this.getX();
		double e = this.getZ();
		super.move(type, movement);
		float f = (float)(this.getX() - d);
		float g = (float)(this.getZ() - e);
		this.autoJump(f, g);
		this.addDistanceMoved(MathHelper.hypot(f, g) * 0.6F);
	}

	public boolean isAutoJumpEnabled() {
		return this.autoJumpEnabled;
	}

	public boolean shouldRotateWithMinecart() {
		return this.client.options.getRotateWithMinecart().getValue();
	}

	protected void autoJump(float dx, float dz) {
		if (this.shouldAutoJump()) {
			Vec3d vec3d = this.getEntityPos();
			Vec3d vec3d2 = vec3d.add(dx, 0.0, dz);
			Vec3d vec3d3 = new Vec3d(dx, 0.0, dz);
			float f = this.getMovementSpeed();
			float g = (float)vec3d3.lengthSquared();
			if (g <= 0.001F) {
				Vec2f vec2f = this.input.getMovementInput();
				float h = f * vec2f.x;
				float i = f * vec2f.y;
				float j = MathHelper.sin(this.getYaw() * (float) (Math.PI / 180.0));
				float k = MathHelper.cos(this.getYaw() * (float) (Math.PI / 180.0));
				vec3d3 = new Vec3d(h * k - i * j, vec3d3.y, i * k + h * j);
				g = (float)vec3d3.lengthSquared();
				if (g <= 0.001F) {
					return;
				}
			}

			float l = MathHelper.inverseSqrt(g);
			Vec3d vec3d4 = vec3d3.multiply(l);
			Vec3d vec3d5 = this.getRotationVecClient();
			float j = (float)(vec3d5.x * vec3d4.x + vec3d5.z * vec3d4.z);
			if (!(j < -0.15F)) {
				ShapeContext shapeContext = ShapeContext.of(this);
				BlockPos blockPos = BlockPos.ofFloored(this.getX(), this.getBoundingBox().maxY, this.getZ());
				BlockState blockState = this.getEntityWorld().getBlockState(blockPos);
				if (blockState.getCollisionShape(this.getEntityWorld(), blockPos, shapeContext).isEmpty()) {
					blockPos = blockPos.up();
					BlockState blockState2 = this.getEntityWorld().getBlockState(blockPos);
					if (blockState2.getCollisionShape(this.getEntityWorld(), blockPos, shapeContext).isEmpty()) {
						float m = 7.0F;
						float n = 1.2F;
						if (this.hasStatusEffect(StatusEffects.JUMP_BOOST)) {
							n += (this.getStatusEffect(StatusEffects.JUMP_BOOST).getAmplifier() + 1) * 0.75F;
						}

						float o = Math.max(f * 7.0F, 1.0F / l);
						Vec3d vec3d7 = vec3d2.add(vec3d4.multiply(o));
						float p = this.getWidth();
						float q = this.getHeight();
						Box box = new Box(vec3d, vec3d7.add(0.0, q, 0.0)).expand(p, 0.0, p);
						Vec3d vec3d6 = vec3d.add(0.0, 0.51F, 0.0);
						vec3d7 = vec3d7.add(0.0, 0.51F, 0.0);
						Vec3d vec3d8 = vec3d4.crossProduct(new Vec3d(0.0, 1.0, 0.0));
						Vec3d vec3d9 = vec3d8.multiply(p * 0.5F);
						Vec3d vec3d10 = vec3d6.subtract(vec3d9);
						Vec3d vec3d11 = vec3d7.subtract(vec3d9);
						Vec3d vec3d12 = vec3d6.add(vec3d9);
						Vec3d vec3d13 = vec3d7.add(vec3d9);
						Iterable<VoxelShape> iterable = this.getEntityWorld().getCollisions(this, box);
						Iterator<Box> iterator = StreamSupport.stream(iterable.spliterator(), false).flatMap(shape -> shape.getBoundingBoxes().stream()).iterator();
						float r = Float.MIN_VALUE;

						while (iterator.hasNext()) {
							Box box2 = (Box)iterator.next();
							if (box2.intersects(vec3d10, vec3d11) || box2.intersects(vec3d12, vec3d13)) {
								r = (float)box2.maxY;
								Vec3d vec3d14 = box2.getCenter();
								BlockPos blockPos2 = BlockPos.ofFloored(vec3d14);

								for (int s = 1; s < n; s++) {
									BlockPos blockPos3 = blockPos2.up(s);
									BlockState blockState3 = this.getEntityWorld().getBlockState(blockPos3);
									VoxelShape voxelShape;
									if (!(voxelShape = blockState3.getCollisionShape(this.getEntityWorld(), blockPos3, shapeContext)).isEmpty()) {
										r = (float)voxelShape.getMax(Axis.Y) + blockPos3.getY();
										if (r - this.getY() > n) {
											return;
										}
									}

									if (s > 1) {
										blockPos = blockPos.up();
										BlockState blockState4 = this.getEntityWorld().getBlockState(blockPos);
										if (!blockState4.getCollisionShape(this.getEntityWorld(), blockPos, shapeContext).isEmpty()) {
											return;
										}
									}
								}
								break;
							}
						}

						if (r != Float.MIN_VALUE) {
							float t = (float)(r - this.getY());
							if (!(t <= 0.5F) && !(t > n)) {
								this.ticksToNextAutoJump = 1;
							}
						}
					}
				}
			}
		}
	}

	protected boolean hasCollidedSoftly(Vec3d adjustedMovement) {
		float f = this.getYaw() * (float) (Math.PI / 180.0);
		double d = MathHelper.sin(f);
		double e = MathHelper.cos(f);
		double g = this.sidewaysSpeed * e - this.forwardSpeed * d;
		double h = this.forwardSpeed * e + this.sidewaysSpeed * d;
		double i = MathHelper.square(g) + MathHelper.square(h);
		double j = MathHelper.square(adjustedMovement.x) + MathHelper.square(adjustedMovement.z);
		if (!(i < 1.0E-5F) && !(j < 1.0E-5F)) {
			double k = g * adjustedMovement.x + h * adjustedMovement.z;
			double l = Math.acos(k / Math.sqrt(i * j));
			return l < 0.13962634F;
		} else {
			return false;
		}
	}

	private boolean shouldAutoJump() {
		return this.isAutoJumpEnabled()
			&& this.ticksToNextAutoJump <= 0
			&& this.isOnGround()
			&& !this.clipAtLedge()
			&& !this.hasVehicle()
			&& this.hasMovementInput()
			&& this.getJumpVelocityMultiplier() >= 1.0;
	}

	/**
	 * {@return whether the player has movement input}
	 */
	private boolean hasMovementInput() {
		return this.input.getMovementInput().lengthSquared() > 0.0F;
	}

	private boolean canSprint(boolean allowTouchingWater) {
		return !this.hasBlindnessEffect()
			&& (this.hasVehicle() ? this.canVehicleSprint(this.getVehicle()) : this.canSprintOrFly())
			&& (allowTouchingWater || !this.isPartlyTouchingWater());
	}

	private boolean canStartSprinting() {
		return !this.isSprinting()
			&& this.input.hasForwardMovement()
			&& this.canSprint(this.getAbilities().flying)
			&& !this.isBlockedFromSprinting()
			&& (!this.isGliding() || this.isSubmergedInWater())
			&& (!this.shouldSlowDown() || this.isSubmergedInWater());
	}

	private boolean canVehicleSprint(Entity vehicle) {
		return vehicle.canSprintAsVehicle() && vehicle.isLogicalSideForUpdatingMovement();
	}

	/**
	 * {@return the color multiplier of vision in water} Visibility in
	 * water is reduced when the player just entered water.
	 */
	public float getUnderwaterVisibility() {
		if (!this.isSubmergedIn(FluidTags.WATER)) {
			return 0.0F;
		} else {
			float f = 600.0F;
			float g = 100.0F;
			if (this.underwaterVisibilityTicks >= 600.0F) {
				return 1.0F;
			} else {
				float h = MathHelper.clamp(this.underwaterVisibilityTicks / 100.0F, 0.0F, 1.0F);
				float i = this.underwaterVisibilityTicks < 100.0F ? 0.0F : MathHelper.clamp((this.underwaterVisibilityTicks - 100.0F) / 500.0F, 0.0F, 1.0F);
				return h * 0.6F + i * 0.39999998F;
			}
		}
	}

	public void onGameModeChanged(GameMode gameMode) {
		if (gameMode == GameMode.SPECTATOR) {
			this.setVelocity(this.getVelocity().withAxis(Axis.Y, 0.0));
		}
	}

	public boolean isSubmergedInWater() {
		return this.isSubmergedInWater;
	}

	protected boolean updateWaterSubmersionState() {
		boolean bl = this.isSubmergedInWater;
		boolean bl2 = super.updateWaterSubmersionState();
		if (this.isSpectator()) {
			return this.isSubmergedInWater;
		} else {
			if (!bl && bl2) {
				this.getEntityWorld()
					.playSoundClient(this.getX(), this.getY(), this.getZ(), SoundEvents.AMBIENT_UNDERWATER_ENTER, SoundCategory.AMBIENT, 1.0F, 1.0F, false);
				this.client.getSoundManager().play(new AmbientSoundLoops.Underwater(this));
			}

			if (bl && !bl2) {
				this.getEntityWorld().playSoundClient(this.getX(), this.getY(), this.getZ(), SoundEvents.AMBIENT_UNDERWATER_EXIT, SoundCategory.AMBIENT, 1.0F, 1.0F, false);
			}

			return this.isSubmergedInWater;
		}
	}

	public Vec3d getLeashPos(float tickProgress) {
		if (this.client.options.getPerspective().isFirstPerson()) {
			float f = MathHelper.lerp(tickProgress * 0.5F, this.getYaw(), this.lastYaw) * (float) (Math.PI / 180.0);
			float g = MathHelper.lerp(tickProgress * 0.5F, this.getPitch(), this.lastPitch) * (float) (Math.PI / 180.0);
			double d = this.getMainArm() == Arm.RIGHT ? -1.0 : 1.0;
			Vec3d vec3d = new Vec3d(0.39 * d, -0.6, 0.3);
			return vec3d.rotateX(-g).rotateY(-f).add(this.getCameraPosVec(tickProgress));
		} else {
			return super.getLeashPos(tickProgress);
		}
	}

	public void onPickupSlotClick(ItemStack cursorStack, ItemStack slotStack, ClickType clickType) {
		this.client.getTutorialManager().onPickupSlotClick(cursorStack, slotStack, clickType);
	}

	public float getBodyYaw() {
		return this.getYaw();
	}

	public void dropCreativeStack(ItemStack stack) {
		this.client.interactionManager.dropCreativeStack(stack);
	}

	public boolean canDropItems() {
		return this.itemDropCooldown.canUse();
	}

	public Cooldown getItemDropCooldown() {
		return this.itemDropCooldown;
	}

	public PlayerInput getLastPlayerInput() {
		return this.lastPlayerInput;
	}

	public HitResult method_76762(float f, Entity entity) {
		ItemStack itemStack = this.getActiveOrMainHandStack();
		AttackRangeComponent attackRangeComponent = (AttackRangeComponent)itemStack.get(DataComponentTypes.ATTACK_RANGE);
		double d = this.getBlockInteractionRange();
		HitResult hitResult = null;
		if (attackRangeComponent != null) {
			hitResult = attackRangeComponent.getHitResult(entity, f, EntityPredicates.CAN_HIT);
			if (hitResult instanceof BlockHitResult) {
				hitResult = method_76764(hitResult, entity.getCameraPosVec(f), d);
			}
		}

		if (hitResult == null || hitResult.getType() == Type.MISS) {
			double e = this.getEntityInteractionRange();
			hitResult = method_76763(entity, d, e, f);
		}

		return hitResult;
	}

	private static HitResult method_76763(Entity entity, double d, double e, float f) {
		double g = Math.max(d, e);
		double h = MathHelper.square(g);
		Vec3d vec3d = entity.getCameraPosVec(f);
		HitResult hitResult = entity.raycast(g, f, false);
		double i = hitResult.getPos().squaredDistanceTo(vec3d);
		if (hitResult.getType() != Type.MISS) {
			h = i;
			g = Math.sqrt(i);
		}

		Vec3d vec3d2 = entity.getRotationVec(f);
		Vec3d vec3d3 = vec3d.add(vec3d2.x * g, vec3d2.y * g, vec3d2.z * g);
		float j = 1.0F;
		Box box = entity.getBoundingBox().stretch(vec3d2.multiply(g)).expand(1.0, 1.0, 1.0);
		EntityHitResult entityHitResult = ProjectileUtil.raycast(entity, vec3d, vec3d3, box, EntityPredicates.CAN_HIT, h);
		return entityHitResult != null && entityHitResult.getPos().squaredDistanceTo(vec3d) < i
			? method_76764(entityHitResult, vec3d, e)
			: method_76764(hitResult, vec3d, d);
	}

	private static HitResult method_76764(HitResult hitResult, Vec3d vec3d, double d) {
		Vec3d vec3d2 = hitResult.getPos();
		if (!vec3d2.isInRange(vec3d, d)) {
			Vec3d vec3d3 = hitResult.getPos();
			Direction direction = Direction.getFacing(vec3d3.x - vec3d.x, vec3d3.y - vec3d.y, vec3d3.z - vec3d.z);
			return BlockHitResult.createMissed(vec3d3, direction, BlockPos.ofFloored(vec3d3));
		} else {
			return hitResult;
		}
	}
}
