# OW Engine → OW Graphics: ECS rendering bridge (Phase 2c / PR #4)

## Responsibility split

- **OW Engine** owns entities, active-scene membership, parent-child relationships and **references** to mesh handles.
- **OW Graphics** owns matrices, cameras, shader programs, renderer backends, GPU resource lifetime and frame drawing.
- **flOW** owns module lifecycle, service registration and dependency ordering.

No low-level OpenGL code, GLSL compiler, camera implementation or matrix multiplication is duplicated in OW Engine.

## Architecture

```text
SceneManager (active scene) ----> RenderWorld <----- ComponentRegistry (ECS transforms)
                                      |
                       WorldMatrices (T * Ry * S, ancestors)
                                      |
                                 Graphics.Transforms
                                      |
                    Graphics.Headless/OpenGLShader + MakeMultiScene
                                      |
                                 Graphics.Renderer
```

`EngineRenderModule` is an opt-in flOW Module that registers `render_world` with contract `ENGINE_RENDER_WORLD`. Dependencies are declared with `require_as` for `scenes`, `components`, `transforms`, `shader`, and `multi_scene`; missing contracts cause flOW boot verification to fail.

## API

```ow
set world: app.service("render_world")
call: world.set_mesh(entity_id, mesh_handle)
set model: world.model_matrix(entity_id)
set pos: world.rendered_position(entity_id)
set drawn int: world.render_frame(shader_id, view_mat4, projection_mat4, 12, 20, 30)
call: world.clear_mesh(entity_id)
```

The engine uses `uModel`, `uView`, `uProjection` uniforms (case-sensitive) and sets them with `GRAPHICS_SHADER.set_mat4`. Shaders **must be compatible** with those names. The caller sets any textures, material uniforms, lighting parameters and GL state (e.g. depth test) before submission.

Return value for `render_frame`: number of successfully drawn meshes (including 0 for an empty scene), **-1 for invalid arguments or adapter failures**. Only active-scene entities with valid associated mesh handles are drawn in stable entity insertion order. Matrix validation occurs before clearing the frame to avoid half-rendering an invalid transform hierarchy.

### Transform contract

An entity's local model matrix is computed entirely through `Graphics.Transforms`:

```
local = translation(x,y,z) * rotation_y(ry) * scaling(sx,sy,sz)
world = parent_world * local
```

The ancestor chain is processed root first, independent of creation order. Parent rotation and scale correctly affect children in rendered space. This is **intentionally different** from legacy `ComponentRegistry.world_position`, which remains translation-only for compatibility. Use `RenderWorld.rendered_position` or the world matrix's [12..14] slots to obtain true rendered coordinates.

**Current limit:** OW Graphics exposes `rotation_y` only, and accepts angles from -π to +π radians. Transforms with nonzero X or Z Euler rotation or out-of-range Y rotation return an empty matrix and cause preflight render failure, rather than being silently ignored. True XYZ rotations should be implemented **upstream in OW Graphics**.

### Mesh ownership and lifecycle

`set_mesh` stores a positive opaque Graphics mesh handle against an existing entity ID. The adapter does **not** allocate, upload, cache or destroy native GPU resources. Game code must create and destroy meshes through the real `GRAPHICS_MESH` service. Destroyed entities are skipped even if an old handle remains in the backing array. Engine entity IDs are monotonic and not reused, preventing stale handle references from being inherited by new entities.

### Running without a GPU

```sh
ow deps install
ow check tests/headless_render_world.ow
ow run tests/headless_render_world.ow
ow check examples/render_world/main.ow
ow run examples/render_world/main.ow
```

The test validates flOW dependency ordering, parent rotation and scale, stable scene isolation, invalid matrices, stale entities, camera projections and multi-object draw counts. This is **contract-level headless verification**, not a pixel test. The next milestone is an OpenGL/Mesa example that creates real shaders, textures, meshes and a window to verify framebuffer output.

The Graphics dependency pin was advanced to the merged Graphics 3D/material/scene functionality. This module remains interpreter-first, and the OW native AOT compiler does not yet execute the full ECS.
