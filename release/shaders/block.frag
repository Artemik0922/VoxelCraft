#version 330 core

in vec2 TexCoord;
in vec3 Normal;
in vec3 FragPos;
in vec3 VertexColor;
in vec4 LightSpacePos;
in float AmbientOcclusion;
flat in int Layer;
in float Emissive;

uniform sampler2DArray blockTextures;
uniform sampler2DShadow shadowMap;
uniform vec3 cameraPos;
uniform vec3 sunDirection;
uniform float fogStart;
uniform float fogEnd;
uniform vec3 skyColor;
uniform vec3 sunColor;
uniform float shadowTexelSize;
uniform int shadowsEnabled;
/** 0 at midnight, 1 in full daylight. */
uniform float daylight;
uniform float waterTime;
/** Array layer holding the water tile, so it can be animated in place. */
uniform int waterLayer;

out vec4 FragColor;

/**
 * 3x3 PCF lookup. Returns 1.0 in full light, 0.0 in full shadow.
 */
float sampleShadow(vec3 normal) {
    if (shadowsEnabled == 0) return 1.0;

    vec3 proj = LightSpacePos.xyz / LightSpacePos.w;
    proj = proj * 0.5 + 0.5;

    // Outside the light frustum: treat as lit
    if (proj.z > 1.0 || proj.x < 0.0 || proj.x > 1.0 || proj.y < 0.0 || proj.y > 1.0) {
        return 1.0;
    }

    // Slope-scaled bias: grazing surfaces need a larger offset
    float cosTheta = clamp(dot(normal, -sunDirection), 0.0, 1.0);
    float bias = mix(0.0035, 0.0006, cosTheta);

    float sum = 0.0;
    for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
            vec2 offset = vec2(x, y) * shadowTexelSize;
            sum += texture(shadowMap, vec3(proj.xy + offset, proj.z - bias));
        }
    }
    return sum / 9.0;
}

void main() {
    // TexCoord spans 0..width on greedy-merged quads; the array texture uses
    // GL_REPEAT so the tile repeats per block instead of stretching.
    vec2 uv = TexCoord;

    // Water scrolls and ripples. Doing it here avoids re-meshing every frame
    // or uploading an animated texture.
    if (Layer == waterLayer) {
        uv += vec2(waterTime * 0.06, waterTime * 0.035);
        uv.x += sin((TexCoord.y + waterTime * 0.35) * 6.2831) * 0.012;
    }

    vec4 texColor = texture(blockTextures, vec3(uv, float(Layer)));

    // Cut out leaf gaps at the halfway point. Mip levels average alpha, which
    // would erode the cutouts with distance, so ease the threshold down as the
    // footprint grows. textureQueryLod needs GLSL 4.0, so the level is derived
    // from the UV derivatives instead.
    vec2 texels = TexCoord * float(textureSize(blockTextures, 0).x);
    float rho = max(length(dFdx(texels)), length(dFdy(texels)));
    float mip = clamp(log2(max(rho, 1.0)), 0.0, 4.0);
    float alphaCutoff = 0.5 * max(1.0 - 0.22 * mip, 0.30);
    if (texColor.a < alphaCutoff) discard;

    vec3 normal = normalize(Normal);

    // Faces pointing away from the sun are self-shadowed anyway
    float facingSun = step(0.001, dot(normal, -sunDirection));
    float shadow = mix(1.0, sampleShadow(normal), facingSun);

    // Flat ambient floor, applied ADDITIVELY so a face is never pure black
    // even when its baked light/AO term is zero (deep pits, cliff undersides).
    // At night it drops towards a dim blue-grey rather than to zero.
    float AMBIENT = mix(0.10, 0.40, daylight);

    // Shadows only remove direct sunlight; ambient always reaches the surface.
    // Direct light fades out entirely once the sun is down.
    float direct = 0.60 * shadow * facingSun * daylight;

    // VertexColor = biome tint * (smooth light * face shading). Recover the
    // scalar light term from the brightest channel so the tint hue itself
    // does not change how lit the surface reads.
    float baked = max(max(VertexColor.r, VertexColor.g), VertexColor.b);
    vec3 tint = (baked > 0.0001) ? VertexColor / baked : vec3(1.0);

    // Tint mask: opaque tiles encode alpha 254 for "never tint this texel",
    // which is how the dirt part of a grass side stays brown in every biome
    // while the grass cap above it takes the biome colour.
    if (texColor.a > 0.9 && texColor.a < 0.999) {
        tint = vec3(1.0);
    }

    float intensity = AMBIENT + (1.0 - AMBIENT) * baked * (0.4 + 0.6 * direct);

    // Ambient occlusion darkens creases but never the direct sunlight term,
    // so lit surfaces keep their contrast
    float occlusion = clamp(AmbientOcclusion, 0.0, 1.0);
    intensity *= mix(occlusion, 1.0, 0.35 * direct);

     // Night light keeps a cool cast; sunColor already carries the moon tint
     vec3 result = texColor.rgb * tint * intensity * sunColor;

     // Emissive blocks glow on their own — they are unaffected by shadows
     // and appear fully lit even at night
     if (Emissive > 0.5) {
         // Boost the color and add a glow based on the texture brightness
         vec3 glow = texColor.rgb * 1.8;
         result = mix(result, glow, Emissive * 0.7);
     }

     // Water surfaces sit slightly darker than the blocks around them, which
     // is what stops a lake reading as flat bright blue
     if (Layer == waterLayer) {
         result *= 0.875;
     }

    // Fade distant geometry into the skybox horizon
    float dist = length(cameraPos - FragPos);
    float fogFactor = clamp((dist - fogStart) / max(fogEnd - fogStart, 1.0), 0.0, 1.0);
    result = mix(result, skyColor, fogFactor);

    FragColor = vec4(result, texColor.a);
}
