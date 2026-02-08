# Slabbed — Work Summary (latest)

## Scope & Goals
- Provide generic slab support so blocks/entities visually anchor to slab surfaces.
- Remove collision offsets to avoid player clipping.
- Support chain offsets (blocks on blocks on slabs) and special structures/entities (beds, tall blocks, minecarts, item frames).
- Fix hanging support (lanterns), top-slab placement, and visual offsets.

## Key Changes Implemented
- **Central offset helpers:**
  - `SlabSupport.shouldOffset()` (boolean) for -0.5 offset eligibility.
  - **New** `SlabSupport.getYOffset()` (double) returning:
    - `-0.5` for blocks on/above bottom slabs (including chain stacks).
    - `+0.5` for hanging blocks (`HANGING=true`) directly under top slabs.
    - `0.0` otherwise.
- **Chain offset:** Recursive down-walk (16 deep) for stacked blocks (sign on fence on slab, etc.).
- **Bed coordination:** Either half on a slab offsets both halves.
- **Double-block handling:** Upper halves check two blocks down.
- **Hanging exclusion from downward offset:** `HANGING=true` blocks aren’t pushed down by slabs; instead they can receive the +0.5 upward offset when under top slabs.
- **Stairs:** Re-enabled slab offset (user-requested) despite possible visual quirks; will refine later.
- **Wall-mounted blocks:** Wall signs, wall banners, wall torches, wall hanging signs check the attached block’s column for slab offset.
- **Top-slab support faces:** `isSideSolid` and `sideCoversSmallSquare` return true for `Direction.DOWN` on top slabs (enables hanging attachments).
- **Model/Outline/BE offsets updated to `getYOffset()`:**
  - `TorchModelOffsetMixin` (model offset in chunk meshing).
  - `SlabSupportStateMixin` (outline/hitbox offset).
  - `BlockEntityOffsetMixin` (block entity rendering).
- **Entity render offsets:**
  - **Minecarts:** `MinecartRenderOffsetMixin` injects in `updateRenderState`, adjusts `state.positionOffset` based on rail slab offset (uses `entity.getEntityWorld()`).
  - **Item frames:** New `ItemFrameRenderOffsetMixin` offsets frames based on the block they’re attached to.
- **Mixin registration:** Added `ItemFrameRenderOffsetMixin` to `slabbed.client.mixins.json`.
- **Top-slab lanterns:** Hanging lanterns under top slabs now receive +0.5 Y to sit flush against the slab bottom.

## Current Status
- Build succeeds with JDK `C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot`.
- Client runs; latest changes active.

## Verified / Expected Behaviors
- Blocks on bottom slabs render at slab height (-0.5); chain stacks supported.
- Beds offset if either half is on slab; tall/double blocks handled.
- Wall-mounted blocks (signs/banners/torches/hanging signs) offset if their attached block is offset.
- Minecarts on rails on slabs should sit correctly (render-state offset).
- Item frames on offset blocks should sit correctly.
- Hanging lanterns under top slabs should sit against slab bottoms (+0.5).
- Stairs now anchor again (visual quirks possible).

## Redstone on Slabs — Investigation Results (Outcome A: Vanilla-Correct)
Investigated why "redstone-on-slabs" shows no behavior change beyond placement/visuals.

**Findings (code analysis of vanilla bytecode + our mixins):**
- Dust placement is canonical: dust occupies `slabPos.up()` (standard vanilla position).
- `calculateWirePowerAt` uses `isSolidBlock()` to gate UP/DOWN step propagation.
- `isSolidBlock()` uses `solidBlockPredicate` (collision shape fullness) — completely separate from `isSideSolid` which our mixins modify. Our mixins do NOT alter `isSolidBlock`.
- Bottom slabs return `isSolidBlock=false` (collision shape is half-height) → vanilla-correct behavior:
  - **Same-level propagation**: works (direct neighbor check, no solidity gate).
  - **UP step**: skipped behind slabs (slab not solid → can't block line of sight). Vanilla-correct.
  - **DOWN step**: checked past slabs (slab not solid → allows down-step). Vanilla-correct.
- `getRenderConnectionType` mixins check `isRedstoneSupportTopSurface(world, sidePos)` at dust's Y level — effectively dead code for typical slab layouts since slabs are one Y below dust.
- Stashed WIP `RedstoneControllerWireLogicalPosMixin` was attempting unnecessary semantic expansion (remapping wire positions in controller). Not needed; same-level propagation already works at canonical positions.

**Decision:** No semantic expansion. Redstone on slabs behaves exactly as vanilla would for partial blocks. Document as unsupported beyond vanilla behavior for alpha.

## Known / Potential Issues & Future Work
- **Stairs:** Visual/face-culling quirks may still occur; needs refinement.
- **Rail slopes:** Transition from ground-height rail to slab-height rail remains visually awkward (asset/geometry issue).
- **Slab on offset objects:** Outline-offset makes face targeting tricky; cosmetic/non-breaking.
- **Placement edge cases:** Gaps under top slabs can still be finicky due to targeting.
- **Regression sweep:** Recheck all categories after lantern/top-slab changes.
- **Redstone on slabs:** Placement and visuals work; power propagation is vanilla-correct (no special slab behavior). Not a bug — document as known limitation.

## Files Touched (recent)
- `src/main/java/com/slabbed/util/SlabSupport.java` — add `getYOffset`, re-enable stairs, +0.5 for hanging under top slabs.
- `src/main/java/com/slabbed/mixin/SlabSupportStateMixin.java` — outline offset via `getYOffset`.
- `src/main/java/com/slabbed/mixin/SlabSupportBlockMixin.java` — top slab DOWN face support already present.
- `src/main/java/com/slabbed/mixin/client/TorchModelOffsetMixin.java` — model offset via `getYOffset`.
- `src/main/java/com/slabbed/mixin/client/BlockEntityOffsetMixin.java` — block entity render offset via `getYOffset`.
- `src/main/java/com/slabbed/mixin/client/MinecartRenderOffsetMixin.java` — render-state offset.
- `src/main/java/com/slabbed/mixin/client/ItemFrameRenderOffsetMixin.java` — new.
- `src/main/resources/slabbed.client.mixins.json` — register item frame mixin.

## Next Steps
- Validate in-game:
  - Hanging lantern on top slab bottom (should be flush).
  - Minecart on rail on slab (no floating).
  - Item frame on offset block (aligned).
  - Stairs on slabs (note remaining quirks).
- Investigate rail slope visuals (deferred).
- Optional: refine stairs offset/face-culling handling.
