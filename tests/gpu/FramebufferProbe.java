package org.owengine.test;

import dev.oreo.RuntimeExtension;
import java.nio.ByteBuffer;
import java.util.List;
import org.lwjgl.BufferUtils;
import static org.lwjgl.opengl.GL33.*;

/**
 * Test-only probe for OW Engine GPU smoke. It reads the OpenGL back buffer
 * while the OW graphics window is still open on the same owner thread.
 * Not installed in the production runtime classpath.
 */
public final class FramebufferProbe implements RuntimeExtension {
    @Override public boolean supports(String operation) {
        return "engine.test.pixel".equals(operation);
    }

    @Override public Object invoke(String operation, List<Object> args) {
        if (!supports(operation)) throw new IllegalArgumentException("Unsupported probe");
        if (args.size() != 2 || !(args.get(0) instanceof Integer x) || !(args.get(1) instanceof Integer y))
            throw new IllegalArgumentException("Expected two integer pixel coordinates");
        if (x < 0 || x >= 320 || y < 0 || y >= 240)
            throw new IllegalArgumentException("Pixel outside 320x240 test framebuffer");
        ByteBuffer rgba = BufferUtils.createByteBuffer(4);
        glFinish();
        glReadBuffer(GL_BACK);
        glReadPixels(x, y, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
        if (glGetError() != GL_NO_ERROR) throw new IllegalStateException("GL pixel read failed");
        return List.of(rgba.get(0) & 255, rgba.get(1) & 255, rgba.get(2) & 255);
    }
}
