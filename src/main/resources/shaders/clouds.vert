#version 330 core

layout (location = 0) in vec3 aPos;
layout (location = 1) in vec3 aColor;

uniform mat4 projection;
uniform mat4 view;
uniform vec3 cameraPos;
uniform vec2 drift;

out vec3 vColor;
out float vFade;

void main() {
    // Drift shifts the whole deck west (-X) between rebuilds; the pattern
    // itself re-keys at whole-cell steps, so the motion is seamless.
    vec3 pos = aPos + vec3(-drift.x, 0.0, -drift.y);

    gl_Position = projection * view * vec4(pos, 1.0);
    vColor = aColor;

    // Fade out well beyond the terrain fog so the grid edge never shows.
    // Inside the render distance the clouds stay fully opaque.
    float dist = length(vec2(pos.x - cameraPos.x, pos.z - cameraPos.z));
    vFade = 1.0 - smoothstep(320.0, 384.0, dist);
}