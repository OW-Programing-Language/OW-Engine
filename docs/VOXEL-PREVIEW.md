# GPU 3D voxel preview (OW Engine PR #5)

This is the first **integrated rendered** OW Engine demonstration. It uses ECS entities and the Phase 2c RenderWorld adapter, while OW Graphics owns the GLFW/OpenGL window, camera matrices, mesh uploads, textures, GLSL shaders and GPU cleanup. All composition modules are lifecycle-managed by flOW.

## What it does

- Opens a real OpenGL 3.3 desktop window (320 x 240) via OW Graphics.
- Uploads six-face indexed cube geometry (UV + face normals) and a 2×2 RGBA8 blue texture.
- Instantiates **two ECS cube entities** referencing one GPU mesh; both are submitted via `RenderWorld`.
- Enables depth testing, applies a perspective camera and textured diffuse GLSL shader.
- Creates a third ECS entity, the camera player. **W/A/S/D** moves the ECS camera player in X/Z; **Escape** closes the window.
- Ensures GPU resources are destroyed before the GLFW context.
- An automatic one-frame Mesa/Xvfb smoke reads actual `GL_BACK` framebuffer pixels from **two separated cubes** and the dark background.

The framebuffer reader is a **test-only** Java OW RuntimeExtension installed only in CI. It is not part of OW Graphics and never shipped to applications.

## Build and run (Linux, from OW Engine parent directory)

Clone `ow-lang`, `OW-Graphics` and `OW-Engine` as sibling directories. The `graphics` source dependency remains pinned in `OW-Engine/dependencies.yml`; source imports continue to resolve through OW's lockfile.

```sh
mvn -f ow-lang/pom.xml -DskipTests install
mvn -f OW-Graphics/native-bridge/pom.xml package dependency:copy-dependencies -DoutputDirectory=target/lib
cd OW-Engine
ow deps install
ow check examples/voxel_preview/main.ow
java -cp "../ow-lang/target/OwIDE.jar:../OW-Graphics/native-bridge/target/classes:../OW-Graphics/native-bridge/target/lib/*" dev.oreo.Main run examples/voxel_preview/main.ow
```

The bridge includes LWJGL/GLFW, so this requires a compatible desktop graphics driver. It is not run through bare `ow run` without native provider dependencies. For Windows, separate classpath items with semicolons and select the LWJGL natives-windows profile.

## Automated Mesa/Xvfb validation

CI builds OW Graphics' **existing** native provider and a tiny test-only framebuffer probe, then runs:

```sh
xvfb-run -a -s "-screen 0 800x600x24" env LIBGL_ALWAYS_SOFTWARE=1 \
  java -cp "../ow-lang/target/OwIDE.jar:../ow-graphics/native-bridge/target/classes:../ow-graphics/native-bridge/target/lib/*:target/probe-classes" \
  dev.oreo.Main run examples/voxel_preview/smoke.ow
```

The test verifies a real blue textured pixel on both left and right cubes, and empty dark background between them. This is end-to-end OW ECS → RenderWorld → OW Graphics → LWJGL → OpenGL → Mesa framebuffer validation.

## Boundaries and limitations

- This is a two-cube **preview**, not a voxel terrain generator or Minecraft clone.
- No physics, collision, chunk streaming or terrain persistence yet.
- Movement uses a fixed per-frame step rather than time-scaled input; frame-independent controller will come later.
- Shader uses straightforward diffuse shading and does not yet implement accurate inverse-transpose normal matrices for nonuniform scale.
- Headless tests keep exercising the same render-world contracts without OpenGL.
- Vulkan and OW native-AOT/FFI remain future work.
