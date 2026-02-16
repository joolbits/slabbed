package net.minecraft.client.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.jtracy.TracyClient;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.block.pattern.CachedBlockPosition;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.EmptyGlyphRect;
import net.minecraft.client.font.GlyphRect;
import net.minecraft.client.font.TextDrawable;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlobalSettings;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.ShaderSourceGetter;
import net.minecraft.client.gui.CubeMapRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.RotatingCubeMapRenderer;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.gui.hud.debug.DebugHudEntries;
import net.minecraft.client.gui.render.BannerResultGuiElementRenderer;
import net.minecraft.client.gui.render.BookModelGuiElementRenderer;
import net.minecraft.client.gui.render.EntityGuiElementRenderer;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.PlayerSkinGuiElementRenderer;
import net.minecraft.client.gui.render.ProfilerChartGuiElementRenderer;
import net.minecraft.client.gui.render.SignGuiElementRenderer;
import net.minecraft.client.gui.render.state.ColoredQuadGuiElementRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.screen.DebugOptionsScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerLikeState;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.TextureFilteringMode;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.command.RenderDispatcher;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.texture.AtlasManager;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.client.util.Pool;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.ResourceFactory;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Style;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportSection;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.util.profiler.Profilers;
import net.minecraft.util.profiler.ScopedProfiler;
import net.minecraft.world.GameMode;
import net.minecraft.world.waypoint.TrackedWaypoint.PitchProvider;
import org.apache.commons.io.IOUtils;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class GameRenderer implements PitchProvider, AutoCloseable {
	private static final Identifier BLUR_ID = Identifier.ofVanilla("blur");
	public static final int field_49904 = 10;
	private static final Logger LOGGER = LogUtils.getLogger();
	/**
	 * Since the camera is conceptualized as a single point, a depth of {@value}
	 * blocks is used to define a rectangular area to be rendered.
	 * 
	 * @see Camera#getProjection()
	 */
	public static final float CAMERA_DEPTH = 0.05F;
	public static final float field_60107 = 100.0F;
	private static final float field_55869 = 20.0F;
	private static final float field_55870 = 7.0F;
	private final MinecraftClient client;
	private final Random random = Random.create();
	private float viewDistanceBlocks;
	public final HeldItemRenderer firstPersonRenderer;
	private final InGameOverlayRenderer overlayRenderer;
	private final BufferBuilderStorage buffers;
	private float nauseaEffectTime;
	private float nauseaEffectSpeed;
	private float fovMultiplier;
	private float lastFovMultiplier;
	private float skyDarkness;
	private float lastSkyDarkness;
	private boolean blockOutlineEnabled = true;
	private long lastWorldIconUpdate;
	private boolean hasWorldIcon;
	private long lastWindowFocusedTime = Util.getMeasuringTimeMs();
	private final LightmapTextureManager lightmapTextureManager;
	private final OverlayTexture overlayTexture = new OverlayTexture();
	@Nullable
	private CameraOverride cameraOverride;
	protected final CubeMapRenderer panoramaRenderer = new CubeMapRenderer(Identifier.ofVanilla("textures/gui/title/background/panorama"));
	protected final RotatingCubeMapRenderer rotatingPanoramaRenderer = new RotatingCubeMapRenderer(this.panoramaRenderer);
	private final Pool pool = new Pool(3);
	private final FogRenderer fogRenderer = new FogRenderer();
	private final GuiRenderer guiRenderer;
	final GuiRenderState guiState;
	private final WorldRenderState worldRenderState = new WorldRenderState();
	private final OrderedRenderCommandQueueImpl orderedRenderCommandQueue;
	private final RenderDispatcher entityRenderDispatcher;
	@Nullable
	private Identifier postProcessorId;
	private boolean postProcessorEnabled;
	private final Camera camera = new Camera();
	private final DiffuseLighting diffuseLighting = new DiffuseLighting();
	private final GlobalSettings globalSettings = new GlobalSettings();
	private final RawProjectionMatrix worldProjectionMatrix = new RawProjectionMatrix("level");
	private final ProjectionMatrix3 hudProjectionMatrix = new ProjectionMatrix3("3d hud", 0.05F, 100.0F);

	public GameRenderer(MinecraftClient client, HeldItemRenderer firstPersonHeldItemRenderer, BufferBuilderStorage buffers, BlockRenderManager blockRenderManager) {
		this.client = client;
		this.firstPersonRenderer = firstPersonHeldItemRenderer;
		this.lightmapTextureManager = new LightmapTextureManager(this, client);
		this.buffers = buffers;
		this.guiState = new GuiRenderState();
		VertexConsumerProvider.Immediate immediate = buffers.getEntityVertexConsumers();
		AtlasManager atlasManager = client.getAtlasManager();
		this.orderedRenderCommandQueue = new OrderedRenderCommandQueueImpl();
		this.entityRenderDispatcher = new RenderDispatcher(
			this.orderedRenderCommandQueue,
			blockRenderManager,
			immediate,
			atlasManager,
			buffers.getOutlineVertexConsumers(),
			buffers.getEffectVertexConsumers(),
			client.textRenderer
		);
		this.guiRenderer = new GuiRenderer(
			this.guiState,
			immediate,
			this.orderedRenderCommandQueue,
			this.entityRenderDispatcher,
			List.of(
				new EntityGuiElementRenderer(immediate, client.getEntityRenderDispatcher()),
				new PlayerSkinGuiElementRenderer(immediate),
				new BookModelGuiElementRenderer(immediate),
				new BannerResultGuiElementRenderer(immediate, atlasManager),
				new SignGuiElementRenderer(immediate, atlasManager),
				new ProfilerChartGuiElementRenderer(immediate)
			)
		);
		this.overlayRenderer = new InGameOverlayRenderer(client, atlasManager, immediate);
	}

	public void close() {
		this.globalSettings.close();
		this.lightmapTextureManager.close();
		this.overlayTexture.close();
		this.pool.close();
		this.guiRenderer.close();
		this.worldProjectionMatrix.close();
		this.hudProjectionMatrix.close();
		this.diffuseLighting.close();
		this.panoramaRenderer.close();
		this.fogRenderer.close();
		this.entityRenderDispatcher.close();
	}

	public OrderedRenderCommandQueueImpl getEntityRenderCommandQueue() {
		return this.orderedRenderCommandQueue;
	}

	public RenderDispatcher getEntityRenderDispatcher() {
		return this.entityRenderDispatcher;
	}

	public WorldRenderState getEntityRenderStates() {
		return this.worldRenderState;
	}

	public void setBlockOutlineEnabled(boolean blockOutlineEnabled) {
		this.blockOutlineEnabled = blockOutlineEnabled;
	}

	public void setCameraOverride(@Nullable CameraOverride cameraOverride) {
		this.cameraOverride = cameraOverride;
	}

	@Nullable
	public CameraOverride getCameraOverride() {
		return this.cameraOverride;
	}

	public boolean isRenderingPanorama() {
		return this.cameraOverride != null;
	}

	public void clearPostProcessor() {
		this.postProcessorId = null;
		this.postProcessorEnabled = false;
	}

	public void togglePostProcessorEnabled() {
		this.postProcessorEnabled = !this.postProcessorEnabled;
	}

	public void onCameraEntitySet(@Nullable Entity entity) {
		switch (entity) {
			case CreeperEntity creeperEntity:
				this.setPostProcessor(Identifier.ofVanilla("creeper"));
				break;
			case SpiderEntity spiderEntity:
				this.setPostProcessor(Identifier.ofVanilla("spider"));
				break;
			case EndermanEntity endermanEntity:
				this.setPostProcessor(Identifier.ofVanilla("invert"));
				break;
			case null:
			default:
				this.clearPostProcessor();
		}
	}

	private void setPostProcessor(Identifier id) {
		this.postProcessorId = id;
		this.postProcessorEnabled = true;
	}

	public void renderBlur() {
		PostEffectProcessor postEffectProcessor = this.client.getShaderLoader().loadPostEffect(BLUR_ID, DefaultFramebufferSet.MAIN_ONLY);
		if (postEffectProcessor != null) {
			postEffectProcessor.render(this.client.getFramebuffer(), this.pool);
		}
	}

	public void preloadPrograms(ResourceFactory factory) {
		GpuDevice gpuDevice = RenderSystem.getDevice();
		ShaderSourceGetter shaderSourceGetter = (id, type) -> {
			Identifier identifier = type.idConverter().toResourcePath(id);

			try {
				Reader reader = factory.getResourceOrThrow(identifier).getReader();

				String var5;
				try {
					var5 = IOUtils.toString(reader);
				} catch (Throwable var8) {
					if (reader != null) {
						try {
							reader.close();
						} catch (Throwable var7) {
							var8.addSuppressed(var7);
						}
					}

					throw var8;
				}

				if (reader != null) {
					reader.close();
				}

				return var5;
			} catch (IOException var9) {
				LOGGER.error("Coudln't preload {} shader {}: {}", type, id, var9);
				return null;
			}
		};
		gpuDevice.precompilePipeline(RenderPipelines.GUI, shaderSourceGetter);
		gpuDevice.precompilePipeline(RenderPipelines.GUI_TEXTURED, shaderSourceGetter);
		if (TracyClient.isAvailable()) {
			gpuDevice.precompilePipeline(RenderPipelines.TRACY_BLIT, shaderSourceGetter);
		}
	}

	public void tick() {
		this.updateFovMultiplier();
		this.lightmapTextureManager.tick();
		ClientPlayerEntity clientPlayerEntity = this.client.player;
		if (this.client.getCameraEntity() == null) {
			this.client.setCameraEntity(clientPlayerEntity);
		}

		this.camera.updateEyeHeight();
		this.firstPersonRenderer.updateHeldItems();
		float f = clientPlayerEntity.nauseaIntensity;
		float g = clientPlayerEntity.getEffectFadeFactor(StatusEffects.NAUSEA, 1.0F);
		if (!(f > 0.0F) && !(g > 0.0F)) {
			this.nauseaEffectSpeed = 0.0F;
		} else {
			this.nauseaEffectSpeed = (f * 20.0F + g * 7.0F) / (f + g);
			this.nauseaEffectTime = this.nauseaEffectTime + this.nauseaEffectSpeed;
		}

		if (this.client.world.getTickManager().shouldTick()) {
			this.lastSkyDarkness = this.skyDarkness;
			if (this.client.inGameHud.getBossBarHud().shouldDarkenSky()) {
				this.skyDarkness += 0.05F;
				if (this.skyDarkness > 1.0F) {
					this.skyDarkness = 1.0F;
				}
			} else if (this.skyDarkness > 0.0F) {
				this.skyDarkness -= 0.0125F;
			}

			this.overlayRenderer.tickFloatingItemTimer();
			Profiler profiler = Profilers.get();
			profiler.push("levelRenderer");
			this.client.worldRenderer.tick(this.camera);
			profiler.pop();
		}
	}

	@Nullable
	public Identifier getPostProcessorId() {
		return this.postProcessorId;
	}

	public void onResized(int width, int height) {
		this.pool.clear();
		this.client.worldRenderer.onResized(width, height);
	}

	public void updateCrosshairTarget(float tickProgress) {
		Entity entity = this.client.getCameraEntity();
		if (entity != null) {
			if (this.client.world != null && this.client.player != null) {
				Profilers.get().push("pick");
				this.client.crosshairTarget = this.client.player.method_76762(tickProgress, entity);
				this.client.targetedEntity = this.client.crosshairTarget instanceof EntityHitResult entityHitResult ? entityHitResult.getEntity() : null;
				Profilers.get().pop();
			}
		}
	}

	private void updateFovMultiplier() {
		float g;
		if (this.client.getCameraEntity() instanceof AbstractClientPlayerEntity abstractClientPlayerEntity) {
			GameOptions gameOptions = this.client.options;
			boolean bl = gameOptions.getPerspective().isFirstPerson();
			float f = gameOptions.getFovEffectScale().getValue().floatValue();
			g = abstractClientPlayerEntity.getFovMultiplier(bl, f);
		} else {
			g = 1.0F;
		}

		this.lastFovMultiplier = this.fovMultiplier;
		this.fovMultiplier = this.fovMultiplier + (g - this.fovMultiplier) * 0.5F;
		this.fovMultiplier = MathHelper.clamp(this.fovMultiplier, 0.1F, 1.5F);
	}

	private float getFov(Camera camera, float tickProgress, boolean changingFov) {
		if (this.isRenderingPanorama()) {
			return 90.0F;
		} else {
			float f = 70.0F;
			if (changingFov) {
				f = this.client.options.getFov().getValue().intValue();
				f *= MathHelper.lerp(tickProgress, this.lastFovMultiplier, this.fovMultiplier);
			}

			if (camera.getFocusedEntity() instanceof LivingEntity livingEntity && livingEntity.isDead()) {
				float g = Math.min(livingEntity.deathTime + tickProgress, 20.0F);
				f /= (1.0F - 500.0F / (g + 500.0F)) * 2.0F + 1.0F;
			}

			CameraSubmersionType cameraSubmersionType = camera.getSubmersionType();
			if (cameraSubmersionType == CameraSubmersionType.LAVA || cameraSubmersionType == CameraSubmersionType.WATER) {
				float g = this.client.options.getFovEffectScale().getValue().floatValue();
				f *= MathHelper.lerp(g, 1.0F, 0.85714287F);
			}

			return f;
		}
	}

	private void tiltViewWhenHurt(MatrixStack matrices, float tickProgress) {
		if (this.client.getCameraEntity() instanceof LivingEntity livingEntity) {
			float f = livingEntity.hurtTime - tickProgress;
			if (livingEntity.isDead()) {
				float g = Math.min(livingEntity.deathTime + tickProgress, 20.0F);
				matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(40.0F - 8000.0F / (g + 200.0F)));
			}

			if (f < 0.0F) {
				return;
			}

			f /= livingEntity.maxHurtTime;
			f = MathHelper.sin(f * f * f * f * (float) Math.PI);
			float g = livingEntity.getDamageTiltYaw();
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-g));
			float h = (float)(-f * 14.0 * this.client.options.getDamageTiltStrength().getValue());
			matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(h));
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(g));
		}
	}

	private void bobView(MatrixStack matrices, float tickProgress) {
		if (this.client.getCameraEntity() instanceof AbstractClientPlayerEntity abstractClientPlayerEntity) {
			ClientPlayerLikeState clientPlayerLikeState = abstractClientPlayerEntity.getState();
			float f = clientPlayerLikeState.getReverseLerpedDistanceMoved(tickProgress);
			float g = clientPlayerLikeState.lerpMovement(tickProgress);
			matrices.translate(MathHelper.sin(f * (float) Math.PI) * g * 0.5F, -Math.abs(MathHelper.cos(f * (float) Math.PI) * g), 0.0F);
			matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(MathHelper.sin(f * (float) Math.PI) * g * 3.0F));
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(Math.abs(MathHelper.cos(f * (float) Math.PI - 0.2F) * g) * 5.0F));
		}
	}

	private void renderHand(float tickProgress, boolean sleeping, Matrix4f positionMatrix) {
		if (!this.isRenderingPanorama()) {
			this.entityRenderDispatcher.render();
			this.buffers.getEntityVertexConsumers().draw();
			MatrixStack matrixStack = new MatrixStack();
			matrixStack.push();
			matrixStack.multiplyPositionMatrix(positionMatrix.invert(new Matrix4f()));
			Matrix4fStack matrix4fStack = RenderSystem.getModelViewStack();
			matrix4fStack.pushMatrix().mul(positionMatrix);
			this.tiltViewWhenHurt(matrixStack, tickProgress);
			if (this.client.options.getBobView().getValue()) {
				this.bobView(matrixStack, tickProgress);
			}

			if (this.client.options.getPerspective().isFirstPerson()
				&& !sleeping
				&& !this.client.options.hudHidden
				&& this.client.interactionManager.getCurrentGameMode() != GameMode.SPECTATOR) {
				this.firstPersonRenderer
					.renderItem(
						tickProgress,
						matrixStack,
						this.client.gameRenderer.getEntityRenderCommandQueue(),
						this.client.player,
						this.client.getEntityRenderDispatcher().getLight(this.client.player, tickProgress)
					);
			}

			matrix4fStack.popMatrix();
			matrixStack.pop();
		}
	}

	public Matrix4f getBasicProjectionMatrix(float fovDegrees) {
		Matrix4f matrix4f = new Matrix4f();
		return matrix4f.perspective(
			fovDegrees * (float) (Math.PI / 180.0),
			(float)this.client.getWindow().getFramebufferWidth() / this.client.getWindow().getFramebufferHeight(),
			0.05F,
			this.getFarPlaneDistance()
		);
	}

	public float getFarPlaneDistance() {
		return Math.max(this.viewDistanceBlocks * 4.0F, this.client.options.getCloudRenderDistance().getValue() * 16);
	}

	public static float getNightVisionStrength(LivingEntity entity, float tickProgress) {
		StatusEffectInstance statusEffectInstance = entity.getStatusEffect(StatusEffects.NIGHT_VISION);
		return !statusEffectInstance.isDurationBelow(200)
			? 1.0F
			: 0.7F + MathHelper.sin((statusEffectInstance.getDuration() - tickProgress) * (float) Math.PI * 0.2F) * 0.3F;
	}

	public void render(RenderTickCounter tickCounter, boolean tick) {
		if (!this.client.isWindowFocused()
			&& this.client.options.pauseOnLostFocus
			&& (!this.client.options.getTouchscreen().getValue() || !this.client.mouse.wasRightButtonClicked())) {
			if (Util.getMeasuringTimeMs() - this.lastWindowFocusedTime > 500L) {
				this.client.openGameMenu(false);
			}
		} else {
			this.lastWindowFocusedTime = Util.getMeasuringTimeMs();
		}

		if (!this.client.skipGameRender) {
			Profiler profiler = Profilers.get();
			profiler.push("camera");
			this.method_76295(tickCounter);
			profiler.pop();
			this.globalSettings
				.set(
					this.client.getWindow().getFramebufferWidth(),
					this.client.getWindow().getFramebufferHeight(),
					this.client.options.getGlintStrength().getValue(),
					this.client.world == null ? 0L : this.client.world.getTime(),
					tickCounter,
					this.client.options.getMenuBackgroundBlurrinessValue(),
					this.camera,
					this.client.options.getTextureFiltering().getValue() == TextureFilteringMode.RGSS
				);
			boolean bl = this.client.isFinishedLoading();
			int i = (int)this.client.mouse.getScaledX(this.client.getWindow());
			int j = (int)this.client.mouse.getScaledY(this.client.getWindow());
			if (bl && tick && this.client.world != null) {
				profiler.push("world");
				this.renderWorld(tickCounter);
				this.updateWorldIcon();
				this.client.worldRenderer.drawEntityOutlinesFramebuffer();
				if (this.postProcessorId != null && this.postProcessorEnabled) {
					PostEffectProcessor postEffectProcessor = this.client.getShaderLoader().loadPostEffect(this.postProcessorId, DefaultFramebufferSet.MAIN_ONLY);
					if (postEffectProcessor != null) {
						postEffectProcessor.render(this.client.getFramebuffer(), this.pool);
					}
				}

				profiler.pop();
			}

			this.fogRenderer.rotate();
			Framebuffer framebuffer = this.client.getFramebuffer();
			RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(framebuffer.getDepthAttachment(), 1.0);
			this.client.gameRenderer.getDiffuseLighting().setShaderLights(DiffuseLighting.Type.ITEMS_3D);
			this.guiState.clear();
			profiler.push("guiExtraction");
			DrawContext drawContext = new DrawContext(this.client, this.guiState, i, j);
			if (bl && tick && this.client.world != null) {
				this.client.inGameHud.render(drawContext, tickCounter);
			}

			if (this.client.getOverlay() != null) {
				try {
					this.client.getOverlay().render(drawContext, i, j, tickCounter.getDynamicDeltaTicks());
				} catch (Throwable var15) {
					CrashReport crashReport = CrashReport.create(var15, "Rendering overlay");
					CrashReportSection crashReportSection = crashReport.addElement("Overlay render details");
					crashReportSection.add("Overlay name", () -> this.client.getOverlay().getClass().getCanonicalName());
					throw new CrashException(crashReport);
				}
			} else if (bl && this.client.currentScreen != null) {
				try {
					this.client.currentScreen.renderWithTooltip(drawContext, i, j, tickCounter.getDynamicDeltaTicks());
				} catch (Throwable var14) {
					CrashReport crashReport = CrashReport.create(var14, "Rendering screen");
					CrashReportSection crashReportSection = crashReport.addElement("Screen render details");
					crashReportSection.add("Screen name", () -> this.client.currentScreen.getClass().getCanonicalName());
					this.client.mouse.addCrashReportSection(crashReportSection, this.client.getWindow());
					throw new CrashException(crashReport);
				}

				if (SharedConstants.CURSOR_POS) {
					this.client.mouse.drawScaledPos(this.client.textRenderer, drawContext);
				}

				try {
					if (this.client.currentScreen != null) {
						this.client.currentScreen.updateNarrator();
					}
				} catch (Throwable var13) {
					CrashReport crashReport = CrashReport.create(var13, "Narrating screen");
					CrashReportSection crashReportSection = crashReport.addElement("Screen details");
					crashReportSection.add("Screen name", () -> this.client.currentScreen.getClass().getCanonicalName());
					throw new CrashException(crashReport);
				}
			}

			if (bl && tick && this.client.world != null) {
				this.client.inGameHud.renderAutosaveIndicator(drawContext, tickCounter);
			}

			if (bl) {
				ScopedProfiler scopedProfiler = profiler.scoped("toasts");

				try {
					this.client.getToastManager().draw(drawContext);
				} catch (Throwable var16) {
					if (scopedProfiler != null) {
						try {
							scopedProfiler.close();
						} catch (Throwable var12) {
							var16.addSuppressed(var12);
						}
					}

					throw var16;
				}

				if (scopedProfiler != null) {
					scopedProfiler.close();
				}
			}

			if (!(this.client.currentScreen instanceof DebugOptionsScreen)) {
				this.client.inGameHud.renderDebugHud(drawContext);
			}

			this.client.inGameHud.renderDeferredSubtitles();
			if (SharedConstants.ACTIVE_TEXT_AREAS) {
				this.renderActiveTextAreas();
			}

			profiler.swap("guiRendering");
			this.guiRenderer.render(this.fogRenderer.getFogBuffer(FogRenderer.FogType.NONE));
			this.guiRenderer.incrementFrame();
			profiler.pop();
			drawContext.applyCursorTo(this.client.getWindow());
			this.orderedRenderCommandQueue.onNextFrame();
			this.entityRenderDispatcher.endLayeredCustoms();
			this.pool.decrementLifespan();
		}
	}

	private void renderActiveTextAreas() {
		this.guiState.createNewRootLayer();
		this.guiState
			.forEachTextElement(
				text -> text.prepare()
					.draw(
						new TextRenderer.GlyphDrawer() {
							private int index;

							@Override
							public void drawGlyph(TextDrawable.DrawnGlyphRect glyph) {
								this.addGlyph(glyph, false);
							}

							@Override
							public void drawEmptyGlyphRect(EmptyGlyphRect rect) {
								this.addGlyph(rect, true);
							}

							private void addGlyph(GlyphRect glyph, boolean empty) {
								int i = (empty ? 128 : 255) - (this.index++ & 1) * 64;
								Style style = glyph.style();
								int j = style.getClickEvent() != null ? i : 0;
								int k = style.getHoverEvent() != null ? i : 0;
								int l = j != 0 && k != 0 ? 0 : i;
								int m = ColorHelper.getArgb(128, j, k, l);
								GameRenderer.this.guiState
									.addSimpleElement(
										new ColoredQuadGuiElementRenderState(
											RenderPipelines.GUI,
											TextureSetup.empty(),
											text.matrix,
											(int)glyph.getLeft(),
											(int)glyph.getTop(),
											(int)glyph.getRight(),
											(int)glyph.getBottom(),
											m,
											m,
											text.clipBounds
										)
									);
							}
						}
					)
			);
	}

	private void updateWorldIcon() {
		if (!this.hasWorldIcon && this.client.isInSingleplayer()) {
			long l = Util.getMeasuringTimeMs();
			if (l - this.lastWorldIconUpdate >= 1000L) {
				this.lastWorldIconUpdate = l;
				IntegratedServer integratedServer = this.client.getServer();
				if (integratedServer != null && !integratedServer.isStopped()) {
					integratedServer.getIconFile().ifPresent(path -> {
						if (Files.isRegularFile(path, new LinkOption[0])) {
							this.hasWorldIcon = true;
						} else {
							this.updateWorldIcon(path);
						}
					});
				}
			}
		}
	}

	private void updateWorldIcon(Path path) {
		if (this.client.worldRenderer.getCompletedChunkCount() > 10 && this.client.worldRenderer.isTerrainRenderComplete()) {
			ScreenshotRecorder.takeScreenshot(this.client.getFramebuffer(), screenshot -> Util.getIoWorkerExecutor().execute(() -> {
				int i = screenshot.getWidth();
				int j = screenshot.getHeight();
				int k = 0;
				int l = 0;
				if (i > j) {
					k = (i - j) / 2;
					i = j;
				} else {
					l = (j - i) / 2;
					j = i;
				}

				try (NativeImage nativeImage2 = new NativeImage(64, 64, false)) {
					screenshot.resizeSubRectTo(k, l, i, j, nativeImage2);
					nativeImage2.writeTo(path);
				} catch (IOException var16) {
					LOGGER.warn("Couldn't save auto screenshot", (Throwable)var16);
				} finally {
					screenshot.close();
				}
			}));
		}
	}

	private boolean shouldRenderBlockOutline() {
		if (!this.blockOutlineEnabled) {
			return false;
		} else {
			Entity entity = this.client.getCameraEntity();
			boolean bl = entity instanceof PlayerEntity && !this.client.options.hudHidden;
			if (bl && !((PlayerEntity)entity).getAbilities().allowModifyWorld) {
				ItemStack itemStack = ((LivingEntity)entity).getMainHandStack();
				HitResult hitResult = this.client.crosshairTarget;
				if (hitResult != null && hitResult.getType() == Type.BLOCK) {
					BlockPos blockPos = ((BlockHitResult)hitResult).getBlockPos();
					BlockState blockState = this.client.world.getBlockState(blockPos);
					if (this.client.interactionManager.getCurrentGameMode() == GameMode.SPECTATOR) {
						bl = blockState.createScreenHandlerFactory(this.client.world, blockPos) != null;
					} else {
						CachedBlockPosition cachedBlockPosition = new CachedBlockPosition(this.client.world, blockPos, false);
						Registry<Block> registry = this.client.world.getRegistryManager().getOrThrow(RegistryKeys.BLOCK);
						bl = !itemStack.isEmpty() && (itemStack.canBreak(cachedBlockPosition) || itemStack.canPlaceOn(cachedBlockPosition));
					}
				}
			}

			return bl;
		}
	}

	public void method_76295(RenderTickCounter renderTickCounter) {
		float f = renderTickCounter.getTickProgress(true);
		ClientPlayerEntity clientPlayerEntity = this.client.player;
		if (clientPlayerEntity != null && this.client.world != null) {
			if (this.client.getCameraEntity() == null) {
				this.client.setCameraEntity(clientPlayerEntity);
			}

			Entity entity = (Entity)(this.client.getCameraEntity() == null ? clientPlayerEntity : this.client.getCameraEntity());
			float g = this.client.world.getTickManager().shouldSkipTick(entity) ? 1.0F : f;
			this.camera.update(this.client.world, entity, !this.client.options.getPerspective().isFirstPerson(), this.client.options.getPerspective().isFrontView(), g);
		}
	}

	public void renderWorld(RenderTickCounter renderTickCounter) {
		float f = renderTickCounter.getTickProgress(true);
		ClientPlayerEntity clientPlayerEntity = this.client.player;
		this.lightmapTextureManager.update(1.0F);
		this.updateCrosshairTarget(f);
		Profiler profiler = Profilers.get();
		boolean bl = this.shouldRenderBlockOutline();
		this.updateCameraState(f);
		this.viewDistanceBlocks = this.client.options.getClampedViewDistance() * 16;
		profiler.push("matrices");
		float g = this.getFov(this.camera, f, true);
		Matrix4f matrix4f = this.getBasicProjectionMatrix(g);
		MatrixStack matrixStack = new MatrixStack();
		this.tiltViewWhenHurt(matrixStack, this.camera.getLastTickProgress());
		if (this.client.options.getBobView().getValue()) {
			this.bobView(matrixStack, this.camera.getLastTickProgress());
		}

		matrix4f.mul(matrixStack.peek().getPositionMatrix());
		float h = this.client.options.getDistortionEffectScale().getValue().floatValue();
		float i = MathHelper.lerp(f, clientPlayerEntity.lastNauseaIntensity, clientPlayerEntity.nauseaIntensity);
		float j = clientPlayerEntity.getEffectFadeFactor(StatusEffects.NAUSEA, f);
		float k = Math.max(i, j) * (h * h);
		if (k > 0.0F) {
			float l = 5.0F / (k * k + 5.0F) - k * 0.04F;
			l *= l;
			Vector3f vector3f = new Vector3f(0.0F, MathHelper.SQUARE_ROOT_OF_TWO / 2.0F, MathHelper.SQUARE_ROOT_OF_TWO / 2.0F);
			float m = (this.nauseaEffectTime + f * this.nauseaEffectSpeed) * (float) (Math.PI / 180.0);
			matrix4f.rotate(m, vector3f);
			matrix4f.scale(1.0F / l, 1.0F, 1.0F);
			matrix4f.rotate(-m, vector3f);
		}

		RenderSystem.setProjectionMatrix(this.worldProjectionMatrix.set(matrix4f), ProjectionType.PERSPECTIVE);
		Quaternionf quaternionf = this.camera.getRotation().conjugate(new Quaternionf());
		Matrix4f matrix4f2 = new Matrix4f().rotation(quaternionf);
		profiler.swap("fog");
		Vector4f vector4f = this.fogRenderer
			.applyFog(this.camera, this.client.options.getClampedViewDistance(), renderTickCounter, this.getSkyDarkness(f), this.client.world);
		GpuBufferSlice gpuBufferSlice = this.fogRenderer.getFogBuffer(FogRenderer.FogType.WORLD);
		profiler.swap("level");
		boolean bl2 = this.client.inGameHud.getBossBarHud().shouldThickenFog();
		this.client
			.worldRenderer
			.render(this.pool, renderTickCounter, bl, this.camera, matrix4f2, matrix4f, this.getProjectionMatrix(g), gpuBufferSlice, vector4f, !bl2);
		profiler.swap("hand");
		boolean bl3 = this.client.getCameraEntity() instanceof LivingEntity && ((LivingEntity)this.client.getCameraEntity()).isSleeping();
		RenderSystem.setProjectionMatrix(
			this.hudProjectionMatrix
				.set(this.client.getWindow().getFramebufferWidth(), this.client.getWindow().getFramebufferHeight(), this.getFov(this.camera, f, false)),
			ProjectionType.PERSPECTIVE
		);
		RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(this.client.getFramebuffer().getDepthAttachment(), 1.0);
		this.renderHand(f, bl3, matrix4f2);
		profiler.swap("screenEffects");
		VertexConsumerProvider.Immediate immediate = this.buffers.getEntityVertexConsumers();
		this.overlayRenderer.renderOverlays(bl3, f, this.orderedRenderCommandQueue);
		this.entityRenderDispatcher.render();
		immediate.draw();
		profiler.pop();
		RenderSystem.setShaderFog(this.fogRenderer.getFogBuffer(FogRenderer.FogType.NONE));
		if (this.client.debugHudEntryList.isEntryVisible(DebugHudEntries.THREE_DIMENSIONAL_CROSSHAIR)
			&& this.client.options.getPerspective().isFirstPerson()
			&& !this.client.options.hudHidden) {
			this.client.getDebugHud().renderDebugCrosshair(this.camera);
		}
	}

	private void updateCameraState(float f) {
		CameraRenderState cameraRenderState = this.worldRenderState.cameraRenderState;
		cameraRenderState.initialized = this.camera.isReady();
		cameraRenderState.pos = this.camera.getCameraPos();
		cameraRenderState.blockPos = this.camera.getBlockPos();
		cameraRenderState.entityPos = this.camera.getFocusedEntity().getLerpedPos(f);
		cameraRenderState.orientation = new Quaternionf(this.camera.getRotation());
	}

	private Matrix4f getProjectionMatrix(float f) {
		float g = Math.max(f, this.client.options.getFov().getValue().intValue());
		return this.getBasicProjectionMatrix(g);
	}

	public void reset() {
		this.overlayRenderer.clearFloatingItem();
		this.client.getMapTextureManager().clear();
		this.camera.reset();
		this.hasWorldIcon = false;
	}

	public void showFloatingItem(ItemStack floatingItem) {
		this.overlayRenderer.setFloatingItem(floatingItem, this.random);
	}

	public MinecraftClient getClient() {
		return this.client;
	}

	public float getSkyDarkness(float tickProgress) {
		return MathHelper.lerp(tickProgress, this.lastSkyDarkness, this.skyDarkness);
	}

	public float getViewDistanceBlocks() {
		return this.viewDistanceBlocks;
	}

	public Camera getCamera() {
		return this.camera;
	}

	public LightmapTextureManager getLightmapTextureManager() {
		return this.lightmapTextureManager;
	}

	public OverlayTexture getOverlayTexture() {
		return this.overlayTexture;
	}

	public Vec3d project(Vec3d sourcePos) {
		Matrix4f matrix4f = this.getBasicProjectionMatrix(this.getFov(this.camera, 0.0F, true));
		Quaternionf quaternionf = this.camera.getRotation().conjugate(new Quaternionf());
		Matrix4f matrix4f2 = new Matrix4f().rotation(quaternionf);
		Matrix4f matrix4f3 = matrix4f.mul(matrix4f2);
		Vec3d vec3d = this.camera.getCameraPos();
		Vec3d vec3d2 = sourcePos.subtract(vec3d);
		Vector3f vector3f = matrix4f3.transformProject(vec3d2.toVector3f());
		return new Vec3d(vector3f);
	}

	public double getPitch() {
		float f = this.camera.getPitch();
		if (f <= -90.0F) {
			return Double.NEGATIVE_INFINITY;
		} else if (f >= 90.0F) {
			return Double.POSITIVE_INFINITY;
		} else {
			float g = this.getFov(this.camera, 0.0F, true);
			return Math.tan(f * (float) (Math.PI / 180.0)) / Math.tan(g / 2.0F * (float) (Math.PI / 180.0));
		}
	}

	public GlobalSettings getGlobalSettings() {
		return this.globalSettings;
	}

	public DiffuseLighting getDiffuseLighting() {
		return this.diffuseLighting;
	}

	public void setWorld(@Nullable ClientWorld world) {
		if (world != null) {
			this.diffuseLighting.updateLevelBuffer(world.getDimension().cardinalLightType());
		}
	}

	public RotatingCubeMapRenderer getRotatingPanoramaRenderer() {
		return this.rotatingPanoramaRenderer;
	}
}
