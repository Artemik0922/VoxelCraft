package com.voxelgame.rendering;

import org.joml.*;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.nio.file.*;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class Shader {
    private int programId;
    private int vertexShaderId;
    private int fragmentShaderId;
    private final Map<String, Integer> uniforms = new HashMap<>();
    
    public Shader(String vertPath, String fragPath) {
        String vertSource = loadSource(vertPath);
        String fragSource = loadSource(fragPath);
        
        programId = glCreateProgram();
        if (programId == 0) {
            throw new RuntimeException("Failed to create shader program");
        }
        
        vertexShaderId = createShader(vertSource, GL_VERTEX_SHADER);
        fragmentShaderId = createShader(fragSource, GL_FRAGMENT_SHADER);
        
        glLinkProgram(programId);
        if (glGetProgrami(programId, GL_LINK_STATUS) == 0) {
            throw new RuntimeException("Failed to link shader program: " + glGetProgramInfoLog(programId));
        }
        
        // Note: glValidateProgram is deliberately not called here. It inspects
        // the *current* GL state, and at link time every sampler still defaults
        // to texture unit 0, which falsely reports a sampler type conflict.
        // Use validate() after the sampler uniforms have been assigned.
    }
    
    /** Validate against current GL state. Call only after uniforms are set. */
    public boolean validate() {
        glValidateProgram(programId);
        if (glGetProgrami(programId, GL_VALIDATE_STATUS) == 0) {
            System.err.println("Shader validation failed: " + glGetProgramInfoLog(programId));
            return false;
        }
        return true;
    }
    
    private String loadSource(String path) {
        // Try classpath first
        try (var is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is != null) {
                return new String(is.readAllBytes());
            }
        } catch (IOException e) {
            // Fall through to file system
        }
        
        // Fallback to file system
        try {
            return new String(Files.readAllBytes(Paths.get(path)));
        } catch (IOException e) {
            throw new RuntimeException("Failed to load shader: " + path, e);
        }
    }
    
    private int createShader(String source, int type) {
        int shaderId = glCreateShader(type);
        if (shaderId == 0) {
            throw new RuntimeException("Failed to create shader of type: " + type);
        }
        
        glShaderSource(shaderId, source);
        glCompileShader(shaderId);
        
        if (glGetShaderi(shaderId, GL_COMPILE_STATUS) == 0) {
            throw new RuntimeException("Failed to compile shader: " + glGetShaderInfoLog(shaderId));
        }
        
        glAttachShader(programId, shaderId);
        return shaderId;
    }
    
    public void bind() {
        glUseProgram(programId);
    }
    
    public void unbind() {
        glUseProgram(0);
    }
    
    public void cleanup() {
        unbind();
        if (programId != 0) {
            glDetachShader(programId, vertexShaderId);
            glDetachShader(programId, fragmentShaderId);
            glDeleteShader(vertexShaderId);
            glDeleteShader(fragmentShaderId);
            glDeleteProgram(programId);
        }
    }
    
    private int getUniformLocation(String name) {
        return uniforms.computeIfAbsent(name, n -> glGetUniformLocation(programId, n));
    }
    
    public void setUniform1i(String name, int value) {
        glUniform1i(getUniformLocation(name), value);
    }
    
    public void setUniform1iv(String name, int[] values) {
        glUniform1iv(getUniformLocation(name), values);
    }
    
    public void setUniform1f(String name, float value) {
        glUniform1f(getUniformLocation(name), value);
    }
    
    public void setUniform2f(String name, Vector2f vec) {
        glUniform2f(getUniformLocation(name), vec.x, vec.y);
    }
    
    public void setUniform3f(String name, Vector3f vec) {
        glUniform3f(getUniformLocation(name), vec.x, vec.y, vec.z);
    }
    
    public void setUniformMat4(String name, Matrix4f mat) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buf = stack.mallocFloat(16);
            mat.get(buf);
            glUniformMatrix4fv(getUniformLocation(name), false, buf);
        }
    }

    public void setUniform4f(String name, float x, float y, float z, float w) {
        glUniform4f(getUniformLocation(name), x, y, z, w);
    }

    public int getProgramId() { return programId; }
}
