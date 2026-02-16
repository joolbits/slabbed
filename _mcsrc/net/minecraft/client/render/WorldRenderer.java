package net.minecraft.client.render;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.SortedSet;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.Block.SetBlockStateFlag;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.DynamicUniforms;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.SimpleFramebufferFactory;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.TextureFilteringMode;
import net.minecraft.client.render.block.entity.BlockEntityRenderManager;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.chunk.AbstractChunkRenderData;
import net.minecraft.client.render.chunk.Buffers;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.render.chunk.ChunkRenderData;
import net.minecraft.client.render.chunk.ChunkRendererRegionBuilder;
import net.minecraft.client.render.chunk.NormalizedRelativePos;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.command.RenderDispatcher;
import net.minecraft.client.render.debug.DebugRenderer;
import net.minecraft.client.render.debug.GameTestDebugRenderer;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.gizmo.GizmoDrawerImpl;
import net.minecraft.client.render.model.ModelBaker;
import net.minecraft.client.render.state.BreakingBlockRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.render.state.OutlineRenderState;
import net.minecraft.client.render.state.SkyRenderState;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.Handle;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.BlockBreakingInfo;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.SynchronousResourceReloader;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.annotation.Debug;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.util.profiler.Profilers;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.BlockView;
import net.minecraft.world.LightType;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.debug.gizmo.GizmoCollectorImpl;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import net.minecraft.world.debug.gizmo.GizmoDrawing.CollectorScope;
import net.minecraft.world.dimension.DimensionType.Skybox;
import net.minecraft.world.tick.TickManager;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class WorldRenderer implements SynchronousResourceReloader, AutoCloseable {
	private static final Identifier TRANSPARENCY = Identifier.ofVanilla("transparency");
	private static final Identifier ENTITY_OUTLINE = Identifier.ofVanilla("entity_outline");
	public static final int SECTION_SIZE = 16;
	public static final int HALF_SECTION_SIZE = 8;
	public static final int NEARBY_SECTION_DISTANCE = 32;
	private static final int MIN_TRANSPARENT_SORT_COUNT = 15;
	private static final float field_64450 = 0.3F;
	private final MinecraftClient client;
	private final EntityRenderManager entityRenderManager;
	private final BlockEntityRenderManager blockEntityRenderManager;
	private final BufferBuilderStorage bufferBuilders;
	@Nullable
	private SkyRendering skyRendering;
	private final CloudRenderer cloudRenderer = new CloudRenderer();
	private final WorldBorderRendering worldBorderRendering = new WorldBorderRendering();
	private final WeatherRendering weatherRendering = new WeatherRendering();
	private final SubmittableBatch particleBatch = new SubmittableBatch();
	public final DebugRenderer debugRenderer = new DebugRenderer();
	public final GameTestDebugRenderer gameTestDebugRenderer = new GameTestDebugRenderer();
	@Nullable
	private ClientWorld world;
	private final ChunkRenderingDataPreparer chunkRenderingDataPreparer = new ChunkRenderingDataPreparer();
	private final ObjectArrayList<ChunkBuilder.BuiltChunk> builtChunks = new ObjectArrayList<>(10000);
	private final ObjectArrayList<ChunkBuilder.BuiltChunk> nearbyChunks = new ObjectArrayList<>(50);
	@Nullable
	private BuiltChunkStorage chunks;
	private int ticks;
	private final Int2ObjectMap<BlockBreakingInfo> blockBreakingInfos = new Int2ObjectOpenHashMap<>();
	private final Long2ObjectMap<SortedSet<BlockBreakingInfo>> blockBreakingProgressions = new Long2ObjectOpenHashMap<>();
	@Nullable
	private Framebuffer entityOutlineFramebuffer;
	private final DefaultFramebufferSet framebufferSet = new DefaultFramebufferSet();
	private int cameraChunkX = Integer.MIN_VALUE;
	private int cameraChunkY = Integer.MIN_VALUE;
	private int cameraChunkZ = Integer.MIN_VALUE;
	private double lastCameraX = Double.MIN_VALUE;
	private double lastCameraY = Double.MIN_VALUE;
	private double lastCameraZ = Double.MIN_VALUE;
	private double lastCameraPitch = Double.MIN_VALUE;
	private double lastCameraYaw = Double.MIN_VALUE;
	@Nullable
	private ChunkBuilder chunkBuilder;
	private int viewDistance = -1;
	private boolean captureFrustum;
	@Nullable
	private Frustum capturedFrustum;
	@Nullable
	private BlockPos lastTranslucencySortCameraPos;
	private int chunkIndex;
	private final WorldRenderState worldRenderState;
	private final OrderedRenderCommandQueueImpl entityRenderCommandQueue;
	private final RenderDispatcher entityRenderDispatcher;
	@Nullable
	private GpuSampler terrainSampler;
	private final GizmoCollectorImpl gizmoCollector = new GizmoCollectorImpl();
	private WorldRenderer.Gizmos gizmos = new WorldRenderer.Gizmos(new GizmoDrawerImpl(), new GizmoDrawerImpl());

	public WorldRenderer(
		MinecraftClient client,
		EntityRenderManager entityRenderManager,
		BlockEntityRenderManager blockEntityRenderManager,
		BufferBuilderStorage bufferBuilders,
		WorldRenderState worldRenderState,
		RenderDispatcher entityRenderDispatcher
	) {
		this.client = client;
		this.entityRenderManager = entityRenderManager;
		this.blockEntityRenderManager = blockEntityRenderManager;
		this.bufferBuilders = bufferBuilders;
		this.entityRenderCommandQueue = entityRenderDispatcher.getQueue();
		this.worldRenderState = worldRenderState;
		this.entityRenderDispatcher = entityRenderDispatcher;
	}

	public void close() {
		if (this.entityOutlineFramebuffer != null) {
			this.entityOutlineFramebuffer.delete();
		}

		if (this.skyRendering != null) {
			this.skyRendering.close();
		}

		if (this.terrainSampler != null) {
			this.terrainSampler.close();
		}

		this.cloudRenderer.close();
	}

	public void reload(ResourceManager manager) {
		this.loadEntityOutlinePostProcessor();
		if (this.skyRendering != null) {
			this.skyRendering.close();
		}

		this.skyRendering = new SkyRendering(this.client.getTextureManager(), this.client.getAtlasManager());
	}

	public void loadEntityOutlinePostProcessor() {
		if (this.entityOutlineFramebuffer != null) {
			this.entityOutlineFramebuffer.delete();
		}

		this.entityOutlineFramebuffer = new SimpleFramebuffer(
			"Entity Outline", this.client.getWindow().getFramebufferWidth(), this.client.getWindow().getFramebufferHeight(), true
		);
	}

	@Nullable
	private PostEffectProcessor getTransparencyPostEffectProcessor() {
		if (!MinecraftClient.usesImprovedTransparency()) {
			return null;
		} else {
			PostEffectProcessor postEffectProcessor = this.client.getShaderLoader().loadPostEffect(TRANSPARENCY, DefaultFramebufferSet.STAGES);
			if (postEffectProcessor == null) {
				this.client.options.getImprovedTransparency().setValue(false);
				this.client.options.write();
			}

			return postEffectProcessor;
		}
	}

	public void drawEntityOutlinesFramebuffer() {
		if (this.canDrawEntityOutlines()) {
			this.entityOutlineFramebuffer.drawBlit(this.client.getFramebuffer().getColorAttachmentView());
		}
	}

	protected boolean canDrawEntityOutlines() {
		return !this.client.gameRenderer.isRenderingPanorama() && this.entityOutlineFramebuffer != null && this.client.player != null;
	}

	public void setWorld(@Nullable ClientWorld world) {
		this.cameraChunkX = Integer.MIN_VALUE;
		this.cameraChunkY = Integer.MIN_VALUE;
		this.cameraChunkZ = Integer.MIN_VALUE;
		this.world = world;
		if (world != null) {
			this.reload();
		} else {
			this.entityRenderManager.clearCamera();
			if (this.chunks != null) {
				this.chunks.clear();
				this.chunks = null;
			}

			if (this.chunkBuilder != null) {
				this.chunkBuilder.stop();
			}

			this.chunkBuilder = null;
			this.chunkRenderingDataPreparer.setStorage(null);
			this.clear();
		}

		this.gameTestDebugRenderer.clear();
	}

	private void clear() {
		this.builtChunks.clear();
		this.nearbyChunks.clear();
	}

	public void reload() {
		if (this.world != null) {
			this.world.reloadColor();
			if (this.chunkBuilder == null) {
				this.chunkBuilder = new ChunkBuilder(
					this.world, this, Util.getMainWorkerExecutor(), this.bufferBuilders, this.client.getBlockRenderManager(), this.client.getBlockEntityRenderDispatcher()
				);
			} else {
				this.chunkBuilder.setWorld(this.world);
			}

			this.cloudRenderer.scheduleTerrainUpdate();
			BlockRenderLayers.setCutoutLeaves(this.client.options.getCutoutLeaves().getValue());
			LeavesBlock.setCutoutLeaves(this.client.options.getCutoutLeaves().getValue());
			this.viewDistance = this.client.options.getClampedViewDistance();
			if (this.chunks != null) {
				this.chunks.clear();
			}

			this.chunkBuilder.cancelAllTasks();
			this.chunks = new BuiltChunkStorage(this.chunkBuilder, this.world, this.client.options.getClampedViewDistance(), this);
			this.chunkRenderingDataPreparer.setStorage(this.chunks);
			this.clear();
			Camera camera = this.client.gameRenderer.getCamera();
			this.chunks.updateCameraPosition(ChunkSectionPos.from(camera.getCameraPos()));
		}
	}

	public void onResized(int width, int height) {
		this.scheduleTerrainUpdate();
		if (this.entityOutlineFramebuffer != null) {
			this.entityOutlineFramebuffer.resize(width, height);
		}
	}

	@Nullable
	public String getChunksDebugString() {
		if (this.chunks == null) {
			return null;
		} else {
			int i = this.chunks.chunks.length;
			int j = this.getCompletedChunkCount();
			return String.format(
				Locale.ROOT,
				"C: %d/%d %sD: %d, %s",
				j,
				i,
				this.client.chunkCullingEnabled ? "(s) " : "",
				this.viewDistance,
				this.chunkBuilder == null ? "null" : this.chunkBuilder.getDebugString()
			);
		}
	}

	@Nullable
	public ChunkBuilder getChunkBuilder() {
		return this.chunkBuilder;
	}

	public double getChunkCount() {
		return this.chunks == null ? 0.0 : this.chunks.chunks.length;
	}

	public double getViewDistance() {
		return this.viewDistance;
	}

	public int getCompletedChunkCount() {
		int i = 0;

		for (ChunkBuilder.BuiltChunk builtChunk : this.builtChunks) {
			if (builtChunk.getCurrentRenderData().hasData()) {
				i++;
			}
		}

		return i;
	}

	public void refreshTerrainSampler() {
		if (this.terrainSampler != null) {
			this.terrainSampler.close();
		}

		this.terrainSampler = null;
	}

	@Nullable
	public String getEntitiesDebugString() {
		return this.world == null
			? null
			: "E: " + this.worldRenderState.entityRenderStates.size() + "/" + this.world.getRegularEntityCount() + ", SD: " + this.world.getSimulationDistance();
	}

	private void updateCamera(Camera camera, Frustum frustum, boolean spectator) {
		Vec3d vec3d = camera.getCameraPos();
		if (this.client.options.getClampedViewDistance() != this.viewDistance) {
			this.reload();
		}

		Profiler profiler = Profilers.get();
		profiler.push("repositionCamera");
		int i = ChunkSectionPos.getSectionCoord(vec3d.getX());
		int j = ChunkSectionPos.getSectionCoord(vec3d.getY());
		int k = ChunkSectionPos.getSectionCoord(vec3d.getZ());
		if (this.cameraChunkX != i || this.cameraChunkY != j || this.cameraChunkZ != k) {
			this.cameraChunkX = i;
			this.cameraChunkY = j;
			this.cameraChunkZ = k;
			this.chunks.updateCameraPosition(ChunkSectionPos.from(vec3d));
			this.worldBorderRendering.markBuffersDirty();
		}

		this.chunkBuilder.setCameraPosition(vec3d);
		double d = Math.floor(vec3d.x / 8.0);
		double e = Math.floor(vec3d.y / 8.0);
		double f = Math.floor(vec3d.z / 8.0);
		if (d != this.lastCameraX || e != this.lastCameraY || f != this.lastCameraZ) {
			this.chunkRenderingDataPreparer.scheduleTerrainUpdate();
		}

		this.lastCameraX = d;
		this.lastCameraY = e;
		this.lastCameraZ = f;
		profiler.pop();
		if (this.capturedFrustum == null) {
			boolean bl = this.client.chunkCullingEnabled;
			if (spectator && this.world.getBlockState(camera.getBlockPos()).isOpaqueFullCube()) {
				bl = false;
			}

			profiler.push("updateSOG");
			this.chunkRenderingDataPreparer.updateSectionOcclusionGraph(bl, camera, frustum, this.builtChunks, this.world.getChunkManager().getActiveSections());
			profiler.pop();
			double g = Math.floor(camera.getPitch() / 2.0F);
			double h = Math.floor(camera.getYaw() / 2.0F);
			if (this.chunkRenderingDataPreparer.updateFrustum() || g != this.lastCameraPitch || h != this.lastCameraYaw) {
				profiler.push("applyFrustum");
				this.applyFrustum(offsetFrustum(frustum));
				profiler.pop();
				this.lastCameraPitch = g;
				this.lastCameraYaw = h;
			}
		}
	}

	public static Frustum offsetFrustum(Frustum frustum) {
		return new Frustum(frustum).coverBoxAroundSetPosition(8);
	}

	private void applyFrustum(Frustum frustum) {
		if (!MinecraftClient.getInstance().isOnThread()) {
			throw new IllegalStateException("applyFrustum called from wrong thread: " + Thread.currentThread().getName());
		} else {
			this.clear();
			this.chunkRenderingDataPreparer.collectChunks(frustum, this.builtChunks, this.nearbyChunks);
		}
	}

	public void addBuiltChunk(ChunkBuilder.BuiltChunk chunk) {
		this.chunkRenderingDataPreparer.schedulePropagationFrom(chunk);
	}

	private Frustum setupFrustum(Matrix4f posMatrix, Matrix4f projMatrix, Vec3d pos) {
		Frustum frustum;
		if (this.capturedFrustum != null && !this.captureFrustum) {
			frustum = this.capturedFrustum;
		} else {
			frustum = new Frustum(posMatrix, projMatrix);
			frustum.setPosition(pos.getX(), pos.getY(), pos.getZ());
		}

		if (this.captureFrustum) {
			this.capturedFrustum = frustum;
			this.captureFrustum = false;
		}

		return frustum;
	}

	public void render(
		ObjectAllocator allocator,
		RenderTickCounter tickCounter,
		boolean renderBlockOutline,
		Camera camera,
		Matrix4f positionMatrix,
		Matrix4f basicProjectionMatrix,
		Matrix4f projectionMatrix,
		GpuBufferSlice fogBuffer,
		Vector4f fogColor,
		boolean renderSky
	) {
		float f = tickCounter.getTickProgress(false);
		this.worldRenderState.time = this.world.getTime();
		this.blockEntityRenderManager.configure(camera);
		this.entityRenderManager.configure(camera, this.client.targetedEntity);
		final Profiler profiler = Profilers.get();
		profiler.push("populateLightUpdates");
		this.world.runQueuedChunkUpdates();
		profiler.swap("runLightUpdates");
		this.world.getChunkManager().getLightingProvider().doLightUpdates();
		profiler.swap("prepareCullFrustum");
		Vec3d vec3d = camera.getCameraPos();
		Frustum frustum = this.setupFrustum(positionMatrix, projectionMatrix, vec3d);
		profiler.swap("cullTerrain");
		this.updateCamera(camera, frustum, this.client.player.isSpectator());
		profiler.swap("compileSections");
		this.updateChunks(camera);
		profiler.swap("extract");
		profiler.push("entities");
		this.fillEntityRenderStates(camera, frustum, tickCounter, this.worldRenderState);
		profiler.swap("blockEntities");
		this.fillBlockEntityRenderStates(camera, f, this.worldRenderState);
		profiler.swap("blockOutline");
		this.fillEntityOutlineRenderStates(camera, this.worldRenderState);
		profiler.swap("blockBreaking");
		this.fillBlockBreakingProgressRenderState(camera, this.worldRenderState);
		profiler.swap("weather");
		this.weatherRendering.buildPrecipitationPieces(this.world, this.ticks, f, vec3d, this.worldRenderState.weatherRenderState);
		profiler.swap("sky");
		this.skyRendering.updateRenderState(this.world, f, camera, this.worldRenderState.skyRenderState);
		profiler.swap("border");
		this.worldBorderRendering
			.updateRenderState(this.world.getWorldBorder(), f, vec3d, this.client.options.getClampedViewDistance() * 16, this.worldRenderState.worldBorderRenderState);
		profiler.pop();
		profiler.swap("debug");
		this.debugRenderer.render(frustum, vec3d.x, vec3d.y, vec3d.z, tickCounter.getTickProgress(false));
		this.gameTestDebugRenderer.render();
		profiler.swap("setupFrameGraph");
		Matrix4fStack matrix4fStack = RenderSystem.getModelViewStack();
		matrix4fStack.pushMatrix();
		matrix4fStack.mul(positionMatrix);
		FrameGraphBuilder frameGraphBuilder = new FrameGraphBuilder();
		this.framebufferSet.mainFramebuffer = frameGraphBuilder.createObjectNode("main", this.client.getFramebuffer());
		int i = this.client.getFramebuffer().textureWidth;
		int j = this.client.getFramebuffer().textureHeight;
		SimpleFramebufferFactory simpleFramebufferFactory = new SimpleFramebufferFactory(i, j, true, 0);
		PostEffectProcessor postEffectProcessor = this.getTransparencyPostEffectProcessor();
		if (postEffectProcessor != null) {
			this.framebufferSet.translucentFramebuffer = frameGraphBuilder.createResourceHandle("translucent", simpleFramebufferFactory);
			this.framebufferSet.itemEntityFramebuffer = frameGraphBuilder.createResourceHandle("item_entity", simpleFramebufferFactory);
			this.framebufferSet.particlesFramebuffer = frameGraphBuilder.createResourceHandle("particles", simpleFramebufferFactory);
			this.framebufferSet.weatherFramebuffer = frameGraphBuilder.createResourceHandle("weather", simpleFramebufferFactory);
			this.framebufferSet.cloudsFramebuffer = frameGraphBuilder.createResourceHandle("clouds", simpleFramebufferFactory);
		}

		if (this.entityOutlineFramebuffer != null) {
			this.framebufferSet.entityOutlineFramebuffer = frameGraphBuilder.createObjectNode("entity_outline", this.entityOutlineFramebuffer);
		}

		FramePass framePass = frameGraphBuilder.createPass("clear");
		this.framebufferSet.mainFramebuffer = framePass.transfer(this.framebufferSet.mainFramebuffer);
		framePass.setRenderer(
			() -> {
				Framebuffer framebuffer = this.client.getFramebuffer();
				RenderSystem.getDevice()
					.createCommandEncoder()
					.clearColorAndDepthTextures(
						framebuffer.getColorAttachment(), ColorHelper.fromFloats(0.0F, fogColor.x, fogColor.y, fogColor.z), framebuffer.getDepthAttachment(), 1.0
					);
			}
		);
		if (renderSky) {
			this.renderSky(frameGraphBuilder, camera, fogBuffer);
		}

		this.renderMain(frameGraphBuilder, frustum, positionMatrix, fogBuffer, renderBlockOutline, this.worldRenderState, tickCounter, profiler);
		PostEffectProcessor postEffectProcessor2 = this.client.getShaderLoader().loadPostEffect(ENTITY_OUTLINE, DefaultFramebufferSet.MAIN_AND_ENTITY_OUTLINE);
		if (this.worldRenderState.hasOutline && postEffectProcessor2 != null) {
			postEffectProcessor2.render(frameGraphBuilder, i, j, this.framebufferSet);
		}

		this.client.particleManager.addToBatch(this.particleBatch, new Frustum(frustum).offset(-3.0F), camera, f);
		this.renderParticles(frameGraphBuilder, fogBuffer);
		CloudRenderMode cloudRenderMode = this.client.options.getCloudRenderModeValue();
		if (cloudRenderMode != CloudRenderMode.OFF) {
			int k = (Integer)camera.getEnvironmentAttributeInterpolator().get(EnvironmentAttributes.CLOUD_COLOR_VISUAL, f);
			if (ColorHelper.getAlpha(k) > 0) {
				float g = (Float)camera.getEnvironmentAttributeInterpolator().get(EnvironmentAttributes.CLOUD_HEIGHT_VISUAL, f);
				this.renderClouds(frameGraphBuilder, cloudRenderMode, this.worldRenderState.cameraRenderState.pos, this.worldRenderState.time, f, k, g);
			}
		}

		this.renderWeather(frameGraphBuilder, fogBuffer);
		if (postEffectProcessor != null) {
			postEffectProcessor.render(frameGraphBuilder, i, j, this.framebufferSet);
		}

		this.renderLateDebug(frameGraphBuilder, this.worldRenderState.cameraRenderState, fogBuffer, positionMatrix);
		profiler.swap("executeFrameGraph");
		frameGraphBuilder.run(allocator, new FrameGraphBuilder.Profiler() {
			@Override
			public void push(String location) {
				profiler.push(location);
			}

			@Override
			public void pop(String location) {
				profiler.pop();
			}
		});
		this.framebufferSet.clear();
		matrix4fStack.popMatrix();
		profiler.pop();
		this.worldRenderState.clear();
	}

	private void renderMain(
		FrameGraphBuilder frameGraphBuilder,
		Frustum frustum,
		Matrix4f posMatrix,
		GpuBufferSlice fogBuffer,
		boolean renderBlockOutline,
		WorldRenderState state,
		RenderTickCounter tickCounter,
		Profiler profiler
	) {
		FramePass framePass = frameGraphBuilder.createPass("main");
		this.framebufferSet.mainFramebuffer = framePass.transfer(this.framebufferSet.mainFramebuffer);
		if (this.framebufferSet.translucentFramebuffer != null) {
			this.framebufferSet.translucentFramebuffer = framePass.transfer(this.framebufferSet.translucentFramebuffer);
		}

		if (this.framebufferSet.itemEntityFramebuffer != null) {
			this.framebufferSet.itemEntityFramebuffer = framePass.transfer(this.framebufferSet.itemEntityFramebuffer);
		}

		if (this.framebufferSet.weatherFramebuffer != null) {
			this.framebufferSet.weatherFramebuffer = framePass.transfer(this.framebufferSet.weatherFramebuffer);
		}

		if (state.hasOutline && this.framebufferSet.entityOutlineFramebuffer != null) {
			this.framebufferSet.entityOutlineFramebuffer = framePass.transfer(this.framebufferSet.entityOutlineFramebuffer);
		}

		Handle<Framebuffer> handle = this.framebufferSet.mainFramebuffer;
		Handle<Framebuffer> handle2 = this.framebufferSet.translucentFramebuffer;
		Handle<Framebuffer> handle3 = this.framebufferSet.itemEntityFramebuffer;
		Handle<Framebuffer> handle4 = this.framebufferSet.entityOutlineFramebuffer;
		framePass.setRenderer(
			() -> {
				RenderSystem.setShaderFog(fogBuffer);
				Vec3d vec3d = state.cameraRenderState.pos;
				double d = vec3d.getX();
				double e = vec3d.getY();
				double f = vec3d.getZ();
				profiler.push("terrain");
				if (this.terrainSampler == null) {
					int i = this.client.options.getTextureFiltering().getValue() == TextureFilteringMode.ANISOTROPIC ? this.client.options.getEffectiveAnisotropy() : 1;
					this.terrainSampler = RenderSystem.getDevice()
						.createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, i, OptionalDouble.empty());
				}

				SectionRenderState sectionRenderState = this.renderBlockLayers(posMatrix, d, e, f);
				sectionRenderState.renderSection(BlockRenderLayerGroup.OPAQUE, this.terrainSampler);
				this.client.gameRenderer.getDiffuseLighting().setShaderLights(DiffuseLighting.Type.LEVEL);
				if (handle3 != null) {
					handle3.get().copyDepthFrom(this.client.getFramebuffer());
				}

				if (this.canDrawEntityOutlines() && handle4 != null) {
					Framebuffer framebuffer = handle4.get();
					RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(framebuffer.getColorAttachment(), 0, framebuffer.getDepthAttachment(), 1.0);
				}

				MatrixStack matrixStack = new MatrixStack();
				VertexConsumerProvider.Immediate immediate = this.bufferBuilders.getEntityVertexConsumers();
				VertexConsumerProvider.Immediate immediate2 = this.bufferBuilders.getEffectVertexConsumers();
				profiler.swap("submitEntities");
				this.pushEntityRenders(matrixStack, state, this.entityRenderCommandQueue);
				profiler.swap("submitBlockEntities");
				this.renderBlockEntities(matrixStack, state, this.entityRenderCommandQueue);
				profiler.swap("renderFeatures");
				this.entityRenderDispatcher.render();
				immediate.drawCurrentLayer();
				this.checkEmpty(matrixStack);
				immediate.draw(RenderLayers.solid());
				immediate.draw(RenderLayers.endPortal());
				immediate.draw(RenderLayers.endGateway());
				immediate.draw(TexturedRenderLayers.getEntitySolid());
				immediate.draw(TexturedRenderLayers.getEntityCutout());
				immediate.draw(TexturedRenderLayers.getBeds());
				immediate.draw(TexturedRenderLayers.getShulkerBoxes());
				immediate.draw(TexturedRenderLayers.getSign());
				immediate.draw(TexturedRenderLayers.getHangingSign());
				immediate.draw(TexturedRenderLayers.getChest());
				this.bufferBuilders.getOutlineVertexConsumers().draw();
				if (renderBlockOutline) {
					this.renderTargetBlockOutline(immediate, matrixStack, false, state);
				}

				profiler.pop();
				this.collectGizmos();
				this.gizmos.standardPrimitives().draw(matrixStack, immediate, state.cameraRenderState, posMatrix);
				immediate.drawCurrentLayer();
				this.checkEmpty(matrixStack);
				immediate.draw(TexturedRenderLayers.getItemTranslucentCull());
				immediate.draw(TexturedRenderLayers.getBannerPatterns());
				immediate.draw(TexturedRenderLayers.getShieldPatterns());
				immediate.draw(RenderLayers.armorEntityGlint());
				immediate.draw(RenderLayers.glint());
				immediate.draw(RenderLayers.glintTranslucent());
				immediate.draw(RenderLayers.entityGlint());
				profiler.push("destroyProgress");
				this.renderBlockDamage(matrixStack, immediate2, state);
				immediate2.draw();
				profiler.pop();
				this.checkEmpty(matrixStack);
				immediate.draw(RenderLayers.waterMask());
				immediate.draw();
				if (handle2 != null) {
					handle2.get().copyDepthFrom(handle.get());
				}

				profiler.push("translucent");
				sectionRenderState.renderSection(BlockRenderLayerGroup.TRANSLUCENT, this.terrainSampler);
				profiler.swap("string");
				sectionRenderState.renderSection(BlockRenderLayerGroup.TRIPWIRE, this.terrainSampler);
				if (renderBlockOutline) {
					this.renderTargetBlockOutline(immediate, matrixStack, true, state);
				}

				immediate.draw();
				profiler.pop();
			}
		);
	}

	private void renderParticles(FrameGraphBuilder frameGraphBuilder, GpuBufferSlice fogBuffer) {
		FramePass framePass = frameGraphBuilder.createPass("particles");
		if (this.framebufferSet.particlesFramebuffer != null) {
			this.framebufferSet.particlesFramebuffer = framePass.transfer(this.framebufferSet.particlesFramebuffer);
			framePass.dependsOn(this.framebufferSet.mainFramebuffer);
		} else {
			this.framebufferSet.mainFramebuffer = framePass.transfer(this.framebufferSet.mainFramebuffer);
		}

		Handle<Framebuffer> handle = this.framebufferSet.mainFramebuffer;
		Handle<Framebuffer> handle2 = this.framebufferSet.particlesFramebuffer;
		framePass.setRenderer(() -> {
			RenderSystem.setShaderFog(fogBuffer);
			if (handle2 != null) {
				handle2.get().copyDepthFrom(handle.get());
			}

			this.particleBatch.submit(this.entityRenderCommandQueue, this.worldRenderState.cameraRenderState);
			this.entityRenderDispatcher.render();
			this.particleBatch.onFrameEnd();
		});
	}

	private void renderClouds(FrameGraphBuilder frameGraphBuilder, CloudRenderMode mode, Vec3d cameraPos, long l, float f, int i, float g) {
		FramePass framePass = frameGraphBuilder.createPass("clouds");
		if (this.framebufferSet.cloudsFramebuffer != null) {
			this.framebufferSet.cloudsFramebuffer = framePass.transfer(this.framebufferSet.cloudsFramebuffer);
		} else {
			this.framebufferSet.mainFramebuffer = framePass.transfer(this.framebufferSet.mainFramebuffer);
		}

		framePass.setRenderer(() -> this.cloudRenderer.renderClouds(i, mode, g, cameraPos, l, f));
	}

	private void renderWeather(FrameGraphBuilder frameGraphBuilder, GpuBufferSlice gpuBufferSlice) {
		int i = this.client.options.getClampedViewDistance() * 16;
		float f = this.client.gameRenderer.getFarPlaneDistance();
		FramePass framePass = frameGraphBuilder.createPass("weather");
		if (this.framebufferSet.weatherFramebuffer != null) {
			this.framebufferSet.weatherFramebuffer = framePass.transfer(this.framebufferSet.weatherFramebuffer);
		} else {
			this.framebufferSet.mainFramebuffer = framePass.transfer(this.framebufferSet.mainFramebuffer);
		}

		framePass.setRenderer(() -> {
			RenderSystem.setShaderFog(gpuBufferSlice);
			VertexConsumerProvider.Immediate immediate = this.bufferBuilders.getEntityVertexConsumers();
			CameraRenderState cameraRenderState = this.worldRenderState.cameraRenderState;
			this.weatherRendering.renderPrecipitation(immediate, cameraRenderState.pos, this.worldRenderState.weatherRenderState);
			this.worldBorderRendering.render(this.worldRenderState.worldBorderRenderState, cameraRenderState.pos, i, f);
			immediate.draw();
		});
	}

	private void renderLateDebug(FrameGraphBuilder frameGraphBuilder, CameraRenderState cameraRenderState, GpuBufferSlice fogBuffer, Matrix4f matrix4f) {
		FramePass framePass = frameGraphBuilder.createPass("late_debug");
		this.framebufferSet.mainFramebuffer = framePass.transfer(this.framebufferSet.mainFramebuffer);
		if (this.framebufferSet.itemEntityFramebuffer != null) {
			this.framebufferSet.itemEntityFramebuffer = framePass.transfer(this.framebufferSet.itemEntityFramebuffer);
		}

		Handle<Framebuffer> handle = this.framebufferSet.mainFramebuffer;
		framePass.setRenderer(() -> {
			RenderSystem.setShaderFog(fogBuffer);
			MatrixStack matrixStack = new MatrixStack();
			VertexConsumerProvider.Immediate immediate = this.bufferBuilders.getEntityVertexConsumers();
			RenderSystem.outputColorTextureOverride = handle.get().getColorAttachmentView();
			RenderSystem.outputDepthTextureOverride = handle.get().getDepthAttachmentView();
			if (!this.gizmos.alwaysOnTopPrimitives().isEmpty()) {
				Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();
				RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(framebuffer.getDepthAttachment(), 1.0);
				this.gizmos.alwaysOnTopPrimitives().draw(matrixStack, immediate, cameraRenderState, matrix4f);
				immediate.drawCurrentLayer();
			}

			RenderSystem.outputColorTextureOverride = null;
			RenderSystem.outputDepthTextureOverride = null;
			this.checkEmpty(matrixStack);
		});
	}

	private void fillEntityRenderStates(Camera camera, Frustum frustum, RenderTickCounter tickCounter, WorldRenderState renderStates) {
		Vec3d vec3d = camera.getCameraPos();
		double d = vec3d.getX();
		double e = vec3d.getY();
		double f = vec3d.getZ();
		TickManager tickManager = this.client.world.getTickManager();
		boolean bl = this.canDrawEntityOutlines();
		Entity.setRenderDistanceMultiplier(
			MathHelper.clamp(this.client.options.getClampedViewDistance() / 8.0, 1.0, 2.5) * this.client.options.getEntityDistanceScaling().getValue()
		);

		for (Entity entity : this.world.getEntities()) {
			if (this.entityRenderManager.shouldRender(entity, frustum, d, e, f) || entity.hasPassengerDeep(this.client.player)) {
				BlockPos blockPos = entity.getBlockPos();
				if ((this.world.isOutOfHeightLimit(blockPos.getY()) || this.isRenderingReady(blockPos))
					&& (
						entity != camera.getFocusedEntity()
							|| camera.isThirdPerson()
							|| camera.getFocusedEntity() instanceof LivingEntity && ((LivingEntity)camera.getFocusedEntity()).isSleeping()
					)
					&& (!(entity instanceof ClientPlayerEntity) || camera.getFocusedEntity() == entity)) {
					if (entity.age == 0) {
						entity.lastRenderX = entity.getX();
						entity.lastRenderY = entity.getY();
						entity.lastRenderZ = entity.getZ();
					}

					float g = tickCounter.getTickProgress(!tickManager.shouldSkipTick(entity));
					EntityRenderState entityRenderState = this.getAndUpdateRenderState(entity, g);
					renderStates.entityRenderStates.add(entityRenderState);
					if (entityRenderState.hasOutline() && bl) {
						renderStates.hasOutline = true;
					}
				}
			}
		}
	}

	private void pushEntityRenders(MatrixStack matrices, WorldRenderState renderStates, OrderedRenderCommandQueue queue) {
		Vec3d vec3d = renderStates.cameraRenderState.pos;
		double d = vec3d.getX();
		double e = vec3d.getY();
		double f = vec3d.getZ();

		for (EntityRenderState entityRenderState : renderStates.entityRenderStates) {
			if (!renderStates.hasOutline) {
				entityRenderState.outlineColor = 0;
			}

			this.entityRenderManager
				.render(entityRenderState, renderStates.cameraRenderState, entityRenderState.x - d, entityRenderState.y - e, entityRenderState.z - f, matrices, queue);
		}
	}

	private void fillBlockEntityRenderStates(Camera camera, float tickProgress, WorldRenderState renderStates) {
		Vec3d vec3d = camera.getCameraPos();
		double d = vec3d.getX();
		double e = vec3d.getY();
		double f = vec3d.getZ();
		MatrixStack matrixStack = new MatrixStack();

		for (ChunkBuilder.BuiltChunk builtChunk : this.builtChunks) {
			List<BlockEntity> list = builtChunk.getCurrentRenderData().getBlockEntities();
			if (!list.isEmpty() && !(builtChunk.method_76298(Util.getMeasuringTimeMs()) < 0.3F)) {
				for (BlockEntity blockEntity : list) {
					BlockPos blockPos = blockEntity.getPos();
					SortedSet<BlockBreakingInfo> sortedSet = this.blockBreakingProgressions.get(blockPos.asLong());
					ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlayCommand;
					if (sortedSet != null && !sortedSet.isEmpty()) {
						matrixStack.push();
						matrixStack.translate(blockPos.getX() - d, blockPos.getY() - e, blockPos.getZ() - f);
						crumblingOverlayCommand = new ModelCommandRenderer.CrumblingOverlayCommand(((BlockBreakingInfo)sortedSet.last()).getStage(), matrixStack.peek());
						matrixStack.pop();
					} else {
						crumblingOverlayCommand = null;
					}

					BlockEntityRenderState blockEntityRenderState = this.blockEntityRenderManager.getRenderState(blockEntity, tickProgress, crumblingOverlayCommand);
					if (blockEntityRenderState != null) {
						renderStates.blockEntityRenderStates.add(blockEntityRenderState);
					}
				}
			}
		}

		Iterator<BlockEntity> iterator = this.world.getBlockEntities().iterator();

		while (iterator.hasNext()) {
			BlockEntity blockEntity2 = (BlockEntity)iterator.next();
			if (blockEntity2.isRemoved()) {
				iterator.remove();
			} else {
				BlockEntityRenderState blockEntityRenderState2 = this.blockEntityRenderManager.getRenderState(blockEntity2, tickProgress, null);
				if (blockEntityRenderState2 != null) {
					renderStates.blockEntityRenderStates.add(blockEntityRenderState2);
				}
			}
		}
	}

	private void renderBlockEntities(MatrixStack matrices, WorldRenderState renderStates, OrderedRenderCommandQueueImpl queue) {
		Vec3d vec3d = renderStates.cameraRenderState.pos;
		double d = vec3d.getX();
		double e = vec3d.getY();
		double f = vec3d.getZ();

		for (BlockEntityRenderState blockEntityRenderState : renderStates.blockEntityRenderStates) {
			BlockPos blockPos = blockEntityRenderState.pos;
			matrices.push();
			matrices.translate(blockPos.getX() - d, blockPos.getY() - e, blockPos.getZ() - f);
			this.blockEntityRenderManager.render(blockEntityRenderState, matrices, queue, renderStates.cameraRenderState);
			matrices.pop();
		}
	}

	private void fillBlockBreakingProgressRenderState(Camera camera, WorldRenderState renderStates) {
		Vec3d vec3d = camera.getCameraPos();
		double d = vec3d.getX();
		double e = vec3d.getY();
		double f = vec3d.getZ();
		renderStates.breakingBlockRenderStates.clear();

		for (Entry<SortedSet<BlockBreakingInfo>> entry : this.blockBreakingProgressions.long2ObjectEntrySet()) {
			BlockPos blockPos = BlockPos.fromLong(entry.getLongKey());
			if (!(blockPos.getSquaredDistanceFromCenter(d, e, f) > 1024.0)) {
				SortedSet<BlockBreakingInfo> sortedSet = (SortedSet<BlockBreakingInfo>)entry.getValue();
				if (sortedSet != null && !sortedSet.isEmpty()) {
					int i = ((BlockBreakingInfo)sortedSet.last()).getStage();
					renderStates.breakingBlockRenderStates.add(new BreakingBlockRenderState(this.world, blockPos, i));
				}
			}
		}
	}

	private void renderBlockDamage(MatrixStack matrices, VertexConsumerProvider.Immediate immediate, WorldRenderState renderStates) {
		Vec3d vec3d = renderStates.cameraRenderState.pos;
		double d = vec3d.getX();
		double e = vec3d.getY();
		double f = vec3d.getZ();

		for (BreakingBlockRenderState breakingBlockRenderState : renderStates.breakingBlockRenderStates) {
			matrices.push();
			BlockPos blockPos = breakingBlockRenderState.entityBlockPos;
			matrices.translate(blockPos.getX() - d, blockPos.getY() - e, blockPos.getZ() - f);
			MatrixStack.Entry entry = matrices.peek();
			VertexConsumer vertexConsumer = new OverlayVertexConsumer(
				immediate.getBuffer((RenderLayer)ModelBaker.BLOCK_DESTRUCTION_RENDER_LAYERS.get(breakingBlockRenderState.breakProgress)), entry, 1.0F
			);
			this.client.getBlockRenderManager().renderDamage(breakingBlockRenderState.blockState, blockPos, breakingBlockRenderState, matrices, vertexConsumer);
			matrices.pop();
		}
	}

	private void fillEntityOutlineRenderStates(Camera camera, WorldRenderState renderStates) {
		renderStates.outlineRenderState = null;
		if (this.client.crosshairTarget instanceof BlockHitResult blockHitResult) {
			if (blockHitResult.getType() != Type.MISS) {
				BlockPos blockPos = blockHitResult.getBlockPos();
				BlockState blockState = this.world.getBlockState(blockPos);
				if (!blockState.isAir() && this.world.getWorldBorder().contains(blockPos)) {
					boolean bl = BlockRenderLayers.getBlockLayer(blockState).isTranslucent();
					boolean bl2 = this.client.options.getHighContrastBlockOutline().getValue();
					ShapeContext shapeContext = ShapeContext.of(camera.getFocusedEntity());
					VoxelShape voxelShape = blockState.getOutlineShape(this.world, blockPos, shapeContext);
					if (SharedConstants.SHAPES) {
						VoxelShape voxelShape2 = blockState.getCollisionShape(this.world, blockPos, shapeContext);
						VoxelShape voxelShape3 = blockState.getCullingShape();
						VoxelShape voxelShape4 = blockState.getRaycastShape(this.world, blockPos);
						renderStates.outlineRenderState = new OutlineRenderState(blockPos, bl, bl2, voxelShape, voxelShape2, voxelShape3, voxelShape4);
					} else {
						renderStates.outlineRenderState = new OutlineRenderState(blockPos, bl, bl2, voxelShape);
					}
				}
			}
		}
	}

	private void renderTargetBlockOutline(
		VertexConsumerProvider.Immediate immediate, MatrixStack matrices, boolean renderBlockOutline, WorldRenderState renderStates
	) {
		OutlineRenderState outlineRenderState = renderStates.outlineRenderState;
		if (outlineRenderState != null) {
			if (outlineRenderState.isTranslucent() == renderBlockOutline) {
				Vec3d vec3d = renderStates.cameraRenderState.pos;
				if (outlineRenderState.highContrast()) {
					VertexConsumer vertexConsumer = immediate.getBuffer(RenderLayers.secondaryBlockOutline());
					this.drawBlockOutline(matrices, vertexConsumer, vec3d.x, vec3d.y, vec3d.z, outlineRenderState, -16777216, 7.0F);
				}

				VertexConsumer vertexConsumer = immediate.getBuffer(RenderLayers.lines());
				int i = outlineRenderState.highContrast() ? -11010079 : ColorHelper.toAlpha(102);
				this.drawBlockOutline(matrices, vertexConsumer, vec3d.x, vec3d.y, vec3d.z, outlineRenderState, i, this.client.getWindow().getMinimumLineWidth());
				immediate.drawCurrentLayer();
			}
		}
	}

	private void checkEmpty(MatrixStack matrices) {
		if (!matrices.isEmpty()) {
			throw new IllegalStateException("Pose stack not empty");
		}
	}

	private EntityRenderState getAndUpdateRenderState(Entity entity, float tickProgress) {
		return this.entityRenderManager.getAndUpdateRenderState(entity, tickProgress);
	}

	private void translucencySort(Vec3d cameraPos) {
		if (!this.builtChunks.isEmpty()) {
			BlockPos blockPos = BlockPos.ofFloored(cameraPos);
			boolean bl = !blockPos.equals(this.lastTranslucencySortCameraPos);
			NormalizedRelativePos normalizedRelativePos = new NormalizedRelativePos();

			for (ChunkBuilder.BuiltChunk builtChunk : this.nearbyChunks) {
				this.scheduleChunkTranslucencySort(builtChunk, normalizedRelativePos, cameraPos, bl, true);
			}

			this.chunkIndex = this.chunkIndex % this.builtChunks.size();
			int i = Math.max(this.builtChunks.size() / 8, 15);

			while (i-- > 0) {
				int j = this.chunkIndex++ % this.builtChunks.size();
				this.scheduleChunkTranslucencySort(this.builtChunks.get(j), normalizedRelativePos, cameraPos, bl, false);
			}

			this.lastTranslucencySortCameraPos = blockPos;
		}
	}

	private void scheduleChunkTranslucencySort(
		ChunkBuilder.BuiltChunk chunk, NormalizedRelativePos relativePos, Vec3d cameraPos, boolean needsUpdate, boolean ignoreCameraAlignment
	) {
		relativePos.with(cameraPos, chunk.getSectionPos());
		boolean bl = chunk.getCurrentRenderData().hasPosition(relativePos);
		boolean bl2 = needsUpdate && (relativePos.isOnCameraAxis() || ignoreCameraAlignment);
		if ((bl2 || bl) && !chunk.isCurrentlySorting() && chunk.hasTranslucentLayer()) {
			chunk.scheduleSort(this.chunkBuilder);
		}
	}

	private SectionRenderState renderBlockLayers(Matrix4fc matrix, double cameraX, double cameraY, double cameraZ) {
		ObjectListIterator<ChunkBuilder.BuiltChunk> objectListIterator = this.builtChunks.listIterator(0);
		EnumMap<BlockRenderLayer, List<RenderPass.RenderObject<GpuBufferSlice[]>>> enumMap = new EnumMap(BlockRenderLayer.class);
		int i = 0;

		for (BlockRenderLayer blockRenderLayer : BlockRenderLayer.values()) {
			enumMap.put(blockRenderLayer, new ArrayList());
		}

		List<DynamicUniforms.ChunkSectionsValue> list = new ArrayList();
		GpuTextureView gpuTextureView = this.client.getTextureManager().getTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).getGlTextureView();
		int j = gpuTextureView.getWidth(0);
		int k = gpuTextureView.getHeight(0);

		while (objectListIterator.hasNext()) {
			ChunkBuilder.BuiltChunk builtChunk = (ChunkBuilder.BuiltChunk)objectListIterator.next();
			AbstractChunkRenderData abstractChunkRenderData = builtChunk.getCurrentRenderData();
			BlockPos blockPos = builtChunk.getOrigin();
			long l = Util.getMeasuringTimeMs();
			int m = -1;

			for (BlockRenderLayer blockRenderLayer2 : BlockRenderLayer.values()) {
				Buffers buffers = abstractChunkRenderData.getBuffersForLayer(blockRenderLayer2);
				if (buffers != null) {
					if (m == -1) {
						m = list.size();
						list.add(
							new DynamicUniforms.ChunkSectionsValue(new Matrix4f(matrix), blockPos.getX(), blockPos.getY(), blockPos.getZ(), builtChunk.method_76298(l), j, k)
						);
					}

					GpuBuffer gpuBuffer;
					VertexFormat.IndexType indexType;
					if (buffers.getIndexBuffer() == null) {
						if (buffers.getIndexCount() > i) {
							i = buffers.getIndexCount();
						}

						gpuBuffer = null;
						indexType = null;
					} else {
						gpuBuffer = buffers.getIndexBuffer();
						indexType = buffers.getIndexType();
					}

					int n = m;
					((List)enumMap.get(blockRenderLayer2))
						.add(
							new RenderPass.RenderObject(
								0,
								buffers.getVertexBuffer(),
								gpuBuffer,
								indexType,
								0,
								buffers.getIndexCount(),
								(gpuBufferSlicesx, uniformUploader) -> uniformUploader.upload("ChunkSection", gpuBufferSlicesx[n])
							)
						);
				}
			}
		}

		GpuBufferSlice[] gpuBufferSlices = RenderSystem.getDynamicUniforms()
			.writeChunkSections((DynamicUniforms.ChunkSectionsValue[])list.toArray(new DynamicUniforms.ChunkSectionsValue[0]));
		return new SectionRenderState(gpuTextureView, enumMap, i, gpuBufferSlices);
	}

	public void rotate() {
		this.cloudRenderer.rotate();
	}

	public void captureFrustum() {
		this.captureFrustum = true;
	}

	public void killFrustum() {
		this.capturedFrustum = null;
	}

	public void tick(Camera camera) {
		if (this.world.getTickManager().shouldTick()) {
			this.ticks++;
		}

		this.weatherRendering
			.addParticlesAndSound(this.world, camera, this.ticks, this.client.options.getParticles().getValue(), this.client.options.getWeatherRadius().getValue());
		this.updateBlockBreakingProgress();
	}

	private void updateBlockBreakingProgress() {
		if (this.ticks % 20 == 0) {
			Iterator<BlockBreakingInfo> iterator = this.blockBreakingInfos.values().iterator();

			while (iterator.hasNext()) {
				BlockBreakingInfo blockBreakingInfo = (BlockBreakingInfo)iterator.next();
				int i = blockBreakingInfo.getLastUpdateTick();
				if (this.ticks - i > 400) {
					iterator.remove();
					this.removeBlockBreakingInfo(blockBreakingInfo);
				}
			}
		}
	}

	private void removeBlockBreakingInfo(BlockBreakingInfo info) {
		long l = info.getPos().asLong();
		Set<BlockBreakingInfo> set = (Set<BlockBreakingInfo>)this.blockBreakingProgressions.get(l);
		set.remove(info);
		if (set.isEmpty()) {
			this.blockBreakingProgressions.remove(l);
		}
	}

	private void renderSky(FrameGraphBuilder frameGraphBuilder, Camera camera, GpuBufferSlice fogBuffer) {
		CameraSubmersionType cameraSubmersionType = camera.getSubmersionType();
		if (cameraSubmersionType != CameraSubmersionType.POWDER_SNOW && cameraSubmersionType != CameraSubmersionType.LAVA && !this.hasBlindnessOrDarkness(camera)) {
			SkyRenderState skyRenderState = this.worldRenderState.skyRenderState;
			if (skyRenderState.skybox != Skybox.NONE) {
				SkyRendering skyRendering = this.skyRendering;
				if (skyRendering != null) {
					FramePass framePass = frameGraphBuilder.createPass("sky");
					this.framebufferSet.mainFramebuffer = framePass.transfer(this.framebufferSet.mainFramebuffer);
					framePass.setRenderer(
						() -> {
							RenderSystem.setShaderFog(fogBuffer);
							if (skyRenderState.skybox == Skybox.END) {
								skyRendering.renderEndSky();
								if (skyRenderState.endFlashIntensity > 1.0E-5F) {
									MatrixStack matrixStack = new MatrixStack();
									skyRendering.drawEndLightFlash(matrixStack, skyRenderState.endFlashIntensity, skyRenderState.endFlashPitch, skyRenderState.endFlashYaw);
								}
							} else {
								MatrixStack matrixStack = new MatrixStack();
								skyRendering.renderTopSky(skyRenderState.skyColor);
								skyRendering.renderGlowingSky(matrixStack, skyRenderState.sunAngle, skyRenderState.sunriseAndSunsetColor);
								skyRendering.renderCelestialBodies(
									matrixStack,
									skyRenderState.sunAngle,
									skyRenderState.moonAngle,
									skyRenderState.starAngle,
									skyRenderState.moonPhase,
									skyRenderState.rainGradient,
									skyRenderState.starBrightness
								);
								if (skyRenderState.shouldRenderSkyDark) {
									skyRendering.renderSkyDark();
								}
							}
						}
					);
				}
			}
		}
	}

	private boolean hasBlindnessOrDarkness(Camera camera) {
		return !(camera.getFocusedEntity() instanceof LivingEntity livingEntity)
			? false
			: livingEntity.hasStatusEffect(StatusEffects.BLINDNESS) || livingEntity.hasStatusEffect(StatusEffects.DARKNESS);
	}

	private void updateChunks(Camera camera) {
		Profiler profiler = Profilers.get();
		profiler.push("populateSectionsToCompile");
		ChunkRendererRegionBuilder chunkRendererRegionBuilder = new ChunkRendererRegionBuilder();
		BlockPos blockPos = camera.getBlockPos();
		List<ChunkBuilder.BuiltChunk> list = Lists.<ChunkBuilder.BuiltChunk>newArrayList();
		long l = MathHelper.floor(this.client.options.getChunkFade().getValue() * 1000.0);

		for (ChunkBuilder.BuiltChunk builtChunk : this.builtChunks) {
			if (builtChunk.needsRebuild() && (builtChunk.getCurrentRenderData() != ChunkRenderData.HIDDEN || builtChunk.shouldBuild())) {
				BlockPos blockPos2 = ChunkSectionPos.from(builtChunk.getSectionPos()).getCenterPos();
				double d = blockPos2.getSquaredDistance(blockPos);
				boolean bl = d < 768.0;
				boolean bl2 = false;
				if (this.client.options.getChunkBuilderMode().getValue() == ChunkBuilderMode.NEARBY) {
					bl2 = bl || builtChunk.needsImportantRebuild();
				} else if (this.client.options.getChunkBuilderMode().getValue() == ChunkBuilderMode.PLAYER_AFFECTED) {
					bl2 = builtChunk.needsImportantRebuild();
				}

				if (!bl && !builtChunk.method_76546()) {
					builtChunk.method_76548(l);
				} else {
					builtChunk.method_76548(0L);
				}

				builtChunk.method_76547(false);
				if (bl2) {
					profiler.push("compileSectionSynchronously");
					this.chunkBuilder.rebuild(builtChunk, chunkRendererRegionBuilder);
					builtChunk.cancelRebuild();
					profiler.pop();
				} else {
					list.add(builtChunk);
				}
			}
		}

		profiler.swap("uploadSectionMeshes");
		this.chunkBuilder.upload();
		profiler.swap("scheduleAsyncCompile");

		for (ChunkBuilder.BuiltChunk builtChunkx : list) {
			builtChunkx.scheduleRebuild(chunkRendererRegionBuilder);
			builtChunkx.cancelRebuild();
		}

		profiler.swap("scheduleTranslucentResort");
		this.translucencySort(camera.getCameraPos());
		profiler.pop();
	}

	private void drawBlockOutline(
		MatrixStack matrices, VertexConsumer vertexConsumer, double x, double y, double z, OutlineRenderState state, int color, float lineWidth
	) {
		BlockPos blockPos = state.pos();
		if (SharedConstants.SHAPES) {
			VertexRendering.drawOutline(
				matrices,
				vertexConsumer,
				state.shape(),
				blockPos.getX() - x,
				blockPos.getY() - y,
				blockPos.getZ() - z,
				ColorHelper.fromFloats(1.0F, 1.0F, 1.0F, 1.0F),
				lineWidth
			);
			if (state.collisionShape() != null) {
				VertexRendering.drawOutline(
					matrices,
					vertexConsumer,
					state.collisionShape(),
					blockPos.getX() - x,
					blockPos.getY() - y,
					blockPos.getZ() - z,
					ColorHelper.fromFloats(0.4F, 0.0F, 0.0F, 0.0F),
					lineWidth
				);
			}

			if (state.occlusionShape() != null) {
				VertexRendering.drawOutline(
					matrices,
					vertexConsumer,
					state.occlusionShape(),
					blockPos.getX() - x,
					blockPos.getY() - y,
					blockPos.getZ() - z,
					ColorHelper.fromFloats(0.4F, 0.0F, 1.0F, 0.0F),
					lineWidth
				);
			}

			if (state.interactionShape() != null) {
				VertexRendering.drawOutline(
					matrices,
					vertexConsumer,
					state.interactionShape(),
					blockPos.getX() - x,
					blockPos.getY() - y,
					blockPos.getZ() - z,
					ColorHelper.fromFloats(0.4F, 0.0F, 0.0F, 1.0F),
					lineWidth
				);
			}
		} else {
			VertexRendering.drawOutline(matrices, vertexConsumer, state.shape(), blockPos.getX() - x, blockPos.getY() - y, blockPos.getZ() - z, color, lineWidth);
		}
	}

	public void updateBlock(BlockView world, BlockPos pos, BlockState oldState, BlockState newState, @SetBlockStateFlag int flags) {
		this.scheduleSectionRender(pos, (flags & Block.REDRAW_ON_MAIN_THREAD) != 0);
	}

	private void scheduleSectionRender(BlockPos pos, boolean important) {
		for (int i = pos.getZ() - 1; i <= pos.getZ() + 1; i++) {
			for (int j = pos.getX() - 1; j <= pos.getX() + 1; j++) {
				for (int k = pos.getY() - 1; k <= pos.getY() + 1; k++) {
					this.scheduleChunkRender(ChunkSectionPos.getSectionCoord(j), ChunkSectionPos.getSectionCoord(k), ChunkSectionPos.getSectionCoord(i), important);
				}
			}
		}
	}

	public void scheduleBlockRenders(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		for (int i = minZ - 1; i <= maxZ + 1; i++) {
			for (int j = minX - 1; j <= maxX + 1; j++) {
				for (int k = minY - 1; k <= maxY + 1; k++) {
					this.scheduleChunkRender(ChunkSectionPos.getSectionCoord(j), ChunkSectionPos.getSectionCoord(k), ChunkSectionPos.getSectionCoord(i));
				}
			}
		}
	}

	public void scheduleBlockRerenderIfNeeded(BlockPos pos, BlockState old, BlockState updated) {
		if (this.client.getBakedModelManager().shouldRerender(old, updated)) {
			this.scheduleBlockRenders(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ());
		}
	}

	public void scheduleChunkRenders3x3x3(int x, int y, int z) {
		this.scheduleChunkRenders(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1);
	}

	public void scheduleChunkRenders(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		for (int i = minZ; i <= maxZ; i++) {
			for (int j = minX; j <= maxX; j++) {
				for (int k = minY; k <= maxY; k++) {
					this.scheduleChunkRender(j, k, i);
				}
			}
		}
	}

	public void scheduleChunkRender(int chunkX, int chunkY, int chunkZ) {
		this.scheduleChunkRender(chunkX, chunkY, chunkZ, false);
	}

	private void scheduleChunkRender(int x, int y, int z, boolean important) {
		this.chunks.scheduleRebuild(x, y, z, important);
	}

	public void onChunkUnload(long sectionPos) {
		ChunkBuilder.BuiltChunk builtChunk = this.chunks.getRenderedChunk(sectionPos);
		if (builtChunk != null) {
			this.chunkRenderingDataPreparer.schedulePropagationFrom(builtChunk);
			builtChunk.method_76547(true);
		}
	}

	public void setBlockBreakingInfo(int entityId, BlockPos pos, int stage) {
		if (stage >= 0 && stage < 10) {
			BlockBreakingInfo blockBreakingInfo = this.blockBreakingInfos.get(entityId);
			if (blockBreakingInfo != null) {
				this.removeBlockBreakingInfo(blockBreakingInfo);
			}

			if (blockBreakingInfo == null
				|| blockBreakingInfo.getPos().getX() != pos.getX()
				|| blockBreakingInfo.getPos().getY() != pos.getY()
				|| blockBreakingInfo.getPos().getZ() != pos.getZ()) {
				blockBreakingInfo = new BlockBreakingInfo(entityId, pos);
				this.blockBreakingInfos.put(entityId, blockBreakingInfo);
			}

			blockBreakingInfo.setStage(stage);
			blockBreakingInfo.setLastUpdateTick(this.ticks);
			this.blockBreakingProgressions
				.computeIfAbsent(blockBreakingInfo.getPos().asLong(), (Long2ObjectFunction<? extends SortedSet<BlockBreakingInfo>>)(l -> Sets.newTreeSet()))
				.add(blockBreakingInfo);
		} else {
			BlockBreakingInfo blockBreakingInfox = this.blockBreakingInfos.remove(entityId);
			if (blockBreakingInfox != null) {
				this.removeBlockBreakingInfo(blockBreakingInfox);
			}
		}
	}

	public boolean isTerrainRenderComplete() {
		return this.chunkBuilder.isEmpty();
	}

	public void scheduleNeighborUpdates(ChunkPos chunkPos) {
		this.chunkRenderingDataPreparer.addNeighbors(chunkPos);
	}

	public void scheduleTerrainUpdate() {
		this.chunkRenderingDataPreparer.scheduleTerrainUpdate();
		this.cloudRenderer.scheduleTerrainUpdate();
	}

	public static int getLightmapCoordinates(BlockRenderView world, BlockPos pos) {
		return getLightmapCoordinates(WorldRenderer.BrightnessGetter.DEFAULT, world, world.getBlockState(pos), pos);
	}

	public static int getLightmapCoordinates(WorldRenderer.BrightnessGetter brightnessGetter, BlockRenderView world, BlockState state, BlockPos pos) {
		if (state.hasEmissiveLighting(world, pos)) {
			return 15728880;
		} else {
			int i = brightnessGetter.packedBrightness(world, pos);
			int j = LightmapTextureManager.getBlockLightCoordinates(i);
			int k = state.getLuminance();
			if (j < k) {
				int l = LightmapTextureManager.getSkyLightCoordinates(i);
				return LightmapTextureManager.pack(k, l);
			} else {
				return i;
			}
		}
	}

	public boolean isRenderingReady(BlockPos pos) {
		ChunkBuilder.BuiltChunk builtChunk = this.chunks.getRenderedChunk(pos);
		return builtChunk != null && builtChunk.currentRenderData.get() != ChunkRenderData.HIDDEN
			? builtChunk.method_76298(Util.getMeasuringTimeMs()) >= 0.3F
			: false;
	}

	@Nullable
	public Framebuffer getEntityOutlinesFramebuffer() {
		return this.framebufferSet.entityOutlineFramebuffer != null ? this.framebufferSet.entityOutlineFramebuffer.get() : null;
	}

	@Nullable
	public Framebuffer getTranslucentFramebuffer() {
		return this.framebufferSet.translucentFramebuffer != null ? this.framebufferSet.translucentFramebuffer.get() : null;
	}

	@Nullable
	public Framebuffer getEntityFramebuffer() {
		return this.framebufferSet.itemEntityFramebuffer != null ? this.framebufferSet.itemEntityFramebuffer.get() : null;
	}

	@Nullable
	public Framebuffer getParticlesFramebuffer() {
		return this.framebufferSet.particlesFramebuffer != null ? this.framebufferSet.particlesFramebuffer.get() : null;
	}

	@Nullable
	public Framebuffer getWeatherFramebuffer() {
		return this.framebufferSet.weatherFramebuffer != null ? this.framebufferSet.weatherFramebuffer.get() : null;
	}

	@Nullable
	public Framebuffer getCloudsFramebuffer() {
		return this.framebufferSet.cloudsFramebuffer != null ? this.framebufferSet.cloudsFramebuffer.get() : null;
	}

	@Debug
	public ObjectArrayList<ChunkBuilder.BuiltChunk> getBuiltChunks() {
		return this.builtChunks;
	}

	@Debug
	public ChunkRenderingDataPreparer getChunkRenderingDataPreparer() {
		return this.chunkRenderingDataPreparer;
	}

	@Nullable
	public Frustum getCapturedFrustum() {
		return this.capturedFrustum;
	}

	public CloudRenderer getCloudRenderer() {
		return this.cloudRenderer;
	}

	public CollectorScope startDrawingGizmos() {
		return GizmoDrawing.using(this.gizmoCollector);
	}

	private void collectGizmos() {
		GizmoDrawerImpl gizmoDrawerImpl = new GizmoDrawerImpl();
		GizmoDrawerImpl gizmoDrawerImpl2 = new GizmoDrawerImpl();
		this.gizmoCollector.add(this.client.getGizmos());
		IntegratedServer integratedServer = this.client.getServer();
		if (integratedServer != null) {
			this.gizmoCollector.add(integratedServer.getGizmoEntries());
		}

		long l = Util.getMeasuringTimeMs();

		for (net.minecraft.world.debug.gizmo.GizmoCollectorImpl.Entry entry : this.gizmoCollector.extractGizmos()) {
			entry.getGizmo().draw(entry.ignoresOcclusion() ? gizmoDrawerImpl2 : gizmoDrawerImpl, entry.getOpacity(l));
		}

		this.gizmos = new WorldRenderer.Gizmos(gizmoDrawerImpl, gizmoDrawerImpl2);
	}

	@FunctionalInterface
	@Environment(EnvType.CLIENT)
	public interface BrightnessGetter {
		WorldRenderer.BrightnessGetter DEFAULT = (world, pos) -> {
			int i = world.getLightLevel(LightType.SKY, pos);
			int j = world.getLightLevel(LightType.BLOCK, pos);
			return Brightness.pack(j, i);
		};

		int packedBrightness(BlockRenderView world, BlockPos pos);
	}

	@Environment(EnvType.CLIENT)
	record Gizmos(GizmoDrawerImpl standardPrimitives, GizmoDrawerImpl alwaysOnTopPrimitives) {
	}
}
