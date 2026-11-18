# Architecture and engineering roadmap

## Separation of responsibilities

OW Engine owns **game state and simulation**. OW Graphics owns **GPU APIs** (textures, buffers, shaders, camera projection and the OpenGL/Vulkan backends). OW Language owns parsing, the interpreter, dependency resolution and (eventually) native AOT / FFI. flOW owns app lifecycle and modular composition.

Never duplicate the OW Graphics matrices, shader code or OpenGL bridge inside this repository.

## Modules

```text
src/
  engine/
    entities/EntityStore.ow       - entity registry, coordinates and lifecycle
    scenes/Scene.ow               - entity membership and tick state
    scenes/SceneManager.ow        - scene switching + spawn/destroy operations
    components/ComponentStore.ow - bounded entity component storage
    components/ComponentRegistry.ow - extensible ECS registry
    components/{TransformComponent,VelocityComponent}.ow
    systems/{EngineSystem,SystemRegistry,MovementSystem}.ow
    events/{EngineEventListener,EventBus}.ow
    persistence/SceneCheckpoint.ow
    runtime/FixedStepLoop.ow      - capped deterministic fixed-step scheduler and optional gameplay systems
    modules/EngineEntitiesModule.ow
    modules/EngineScenesModule.ow
    modules/EngineRuntimeModule.ow
    modules/{EngineComponentsModule,EngineSystemsModule,EngineEventsModule}.ow
  main.ow                         - headless composition root
tests/
  headless_engine.ow              - state, scene, scheduler, flOW contracts
  headless_graphics.ow            - dependency and headless-renderer integration
  headless_ecs.ow                 - ECS lifecycle, movement, ticks and cleanup
  headless_hierarchy_events.ow    - child transforms, lifecycle events
  headless_checkpoint.ow          - JSON state roundtrip and rejection
examples/ecs_motion/main.ow      - playable state-only movement demonstration
```

Service dependencies:

```text
engine.runtime -> ENGINE_SCENES (scenes)
engine.scenes  -> ENGINE_ENTITIES (entities)
engine.components -> ENGINE_ENTITIES (entities)
engine.systems  -> ENGINE_COMPONENTS (components), ENGINE_SCENES (scenes)
engine.events -> no requirements
engine.entities -> no requirements

Graphics.HeadlessRenderer -> GRAPHICS_RENDERER (renderer), independent
```

All modules are flOW classic `Module` implementations, register their services under unique names, and declare typed requirements via `app.require_as`. Components are passed explicitly at the composition root to avoid implicit shared global state. flOW starts in dependency order even when registered in reverse.

### Runtime semantics

- `update(elapsed_ms)` accumulates time and advances fixed `interval_ms` ticks.
- Maximum catch-up is 8 simulation steps per call. Excess frame time is intentionally dropped.
- `update(-1)` returns -1. Other calls return the number of executed ticks.
- Only the active scene advances; inactive scenes retain state.
- Entity IDs never alias a destroyed entity. Storage is bounded and does not reuse slots in v0.1.
- Entity deletion removes the ID from all scenes. Once `scenes.attach_components(components)` is called, it also cleans every component store for that entity.
- The optional `runtime.attach_systems(systems, components)` runs enabled systems before each active scene tick; existing v0.1 code remains unaffected.

### ECS semantics

- Component stores are indexed by monotonically increasing entity IDs, allowing deterministic O(1) component lookups and no accidental reattachment to stale IDs.
- The registry provides named component types and generic `put/get/has/remove` operations; `get` returns `0` for absent values.
- Built-in `TransformComponent` stores translation, Euler rotation and scale. `VelocityComponent` stores velocity in world units per second.
- `MovementSystem` iterates **active-scene entities only** and updates transforms using `delta_ms / 1000.0` seconds.
- `SystemRegistry` keeps systems in insertion order, enforces unique names, and supports enable/disable.
- The ECS registry mirrors its transform writes into the legacy `EntityStore` position fields. Direct legacy writes do not flow back into ECS components.
- No component persistence, archetype query optimizer, transform parenting, multithreading or physics is claimed at this stage.

### Phase 2b: hierarchy, events and checkpoints

- `TransformComponent.parent_id` is 0 for roots. `set_parent` verifies that both entities exist, rejects self-parenting and ancestry cycles, and uses `sync_all_positions` to keep legacy entity positions consistent with the composed world translation.
- World position sums parent translations only; parent rotations, scale multiplication and rendering matrices will use OW Graphics in a later phase. Parent removal preserves children's world **positions** by detaching direct children before the deletion.
- `EventBus` supports up to 64 subscriptions, deterministic insertion order, subscription tokens, safe unsubscribes during dispatch and a bounded recursive publish depth. Event failures do not make scene changes transactional.
- `EngineEventsModule` exposes `events` through flOW. `scenes.attach_events(events)` and `runtime.attach_events(events)` are opt-in and preserve compatibility.
- `SceneCheckpoint` captures and restores versioned JSON-friendly built-in components (transform and velocity) on the **same existing active scene, using matching IDs and order**. This is not a full save/load implementation. File I/O, ID remapping, custom component codecs and safe atomic restores remain a future phase.

### Graphics integration

`dependencies.yml` pins flOW and Graphics source revisions. The graphics smoke test consumes the real `GRAPHICS_RENDERER` contract using `Graphics.HeadlessRenderer`. It does not import OpenGL native libraries. The engine's graphics orchestration layer is intentionally deferred until the camera/projection APIs are merged in OW Graphics. Avoid declaring a render-ready engine before a real framebuffer smoke test exists.

## Milestones

- **Phase 1 (merged):** flOW modules, entity registry, named scenes, deterministic fixed-step loop, headless tests, CI.
- **Phase 2a (this PR):** ECS component stores, transforms / velocities, movement systems, flOW service integration and deterministic headless tests.
- **Phase 2b (this PR):** parent/child world translations, opt-in lifecycle event bus, and versioned same-live-scene JSON checkpoints.
- **Phase 2c:** full transform composition via OW Graphics, robust disk save/load with ID remapping and custom-component codecs, event/system dependency ordering.
- **Phase 3:** render-world adapter over OW Graphics (camera, mesh, materials, scene submission), headless + Mesa integration tests.
- **Phase 4:** input action mapping, collision/physics abstractions, asset lifecycle, game window loop.
- **Phase 5:** voxel chunks, meshing, procedural terrain, streaming, networking and an OWCraft demonstrator.

## Definition of done

Each new feature should include an OW example, headless tests, proper flOW service boundaries and GitHub CI. Features that need a GPU must add Mesa/Xvfb pixel validation rather than only testing mocked calls.


## Phase 2c (PR #4): ECS-to-Graphics adapter

The new `EngineRenderModule` declares flOW dependencies on `scenes`, `components`, `transforms`, `shader` and `multi_scene`. `RenderWorld` stores non-owning mesh handle mappings and submits active-scene meshes through OW Graphics. `WorldMatrices` computes parent-first T * Ry * S hierarchy transforms with Graphics.Transforms, while the legacy ECS world-position function remains translation-only for backwards compatibility. GPU ownership remains with OW Graphics; see [RENDER-WORLD.md](RENDER-WORLD.md). Current limits: only Y Euler rotation, no real OpenGL framebuffer validation of the integrated engine, and no automatic mesh/texture ownership.
