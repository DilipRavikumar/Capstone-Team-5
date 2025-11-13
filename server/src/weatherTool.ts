import { z } from "zod";

export const weatherToolConfig = {
  description: "Get current weather for a location (uses Open-Meteo geocoding + current weather).",
  inputSchema: {
    location: z.string().describe("Location name, e.g. 'New York'")
  }
};

async function fetchJson(url: string): Promise<any> {
  if (typeof fetch !== "undefined") {
    const res = await fetch(url);
    if (!res.ok) throw new Error(`HTTP ${res.status} - ${res.statusText}`);
    return res.json();
  }

  // fallback for older Node versions
  return new Promise((resolve, reject) => {
    const https = require("https");
    https
      .get(url, (res: any) => {
        let data = "";
        res.on("data", (chunk: any) => (data += chunk));
        res.on("end", () => {
          try {
            resolve(JSON.parse(data));
          } catch (err) {
            reject(err);
          }
        });
      })
      .on("error", reject);
  });
}

const weatherCodes: Record<number, string> = {
  0: "Clear sky",
  1: "Mainly clear",
  2: "Partly cloudy",
  3: "Overcast",
  45: "Fog",
  48: "Depositing rime fog",
  51: "Light drizzle",
  53: "Moderate drizzle",
  55: "Dense drizzle",
  56: "Light freezing drizzle",
  57: "Dense freezing drizzle",
  61: "Slight rain",
  63: "Moderate rain",
  65: "Heavy rain",
  66: "Light freezing rain",
  67: "Heavy freezing rain",
  71: "Slight snow fall",
  73: "Moderate snow fall",
  75: "Heavy snow fall",
  77: "Snow grains",
  80: "Slight rain showers",
  81: "Moderate rain showers",
  82: "Violent rain showers",
  85: "Slight snow showers",
  86: "Heavy snow showers",
  95: "Thunderstorm",
  96: "Thunderstorm with slight hail",
  99: "Thunderstorm with heavy hail"
};

export async function weatherToolHandler({ location }: { location: string }) {
  try {
    const geoUrl = `https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(
      location
    )}&count=1`;
    const geo = await fetchJson(geoUrl);
    if (!geo || !geo.results || geo.results.length === 0) {
      return {
        content: [
          { type: "text" as const, text: `Location not found: ${location}` }
        ]
      };
    }

    const place = geo.results[0];
    const lat = place.latitude;
    const lon = place.longitude;
    const name = [place.name, place.admin1, place.country].filter(Boolean).join(", ");

    const weatherUrl = `https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lon}&current_weather=true&temperature_unit=celsius&windspeed_unit=kmh`;
    const w = await fetchJson(weatherUrl);
    const cw = w?.current_weather;
    if (!cw) {
      return {
        content: [
          { type: "text" as const, text: `No current weather available for ${name}` }
        ]
      };
    }

    const temp = cw.temperature;
    const wind = cw.windspeed;
    const code: number = cw.weathercode;
    const desc = weatherCodes[code] || `code ${code}`;

    const text = `${name} (${lat.toFixed(3)}, ${lon.toFixed(3)}): ${temp}°C, ${desc}. Wind ${wind} km/h.`;

    return {
      content: [{ type: "text" as const, text }]
    };
  } catch (err: any) {
    return {
      content: [{ type: "text" as const, text: `Error fetching weather: ${err.message}` }]
    };
  }
}
