#version 330 core

layout (location = 0) in vec3 aPos;
layout (location = 1) in vec2 aTexCoord;
layout (location = 2) in vec3 aNormal;
layout (location = 3) in vec3 aColor;
layout (location = 4) in float aLayer;
layout (location = 5) in float aAO;
layout (location = 6) in float aWave;
layout (location = 7) in float aEmissive;

uniform mat4 model;
uniform mat4 view;
uniform mat4 projection;
uniform mat4 lightSpace;
uniform float windTime;
uniform float windStrength;
/** World position of anything that should push plants aside. */
uniform vec3 trampleOrigin;
uniform float trampleRadius;

out vec2 TexCoord;
out vec3 Normal;
out vec3 FragPos;
out vec3 VertexColor;
out vec4 LightSpacePos;
// Default (smooth) interpolation spreads the per-vertex AO across the quad
out float AmbientOcclusion;
flat out int Layer;
out float Emissive;

void main() {
    vec4 world = model * vec4(aPos, 1.0);

    // ---- wind and trampling -----------------------------------------
    // aWave is 0 for solid geometry, so this costs a multiply and nothing
    // else for the vast majority of vertices.
    if (aWave > 0.001) {
        // Two out-of-phase waves keyed to world position, so neighbouring
        // plants never sway in lockstep
        float phase = world.x * 0.7 + world.z * 0.6;
        float sway = sin(windTime * 1.7 + phase)
                   + 0.4 * sin(windTime * 3.1 + phase * 1.9);

        // sway peaks at 1.4, so these stay within a 0.08 block amplitude
        world.x += sway * 0.040 * windStrength * aWave;
        world.z += sway * 0.030 * windStrength * aWave;

        // Bend away from the player. Purely horizontal: pushing vertices
        // down sank plants into the ground, since the player stands inside
        // the very grass being trampled.
        vec3 away = world.xyz - trampleOrigin;
        away.y = 0.0;
        float dist = length(away);

        if (dist < trampleRadius && dist > 0.0001) {
            float push = (1.0 - dist / trampleRadius);
            push = push * push * aWave;

            world.xz += normalize(away).xz * push * 0.22;
        }
    }

    gl_Position = projection * view * world;
    FragPos = world.xyz;
    LightSpacePos = lightSpace * world;
    TexCoord = aTexCoord;
    Normal = aNormal;
    VertexColor = aColor;
     AmbientOcclusion = aAO;
     Layer = int(aLayer + 0.5);
     Emissive = aEmissive;
 }
