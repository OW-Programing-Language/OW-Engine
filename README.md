# OW Engine 🦉

**A modular game-engine foundation for the OW programming language, powered by flOW and OW Graphics.**

OW Engine is an early-stage, **headless-first** 2D/3D game-engine project written in OW. The first milestone implements deterministic game state and simulation plumbing; it does **not** yet render a world, simulate physics, or provide a finished 3D game engine.

## Current capabilities

- **Entities:** unique monotonic entity IDs, names, lifecycle and integer 3D positions.
- **Scenes:** multiple named scenes, active-scene switching, entity membership, independent tick counters.
- **Runtime:** fixed-step simulation in milliseconds, with a catch-up cap to avoid unlimited work after a stalled frame.
- **flOW:** independently registered entity, scene and runtime modules, service contracts, dependency-ordered startup and shutdown.
- **OW Graphics:** an explicitly SHA-pinned source dependency, with a GPU-free integration test using its real headless rendering contract.
- **Tests:** native OW assertions for lifecycle, scene isolation, entity invalidation, fixed-step logic and graphics-service compatibility.

No OpenGL or GLFW installation is needed for the headless tests.

## Architecture

```text
OW game / OWCraft (future)
   |
   v
OW Engine
  entities     - stable IDs, names and positions
  scenes       - named scenes and entity membership
  runtime      - fixed-step deterministic simulation
  modules      - flOW lifecycle and service contracts
   |
   +-- flOW            (pinned source dependency)
   +-- OW Graphics     (pinned source dependency)
         |
         +-- GLFW/OpenGL or headless backend
```

Rendering and GPU resource ownership remain in [OW Graphics](https://github.com/OW-Programing-Language/OW-Graphics). OW Engine will eventually orchestrate scenes, rendering systems and world state, without duplicating graphics APIs.

## Get started

Install OW from [ow-lang](https://github.com/OW-Programing-Language/ow-lang) and Git. From the project root:

```sh
ow deps install
ow check src/main.ow
ow run src/main.ow
ow check tests/headless_engine.ow
ow run tests/headless_engine.ow
ow check tests/headless_graphics.ow
ow run tests/headless_graphics.ow
```

For a source-built compiler, replace `ow` with `java -jar /path/to/ow-lang/target/OwIDE.jar`.

The committed `dependencies.lock.yml` pins flOW and OW Graphics to tested **upstream revisions**. Update those pins intentionally when new OW Graphics APIs (including 3D cameras) are merged. This is a source dependency, not a vendored copy.

## Example

```ow
import "@flow/src/App.ow"
import "src/engine/modules/EngineEntitiesModule.ow"
import "src/engine/modules/EngineScenesModule.ow"
import "src/engine/modules/EngineRuntimeModule.ow"

main:
  set entities: new EngineEntitiesModule()
  set scenes: new EngineScenesModule(entities.store())
  set runtime: new EngineRuntimeModule(scenes.store(), 16)
  set app: new App()
  call: app.use(runtime)
  call: app.use(scenes)
  call: app.use(entities)
  call: assert(app.boot() == 1)
  set world: app.service("scenes")
  call: world.add_scene("overworld")
  set player int: world.spawn("player")
  call: entities.store().set_position(player, 3, 0, 8)
  call: app.service("runtime").update(16)
  call: app.stop()
```

See [Architecture and roadmap](docs/ARCHITECTURE.md) and the executable [headless demo](src/main.ow) for working entry points.

## Scope and limitations

- Entity storage: 256 lifetime allocations per store (no slot reuse yet).
- Scene storage: 32 named scenes, 128 entity IDs per scene.
- Positions are integer coordinates in this first iteration. No rotations or physics yet.
- Fixed-step loop advances scene counters only; gameplay systems will be added later.
- OW's Java-hosted interpreter runs this foundation; this is **not** native-AOT gameplay code.
- OW Graphics GPU integration, interactive game window and 3D draw submission are future integration milestones.

## Links

- [OW language](https://github.com/OW-Programing-Language/ow-lang)
- [flOW framework](https://github.com/OW-Programing-Language/flOW---OW-Modular-Framework)
- [OW Graphics](https://github.com/OW-Programing-Language/OW-Graphics)
