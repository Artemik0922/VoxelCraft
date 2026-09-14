#version 330 core

in vec2 vUV;
out vec4 fragColor;

uniform sampler2D uTex;
uniform vec2 uDirection;
uniform vec2 uTexel;
uniform int uFirstPass; // 1 = bright-pass threshold applied while sampling the scene

// 5-tap Gaussian (sigma ~ 1.0), weights sum to 1.0
const float weights[5] = float[](0.227027, 0.1945946, 0.1216216, 0.054054, 0.016216);

vec3 bright(vec3 c) {
    float lum = dot(c, vec3(0.299, 0.587, 0.114));
    return c * smoothstep(1.0, 1.35, lum);
}

void main() {
    vec2 step = uDirection * uTexel;
    vec3 sum = vec3(0.0);

    vec3 center = texture(uTex, vUV).rgb;
    if (uFirstPass == 1) center = bright(center);
    sum += center * weights[0];

    for (int i = 1; i < 5; i++) {
        float w = float(i);
        vec3 a = texture(uTex, vUV + step * w).rgb;
        vec3 b = texture(uTex, vUV - step * w).rgb;
        if (uFirstPass == 1) {
            a = bright(a);
            b = bright(b);
        }
        sum += (a + b) * weights[i];
    }

    fragColor = vec4(sum, 1.0);
}