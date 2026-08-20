#version 330 core

in vec3 Direction;

uniform vec3 horizonColor;
uniform vec3 zenithColor;
uniform vec3 sunDirection;
uniform vec3 sunColor;
uniform vec3 moonDirection;
uniform float daylight;
uniform float time;
uniform float overcast;
uniform float flash;

out vec4 FragColor;

// --- value noise ------------------------------------------------------

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

/** Stable pseudo-random for a star cell. */
float hash3(vec3 p) {
    return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453123);
}

void main() {
    vec3 dir = normalize(Direction);

    // Vertical gradient: bright near the horizon, deeper blue overhead
    float height = clamp(dir.y, -1.0, 1.0);
    float t = pow(clamp(height, 0.0, 1.0), 0.55);
    vec3 color = mix(horizonColor, zenithColor, t);

    // Slightly darker band below the horizon so the ground edge reads clearly
    if (height < 0.0) {
        color = mix(horizonColor, horizonColor * 0.72, clamp(-height * 2.0, 0.0, 1.0));
    }

    // --- stars -------------------------------------------------------
    // Only visible once the sky darkens, and never below the horizon.
    // Overcast clouds hide them entirely.
    float starVisibility = (1.0 - smoothstep(0.0, 0.45, daylight))
                         * smoothstep(-0.05, 0.20, dir.y)
                         * (1.0 - overcast);
    if (starVisibility > 0.01) {
        // Quantise the direction into cells and light a few of them
        vec3 cell = floor(dir * 190.0);
        float n = hash3(cell);
        if (n > 0.9965) {
            // Twinkle, each star with its own phase
            float twinkle = 0.65 + 0.35 * sin(time * 1.7 + n * 900.0);
            float brightness = (n - 0.9965) / 0.0035;
            color += vec3(0.95, 0.96, 1.0) * brightness * twinkle * starVisibility;
        }
    }

    // --- sun ---------------------------------------------------------
    float sunDot = max(dot(dir, normalize(-sunDirection)), 0.0);
    float disc = smoothstep(0.9975, 0.9990, sunDot);
    float glow = pow(sunDot, 220.0) * 0.55 + pow(sunDot, 12.0) * 0.10;
    color += sunColor * glow;

    // --- moon --------------------------------------------------------
    float moonDot = max(dot(dir, normalize(-moonDirection)), 0.0);
    float moonDisc = smoothstep(0.9986, 0.9994, moonDot);
    color += vec3(0.75, 0.80, 0.95) * pow(moonDot, 400.0) * 0.35;
    color = mix(color, vec3(0.92, 0.94, 1.0), moonDisc);

    // Sun disc always draws on top of the clouds (the cloud layer is drawn
    // separately, after the opaque world, in CloudLayer)

    // --- weather [WX] ----------------------------------------------
    // Overcast: dim the whole sky toward slate grey
    color = mix(color, color * 0.45 + vec3(0.18, 0.19, 0.22) * 0.55, overcast);
    // Lightning flash: brighten everything for a moment
    color += vec3(0.95, 0.97, 1.0) * flash * 0.9;

    FragColor = vec4(color, 1.0);
}