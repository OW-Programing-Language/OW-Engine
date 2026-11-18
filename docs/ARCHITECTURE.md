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
    runtime/FixedStepLoop.ow      - capped deterministic fixed-step scheduler
    modules/EngineEntitiesModule.ow
    modules/EngineScenesModule.ow
    modules/EngineRuntimeModule.ow
  main.ow                         - headless composition root
tests/
  headless_engine.ow              - state, scene, scheduler, flOW contracts
  headless_graphics.ow            - dependency and headless-renderer integration
```

Service dependencies:

```text
engine.runtime -> ENGINE_SCENES (scenes)
engine.scenes  -> ENGINE_ENTITIES (entities)
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
- Entity deletion removes the ID from all scenes.

### Graphics integration

`dependencies.yml` pins flOW and Graphics source revisions. The graphics smoke test consumes the real `GRAPHICS_RENDERER` contract using `Graphics.HeadlessRenderer`. It does not import OpenGL native libraries. The engine's graphics orchestration layer is intentionally deferred until the camera/projection APIs are merged in OW Graphics. Avoid declaring a render-ready engine before a real framebuffer smoke test exists.

## Milestones

- **Phase 1 (this PR):** flOW modules, entity registry, named scenes, deterministic fixed-step loop, headless tests, CI.
- **Phase 2:** component stores and transform hierarchy, scriptable systems, lifecycle events, event bus and save/load contracts.
- **Phase 3:** render-world adapter over OW Graphics (camera, mesh, materials, scene submission), headless + Mesa integration tests.
- **Phase 4:** input action mapping, collision/physics abstractions, asset lifecycle, game window loop.
- **Phase 5:** voxel chunks, meshing, procedural terrain, streaming, networking and an OWCraft demonstrator.

## Definition of done

Each new feature should include an OW example, headless tests, proper flOW service boundaries and GitHub CI. Features that need a GPU must add Mesa/Xvfb pixel validation rather than only testing mocked calls.
