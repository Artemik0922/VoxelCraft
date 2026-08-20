#version 330 core

in vec2 TexCoord;
in float Shade;

uniform sampler2D skin;
uniform float lightLevel;
uniform vec3 sunColor;
uniform float hurtFlash;

out vec4 FragColor;

void main() {
    vec4 texColor = texture(skin, TexCoord);
    if (texColor.a < 0.5) discard;
    vec3 result = texColor.rgb * Shade * lightLevel * sunColor;
    // [GP-038] Flash the mob red while it is hurt
    result = mix(result, vec3(1.0, 0.25, 0.2), hurtFlash);
    FragColor = vec4(result, texColor.a);
}
