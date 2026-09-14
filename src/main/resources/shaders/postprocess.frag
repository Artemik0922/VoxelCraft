#version 330 core

in vec2 vUV;
out vec4 fragColor;

uniform sampler2D uScene;
uniform sampler2D uBloom;
uniform float uBloomStrength;
uniform float uFxaaEnabled;
uniform vec2 uResolution;

// FXAA 3.11 style: luma-based edge direction detection over the 3x3
// neighbourhood, then a directional blend along the edge.
vec3 fxaaLuma(sampler2D tex, vec2 uv, vec2 res) {
    const float spanMax = 8.0;
    const float reduceMul = 1.0 / 8.0;

    vec3 rgbNW = texture(tex, uv + vec2(-1.0, -1.0) / res).rgb;
    vec3 rgbNE = texture(tex, uv + vec2( 1.0, -1.0) / res).rgb;
    vec3 rgbSW = texture(tex, uv + vec2(-1.0,  1.0) / res).rgb;
    vec3 rgbSE = texture(tex, uv + vec2( 1.0,  1.0) / res).rgb;
    vec3 rgbM  = texture(tex, uv).rgb;

    vec3 luma = vec3(0.299, 0.587, 0.114);
    float lumaNW = dot(rgbNW, luma);
    float lumaNE = dot(rgbNE, luma);
    float lumaSW = dot(rgbSW, luma);
    float lumaSE = dot(rgbSE, luma);
    float lumaM  = dot(rgbM,  luma);

    float lumaMin = min(lumaM, min(min(lumaNW, lumaNE), min(lumaSW, lumaSE)));
    float lumaMax = max(lumaM, max(max(lumaNW, lumaNE), max(lumaSW, lumaSE)));

    vec2 dir;
    dir.x = -((lumaNW + lumaNE) - (lumaSW + lumaSE));
    dir.y =  ((lumaNW + lumaSW) - (lumaNE + lumaSE));

    float dirReduce = max((lumaNW + lumaNE + lumaSW + lumaSE) * (0.25 * reduceMul), 1.0 / 128.0);
    float rcpDirMin = 1.0 / (min(abs(dir.x), abs(dir.y)) + dirReduce);

    dir = min(vec2(spanMax, spanMax),
        max(vec2(-spanMax, -spanMax), dir * rcpDirMin)) / res;

    vec3 rgbA = 0.5 * (texture(tex, uv + dir * (-1.0 / 6.0)).rgb
                     + texture(tex, uv + dir * ( 1.0 / 6.0)).rgb);
    vec3 rgbB = rgbA * 0.5 + 0.25 * (texture(tex, uv + dir * -0.5).rgb
                                   + texture(tex, uv + dir *  0.5).rgb);
    float lumaB = dot(rgbB, luma);
    return (lumaB < lumaMin || lumaB > lumaMax) ? rgbA : rgbB;
}

void main() {
    vec3 color = texture(uScene, vUV).rgb;
    if (uBloomStrength > 0.001) {
        color += texture(uBloom, vUV).rgb * uBloomStrength;
    }
    if (uFxaaEnabled > 0.5) {
        color = fxaaLuma(uScene, vUV, uResolution);
    }
    fragColor = vec4(color, 1.0);
}